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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// Presupuesto de memoria y máximo de claves. Los costes se miden con la propia contabilidad
// del limitador (no con el heap), y los presupuestos se eligen a partir de ellos para provocar cada caso
class LimitadorPresupuestoTest {

    private static final Instant INICIO = Instant.parse("2026-11-04T00:00:00Z");
    private static final long HOLGADO = 1L << 20;

    private static final Politica DIEZ_MINUTOS = new Politica("diez", List.of(new Ventana(Duration.ofMinutes(10), 30)));
    private static final Politica AMPLIA = new Politica("amplia", List.of(new Ventana(Duration.ofMinutes(10), 1_000)));

    private final RelojAjustable reloj = new RelojAjustable(INICIO, ZoneId.of("America/El_Salvador"));

    private LimitadorSolicitudes limitador(int maxClaves, long presupuesto) {
        return new LimitadorSolicitudes("prueba", reloj, maxClaves, presupuesto);
    }

    private static long umbral(long presupuesto) {
        return presupuesto - presupuesto * LimitadorSolicitudes.RESERVA_CRECIMIENTO_PORCENTAJE / 100;
    }

    // Presupuesto cuyo umbral para claves nuevas queda en [desde, hasta)
    private static long presupuestoConUmbralEntre(long desde, long hasta) {
        for (long presupuesto = desde; presupuesto < 2 * hasta; presupuesto++) {
            long umbral = umbral(presupuesto);
            if (umbral >= desde && umbral < hasta) {
                return presupuesto;
            }
        }
        throw new AssertionError("No hay presupuesto con umbral entre " + desde + " y " + hasta);
    }

    private long bytesFijos(int maxClaves) {
        return limitador(maxClaves, HOLGADO).bytesUsados();
    }

    // Coste contable de una clave recién creada (con su arreglo inicial)
    private long costeClave(int maxClaves, Politica politica, String clave) {
        LimitadorSolicitudes sonda = limitador(maxClaves, HOLGADO);
        long antes = sonda.bytesUsados();
        sonda.consumir(politica, clave, 1);
        return sonda.bytesUsados() - antes;
    }

    private static void assertCoherente(LimitadorSolicitudes limitador) {
        assertThat(limitador.bytesUsados()).isEqualTo(limitador.bytesRecalculados());
        assertThat(limitador.bytesUsados()).isLessThanOrEqualTo(limitador.presupuesto());
        assertThat(limitador.indiceCoherente()).isTrue();
    }

    private static String ip(int i) {
        return String.format("ip4:10.0.%03d.%03d", i / 256, i % 256);
    }

    // ================= contabilidad

    // Cubre la tabla del mapa y, por clave, nodos del mapa y del índice, clave, registro y arreglo completo
    @Test
    void laContabilidadIncluyeTodasLasEstructuras() {
        LimitadorSolicitudes vacio = limitador(25_000, 32L << 20);
        // 25 000 claves: tabla de 65 536 cubetas de 8 bytes, más los objetos del mapa y del índice
        assertThat(vacio.bytesUsados()).isEqualTo(LimitadorSolicitudes.BYTES_ESTRUCTURAS + 65_536L * 8);

        // Clave de 7 caracteres ("diez|ip") con arreglo inicial de 4: 48 + 64 + 96 + (32 + 40) + 24 + 16
        assertThat(costeClave(10, DIEZ_MINUTOS, "ip")).isEqualTo(320);
    }

    @Test
    void laContabilidadCoincideConLoQueGuardaTrasCadaOperacion() {
        LimitadorSolicitudes acotado = limitador(40, 16L << 10);
        List<Reserva> reservas = new ArrayList<>();
        ThreadLocalRandom azar = ThreadLocalRandom.current();
        for (int i = 0; i < 5_000; i++) {
            int cliente = azar.nextInt(60);
            Reserva reserva = acotado.reservar(AMPLIA, ip(cliente), cliente % 3 == 0 ? 10 : 1);
            if (reserva.resultado().permitido()) {
                reservas.add(reserva);
            }
            if (azar.nextInt(4) == 0 && !reservas.isEmpty()) {
                reservas.remove(azar.nextInt(reservas.size())).anular();
            }
            if (azar.nextInt(50) == 0) {
                reloj.avanzar(Duration.ofSeconds(azar.nextInt(1, 400)));
            }
            assertCoherente(acotado);
        }
    }

    @Test
    void unRechazoPorLimiteNoAsignaNada() {
        LimitadorSolicitudes acotado = limitador(10, HOLGADO);
        for (int i = 0; i < 30; i++) {
            acotado.consumir(DIEZ_MINUTOS, "a", 1);
        }
        long usados = acotado.bytesUsados();

        for (int i = 0; i < 1_000; i++) {
            Resultado rechazo = acotado.consumir(DIEZ_MINUTOS, "a", 1);
            assertThat(rechazo.permitido()).isFalse();
            assertThat(rechazo.sinCapacidad()).isFalse();
        }

        assertThat(acotado.bytesUsados()).isEqualTo(usados);
        assertThat(acotado.numeroClaves()).isEqualTo(1);
    }

    // ================= sin capacidad

    @Test
    void conLaTablaLlenaUnaClaveNuevaSeRechazaSinCrearseNiExpulsarANadie() {
        LimitadorSolicitudes acotado = limitador(3, HOLGADO);
        for (int i = 0; i < 3; i++) {
            assertThat(acotado.consumir(DIEZ_MINUTOS, ip(i), 1).permitido()).isTrue();
        }
        long usados = acotado.bytesUsados();

        for (int i = 3; i < 1_000; i++) {
            Resultado rechazo = acotado.consumir(DIEZ_MINUTOS, ip(i), 1);
            assertThat(rechazo.permitido()).isFalse();
            assertThat(rechazo.sinCapacidad()).isTrue();
        }

        assertThat(acotado.numeroClaves()).isEqualTo(3);
        assertThat(acotado.bytesUsados()).isEqualTo(usados);
        for (int i = 0; i < 3; i++) {
            assertThat(acotado.contiene(DIEZ_MINUTOS, ip(i))).isTrue();
        }
        assertCoherente(acotado);
    }

    @Test
    void sinCapacidadLosContadoresActivosSiguenIntactos() {
        LimitadorSolicitudes acotado = limitador(5, HOLGADO);
        // La última llega a su límite de 30
        int[] solicitudes = {1, 8, 15, 22, 30};
        for (int i = 0; i < 5; i++) {
            reloj.fijar(INICIO.plusSeconds(37L * i));
            for (int j = 0; j < solicitudes[i]; j++) {
                acotado.consumir(DIEZ_MINUTOS, ip(i), 1);
            }
        }
        long[] vencimientos = new long[5];
        int[] guardadas = new int[5];
        for (int i = 0; i < 5; i++) {
            vencimientos[i] = acotado.venceEn(DIEZ_MINUTOS, ip(i));
            guardadas[i] = acotado.solicitudesGuardadas(DIEZ_MINUTOS, ip(i));
        }
        Duration esperaLleno = acotado.consumir(DIEZ_MINUTOS, ip(4), 1).reintentarEn();

        for (int i = 5; i < 2_000; i++) {
            assertThat(acotado.consumir(DIEZ_MINUTOS, ip(i), 1).sinCapacidad()).isTrue();
        }

        for (int i = 0; i < 5; i++) {
            assertThat(acotado.venceEn(DIEZ_MINUTOS, ip(i))).isEqualTo(vencimientos[i]);
            assertThat(acotado.solicitudesGuardadas(DIEZ_MINUTOS, ip(i))).isEqualTo(guardadas[i]);
        }
        assertThat(acotado.consumir(DIEZ_MINUTOS, ip(4), 1).reintentarEn()).isEqualTo(esperaLleno);
    }

    // ================= recuperación y tiempo de reintento

    @Test
    void unRegistroSeRetiraJustoCuandoVenceSuUltimaSolicitudYDevuelveSuMemoria() {
        LimitadorSolicitudes acotado = limitador(10, HOLGADO);
        long vacio = acotado.bytesUsados();
        acotado.consumir(DIEZ_MINUTOS, "a", 1);
        reloj.fijar(INICIO.plus(Duration.ofMinutes(4)));
        acotado.consumir(DIEZ_MINUTOS, "a", 1);
        long conA = acotado.bytesUsados();
        assertThat(acotado.venceEn(DIEZ_MINUTOS, "a")).isEqualTo(INICIO.plus(Duration.ofMinutes(14)).toEpochMilli());

        reloj.fijar(INICIO.plus(Duration.ofMinutes(14)).minusMillis(1));
        acotado.consumir(DIEZ_MINUTOS, "b", 1);
        assertThat(acotado.contiene(DIEZ_MINUTOS, "a")).isTrue();
        long conAyB = acotado.bytesUsados();

        reloj.fijar(INICIO.plus(Duration.ofMinutes(14)));
        acotado.consumir(DIEZ_MINUTOS, "b", 1);
        assertThat(acotado.contiene(DIEZ_MINUTOS, "a")).isFalse();
        assertThat(acotado.bytesUsados()).isEqualTo(conAyB - (conA - vacio));
        assertCoherente(acotado);
    }

    @Test
    void anularLaUnicaSolicitudRetiraElRegistro() {
        LimitadorSolicitudes acotado = limitador(10, HOLGADO);
        long vacio = acotado.bytesUsados();
        Reserva reserva = acotado.reservar(DIEZ_MINUTOS, "a", 1);
        reserva.anular();

        acotado.consumir(DIEZ_MINUTOS, "b", 1);

        assertThat(acotado.contiene(DIEZ_MINUTOS, "a")).isFalse();
        assertThat(acotado.bytesUsados()).isEqualTo(vacio + costeClave(10, DIEZ_MINUTOS, "b"));
        assertCoherente(acotado);
    }

    @Test
    void conLaTablaLlenaLaCapacidadVuelveJustoAlVencerElPrimerRegistro() {
        LimitadorSolicitudes acotado = limitador(2, HOLGADO);
        acotado.consumir(DIEZ_MINUTOS, "a", 1);
        reloj.fijar(INICIO.plus(Duration.ofMinutes(3)));
        acotado.consumir(DIEZ_MINUTOS, "b", 1);

        reloj.fijar(INICIO.plus(Duration.ofMinutes(5)));
        Resultado rechazo = acotado.consumir(DIEZ_MINUTOS, "c", 1);
        assertThat(rechazo.sinCapacidad()).isTrue();
        assertThat(rechazo.reintentarEn()).isEqualTo(Duration.ofMinutes(5));

        reloj.fijar(INICIO.plus(Duration.ofMinutes(10)).minusMillis(1));
        rechazo = acotado.consumir(DIEZ_MINUTOS, "c", 1);
        assertThat(rechazo.sinCapacidad()).isTrue();
        assertThat(rechazo.reintentarEn()).isEqualTo(Duration.ofMillis(1));

        reloj.fijar(INICIO.plus(Duration.ofMinutes(10)));
        assertThat(acotado.consumir(DIEZ_MINUTOS, "c", 1).permitido()).isTrue();
        assertThat(acotado.contiene(DIEZ_MINUTOS, "a")).isFalse();
    }

    // Una clave larga necesita lo que liberan dos cortas: el reintento es el vencimiento de la segunda, no
    // el de la primera
    @Test
    void elTiempoDeReintentoEsElDeLosRegistrosNecesariosParaCaber() {
        String larga = "x".repeat(40);
        long fijos = bytesFijos(10);
        long corta = costeClave(10, DIEZ_MINUTOS, "c1");
        long costeLarga = costeClave(10, DIEZ_MINUTOS, larga);
        assertThat(costeLarga).isGreaterThan(corta).isLessThan(2 * corta);
        // Caben tres cortas; la larga solo cabe tras retirar dos
        long presupuesto = presupuestoConUmbralEntre(
                Math.max(fijos + 3 * corta, fijos + corta + costeLarga), fijos + 2 * corta + costeLarga);
        LimitadorSolicitudes acotado = limitador(10, presupuesto);
        for (int i = 1; i <= 3; i++) {
            reloj.fijar(INICIO.plus(Duration.ofMinutes(i - 1)));
            assertThat(acotado.consumir(DIEZ_MINUTOS, "c" + i, 1).permitido()).isTrue();
        }

        reloj.fijar(INICIO.plus(Duration.ofMinutes(3)));
        Resultado rechazo = acotado.consumir(DIEZ_MINUTOS, larga, 1);
        assertThat(rechazo.sinCapacidad()).isTrue();
        // c1 vence a los 10 minutos y c2 a los 11
        assertThat(rechazo.reintentarEn()).isEqualTo(Duration.ofMinutes(8));

        reloj.fijar(INICIO.plus(Duration.ofMinutes(10)));
        assertThat(acotado.consumir(DIEZ_MINUTOS, larga, 1).sinCapacidad()).isTrue();
        reloj.fijar(INICIO.plus(Duration.ofMinutes(11)));
        assertThat(acotado.consumir(DIEZ_MINUTOS, larga, 1).permitido()).isTrue();
        assertCoherente(acotado);
    }

    // ================= reserva de crecimiento

    @Test
    void conElUmbralAlcanzadoSeRechazanClavesNuevasPeroLasExistentesCrecen() {
        long fijos = bytesFijos(10);
        long coste = costeClave(10, AMPLIA, "c1");
        // Cabe una clave y no dos, y la reserva del 10 % deja crecer la primera
        long presupuesto = presupuestoConUmbralEntre(fijos + coste, fijos + 2 * coste);
        assertThat(presupuesto - umbral(presupuesto)).isGreaterThanOrEqualTo(48);
        LimitadorSolicitudes acotado = limitador(10, presupuesto);
        assertThat(acotado.consumir(AMPLIA, "c1", 1).permitido()).isTrue();

        assertThat(acotado.consumir(AMPLIA, "c2", 1).sinCapacidad()).isTrue();
        for (int i = 0; i < 15; i++) {
            assertThat(acotado.consumir(AMPLIA, "c1", 1).permitido()).as("solicitud %d", i).isTrue();
        }

        assertThat(acotado.mayorCapacidad()).isEqualTo(16);
        assertThat(acotado.consumir(AMPLIA, "c2", 1).sinCapacidad()).isTrue();
        assertCoherente(acotado);
    }

    // Cuando el doble no cabe, el arreglo crece lo que quepa; sin sitio para un instante más, 429 sin capacidad
    @Test
    void siElDobleNoCabeElArregloCreceLoQueQuepa() {
        LimitadorSolicitudes acotado = limitador(10, 2_000);
        acotado.consumir(AMPLIA, "a", 1);
        long libres = (acotado.presupuesto() - acotado.bytesUsados()) / LimitadorSolicitudes.BYTES_POR_INSTANTE;
        long capacidadFinal = LimitadorSolicitudes.CAPACIDAD_INICIAL + libres;
        // Ninguna sucesión de dobles desde 4 llega a esa capacidad
        assertThat(Long.bitCount(capacidadFinal)).isGreaterThan(1);

        int admitidas = 1;
        Resultado resultado;
        while ((resultado = acotado.consumir(AMPLIA, "a", 1)).permitido()) {
            admitidas++;
        }

        assertThat(resultado.sinCapacidad()).isTrue();
        assertThat(resultado.reintentarEn()).isEqualTo(Duration.ofMinutes(10));
        assertThat(admitidas).isEqualTo(capacidadFinal);
        assertThat(acotado.mayorCapacidad()).isEqualTo(capacidadFinal);
        assertThat(acotado.presupuesto() - acotado.bytesUsados()).isLessThan(LimitadorSolicitudes.BYTES_POR_INSTANTE);
        assertCoherente(acotado);
    }

    // ================= barrido

    // "viejo" tiene 60 solicitudes vencidas en su arreglo y una vigente (fuera de la ventana de 1 minuto pero
    // dentro de la de 10); "activo" está lleno. El barrido libera el arreglo sobrante de "viejo" y la clave
    // nueva cabe, sin tocar instantes vigentes ni vencimientos
    private static final Politica DOBLE = new Politica("doble", List.of(
            new Ventana(Duration.ofMinutes(1), 100), new Ventana(Duration.ofMinutes(10), 100)));
    private static final Politica TRES = new Politica("tres", List.of(new Ventana(Duration.ofMinutes(10), 3)));

    private void prepararBarrido(LimitadorSolicitudes acotado) {
        for (int i = 0; i < 60; i++) {
            assertThat(acotado.consumir(DOBLE, "viejo", 1).permitido()).isTrue();
        }
        reloj.fijar(INICIO.plus(Duration.ofMinutes(4)));
        for (int i = 0; i < 3; i++) {
            assertThat(acotado.consumir(TRES, "activo", 1).permitido()).isTrue();
        }
        reloj.fijar(INICIO.plus(Duration.ofMinutes(5)));
        assertThat(acotado.consumir(DOBLE, "viejo", 1).permitido()).isTrue();
        assertThat(acotado.mayorCapacidad()).isEqualTo(64);
        reloj.fijar(INICIO.plus(Duration.ofMinutes(11)));
    }

    private void assertContadoresIntactosTrasBarrido(LimitadorSolicitudes acotado) {
        assertThat(acotado.venceEn(DOBLE, "viejo")).isEqualTo(INICIO.plus(Duration.ofMinutes(15)).toEpochMilli());
        assertThat(acotado.solicitudesGuardadas(DOBLE, "viejo")).isEqualTo(1);
        assertThat(acotado.venceEn(TRES, "activo")).isEqualTo(INICIO.plus(Duration.ofMinutes(14)).toEpochMilli());
        assertThat(acotado.solicitudesGuardadas(TRES, "activo")).isEqualTo(3);
        Resultado lleno = acotado.consumir(TRES, "activo", 1);
        assertThat(lleno.permitido()).isFalse();
        assertThat(lleno.sinCapacidad()).isFalse();
        assertThat(lleno.reintentarEn()).isEqualTo(Duration.ofMinutes(3));
        assertCoherente(acotado);
    }

    @Test
    void elBarridoLiberaSoloInstantesVencidosYPermiteAdmitirLaSolicitud() {
        long fijos = bytesFijos(10);
        long viejo = costeClave(10, DOBLE, "viejo");
        long activo = costeClave(10, TRES, "activo");
        long nueva = costeClave(10, DOBLE, "nueva");
        long sobrante = (64L - LimitadorSolicitudes.CAPACIDAD_INICIAL) * LimitadorSolicitudes.BYTES_POR_INSTANTE;
        long presupuesto = presupuestoConUmbralEntre(fijos + viejo + activo + nueva,
                fijos + viejo + sobrante + activo + nueva);
        LimitadorSolicitudes acotado = limitador(10, presupuesto);
        prepararBarrido(acotado);

        assertThat(acotado.consumir(DOBLE, "nueva", 1).permitido()).isTrue();

        assertThat(acotado.mayorCapacidad()).isEqualTo(LimitadorSolicitudes.CAPACIDAD_INICIAL);
        assertContadoresIntactosTrasBarrido(acotado);
    }

    // Con el máximo de claves alcanzado el barrido no puede liberar una clave: la nueva se rechaza, y los
    // contadores activos siguen presentes con sus instantes vigentes y sus vencimientos
    @Test
    void conLaTablaLlenaElBarridoNoRetiraNiAlteraContadoresActivos() {
        LimitadorSolicitudes acotado = limitador(2, HOLGADO);
        prepararBarrido(acotado);

        Resultado rechazo = acotado.consumir(DOBLE, "nueva", 1);

        assertThat(rechazo.sinCapacidad()).isTrue();
        assertThat(rechazo.reintentarEn()).isEqualTo(Duration.ofMinutes(3));
        assertThat(acotado.numeroClaves()).isEqualTo(2);
        assertContadoresIntactosTrasBarrido(acotado);
    }

    // ================= concurrencia

    // Presupuesto pequeño, claves nuevas, reservas anuladas y reloj avanzando desde varios hilos: la
    // contabilidad sigue exacta, nunca se supera el presupuesto ni el límite de una clave
    @Test
    void conSolicitudesSimultaneasSeMantienenElPresupuestoYLosLimites() throws Exception {
        LimitadorSolicitudes acotado = limitador(100, 24L << 10);
        Politica cinco = new Politica("cinco", List.of(new Ventana(Duration.ofMinutes(10), 5)));
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(9);
        try {
            List<Future<?>> tareas = new ArrayList<>();
            for (int h = 0; h < 8; h++) {
                tareas.add(hilos.submit(() -> {
                    salida.await();
                    ThreadLocalRandom azar = ThreadLocalRandom.current();
                    for (int i = 0; i < 20_000; i++) {
                        int cliente = azar.nextInt(300);
                        Reserva reserva = acotado.reservar(cinco, ip(cliente), cliente % 3 == 0 ? 10 : 1);
                        if (reserva.resultado().permitido() && azar.nextBoolean()) {
                            reserva.anular();
                        }
                    }
                    return null;
                }));
            }
            tareas.add(hilos.submit(() -> {
                salida.await();
                for (int i = 0; i < 2_000; i++) {
                    reloj.avanzar(Duration.ofSeconds(15));
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

        assertThat(acotado.numeroClaves()).isLessThanOrEqualTo(100);
        assertThat(acotado.mayorCapacidad()).isLessThanOrEqualTo(50);
        assertCoherente(acotado);
    }
}
