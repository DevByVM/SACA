package com.saca.api.proteccion;

import com.saca.api.RelojAjustable;
import com.saca.api.proteccion.LimitadorSolicitudes.Politica;
import com.saca.api.proteccion.LimitadorSolicitudes.Reserva;
import com.saca.api.proteccion.LimitadorSolicitudes.Ventana;
import com.saca.api.proteccion.ResolutorIpCliente.ClienteSolicitud;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// Contador agregado por bloque IPv6 /48 además del de cada /64, con los límites por defecto
// (registro y reenvío 20 cada 10 minutos por /64, ×10 por bloque; ×10 y ×100 en redes compartidas; login 30
// por /64 y 300 por bloque). Un único contador por bloque, compartido si el bloque se solapa con un rango
// declarado, y en el login sin capacidad cuentan los contadores que ya existen
class LimitadorAgregadoTest {

    private static final Instant INICIO = Instant.parse("2026-11-04T00:00:00Z");

    // Solo importa el nombre: identifica los contadores al consultarlos
    private static final Politica REGISTRO = consulta("registro-reenvio");
    private static final Politica REGISTRO_BLOQUE = consulta("registro-reenvio/48");
    private static final Politica LOGIN = consulta("login-fallos");
    private static final Politica LOGIN_BLOQUE = consulta("login-fallos/48");

    private final RelojAjustable reloj = new RelojAjustable(INICIO, ZoneId.of("America/El_Salvador"));
    private final ResolutorIpCliente resolutor = new ResolutorIpCliente("", "2001:db8:5::/48");
    // Un /56 compartido dentro de los bloques 20, 21 y 24, y otro en el bloque 23 (que no se solapa con el 22)
    private final ResolutorIpCliente conRangoEnBloque = new ResolutorIpCliente("",
            "2001:db8:20:100::/56, 2001:db8:21:100::/56, 2001:db8:23:100::/56, 2001:db8:24:100::/56");

    private static Politica consulta(String nombre) {
        return new Politica(nombre, List.of(new Ventana(Duration.ofMinutes(1), 1)));
    }

    private ProteccionSolicitudes proteccion(int maxClaves) {
        return new ProteccionSolicitudes(reloj, maxClaves, 10, 30, 200, 20, 100, 30, 500);
    }

    private ClienteSolicitud cliente(String ip) {
        MockHttpServletRequest solicitud = new MockHttpServletRequest();
        solicitud.setRemoteAddr(ip);
        return resolutor.resolver(solicitud);
    }

    // Prefijo /64 número n dentro del bloque 2001:db8:<bloque>::/48
    private ClienteSolicitud ipv6(int bloque, int prefijo) {
        return ipv6(resolutor, bloque, prefijo);
    }

    private static ClienteSolicitud ipv6(ResolutorIpCliente resolutor, int bloque, int prefijo) {
        MockHttpServletRequest solicitud = new MockHttpServletRequest();
        solicitud.setRemoteAddr("2001:db8:" + Integer.toHexString(bloque) + ":" + Integer.toHexString(prefijo) + "::1");
        return resolutor.resolver(solicitud);
    }

    private static int admitidas(Runnable accion, int maximo) {
        int admitidas = 0;
        for (int i = 0; i < maximo; i++) {
            try {
                accion.run();
                admitidas++;
            } catch (LimiteSolicitudesExcedidoException e) {
                // rechazada
            }
        }
        return admitidas;
    }

    @Test
    void lasReservasSimultaneasNoSuperanNiElLimiteDelPrefijoNiElDelBloque() throws Exception {
        ProteccionSolicitudes protegida = proteccion(50_000);
        LimitadorSolicitudes registro = protegida.limitadorRegistro();
        LimitadorSolicitudes login = protegida.limitadorLogin();
        AtomicInteger registrosAdmitidos = new AtomicInteger();
        AtomicInteger loginsAdmitidos = new AtomicInteger();
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(16);
        try {
            List<Future<?>> tareas = new ArrayList<>();
            for (int h = 0; h < 16; h++) {
                tareas.add(hilos.submit(() -> {
                    salida.await();
                    ThreadLocalRandom azar = ThreadLocalRandom.current();
                    for (int i = 0; i < 150; i++) {
                        ClienteSolicitud cliente = ipv6(7, azar.nextInt(50));
                        try {
                            protegida.consumirRegistroOReenvio(cliente);
                            registrosAdmitidos.incrementAndGet();
                        } catch (LimiteSolicitudesExcedidoException e) {
                            // rechazada por alguno de los dos niveles
                        }
                        try {
                            protegida.reservarIntentoLogin(cliente);
                            loginsAdmitidos.incrementAndGet();
                        } catch (LimiteSolicitudesExcedidoException e) {
                            // rechazado por alguno de los dos niveles
                        }
                    }
                    return null;
                }));
            }
            salida.countDown();
            for (Future<?> tarea : tareas) {
                tarea.get(60, TimeUnit.SECONDS);
            }
        } finally {
            hilos.shutdownNow();
        }

        // 2 400 intentos repartidos en 50 prefijos (hasta 1 000 por los /64): el bloque los deja en 200 y 300
        assertThat(registrosAdmitidos.get()).isEqualTo(200);
        assertThat(loginsAdmitidos.get()).isEqualTo(300);
        assertThat(registro.solicitudesGuardadas(REGISTRO_BLOQUE, "ip6:2001:db8:7::/48")).isEqualTo(200);
        assertThat(login.solicitudesGuardadas(LOGIN_BLOQUE, "ip6:2001:db8:7::/48")).isEqualTo(300);
        int sumaPrefijos = 0;
        for (int p = 0; p < 50; p++) {
            int guardadas = registro.solicitudesGuardadas(REGISTRO, ipv6(7, p).clave());
            assertThat(guardadas).isLessThanOrEqualTo(20);
            sumaPrefijos += guardadas;
        }
        assertThat(sumaPrefijos).isEqualTo(200);
    }

    @Test
    void siRechazaElPrefijoNoCuentaEnElBloque() {
        ProteccionSolicitudes protegida = proteccion(50_000);
        LimitadorSolicitudes registro = protegida.limitadorRegistro();
        ClienteSolicitud lleno = ipv6(8, 1);

        assertThat(admitidas(() -> protegida.consumirRegistroOReenvio(lleno), 70)).isEqualTo(20);

        assertThat(registro.solicitudesGuardadas(REGISTRO, lleno.clave())).isEqualTo(20);
        assertThat(registro.solicitudesGuardadas(REGISTRO_BLOQUE, lleno.claveAgregada())).isEqualTo(20);
    }

    @Test
    void siRechazaElBloqueNoCuentaEnElPrefijoNiCreaClaves() {
        ProteccionSolicitudes protegida = proteccion(50_000);
        LimitadorSolicitudes registro = protegida.limitadorRegistro();
        for (int p = 0; p < 9; p++) {
            ClienteSolicitud cliente = ipv6(9, p);
            assertThat(admitidas(() -> protegida.consumirRegistroOReenvio(cliente), 20)).isEqualTo(20);
        }
        ClienteSolicitud conHueco = ipv6(9, 100);
        assertThat(admitidas(() -> protegida.consumirRegistroOReenvio(conHueco), 19)).isEqualTo(19);
        ClienteSolicitud ultimo = ipv6(9, 101);
        assertThat(admitidas(() -> protegida.consumirRegistroOReenvio(ultimo), 1)).isEqualTo(1);
        int claves = registro.numeroClaves();

        // El bloque está en 200: su prefijo con hueco y uno nuevo se rechazan sin contar ni crear nada
        assertThat(admitidas(() -> protegida.consumirRegistroOReenvio(conHueco), 10)).isZero();
        ClienteSolicitud nuevo = ipv6(9, 102);
        assertThat(admitidas(() -> protegida.consumirRegistroOReenvio(nuevo), 10)).isZero();

        assertThat(registro.solicitudesGuardadas(REGISTRO, conHueco.clave())).isEqualTo(19);
        assertThat(registro.solicitudesGuardadas(REGISTRO_BLOQUE, conHueco.claveAgregada())).isEqualTo(200);
        assertThat(registro.contiene(REGISTRO, nuevo.clave())).isFalse();
        assertThat(registro.numeroClaves()).isEqualTo(claves);
        // Otro bloque no se ve afectado
        assertThat(admitidas(() -> protegida.consumirRegistroOReenvio(ipv6(10, 1)), 20)).isEqualTo(20);
    }

    @Test
    void unLoginCorrectoDevuelveElFalloReservadoEnLosDosNiveles() {
        ProteccionSolicitudes protegida = proteccion(50_000);
        LimitadorSolicitudes login = protegida.limitadorLogin();
        ClienteSolicitud cliente = ipv6(11, 1);
        protegida.reservarIntentoLogin(cliente);

        Reserva correcto = protegida.reservarIntentoLogin(cliente);
        assertThat(login.solicitudesGuardadas(LOGIN, cliente.clave())).isEqualTo(2);
        assertThat(login.solicitudesGuardadas(LOGIN_BLOQUE, cliente.claveAgregada())).isEqualTo(2);

        protegida.loginCorrecto(correcto);
        protegida.loginCorrecto(correcto);

        assertThat(login.solicitudesGuardadas(LOGIN, cliente.clave())).isEqualTo(1);
        assertThat(login.solicitudesGuardadas(LOGIN_BLOQUE, cliente.claveAgregada())).isEqualTo(1);
    }

    @Test
    void enRedesCompartidasElPrefijoTieneDiezVecesYElBloqueCienVeces() {
        ProteccionSolicitudes protegida = proteccion(50_000);
        // 2001:db8:5::/48 está declarado compartido: 200 por /64 y 2 000 por bloque cada 10 minutos
        for (int p = 0; p < 10; p++) {
            ClienteSolicitud compartida = ipv6(5, p);
            assertThat(compartida.redCompartida()).isTrue();
            assertThat(admitidas(() -> protegida.consumirRegistroOReenvio(compartida), 250)).as("prefijo %d", p)
                    .isEqualTo(200);
        }
        assertThat(admitidas(() -> protegida.consumirRegistroOReenvio(ipv6(5, 10)), 10)).isZero();

        // Un bloque sin declarar: 20 por /64 y 200 por bloque
        for (int p = 0; p < 10; p++) {
            ClienteSolicitud normal = ipv6(6, p);
            assertThat(normal.redCompartida()).isFalse();
            assertThat(admitidas(() -> protegida.consumirRegistroOReenvio(normal), 25)).isEqualTo(20);
        }
        assertThat(admitidas(() -> protegida.consumirRegistroOReenvio(ipv6(6, 10)), 10)).isZero();
    }

    // Las claves de bloque cuentan en el mismo máximo (15 en registro con 50 en total): un cliente de un bloque
    // nuevo necesita dos y se rechaza si solo queda una, sin expulsar a nadie; uno de un bloque conocido cabe
    @Test
    void lasClavesDeBloqueCuentanEnElMaximoYNuncaSeExpulsanContadoresActivos() {
        ProteccionSolicitudes protegida = proteccion(50);
        LimitadorSolicitudes registro = protegida.limitadorRegistro();
        List<ClienteSolicitud> admitidos = new ArrayList<>();
        for (int b = 0; b < 7; b++) {
            ClienteSolicitud cliente = ipv6(0x100 + b, 1);
            protegida.consumirRegistroOReenvio(cliente);
            admitidos.add(cliente);
        }
        assertThat(registro.numeroClaves()).isEqualTo(14);

        reloj.fijar(INICIO.plus(Duration.ofHours(1)));
        LimiteSolicitudesExcedidoException rechazo = null;
        try {
            protegida.consumirRegistroOReenvio(ipv6(0x200, 1));
        } catch (LimiteSolicitudesExcedidoException e) {
            rechazo = e;
        }
        assertThat(rechazo).isNotNull();
        // Hasta que venza el primer registro: 24 horas desde que se creó
        assertThat(rechazo.segundosParaReintentar()).isEqualTo(Duration.ofHours(23).toSeconds());
        assertThat(registro.numeroClaves()).isEqualTo(14);
        for (ClienteSolicitud cliente : admitidos) {
            assertThat(registro.solicitudesGuardadas(REGISTRO, cliente.clave())).isEqualTo(1);
            assertThat(registro.solicitudesGuardadas(REGISTRO_BLOQUE, cliente.claveAgregada())).isEqualTo(1);
        }

        protegida.consumirRegistroOReenvio(ipv6(0x100, 2));
        assertThat(registro.numeroClaves()).isEqualTo(15);
        assertThat(admitidas(() -> protegida.consumirRegistroOReenvio(cliente("192.0.2.200")), 1)).isZero();
    }

    // ================= un único contador por bloque /48 con un /56 compartido dentro

    // Prefijos del /56 compartido (2001:db8:<bloque>:1xx::) y del resto del bloque
    private ClienteSolicitud compartido(int bloque, int n) {
        return ipv6(conRangoEnBloque, bloque, 0x100 + n);
    }

    private ClienteSolicitud fueraDelRango(int bloque, int n) {
        return ipv6(conRangoEnBloque, bloque, n);
    }

    @Test
    void dentroYFueraDelRangoCompartidoUsanElMismoContadorDeBloque() {
        ProteccionSolicitudes protegida = proteccion(50_000);
        LimitadorSolicitudes registro = protegida.limitadorRegistro();
        ClienteSolicitud dentro = compartido(0x20, 0);
        ClienteSolicitud fuera = fueraDelRango(0x20, 0);
        assertThat(dentro.redCompartida()).isTrue();
        assertThat(fuera.redCompartida()).isFalse();
        assertThat(dentro.claveAgregada()).isEqualTo("ip6:2001:db8:20::/48").isEqualTo(fuera.claveAgregada());

        // 10 prefijos compartidos llenan las 2 000 del bloque: el resto del bloque ya no tiene cuota propia
        for (int n = 0; n < 10; n++) {
            ClienteSolicitud cliente = compartido(0x20, n);
            assertThat(admitidas(() -> protegida.consumirRegistroOReenvio(cliente), 250)).as("compartido %d", n)
                    .isEqualTo(200);
        }
        assertThat(admitidas(() -> protegida.consumirRegistroOReenvio(fuera), 20)).isZero();
        assertThat(registro.solicitudesGuardadas(REGISTRO_BLOQUE, "ip6:2001:db8:20::/48")).isEqualTo(2_000);
        assertThat(registro.contiene(REGISTRO_BLOQUE, "ip6:2001:db8:20::/48#compartida")).isFalse();
    }

    // Fuera del rango, cada /64 conserva su límite de 20 pero el bloque tiene la cuota mayor (2 000) para todos;
    // entre las dos partes no pasan de 2 000
    @Test
    void elBloqueSolapadoTieneLaCuotaMayorUnaSolaVez() {
        ProteccionSolicitudes protegida = proteccion(50_000);
        int fuera = 0;
        for (int n = 0; n < 15; n++) {
            ClienteSolicitud cliente = fueraDelRango(0x21, n);
            assertThat(admitidas(() -> protegida.consumirRegistroOReenvio(cliente), 25)).isEqualTo(20);
            fuera += 20;
        }
        assertThat(fuera).isEqualTo(300);
        int dentro = 0;
        for (int n = 0; n < 12; n++) {
            ClienteSolicitud cliente = compartido(0x21, n);
            dentro += admitidas(() -> protegida.consumirRegistroOReenvio(cliente), 250);
        }
        assertThat(fuera + dentro).isEqualTo(2_000);

        // Un rango declarado en otro bloque no cambia nada: 200 entre todos sus /64
        int sinSolape = 0;
        for (int n = 0; n < 15; n++) {
            ClienteSolicitud cliente = fueraDelRango(0x22, n);
            assertThat(cliente.bloqueCompartido()).isFalse();
            sinSolape += admitidas(() -> protegida.consumirRegistroOReenvio(cliente), 25);
        }
        assertThat(sinSolape).isEqualTo(200);
        assertThat(compartido(0x23, 0).bloqueCompartido()).isTrue();
    }

    @Test
    void conSolicitudesSimultaneasDentroYFueraDelRangoElBloqueNoPasaDeSuCuota() throws Exception {
        ProteccionSolicitudes protegida = proteccion(50_000);
        LimitadorSolicitudes registro = protegida.limitadorRegistro();
        AtomicInteger admitidas = new AtomicInteger();
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(16);
        try {
            List<Future<?>> tareas = new ArrayList<>();
            for (int h = 0; h < 16; h++) {
                tareas.add(hilos.submit(() -> {
                    salida.await();
                    ThreadLocalRandom azar = ThreadLocalRandom.current();
                    for (int i = 0; i < 400; i++) {
                        int n = azar.nextInt(60);
                        ClienteSolicitud cliente = n < 20 ? compartido(0x24, n) : fueraDelRango(0x24, n);
                        try {
                            protegida.consumirRegistroOReenvio(cliente);
                            admitidas.incrementAndGet();
                        } catch (LimiteSolicitudesExcedidoException e) {
                            // rechazada por el /64 o por el bloque
                        }
                    }
                    return null;
                }));
            }
            salida.countDown();
            for (Future<?> tarea : tareas) {
                tarea.get(60, TimeUnit.SECONDS);
            }
        } finally {
            hilos.shutdownNow();
        }

        assertThat(admitidas.get()).isEqualTo(2_000);
        assertThat(registro.solicitudesGuardadas(REGISTRO_BLOQUE, "ip6:2001:db8:24::/48")).isEqualTo(2_000);
        int suma = 0;
        for (int n = 0; n < 60; n++) {
            ClienteSolicitud cliente = n < 20 ? compartido(0x24, n) : fueraDelRango(0x24, n);
            int guardadas = registro.solicitudesGuardadas(REGISTRO, cliente.clave());
            assertThat(guardadas).isLessThanOrEqualTo(n < 20 ? 200 : 20);
            suma += guardadas;
        }
        assertThat(suma).isEqualTo(2_000);
    }

    // ================= login sin capacidad: los contadores existentes siguen contando

    // Máximo de 10 claves: 5 de login. El cliente A ocupa su /64 y su /48, y tres IPv4 las otras tres
    private ProteccionSolicitudes loginLleno() {
        ProteccionSolicitudes protegida = proteccion(10);
        protegida.reservarIntentoLogin(ipv6(0x30, 1));
        for (int i = 1; i <= 3; i++) {
            protegida.reservarIntentoLogin(cliente("192.0.2." + i));
        }
        assertThat(protegida.limitadorLogin().numeroClaves()).isEqualTo(5);
        return protegida;
    }

    @Test
    void conElLoginSinCapacidadLosPrefijosNuevosCuentanEnSuBloqueHastaSuLimite() {
        ProteccionSolicitudes protegida = loginLleno();
        LimitadorSolicitudes login = protegida.limitadorLogin();

        int admitidos = 0;
        LimiteSolicitudesExcedidoException rechazo = null;
        for (int n = 2; n < 1_000 && rechazo == null; n++) {
            try {
                Reserva intento = protegida.reservarIntentoLogin(ipv6(0x30, n));
                assertThat(intento.resultado().permitido()).isTrue();
                admitidos++;
            } catch (LimiteSolicitudesExcedidoException e) {
                rechazo = e;
            }
        }

        // 300 por bloque, uno ya del cliente A
        assertThat(admitidos).isEqualTo(299);
        assertThat(rechazo).isNotNull();
        assertThat(login.solicitudesGuardadas(LOGIN_BLOQUE, "ip6:2001:db8:30::/48")).isEqualTo(300);
        assertThat(login.numeroClaves()).isEqualTo(5);
        assertThat(login.contiene(LOGIN, ipv6(0x30, 2).clave())).isFalse();
        assertThat(login.solicitudesGuardadas(LOGIN, ipv6(0x30, 1).clave())).isEqualTo(1);
    }

    // Sin ningún contador del cliente se mantiene L3: no cuenta, no hay 429 y no se crea nada
    @Test
    void conElLoginSinCapacidadUnBloqueDesconocidoConservaL3() {
        ProteccionSolicitudes protegida = loginLleno();
        LimitadorSolicitudes login = protegida.limitadorLogin();
        long usados = login.bytesUsados();

        for (int n = 0; n < 500; n++) {
            Reserva intento = protegida.reservarIntentoLogin(ipv6(0x31, n));
            assertThat(intento.resultado().sinCapacidad()).isTrue();
        }

        assertThat(login.numeroClaves()).isEqualTo(5);
        assertThat(login.bytesUsados()).isEqualTo(usados);
        assertThat(login.contiene(LOGIN_BLOQUE, "ip6:2001:db8:31::/48")).isFalse();
    }

    @Test
    void anularUnaReservaParcialSoloQuitaSuIncremento() {
        ProteccionSolicitudes protegida = loginLleno();
        LimitadorSolicitudes login = protegida.limitadorLogin();
        ClienteSolicitud conocido = ipv6(0x30, 1);

        Reserva parcial = protegida.reservarIntentoLogin(ipv6(0x30, 2));
        Reserva otra = protegida.reservarIntentoLogin(ipv6(0x30, 3));
        assertThat(login.solicitudesGuardadas(LOGIN_BLOQUE, conocido.claveAgregada())).isEqualTo(3);

        protegida.loginCorrecto(parcial);
        protegida.loginCorrecto(parcial);

        assertThat(login.solicitudesGuardadas(LOGIN_BLOQUE, conocido.claveAgregada())).isEqualTo(2);
        assertThat(login.solicitudesGuardadas(LOGIN, conocido.clave())).isEqualTo(1);
        protegida.loginCorrecto(otra);
        assertThat(login.solicitudesGuardadas(LOGIN_BLOQUE, conocido.claveAgregada())).isEqualTo(1);
    }

    // Prefijos existentes y nuevos del mismo bloque a la vez, con la tabla llena: el bloque no pasa de 300, el
    // /64 existente de 30 y no se crea ninguna clave
    @Test
    void conElLoginSinCapacidadLasReservasSimultaneasRespetanAmbosLimites() throws Exception {
        ProteccionSolicitudes protegida = loginLleno();
        LimitadorSolicitudes login = protegida.limitadorLogin();
        AtomicInteger admitidos = new AtomicInteger();
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(16);
        try {
            List<Future<?>> tareas = new ArrayList<>();
            for (int h = 0; h < 16; h++) {
                tareas.add(hilos.submit(() -> {
                    salida.await();
                    ThreadLocalRandom azar = ThreadLocalRandom.current();
                    for (int i = 0; i < 100; i++) {
                        ClienteSolicitud cliente = azar.nextInt(4) == 0 ? ipv6(0x30, 1) : ipv6(0x30, 2 + azar.nextInt(500));
                        try {
                            if (protegida.reservarIntentoLogin(cliente).resultado().permitido()) {
                                admitidos.incrementAndGet();
                            }
                        } catch (LimiteSolicitudesExcedidoException e) {
                            // límite del /64 o del bloque
                        }
                    }
                    return null;
                }));
            }
            salida.countDown();
            for (Future<?> tarea : tareas) {
                tarea.get(60, TimeUnit.SECONDS);
            }
        } finally {
            hilos.shutdownNow();
        }

        assertThat(admitidos.get()).isEqualTo(299);
        assertThat(login.solicitudesGuardadas(LOGIN_BLOQUE, "ip6:2001:db8:30::/48")).isEqualTo(300);
        assertThat(login.solicitudesGuardadas(LOGIN, ipv6(0x30, 1).clave())).isLessThanOrEqualTo(30);
        assertThat(login.numeroClaves()).isEqualTo(5);
    }

    // El registro no cambia: sin sitio para el /64 nuevo responde 429 y no cuenta en el bloque existente
    @Test
    void elRegistroSinCapacidadNoCuentaEnLosContadoresExistentes() {
        ProteccionSolicitudes protegida = proteccion(10);
        LimitadorSolicitudes registro = protegida.limitadorRegistro();
        protegida.consumirRegistroOReenvio(ipv6(0x40, 1));
        protegida.consumirRegistroOReenvio(cliente("192.0.2.50"));
        assertThat(registro.numeroClaves()).isEqualTo(3);

        assertThat(admitidas(() -> protegida.consumirRegistroOReenvio(ipv6(0x40, 2)), 10)).isZero();
        assertThat(registro.solicitudesGuardadas(REGISTRO_BLOQUE, "ip6:2001:db8:40::/48")).isEqualTo(1);
    }
}
