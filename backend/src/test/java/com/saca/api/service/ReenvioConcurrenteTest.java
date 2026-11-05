package com.saca.api.service;

import com.saca.api.CarnetsDePrueba;
import com.saca.api.RelojAjustable;
import com.saca.api.dto.LoginRequest;
import com.saca.api.dto.LoginResponse;
import com.saca.api.dto.RegistroRequest;
import com.saca.api.dto.request.ActivacionCuentaRequest;
import com.saca.api.dto.request.ReenvioActivacionRequest;
import com.saca.api.entity.EstadoCorreo;
import com.saca.api.entity.Estudiante;
import com.saca.api.entity.TokenActivacion;
import com.saca.api.exception.ValidacionNegocioException;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.repository.TokenActivacionRepository;
import com.saca.api.service.BloqueoCuentaCasos.Pausa;
import com.saca.api.service.BloqueoCuentaCasos.VerificadorContado;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;

// Reenvíos simultáneos entre sí, con inicios de sesión y con la activación de la misma cuenta. La
// cuenta se bloquea antes de comprobar cupos y de tocar sus tokens, en el mismo orden que el registro y la
// activación. Se ejecuta sobre H2 y, pasando la base por propiedades, sobre un MySQL desechable
@SpringBootTest(properties = {
        "SACA_CORREO_REMITENTE=saca@pruebas.ues.edu.sv",
        "SACA_FRONTEND_URL=https://saca.pruebas.local"
})
@ActiveProfiles("test")
@Import(BloqueoCuentaCasos.Configuracion.class)
class ReenvioConcurrenteTest {

    private static final String ANTERIOR = "Anterior123";
    private static final String INCORRECTA = "Incorrecta1";

    @TestConfiguration
    static class Correo {
        @Bean
        JavaMailSender clienteSmtpDePrueba() {
            return mock(JavaMailSender.class);
        }

        @Bean
        @Primary
        EnvioCorreoActivacion envioInmediato() {
            return new EnvioCorreoActivacion() {
                @Override
                public void programar(Runnable envio, Runnable siDescartado) {
                    ejecutar(envio);
                }
            };
        }
    }

    @Autowired
    private RegistroCuentaService registroCuenta;

    @Autowired
    private EstudianteService estudianteService;

    @Autowired
    private TokenActivacionService tokens;

    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private TokenActivacionRepository tokenRepository;

    @Autowired
    private JavaMailSender clienteSmtp;

    @Autowired
    private RelojAjustable reloj;

    @Autowired
    private VerificadorContado verificador;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final List<String> enviados = new CopyOnWriteArrayList<>();
    private ExecutorService hilos;

    @BeforeEach
    void preparar() {
        reloj.fijar(Instant.now());
        verificador.reales.set(0);
        verificador.fallidas.set(0);
        verificador.porContrasenia.clear();
        verificador.pausas.clear();
        enviados.clear();
        reset(clienteSmtp);
        doAnswer(invocacion -> enviados.add(((SimpleMailMessage) invocacion.getArgument(0)).getTo()[0]))
                .when(clienteSmtp).send(any(SimpleMailMessage.class));
        hilos = Executors.newCachedThreadPool();
    }

    @AfterEach
    void cerrar() {
        hilos.shutdownNow();
    }

    // ================= utilidades

    private String registrar() {
        String carnet = CarnetsDePrueba.nuevo("RC");
        RegistroRequest request = new RegistroRequest();
        request.setCarnet(carnet);
        registroCuenta.registrar(request);
        return carnet.toLowerCase(Locale.ROOT) + "@ues.edu.sv";
    }

    private void reenviar(String correo) {
        registroCuenta.reenviar(new ReenvioActivacionRequest(correo));
    }

    // Cuenta pendiente con una contraseña conocida (como tras una corrección administrativa) y su token inicial
    private Estudiante pendienteConContrasenia(int intentosFallidos) {
        String carnet = CarnetsDePrueba.nuevo("RC");
        Estudiante estudiante = new Estudiante();
        estudiante.setCarnet(carnet);
        estudiante.setCorreoInstitucional(carnet.toLowerCase(Locale.ROOT) + "@ues.edu.sv");
        estudiante.setContrasenia(passwordEncoder.encode(ANTERIOR));
        estudiante.setCuentaActiva(true);
        estudiante.setIntentosFallidos(intentosFallidos);
        estudiante.setEstadoCorreo(EstadoCorreo.PENDIENTE);
        estudiante = estudianteRepository.save(estudiante);
        tokens.generar(estudiante);
        return estudiante;
    }

    private Estudiante cuenta(String correo) {
        return estudianteRepository.findByCorreoInstitucional(correo).orElseThrow();
    }

    private List<TokenActivacion> tokensDe(Estudiante estudiante) {
        return tokenRepository.findByEstudianteIdOrderByCreadoEnAsc(estudiante.getId());
    }

    private static long vigentes(List<TokenActivacion> lista) {
        return lista.stream().filter(token -> token.getUsadoEn() == null && token.getInvalidadoEn() == null).count();
    }

    private long enviadosA(String correo) {
        return enviados.stream().filter(correo::equals).count();
    }

    private LoginResponse login(Estudiante estudiante, String contrasenia) {
        LoginRequest peticion = new LoginRequest();
        peticion.setCorreoInstitucional(estudiante.getCorreoInstitucional());
        peticion.setContrasenia(contrasenia);
        return estudianteService.login(peticion);
    }

    private <T> List<T> simultaneos(List<Callable<T>> tareas) throws Exception {
        CountDownLatch salida = new CountDownLatch(1);
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
    }

    private List<Callable<Object>> reenvios(String correo, int cuantos) {
        List<Callable<Object>> tareas = new ArrayList<>();
        for (int i = 0; i < cuantos; i++) {
            tareas.add(() -> {
                reenviar(correo);
                return null;
            });
        }
        return tareas;
    }

    // ================= reenvíos simultáneos

    // Doce reenvíos a la vez de la misma cuenta: uno solo emite (el intervalo de un minuto ya lo frena el primero),
    // y queda un único token vigente
    @Test
    void variosReenviosSimultaneosEmitenUnSoloTokenVigente() throws Exception {
        for (int ronda = 0; ronda < 3; ronda++) {
            String correo = registrar();
            reloj.avanzar(Duration.ofMinutes(2));

            simultaneos(reenvios(correo, 12));

            List<TokenActivacion> lista = tokensDe(cuenta(correo));
            assertThat(lista).as("ronda %d", ronda).hasSize(2);
            assertThat(vigentes(lista)).isEqualTo(1);
            assertThat(enviadosA(correo)).isEqualTo(2);
        }
    }

    // Con cuatro correos ya en las últimas 24 horas, una ráfaga simultánea solo consigue el quinto
    @Test
    void losReenviosSimultaneosNoSuperanCincoCorreosEn24Horas() throws Exception {
        String correo = registrar();
        for (int i = 0; i < 3; i++) {
            reloj.avanzar(Duration.ofMinutes(2));
            reenviar(correo);
        }
        assertThat(tokensDe(cuenta(correo))).hasSize(4);
        reloj.avanzar(Duration.ofMinutes(2));

        simultaneos(reenvios(correo, 12));

        assertThat(tokensDe(cuenta(correo))).hasSize(5);
        assertThat(vigentes(tokensDe(cuenta(correo)))).isEqualTo(1);
        assertThat(enviadosA(correo)).isEqualTo(5);
        reloj.avanzar(Duration.ofMinutes(2));
        simultaneos(reenvios(correo, 4));
        assertThat(tokensDe(cuenta(correo))).hasSize(5);
    }

    // Con cuatro correos enviados y un quinto en la cola (aún sin intentar), una ráfaga simultánea no emite nada:
    // el de la cola ya ocupa la quinta plaza
    @Test
    void unCorreoEnColaCuentaParaLasRafagasSimultaneas() throws Exception {
        String correo = registrar();
        for (int i = 0; i < 3; i++) {
            reloj.avanzar(Duration.ofMinutes(2));
            reenviar(correo);
        }
        reloj.avanzar(Duration.ofMinutes(2));
        Estudiante estudiante = cuenta(correo);
        // Quinto correo emitido y en cola: el envío no se ejecuta en esta prueba
        tokens.generar(estudiante);
        reloj.avanzar(Duration.ofMinutes(2));

        simultaneos(reenvios(correo, 12));

        assertThat(tokensDe(estudiante)).hasSize(5);
        assertThat(enviadosA(correo)).isEqualTo(4);
    }

    // ================= reenvío y login

    // Un login de la cuenta pendiente queda en su comparación mientras se reenvía el enlace: el reenvío no toca el
    // contador, así que el login compensa (o cuenta) exactamente lo suyo
    @Test
    void unReenvioDuranteUnLoginNoAlteraElContador() throws Exception {
        for (String contrasenia : new String[] {ANTERIOR, INCORRECTA}) {
            Estudiante estudiante = pendienteConContrasenia(1);
            reloj.avanzar(Duration.ofMinutes(2));
            Pausa pausa = new Pausa(contrasenia, new CountDownLatch(1), new CountDownLatch(1));
            verificador.pausas.add(pausa);
            Future<LoginResponse> enCurso = hilos.submit(() -> login(estudiante, contrasenia));
            assertThat(pausa.dentro().await(60, TimeUnit.SECONDS)).isTrue();

            reenviar(estudiante.getCorreoInstitucional());
            assertThat(tokensDe(estudiante)).hasSize(2);

            pausa.seguir().countDown();
            assertThat(enCurso.get(60, TimeUnit.SECONDS).autenticado()).isFalse();

            Estudiante despues = estudianteRepository.findById(estudiante.getId()).orElseThrow();
            assertThat(despues.getIntentosFallidos()).as(contrasenia).isEqualTo(contrasenia.equals(ANTERIOR) ? 1 : 2);
            assertThat(despues.getBloqueadoHasta()).isNull();
            assertThat(despues.getEstadoCorreo()).isEqualTo(EstadoCorreo.PENDIENTE);
            assertThat(despues.getContrasenia()).isEqualTo(estudiante.getContrasenia());
        }
    }

    @Test
    void reenviosYLoginsSimultaneosMantienenElContadorExacto() throws Exception {
        for (int ronda = 0; ronda < 3; ronda++) {
            verificador.fallidas.set(0);
            Estudiante estudiante = pendienteConContrasenia(0);
            reloj.avanzar(Duration.ofMinutes(2));
            List<Callable<Object>> tareas = new ArrayList<>(reenvios(estudiante.getCorreoInstitucional(), 6));
            for (int i = 0; i < 10; i++) {
                String contrasenia = i % 2 == 0 ? INCORRECTA : ANTERIOR;
                tareas.add(() -> login(estudiante, contrasenia));
            }

            simultaneos(tareas);

            Estudiante despues = estudianteRepository.findById(estudiante.getId()).orElseThrow();
            int fallos = despues.getIntentosFallidos();
            assertThat(fallos).as("ronda %d", ronda).isEqualTo(verificador.fallidas.get());
            assertThat(despues.getBloqueadoHasta() != null).isEqualTo(fallos >= 3);
            assertThat(tokensDe(estudiante)).hasSize(2);
            assertThat(vigentes(tokensDe(estudiante))).isEqualTo(1);
        }
    }

    // ================= reenvío y activación

    // Activación con el enlace inicial y reenvío a la vez: o se activa y el reenvío ya no emite, o se reenvía y el
    // enlace inicial deja de valer. Nunca las dos cosas, ni un interbloqueo
    @Test
    void unReenvioYUnaActivacionSimultaneosNoSeContradicen() throws Exception {
        int activadas = 0;
        int reenviadas = 0;
        for (int ronda = 0; ronda < 12; ronda++) {
            Estudiante estudiante = pendienteConContrasenia(0);
            String inicial = tokens.generar(estudiante).valor();
            reloj.avanzar(Duration.ofMinutes(2));
            Callable<Object> activar = () -> {
                try {
                    registroCuenta.activar(new ActivacionCuentaRequest(inicial, "Nombre Activado", "Elegida456"));
                    return "activada";
                } catch (ValidacionNegocioException e) {
                    return e.getMessage();
                }
            };
            Callable<Object> reenvio = () -> {
                reenviar(estudiante.getCorreoInstitucional());
                return null;
            };

            List<Object> resultados;
            try {
                resultados = simultaneos(List.of(activar, reenvio));
            } catch (ExecutionException e) {
                throw new AssertionError("Ronda " + ronda + ": error inesperado", e.getCause());
            }

            Estudiante despues = estudianteRepository.findById(estudiante.getId()).orElseThrow();
            List<TokenActivacion> lista = tokensDe(estudiante);
            if ("activada".equals(resultados.get(0))) {
                activadas++;
                assertThat(despues.getEstadoCorreo()).isEqualTo(EstadoCorreo.CONFIRMADO);
                assertThat(lista).hasSize(2);
                assertThat(vigentes(lista)).isZero();
            } else {
                reenviadas++;
                assertThat(resultados.get(0)).isEqualTo("El enlace de activación no es válido.");
                assertThat(despues.getEstadoCorreo()).isEqualTo(EstadoCorreo.PENDIENTE);
                assertThat(lista).hasSize(3);
                assertThat(vigentes(lista)).isEqualTo(1);
            }
            assertThat(despues.getIntentosFallidos()).isZero();
            assertThat(despues.getBloqueadoHasta()).isNull();
        }
        assertThat(activadas + reenviadas).isEqualTo(12);
    }
}
