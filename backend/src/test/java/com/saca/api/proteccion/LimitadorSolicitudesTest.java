package com.saca.api.proteccion;

import com.saca.api.RelojAjustable;
import com.saca.api.proteccion.LimitadorSolicitudes.Politica;
import com.saca.api.proteccion.LimitadorSolicitudes.Reserva;
import com.saca.api.proteccion.LimitadorSolicitudes.Resultado;
import com.saca.api.proteccion.LimitadorSolicitudes.Ventana;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Ventanas deslizantes exactas, multiplicador, reservas, concurrencia y tamaño de los
// arreglos, con reloj ajustable y un presupuesto holgado. El presupuesto y el máximo de claves se prueban en
// LimitadorPresupuestoTest
class LimitadorSolicitudesTest {

    private static final Instant INICIO = Instant.parse("2026-11-04T00:00:00Z");

    private final RelojAjustable reloj = new RelojAjustable(INICIO, ZoneId.of("America/El_Salvador"));
    private final LimitadorSolicitudes limitador = conClaves(50_000);

    private LimitadorSolicitudes conClaves(int maxClaves) {
        return new LimitadorSolicitudes("prueba", reloj, maxClaves, 64L << 20);
    }

    private static Politica politica(Duration duracion, int limite) {
        return new Politica("prueba", List.of(new Ventana(duracion, limite)));
    }

    private int consumirHastaRechazo(Politica politica, String clave, int multiplicador) {
        int permitidas = 0;
        while (limitador.consumir(politica, clave, multiplicador).permitido()) {
            permitidas++;
            assertThat(permitidas).isLessThan(100_000);
        }
        return permitidas;
    }

    @Test
    void ventanaDeDiezMinutos() {
        Politica diezMinutos = politica(Duration.ofMinutes(10), 20);

        assertThat(consumirHastaRechazo(diezMinutos, "a", 1)).isEqualTo(20);

        Resultado rechazo = limitador.consumir(diezMinutos, "a", 1);
        assertThat(rechazo.permitido()).isFalse();
        assertThat(rechazo.reintentarEn()).isEqualTo(Duration.ofMinutes(10));

        reloj.fijar(INICIO.plus(Duration.ofMinutes(10)).minusMillis(1));
        assertThat(limitador.consumir(diezMinutos, "a", 1).permitido()).isFalse();
        reloj.fijar(INICIO.plus(Duration.ofMinutes(10)));
        assertThat(consumirHastaRechazo(diezMinutos, "a", 1)).isEqualTo(20);
    }

    @Test
    void ventanaDeVeinticuatroHoras() {
        Politica dia = politica(Duration.ofHours(24), 100);

        assertThat(consumirHastaRechazo(dia, "a", 1)).isEqualTo(100);

        reloj.fijar(INICIO.plus(Duration.ofHours(23)));
        assertThat(limitador.consumir(dia, "a", 1).permitido()).isFalse();
        reloj.fijar(INICIO.plus(Duration.ofHours(24)));
        assertThat(limitador.consumir(dia, "a", 1).permitido()).isTrue();
    }

    @Test
    void ventanaDeUnMinuto() {
        Politica minuto = politica(Duration.ofMinutes(1), 30);

        assertThat(consumirHastaRechazo(minuto, "a", 1)).isEqualTo(30);

        reloj.fijar(INICIO.plusSeconds(59));
        assertThat(limitador.consumir(minuto, "a", 1).permitido()).isFalse();
        reloj.fijar(INICIO.plusSeconds(60));
        assertThat(consumirHastaRechazo(minuto, "a", 1)).isEqualTo(30);
    }

    @Test
    void laVentanaSeDeslizaYLiberaSoloLasSolicitudesQueSalen() {
        Politica diezMinutos = politica(Duration.ofMinutes(10), 20);
        for (int i = 0; i < 12; i++) {
            limitador.consumir(diezMinutos, "a", 1);
        }
        reloj.fijar(INICIO.plus(Duration.ofMinutes(5)));
        assertThat(consumirHastaRechazo(diezMinutos, "a", 1)).isEqualTo(8);

        // A los 10 minutos salen las 12 primeras; las 8 de los 5 minutos siguen contando
        Resultado rechazo = limitador.consumir(diezMinutos, "a", 1);
        assertThat(rechazo.reintentarEn()).isEqualTo(Duration.ofMinutes(5));
        reloj.fijar(INICIO.plus(Duration.ofMinutes(10)));
        assertThat(consumirHastaRechazo(diezMinutos, "a", 1)).isEqualTo(12);
    }

    @Test
    void conVariasVentanasUnRechazoNoConsumeEnNinguna() {
        Politica doble = new Politica("doble", List.of(
                new Ventana(Duration.ofMinutes(10), 20), new Ventana(Duration.ofHours(24), 30)));

        assertThat(consumirHastaRechazo(doble, "a", 1)).isEqualTo(20);
        reloj.fijar(INICIO.plus(Duration.ofMinutes(10)));
        assertThat(consumirHastaRechazo(doble, "a", 1)).isEqualTo(10);

        // Bloquea la ventana diaria; la de 10 minutos tiene cupo, pero los rechazos no deben consumirlo
        for (int i = 0; i < 50; i++) {
            Resultado rechazo = limitador.consumir(doble, "a", 1);
            assertThat(rechazo.permitido()).isFalse();
            // Faltan 24 h menos 10 min para que salgan las 20 solicitudes del inicio
            assertThat(rechazo.reintentarEn()).isEqualTo(Duration.ofHours(24).minusMinutes(10));
        }
        // A las 24 h salen las 20 iniciales y quedan 10 en la diaria: caben 20 (lo que deja la de 10 minutos).
        // Si los 50 rechazos hubieran contado en la ventana diaria, seguiría llena
        reloj.fijar(INICIO.plus(Duration.ofHours(24)));
        assertThat(consumirHastaRechazo(doble, "a", 1)).isEqualTo(20);
    }

    // Instantes que no coinciden con ninguna división de la ventana: cada solicitud cuenta exactamente su
    // duración completa
    @Test
    void cadaSolicitudCuentaLaDuracionCompletaDeLaVentana() {
        Politica diezMinutos = politica(Duration.ofMinutes(10), 1);
        Instant solicitud = INICIO.plusMillis(19_999);
        reloj.fijar(solicitud);
        assertThat(limitador.consumir(diezMinutos, "a", 1).permitido()).isTrue();

        reloj.fijar(solicitud.plus(Duration.ofMinutes(10)).minusMillis(1));
        Resultado rechazo = limitador.consumir(diezMinutos, "a", 1);
        assertThat(rechazo.permitido()).isFalse();
        assertThat(rechazo.reintentarEn()).isEqualTo(Duration.ofMillis(1));

        reloj.fijar(solicitud.plus(Duration.ofMinutes(10)));
        assertThat(limitador.consumir(diezMinutos, "a", 1).permitido()).isTrue();
    }

    @Test
    void laVentanaDiariaNoCaducaAntesDeVeinticuatroHoras() {
        Politica dia = politica(Duration.ofHours(24), 2);
        Instant primera = INICIO.plus(Duration.ofMinutes(47)).plusSeconds(59);
        Instant segunda = primera.plus(Duration.ofHours(3)).plusMillis(321);
        reloj.fijar(primera);
        limitador.consumir(dia, "a", 1);
        reloj.fijar(segunda);
        limitador.consumir(dia, "a", 1);

        reloj.fijar(primera.plus(Duration.ofHours(24)).minusMillis(1));
        assertThat(limitador.consumir(dia, "a", 1).permitido()).isFalse();
        reloj.fijar(primera.plus(Duration.ofHours(24)));
        assertThat(limitador.consumir(dia, "a", 1).permitido()).isTrue();

        // Ahora están la segunda y la tercera: se libera cuando salga la segunda, ni un milisegundo antes
        Resultado rechazo = limitador.consumir(dia, "a", 1);
        assertThat(rechazo.reintentarEn()).isEqualTo(Duration.between(primera, segunda));
        reloj.fijar(segunda.plus(Duration.ofHours(24)).minusMillis(1));
        assertThat(limitador.consumir(dia, "a", 1).permitido()).isFalse();
        reloj.fijar(segunda.plus(Duration.ofHours(24)));
        assertThat(limitador.consumir(dia, "a", 1).permitido()).isTrue();
    }

    @Test
    void siElRelojRetrocedeNingunaSolicitudDejaDeContarAntes() {
        Politica diezMinutos = politica(Duration.ofMinutes(10), 2);
        reloj.fijar(INICIO.plus(Duration.ofMinutes(5)));
        limitador.consumir(diezMinutos, "a", 1);
        reloj.fijar(INICIO);
        limitador.consumir(diezMinutos, "a", 1);

        // La segunda se guarda con el instante de la primera, posterior: cuenta hasta los 15 minutos
        reloj.fijar(INICIO.plus(Duration.ofMinutes(15)).minusMillis(1));
        assertThat(limitador.consumir(diezMinutos, "a", 1).permitido()).isFalse();
        reloj.fijar(INICIO.plus(Duration.ofMinutes(15)));
        assertThat(consumirHastaRechazo(diezMinutos, "a", 1)).isEqualTo(2);
    }

    @Test
    void unaReservaAnuladaDejaDeContarYSoloSeAnulaUnaVez() {
        Politica diezMinutos = politica(Duration.ofMinutes(10), 2);
        Reserva primera = limitador.reservar(diezMinutos, "a", 1);
        Reserva segunda = limitador.reservar(diezMinutos, "a", 1);
        assertThat(primera.resultado().permitido()).isTrue();
        assertThat(segunda.resultado().permitido()).isTrue();

        Reserva rechazada = limitador.reservar(diezMinutos, "a", 1);
        assertThat(rechazada.resultado().permitido()).isFalse();
        // Anular un rechazo no libera nada
        rechazada.anular();
        assertThat(limitador.consumir(diezMinutos, "a", 1).permitido()).isFalse();

        primera.anular();
        primera.anular();
        assertThat(consumirHastaRechazo(diezMinutos, "a", 1)).isEqualTo(1);
    }

    @Test
    void anularUnaReservaNoAdelantaLaSalidaDeLasDemas() {
        Politica diezMinutos = politica(Duration.ofMinutes(10), 2);
        limitador.consumir(diezMinutos, "a", 1);
        reloj.fijar(INICIO.plus(Duration.ofMinutes(4)));
        Reserva intermedia = limitador.reservar(diezMinutos, "a", 1);
        reloj.fijar(INICIO.plus(Duration.ofMinutes(6)));
        intermedia.anular();
        limitador.consumir(diezMinutos, "a", 1);

        // Siguen la del inicio y la de los 6 minutos: se libera cupo a los 10 minutos del inicio
        Resultado rechazo = limitador.consumir(diezMinutos, "a", 1);
        assertThat(rechazo.permitido()).isFalse();
        assertThat(rechazo.reintentarEn()).isEqualTo(Duration.ofMinutes(4));
    }

    @Test
    void elMultiplicadorAmpliaElLimiteSinEliminarlo() {
        Politica diezMinutos = politica(Duration.ofMinutes(10), 20);

        assertThat(consumirHastaRechazo(diezMinutos, "compartida", 10)).isEqualTo(200);
        assertThat(consumirHastaRechazo(diezMinutos, "normal", 1)).isEqualTo(20);
    }

    @Test
    void lasSolicitudesSimultaneasNoSuperanElLimite() throws Exception {
        Politica diezMinutos = politica(Duration.ofMinutes(10), 50);
        AtomicInteger permitidas = new AtomicInteger();
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> tareas = new ArrayList<>();
            for (int h = 0; h < 8; h++) {
                tareas.add(hilos.submit(() -> {
                    salida.await();
                    for (int i = 0; i < 100; i++) {
                        if (limitador.consumir(diezMinutos, "a", 1).permitido()) {
                            permitidas.incrementAndGet();
                        }
                    }
                    return null;
                }));
            }
            salida.countDown();
            for (Future<?> tarea : tareas) {
                tarea.get(30, TimeUnit.SECONDS);
            }
        } finally {
            hilos.shutdownNow();
        }

        assertThat(permitidas.get()).isEqualTo(50);
    }

    // Ninguna reserva se anula mientras otras siguen en curso (como comparaciones BCrypt simultáneas): las
    // admitidas nunca superan el límite. Las que se anulan después devuelven su cupo
    @Test
    void lasReservasSimultaneasNoSuperanElLimiteYLasAnuladasDevuelvenSuCupo() throws Exception {
        Politica diezMinutos = politica(Duration.ofMinutes(10), 30);
        ConcurrentLinkedQueue<Reserva> admitidas = new ConcurrentLinkedQueue<>();
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(16);
        try {
            List<Future<?>> tareas = new ArrayList<>();
            for (int h = 0; h < 200; h++) {
                tareas.add(hilos.submit(() -> {
                    salida.await();
                    Reserva reserva = limitador.reservar(diezMinutos, "a", 1);
                    if (reserva.resultado().permitido()) {
                        admitidas.add(reserva);
                    }
                    return null;
                }));
            }
            salida.countDown();
            for (Future<?> tarea : tareas) {
                tarea.get(30, TimeUnit.SECONDS);
            }
        } finally {
            hilos.shutdownNow();
        }
        assertThat(admitidas).hasSize(30);

        // Diez resultan correctas: su cupo vuelve, el de las otras veinte no
        admitidas.stream().limit(10).forEach(Reserva::anular);
        assertThat(consumirHastaRechazo(diezMinutos, "a", 1)).isEqualTo(10);
    }

    @Test
    void rechazaConfiguracionesNoValidas() {
        assertThatThrownBy(() -> new Ventana(Duration.ofMinutes(10), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Ventana(Duration.ZERO, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Ventana(Duration.ofDays(8), 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> conClaves(0)).isInstanceOf(IllegalArgumentException.class);
        // El presupuesto debe cubrir al menos la tabla del mapa y la reserva de crecimiento
        assertThatThrownBy(() -> new LimitadorSolicitudes("prueba", reloj, 50_000, 64L << 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ================= cota de memoria: capacidad real de los arreglos, sin medir el heap

    // Como el login: 30 en 10 minutos y 200 en 24 horas
    private static final Politica LOGIN = new Politica("login", List.of(
            new Ventana(Duration.ofMinutes(10), 30), new Ventana(Duration.ofHours(24), 200)));

    // Llena la ventana diaria avanzando 10 minutos cada vez que se agota la corta
    private static void saturarDia(LimitadorSolicitudes limitador, RelojAjustable reloj, String clave, int multiplicador) {
        for (int bloque = 0; bloque < 10; bloque++) {
            while (limitador.consumir(LOGIN, clave, multiplicador).permitido()) {
                // consume hasta el límite de 10 minutos
            }
            reloj.avanzar(Duration.ofMinutes(10));
        }
    }

    @Test
    void elArregloDeUnaClaveNoSuperaSuLimiteEfectivo() {
        LimitadorSolicitudes normal = conClaves(10);
        saturarDia(normal, reloj, "normal", 1);
        for (int i = 0; i < 1_000; i++) {
            assertThat(normal.consumir(LOGIN, "normal", 1).permitido()).isFalse();
        }
        assertThat(normal.mayorCapacidad()).isEqualTo(200);

        reloj.fijar(INICIO);
        LimitadorSolicitudes compartida = conClaves(10);
        saturarDia(compartida, reloj, "compartida", 10);
        assertThat(compartida.consumir(LOGIN, "compartida", 10).permitido()).isFalse();
        // 2 000 exactos, no la potencia de dos siguiente (2 048)
        assertThat(compartida.mayorCapacidad()).isEqualTo(2_000);
    }

    @Test
    void unLimiteMenorQueLaCapacidadInicialNoReservaDeMas() {
        assertThat(limitador.consumir(politica(Duration.ofMinutes(10), 1), "a", 1).permitido()).isTrue();
        assertThat(limitador.mayorCapacidad()).isEqualTo(1);
    }

    @Test
    void elArregloSeReduceCuandoSalenLasSolicitudes() {
        Politica dia = politica(Duration.ofHours(24), 200);
        for (int i = 0; i < 199; i++) {
            limitador.consumir(dia, "a", 1);
        }
        reloj.fijar(INICIO.plus(Duration.ofHours(1)));
        limitador.consumir(dia, "a", 1);
        assertThat(limitador.mayorCapacidad()).isEqualTo(200);

        // Queda una vigente: el arreglo deja de ocupar 200
        reloj.fijar(INICIO.plus(Duration.ofHours(24)));
        limitador.consumir(dia, "a", 1);
        assertThat(limitador.mayorCapacidad()).isLessThanOrEqualTo(100);

        // Sin ninguna vigente vuelve a la capacidad inicial
        reloj.fijar(INICIO.plus(Duration.ofHours(50)));
        limitador.consumir(dia, "a", 1);
        assertThat(limitador.mayorCapacidad()).isEqualTo(LimitadorSolicitudes.CAPACIDAD_INICIAL);
    }

    // Peor caso: todas las claves llenas con el multiplicador ×10. La capacidad total es exactamente
    // claves × 2 000; las claves que llegan con la tabla llena se rechazan y no ocupan nada
    @Test
    void elPeorCasoEsMaxClavesPorLaCapacidadMaxima() {
        LimitadorSolicitudes acotado = conClaves(20);
        for (int i = 0; i < 30; i++) {
            reloj.fijar(INICIO);
            saturarDia(acotado, reloj, "compartida-" + i, 10);
            assertThat(acotado.capacidadTotal()).isLessThanOrEqualTo(20L * 2_000);
        }
        assertThat(acotado.numeroClaves()).isEqualTo(20);
        assertThat(acotado.capacidadTotal()).isEqualTo(20L * 2_000);
    }

    // Reservas, anulaciones y claves nuevas desde varios hilos mientras el reloj avanza: ningún arreglo
    // supera su capacidad ni la tabla su máximo (agregar falla si se rompe la cota)
    @Test
    void laCotaSeMantieneConSolicitudesSimultaneas() throws Exception {
        LimitadorSolicitudes acotado = conClaves(64);
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(9);
        try {
            List<Future<?>> tareas = new ArrayList<>();
            for (int h = 0; h < 8; h++) {
                tareas.add(hilos.submit(() -> {
                    salida.await();
                    ThreadLocalRandom azar = ThreadLocalRandom.current();
                    for (int i = 0; i < 20_000; i++) {
                        int cliente = azar.nextInt(200);
                        int multiplicador = cliente % 4 == 0 ? 10 : 1;
                        Reserva reserva = acotado.reservar(LOGIN, "c" + cliente, multiplicador);
                        if (azar.nextBoolean()) {
                            reserva.anular();
                        }
                    }
                    return null;
                }));
            }
            tareas.add(hilos.submit(() -> {
                salida.await();
                for (int i = 0; i < 2_000; i++) {
                    reloj.avanzar(Duration.ofSeconds(30));
                    Thread.yield();
                }
                return null;
            }));
            salida.countDown();
            for (Future<?> tarea : tareas) {
                tarea.get(60, TimeUnit.SECONDS);
            }
        } finally {
            hilos.shutdownNow();
        }

        assertThat(acotado.numeroClaves()).isLessThanOrEqualTo(64);
        assertThat(acotado.mayorCapacidad()).isLessThanOrEqualTo(2_000);
        assertThat(acotado.capacidadTotal()).isLessThanOrEqualTo(64L * 2_000);
        assertThat(acotado.bytesUsados()).isEqualTo(acotado.bytesRecalculados());
        assertThat(acotado.indiceCoherente()).isTrue();
    }

}
