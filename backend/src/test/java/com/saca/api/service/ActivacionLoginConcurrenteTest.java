package com.saca.api.service;

import com.saca.api.CarnetsDePrueba;
import com.saca.api.RelojAjustable;
import com.saca.api.dto.LoginRequest;
import com.saca.api.dto.LoginResponse;
import com.saca.api.dto.request.ActivacionCuentaRequest;
import com.saca.api.entity.EstadoCorreo;
import com.saca.api.entity.Estudiante;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.service.BloqueoCuentaCasos.Pausa;
import com.saca.api.service.BloqueoCuentaCasos.VerificadorContado;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// Activación mientras hay inicios de sesión en curso sobre la misma cuenta pendiente. La
// activación no toca el contador ni el bloqueo, así que cada login en curso compensa o cuenta exactamente lo suyo
// (condición de diseño: nada reinicia el contador de una cuenta pendiente durante un login; ver
// EstudianteRepository.compensarIntentoFallido). Las cuentas pendientes tienen aquí una contraseña conocida (como tras una
// corrección administrativa) para poder ejercer la compensación
@SpringBootTest
@ActiveProfiles("test")
@Import(BloqueoCuentaCasos.Configuracion.class)
class ActivacionLoginConcurrenteTest {

    private static final String ANTERIOR = "Anterior123";
    private static final String NUEVA = "Elegida456";
    private static final String INCORRECTA = "Incorrecta1";

    @Autowired
    private EstudianteService estudianteService;

    @Autowired
    private RegistroCuentaService registroCuenta;

    @Autowired
    private TokenActivacionService tokens;

    @Autowired
    private EstudianteRepository repository;

    @Autowired
    private RelojAjustable reloj;

    @Autowired
    private VerificadorContado verificador;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private ExecutorService segundoPlano;

    @BeforeEach
    void preparar() {
        reloj.fijar(Instant.now());
        verificador.reales.set(0);
        verificador.fallidas.set(0);
        verificador.porContrasenia.clear();
        verificador.pausas.clear();
        segundoPlano = Executors.newCachedThreadPool();
    }

    @AfterEach
    void cerrar() {
        segundoPlano.shutdownNow();
    }

    private Estudiante pendiente(int intentosFallidos) {
        String carnet = CarnetsDePrueba.nuevo("AL");
        Estudiante estudiante = new Estudiante();
        estudiante.setCarnet(carnet);
        estudiante.setCorreoInstitucional(carnet.toLowerCase(Locale.ROOT) + "@ues.edu.sv");
        estudiante.setContrasenia(passwordEncoder.encode(ANTERIOR));
        estudiante.setCuentaActiva(true);
        estudiante.setIntentosFallidos(intentosFallidos);
        estudiante.setEstadoCorreo(EstadoCorreo.PENDIENTE);
        return repository.save(estudiante);
    }

    private LoginResponse login(Estudiante estudiante, String contrasenia) {
        LoginRequest peticion = new LoginRequest();
        peticion.setCorreoInstitucional(estudiante.getCorreoInstitucional());
        peticion.setContrasenia(contrasenia);
        return estudianteService.login(peticion);
    }

    private void activar(Estudiante estudiante) {
        String token = tokens.generar(estudiante).valor();
        registroCuenta.activar(new ActivacionCuentaRequest(token, "Nombre Activado", NUEVA));
    }

    private Future<LoginResponse> retenido(Estudiante estudiante, String contrasenia, Pausa pausa) throws Exception {
        Future<LoginResponse> login = segundoPlano.submit(() -> login(estudiante, contrasenia));
        assertThat(pausa.dentro().await(60, TimeUnit.SECONDS)).isTrue();
        return login;
    }

    private Pausa pausar(String contrasenia) {
        Pausa pausa = new Pausa(contrasenia, new CountDownLatch(1), new CountDownLatch(1));
        verificador.pausas.add(pausa);
        return pausa;
    }

    private Estudiante leer(Estudiante estudiante) {
        return repository.findById(estudiante.getId()).orElseThrow();
    }

    private void assertActivada(Estudiante estudiante) {
        Estudiante activada = leer(estudiante);
        assertThat(activada.getEstadoCorreo()).isEqualTo(EstadoCorreo.CONFIRMADO);
        assertThat(activada.getNombre()).isEqualTo("Nombre Activado");
        assertThat(passwordEncoder.matches(NUEVA, activada.getContrasenia())).isTrue();
    }

    // Un login con la contraseña anterior (correcta) queda en su comparación; mientras, la cuenta se activa. Al
    // terminar, compensa su reserva: el contador vuelve a su valor y la activación no lo ha tocado
    @Test
    void unLoginCorrectoEnCursoCompensaSuReservaAunqueLaCuentaSeActive() throws Exception {
        Estudiante estudiante = pendiente(1);
        Pausa pausa = pausar(ANTERIOR);
        Future<LoginResponse> enCurso = retenido(estudiante, ANTERIOR, pausa);
        assertThat(leer(estudiante).getIntentosFallidos()).isEqualTo(2);

        activar(estudiante);
        assertThat(leer(estudiante).getIntentosFallidos()).isEqualTo(2);

        pausa.seguir().countDown();
        assertThat(enCurso.get(60, TimeUnit.SECONDS).autenticado()).isFalse();

        assertActivada(estudiante);
        assertThat(leer(estudiante).getIntentosFallidos()).isEqualTo(1);
        assertThat(leer(estudiante).getBloqueadoHasta()).isNull();
        assertThat(login(estudiante, NUEVA).autenticado()).isTrue();
    }

    // Un fallo en curso sigue contando después de la activación, y si con él se llega a tres la cuenta, ya
    // confirmada, queda bloqueada cinco minutos como cualquier otra
    @Test
    void unFalloEnCursoSigueContandoTrasLaActivacion() throws Exception {
        Estudiante estudiante = pendiente(2);
        Pausa pausa = pausar(INCORRECTA);
        Future<LoginResponse> enCurso = retenido(estudiante, INCORRECTA, pausa);

        activar(estudiante);
        pausa.seguir().countDown();
        enCurso.get(60, TimeUnit.SECONDS);

        assertActivada(estudiante);
        assertThat(leer(estudiante).getIntentosFallidos()).isEqualTo(3);
        Timestamp hasta = leer(estudiante).getBloqueadoHasta();
        assertThat(hasta).isNotNull();
        assertThat(hasta.toLocalDateTime()).isAfter(LocalDateTime.ofInstant(reloj.instant(), ZoneId.systemDefault()));
        assertThat(login(estudiante, NUEVA).autenticado()).isFalse();
    }

    // Logins con la contraseña anterior y con una incorrecta, simultáneos con la activación: al final la cuenta está
    // activada y el contador es exactamente el número de comparaciones fallidas. Un acierto con la contraseña
    // anterior (cuenta aún pendiente) se compensa; si el login lee la cuenta ya activada, esa contraseña es un fallo
    @Test
    void conLoginsSimultaneosLaActivacionNoAlteraElContador() throws Exception {
        for (int ronda = 0; ronda < 3; ronda++) {
            verificador.fallidas.set(0);
            Estudiante estudiante = pendiente(0);
            String token = tokens.generar(estudiante).valor();
            List<Future<?>> tareas = new ArrayList<>();
            CountDownLatch salida = new CountDownLatch(1);
            for (int i = 0; i < 12; i++) {
                String contrasenia = i % 2 == 0 ? INCORRECTA : ANTERIOR;
                tareas.add(segundoPlano.submit(() -> {
                    salida.await();
                    return login(estudiante, contrasenia);
                }));
            }
            tareas.add(segundoPlano.submit(() -> {
                salida.await();
                registroCuenta.activar(new ActivacionCuentaRequest(token, "Nombre Activado", NUEVA));
                return null;
            }));
            salida.countDown();
            for (Future<?> tarea : tareas) {
                tarea.get(60, TimeUnit.SECONDS);
            }

            assertActivada(estudiante);
            int fallos = leer(estudiante).getIntentosFallidos();
            assertThat(fallos).as("ronda %d", ronda).isEqualTo(verificador.fallidas.get());
            assertThat(leer(estudiante).getBloqueadoHasta() != null).as("ronda %d", ronda).isEqualTo(fallos >= 3);
        }
    }
}
