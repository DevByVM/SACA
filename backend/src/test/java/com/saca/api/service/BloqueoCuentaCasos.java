package com.saca.api.service;

import com.saca.api.CarnetsDePrueba;
import com.saca.api.RelojAjustable;
import com.saca.api.dto.ActualizarPerfilRequest;
import com.saca.api.dto.LoginRequest;
import com.saca.api.dto.LoginResponse;
import com.saca.api.entity.EstadoCorreo;
import com.saca.api.entity.Estudiante;
import com.saca.api.repository.EstudianteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// Bloqueo por cuenta (3 fallos, 5 minutos) con intentos simultáneos, y escrituras del login y
// del perfil que no se pisan. Los casos se ejecutan sobre H2 (BloqueoCuentaConcurrenteTest) y sobre un MySQL
// real desechable (BloqueoCuentaMysqlTest), porque los dos evalúan de forma distinta las asignaciones de un UPDATE
abstract class BloqueoCuentaCasos {

    static final String CLAVE = "Segura123";
    static final String CLAVE_NUEVA = "Distinta456";
    static final String INCORRECTA = "Incorrecta1";

    @TestConfiguration
    static class Configuracion {
        @Bean
        @Primary
        RelojAjustable relojBloqueo() {
            return new RelojAjustable(Instant.now(), ZoneId.of("America/El_Salvador"));
        }

        @Bean
        @Primary
        VerificadorContado verificadorContado() {
            return new VerificadorContado();
        }
    }

    // Cuenta las comparaciones con el hash de la cuenta (las ficticias, de una cuenta bloqueada, no pasan por
    // aquí), en total y por contraseña. Para intercalar solicitudes, una comparación con la contraseña de la
    // siguiente pausa pendiente avisa al entrar y espera dentro hasta que se la deja seguir
    static class VerificadorContado extends VerificadorContrasenias {
        final AtomicInteger reales = new AtomicInteger();
        final AtomicInteger fallidas = new AtomicInteger();
        final Map<String, AtomicInteger> porContrasenia = new ConcurrentHashMap<>();
        final ConcurrentLinkedQueue<Pausa> pausas = new ConcurrentLinkedQueue<>();

        @Override
        public boolean coincide(String contrasenia, String hashGuardado) {
            reales.incrementAndGet();
            porContrasenia.computeIfAbsent(contrasenia, c -> new AtomicInteger()).incrementAndGet();
            Pausa pausa = pausas.peek();
            if (pausa != null && pausa.contrasenia().equals(contrasenia) && pausas.remove(pausa)) {
                pausa.dentro().countDown();
                try {
                    if (!pausa.seguir().await(60, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("La prueba no liberó la comparación retenida");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
            boolean coinciden = super.coincide(contrasenia, hashGuardado);
            if (!coinciden) {
                fallidas.incrementAndGet();
            }
            return coinciden;
        }

        int comparaciones(String contrasenia) {
            AtomicInteger cuenta = porContrasenia.get(contrasenia);
            return cuenta == null ? 0 : cuenta.get();
        }
    }

    record Pausa(String contrasenia, CountDownLatch dentro, CountDownLatch seguir) {
    }

    @Autowired
    private EstudianteService servicio;

    @Autowired
    private EstudianteRepository repository;

    @Autowired
    private RelojAjustable reloj;

    @Autowired
    private VerificadorContado verificador;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private ExecutorService segundoPlano;

    @BeforeEach
    void reiniciar() {
        reloj.fijar(Instant.now());
        verificador.reales.set(0);
        verificador.porContrasenia.clear();
        verificador.pausas.clear();
        segundoPlano = Executors.newCachedThreadPool();
    }

    @AfterEach
    void cerrar() {
        segundoPlano.shutdownNow();
    }

    // La siguiente comparación con esa contraseña quedará retenida dentro de BCrypt, ya hecha su reserva
    private Pausa pausar(String contrasenia) {
        Pausa pausa = new Pausa(contrasenia, new CountDownLatch(1), new CountDownLatch(1));
        verificador.pausas.add(pausa);
        return pausa;
    }

    // Lanza el inicio de sesión en otro hilo y espera a que quede retenido en su comparación
    private Future<LoginResponse> retenido(Estudiante estudiante, Pausa pausa) throws InterruptedException {
        Future<LoginResponse> login = segundoPlano.submit(() -> login(estudiante, pausa.contrasenia()));
        assertThat(pausa.dentro().await(60, TimeUnit.SECONDS)).isTrue();
        return login;
    }

    private static LoginResponse soltar(Pausa pausa, Future<LoginResponse> login) throws Exception {
        pausa.seguir().countDown();
        return login.get(60, TimeUnit.SECONDS);
    }

    private Estudiante conBloqueo(Estudiante estudiante, Duration desdeAhora) {
        estudiante.setBloqueadoHasta(Timestamp.valueOf(
                LocalDateTime.ofInstant(reloj.instant(), ZoneId.systemDefault()).plus(desdeAhora)));
        return repository.save(estudiante);
    }

    private boolean bloqueada(Estudiante estudiante) {
        Timestamp hasta = leer(estudiante).getBloqueadoHasta();
        return hasta != null && hasta.getTime() > Timestamp.valueOf(
                LocalDateTime.ofInstant(reloj.instant(), ZoneId.systemDefault())).getTime();
    }

    private Estudiante cuenta(EstadoCorreo estado, int intentosFallidos) {
        Estudiante estudiante = new Estudiante();
        estudiante.setNombre("Prueba Bloqueo");
        estudiante.setCarnet(CarnetsDePrueba.nuevo("BC"));
        estudiante.setCorreoInstitucional("bloqueo." + UUID.randomUUID().toString().substring(0, 8) + "@ues.edu.sv");
        estudiante.setContrasenia(passwordEncoder.encode(CLAVE));
        estudiante.setCuentaActiva(true);
        estudiante.setIntentosFallidos(intentosFallidos);
        estudiante.setEstadoCorreo(estado);
        return repository.save(estudiante);
    }

    private LoginResponse login(Estudiante estudiante, String contrasenia) {
        LoginRequest peticion = new LoginRequest();
        peticion.setCorreoInstitucional(estudiante.getCorreoInstitucional());
        peticion.setContrasenia(contrasenia);
        return servicio.login(peticion);
    }

    private Estudiante leer(Estudiante estudiante) {
        return repository.findById(estudiante.getId()).orElseThrow();
    }

    // Lo que el login guarda como fin del bloqueo si el fallo ocurre ahora
    private long bloqueoDesdeAhora() {
        return Timestamp.valueOf(LocalDateTime.ofInstant(reloj.instant(), ZoneId.systemDefault()).plusMinutes(5)).getTime();
    }

    private static <T> List<T> simultaneos(List<Callable<T>> tareas) throws Exception {
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(tareas.size());
        try {
            List<Future<T>> pendientes = new ArrayList<>();
            for (Callable<T> tarea : tareas) {
                pendientes.add(hilos.submit(() -> {
                    salida.await();
                    return tarea.call();
                }));
            }
            salida.countDown();
            List<T> resultados = new ArrayList<>();
            for (Future<T> pendiente : pendientes) {
                resultados.add(pendiente.get(120, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            hilos.shutdownNow();
        }
    }

    @Test
    void cincuentaFallosSimultaneosSoloComparanTresContraseniasAntesDelBloqueo() throws Exception {
        Estudiante estudiante = cuenta(null, 0);
        List<Callable<LoginResponse>> tareas = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            tareas.add(() -> login(estudiante, INCORRECTA));
        }

        List<LoginResponse> respuestas = simultaneos(tareas);

        assertThat(respuestas).noneMatch(LoginResponse::autenticado);
        assertThat(verificador.reales.get()).isEqualTo(3);
        Estudiante guardado = leer(estudiante);
        assertThat(guardado.getIntentosFallidos()).isEqualTo(3);
        assertThat(guardado.getBloqueadoHasta().getTime()).isEqualTo(bloqueoDesdeAhora());

        // Bloqueada: ni la contraseña correcta entra, y no se compara con su hash
        assertThat(login(estudiante, CLAVE).autenticado()).isFalse();
        assertThat(verificador.reales.get()).isEqualTo(3);
    }

    // Antes de llegar a tres no se bloquea: en MySQL, asignar intentos_fallidos antes que bloqueado_hasta haría que
    // la segunda reserva ya viera 3 y bloqueara
    @Test
    void laSegundaReservaNoBloqueaYLaTerceraSi() {
        Estudiante estudiante = cuenta(null, 1);

        login(estudiante, INCORRECTA);
        assertThat(leer(estudiante).getIntentosFallidos()).isEqualTo(2);
        assertThat(leer(estudiante).getBloqueadoHasta()).isNull();

        login(estudiante, INCORRECTA);
        assertThat(leer(estudiante).getIntentosFallidos()).isEqualTo(3);
        assertThat(leer(estudiante).getBloqueadoHasta().getTime()).isEqualTo(bloqueoDesdeAhora());
    }

    // Cada acierto reinicia el contador, así que entre dos aciertos (o hasta el primero) se comparan como mucho
    // tres contraseñas; al final el estado es el de algún orden secuencial
    @Test
    void conFallosYAciertosSimultaneosNuncaSeComparanMasDeTresEntreAciertos() throws Exception {
        for (int ronda = 0; ronda < 3; ronda++) {
            verificador.reales.set(0);
            Estudiante estudiante = cuenta(null, 0);
            List<Callable<LoginResponse>> tareas = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                String contrasenia = i % 4 == 0 ? CLAVE : INCORRECTA;
                tareas.add(() -> login(estudiante, contrasenia));
            }

            List<LoginResponse> respuestas = simultaneos(tareas);

            long aciertos = respuestas.stream().filter(LoginResponse::autenticado).count();
            assertThat(verificador.reales.get()).isLessThanOrEqualTo((int) (3 * (aciertos + 1)));
            Estudiante guardado = leer(estudiante);
            assertThat(guardado.getIntentosFallidos()).isBetween(0, 3);
            assertThat(guardado.getBloqueadoHasta() != null).isEqualTo(guardado.getIntentosFallidos() == 3);
        }
    }

    // El login ya no guarda la entidad completa ni el perfil la suya: ninguno deshace lo que escribe el otro
    @Test
    void unCambioDeContraseniaSimultaneoConFallosDeLoginSeConservaYLosFallosTambien() throws Exception {
        for (int ronda = 0; ronda < 3; ronda++) {
            Estudiante estudiante = cuenta(null, 0);
            ActualizarPerfilRequest cambio = new ActualizarPerfilRequest();
            cambio.setNombre("Prueba Bloqueo");
            cambio.setContrasenia(CLAVE_NUEVA);
            cambio.setContraseniaActual(CLAVE);
            List<Callable<String>> tareas = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                tareas.add(() -> String.valueOf(login(estudiante, INCORRECTA).autenticado()));
                if (i == 1) {
                    tareas.add(() -> servicio.actualizarPerfil(estudiante.getId(), cambio));
                }
            }

            List<String> resultados = simultaneos(tareas);

            assertThat(resultados).contains("Perfil actualizado correctamente");
            Estudiante guardado = leer(estudiante);
            assertThat(passwordEncoder.matches(CLAVE_NUEVA, guardado.getContrasenia())).isTrue();
            assertThat(guardado.getIntentosFallidos()).isEqualTo(3);
            assertThat(guardado.getBloqueadoHasta()).isNotNull();
        }
    }

    // Si la contraseña cambió entre la comprobación y la escritura, no se sobrescribe
    @Test
    void unCambioDeContraseniaConLaActualYaObsoletaNoSobrescribe() {
        Estudiante estudiante = cuenta(null, 0);
        String otra = passwordEncoder.encode("OtraClave789");
        repository.actualizarNombreYContrasenia(estudiante.getId(), "Prueba Bloqueo", estudiante.getContrasenia(), otra);

        int afectadas = repository.actualizarNombreYContrasenia(
                estudiante.getId(), "Prueba Bloqueo", estudiante.getContrasenia(), passwordEncoder.encode(CLAVE_NUEVA));

        assertThat(afectadas).isZero();
        assertThat(leer(estudiante).getContrasenia()).isEqualTo(otra);
    }

    @Test
    void unaCuentaPendienteConSuContraseniaNoAcumulaFallos() throws Exception {
        Estudiante sinFallos = cuenta(EstadoCorreo.PENDIENTE, 0);
        assertThat(login(sinFallos, CLAVE).autenticado()).isFalse();
        assertThat(leer(sinFallos).getIntentosFallidos()).isZero();
        assertThat(leer(sinFallos).getBloqueadoHasta()).isNull();

        // Con dos fallos, la reserva del acierto llega a tres y bloquea: la compensación deshace las dos cosas
        Estudiante conDosFallos = cuenta(EstadoCorreo.PENDIENTE, 2);
        assertThat(login(conDosFallos, CLAVE).autenticado()).isFalse();
        assertThat(leer(conDosFallos).getIntentosFallidos()).isEqualTo(2);
        assertThat(leer(conDosFallos).getBloqueadoHasta()).isNull();

        // Un fallo sí cuenta y bloquea
        assertThat(login(conDosFallos, INCORRECTA).autenticado()).isFalse();
        assertThat(leer(conDosFallos).getIntentosFallidos()).isEqualTo(3);
        assertThat(leer(conDosFallos).getBloqueadoHasta()).isNotNull();

        // Varios aciertos simultáneos tampoco dejan nada
        Estudiante simultanea = cuenta(EstadoCorreo.PENDIENTE, 0);
        List<Callable<LoginResponse>> tareas = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tareas.add(() -> login(simultanea, CLAVE));
        }
        assertThat(simultaneos(tareas)).noneMatch(LoginResponse::autenticado);
        assertThat(leer(simultanea).getIntentosFallidos()).isZero();
        assertThat(leer(simultanea).getBloqueadoHasta()).isNull();
    }

    @Test
    void elBloqueoDuraCincoMinutosYUnFalloTrasVencerVuelveABloquear() {
        Instant inicio = reloj.instant();
        Estudiante estudiante = cuenta(null, 0);
        for (int i = 0; i < 3; i++) {
            login(estudiante, INCORRECTA);
        }

        reloj.fijar(inicio.plus(Duration.ofMinutes(5)).minusMillis(1));
        assertThat(login(estudiante, CLAVE).autenticado()).isFalse();
        assertThat(verificador.reales.get()).isEqualTo(3);

        reloj.fijar(inicio.plus(Duration.ofMinutes(5)));
        assertThat(login(estudiante, CLAVE).autenticado()).isTrue();
        assertThat(leer(estudiante).getIntentosFallidos()).isZero();
        assertThat(leer(estudiante).getBloqueadoHasta()).isNull();

        // Tres fallos más, y tras vencer ese bloqueo un solo fallo basta para bloquear de nuevo (como antes)
        for (int i = 0; i < 3; i++) {
            login(estudiante, INCORRECTA);
        }
        reloj.fijar(inicio.plus(Duration.ofMinutes(10)));
        login(estudiante, INCORRECTA);
        assertThat(leer(estudiante).getIntentosFallidos()).isEqualTo(4);
        assertThat(leer(estudiante).getBloqueadoHasta().getTime()).isEqualTo(bloqueoDesdeAhora());
        assertThat(login(estudiante, CLAVE).autenticado()).isFalse();
    }

    // ================= compensación de cuentas pendientes con solicitudes intercaladas

    // P (correcta) reserva y queda en su comparación; 1 ms después W (incorrecta) llega a tres y bloquea; P
    // compensa. Sin P, W habría dejado 2 fallos y ningún bloqueo: eso es lo que debe quedar
    @Test
    void laCompensacionRetiraElBloqueoAlQueSoloSeLlegoPorSuReserva() throws Exception {
        Estudiante estudiante = cuenta(EstadoCorreo.PENDIENTE, 1);
        Pausa pausa = pausar(CLAVE);
        Future<LoginResponse> correcta = retenido(estudiante, pausa);
        assertThat(leer(estudiante).getIntentosFallidos()).isEqualTo(2);

        reloj.avanzar(Duration.ofMillis(1));
        assertThat(login(estudiante, INCORRECTA).autenticado()).isFalse();
        assertThat(leer(estudiante).getIntentosFallidos()).isEqualTo(3);
        assertThat(bloqueada(estudiante)).isTrue();

        assertThat(soltar(pausa, correcta).autenticado()).isFalse();

        assertThat(leer(estudiante).getIntentosFallidos()).isEqualTo(2);
        assertThat(leer(estudiante).getBloqueadoHasta()).isNull();
        // Y el siguiente fallo bloquea, como tras dos fallos
        login(estudiante, INCORRECTA);
        assertThat(leer(estudiante).getIntentosFallidos()).isEqualTo(3);
        assertThat(bloqueada(estudiante)).isTrue();
    }

    // Dos aciertos en curso: el segundo bloquea al llegar a tres. Cuando el primero compensa, sin él quedaría un
    // solo fallo más la reserva del segundo (2), sin bloqueo; y tras el segundo, el fallo inicial
    @Test
    void dosAciertosIntercaladosDejanSoloElFalloInicial() throws Exception {
        Estudiante estudiante = cuenta(EstadoCorreo.PENDIENTE, 1);
        Pausa primera = pausar(CLAVE);
        Pausa segunda = pausar(CLAVE);
        Future<LoginResponse> p1 = retenido(estudiante, primera);
        reloj.avanzar(Duration.ofMillis(1));
        Future<LoginResponse> p2 = retenido(estudiante, segunda);
        assertThat(leer(estudiante).getIntentosFallidos()).isEqualTo(3);
        assertThat(bloqueada(estudiante)).isTrue();

        soltar(primera, p1);
        assertThat(leer(estudiante).getIntentosFallidos()).isEqualTo(2);
        assertThat(leer(estudiante).getBloqueadoHasta()).isNull();

        soltar(segunda, p2);
        assertThat(leer(estudiante).getIntentosFallidos()).isEqualTo(1);
        assertThat(leer(estudiante).getBloqueadoHasta()).isNull();
    }

    // Con un bloqueo vencido y 3 fallos, P reserva (4) y vuelve a bloquear; W, 1 ms después, encuentra la
    // cuenta bloqueada y ni se compara. Al compensar, P la deja como estaba: 3 fallos y sin bloqueo vigente
    @Test
    void conUnBloqueoVencidoLaCompensacionDejaLaCuentaComoEstaba() throws Exception {
        Estudiante estudiante = conBloqueo(cuenta(EstadoCorreo.PENDIENTE, 3), Duration.ofMinutes(-1));
        Pausa pausa = pausar(CLAVE);
        Future<LoginResponse> correcta = retenido(estudiante, pausa);
        assertThat(leer(estudiante).getIntentosFallidos()).isEqualTo(4);
        assertThat(bloqueada(estudiante)).isTrue();

        reloj.avanzar(Duration.ofMillis(1));
        assertThat(login(estudiante, INCORRECTA).autenticado()).isFalse();
        assertThat(verificador.comparaciones(INCORRECTA)).isZero();

        soltar(pausa, correcta);

        assertThat(leer(estudiante).getIntentosFallidos()).isEqualTo(3);
        assertThat(bloqueada(estudiante)).isFalse();
        // Como tras vencer un bloqueo: un fallo vuelve a bloquear
        login(estudiante, INCORRECTA);
        assertThat(leer(estudiante).getIntentosFallidos()).isEqualTo(4);
        assertThat(bloqueada(estudiante)).isTrue();
    }

    // Aciertos y fallos simultáneos sobre una cuenta pendiente mientras el reloj avanza de milisegundo en
    // milisegundo: los aciertos no dejan rastro (fallos = inicial + fallos comparados) y la cuenta queda
    // bloqueada si y solo si hay al menos tres
    @Test
    void conAciertosYFallosSimultaneosUnaCuentaPendienteSoloSeBloqueaConTresFallosEfectivos() throws Exception {
        for (int ronda = 0; ronda < 4; ronda++) {
            verificador.porContrasenia.clear();
            Estudiante estudiante = cuenta(EstadoCorreo.PENDIENTE, 1);
            List<Callable<LoginResponse>> tareas = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                String contrasenia = i % 3 == 0 ? INCORRECTA : CLAVE;
                tareas.add(() -> login(estudiante, contrasenia));
            }
            // Un milisegundo del reloj por cada milisegundo real: el bloqueo no puede vencer durante la ronda
            Instant inicioRonda = reloj.instant();
            Future<?> tictac = segundoPlano.submit(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        reloj.avanzar(Duration.ofMillis(1));
                        Thread.sleep(1);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            try {
                assertThat(simultaneos(tareas)).noneMatch(LoginResponse::autenticado);
            } finally {
                tictac.cancel(true);
            }
            assertThat(Duration.between(inicioRonda, reloj.instant())).isLessThan(Duration.ofMinutes(5));

            int fallos = leer(estudiante).getIntentosFallidos();
            assertThat(fallos).as("ronda %d", ronda).isEqualTo(1 + verificador.comparaciones(INCORRECTA));
            assertThat(bloqueada(estudiante)).as("ronda %d", ronda).isEqualTo(fallos >= 3);
        }
    }
}
