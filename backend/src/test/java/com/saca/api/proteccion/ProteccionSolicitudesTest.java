package com.saca.api.proteccion;

import com.saca.api.RelojAjustable;
import com.saca.api.proteccion.LimitadorSolicitudes.Reserva;
import com.saca.api.proteccion.ResolutorIpCliente.ClienteSolicitud;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Límites por defecto, cargados de application.properties sin sobrescribir ningún valor
// (registro y reenvío 20/10 min y 100/24 h, consulta y activación 30/min, correo global 500/h, redes
// compartidas ×10), y presupuesto y máximo de claves por grupo. Las operaciones de protección que usan el
// registro, el reenvío, la activación y el envío de correos se prueban aquí sin pasar por los endpoints
@SpringBootTest(properties = "SACA_IP_REDES_COMPARTIDAS=198.51.100.0/24")
@ActiveProfiles("test")
class ProteccionSolicitudesTest {

    private static final Instant INICIO = Instant.parse("2026-11-04T00:00:00Z");

    @TestConfiguration
    static class ConfiguracionReloj {
        @Bean
        @Primary
        RelojAjustable relojDePrueba() {
            return new RelojAjustable(INICIO, ZoneId.of("America/El_Salvador"));
        }
    }

    @Autowired
    private ProteccionSolicitudes proteccion;

    @Autowired
    private ResolutorIpCliente resolutor;

    @Autowired
    private RelojAjustable reloj;

    private static ClienteSolicitud cliente(String clave) {
        return new ClienteSolicitud(clave, false);
    }

    private static int veces(Runnable accion) {
        int permitidas = 0;
        while (true) {
            try {
                accion.run();
                permitidas++;
            } catch (LimiteSolicitudesExcedidoException e) {
                return permitidas;
            }
        }
    }

    @Test
    void registroYReenvioCompartenVeintePorDiezMinutosYCienPorDia() {
        reloj.fijar(INICIO);
        ClienteSolicitud ip = cliente("ip4:192.0.2.10");

        assertThat(veces(() -> proteccion.consumirRegistroOReenvio(ip))).isEqualTo(20);
        for (int bloque = 1; bloque < 5; bloque++) {
            reloj.fijar(INICIO.plus(Duration.ofMinutes(10L * bloque)));
            assertThat(veces(() -> proteccion.consumirRegistroOReenvio(ip))).as("bloque %d", bloque).isEqualTo(20);
        }

        // 100 en el día: la ventana de 10 minutos vuelve a tener cupo, pero la diaria no
        reloj.fijar(INICIO.plus(Duration.ofMinutes(50)));
        LimiteSolicitudesExcedidoException rechazo = null;
        try {
            proteccion.consumirRegistroOReenvio(ip);
        } catch (LimiteSolicitudesExcedidoException e) {
            rechazo = e;
        }
        assertThat(rechazo).isNotNull();
        assertThat(rechazo.getMessage()).isEqualTo("Demasiadas solicitudes desde tu red. Inténtalo de nuevo en unos minutos.");
        assertThat(rechazo.segundosParaReintentar()).isEqualTo(Duration.ofHours(24).minusMinutes(50).toSeconds());

        reloj.fijar(INICIO.plus(Duration.ofHours(24)));
        assertThat(veces(() -> proteccion.consumirRegistroOReenvio(ip))).isEqualTo(20);
    }

    @Test
    void consultaYActivacionTienenTreintaPorMinuto() {
        reloj.fijar(INICIO);
        ClienteSolicitud ip = cliente("ip4:192.0.2.20");

        assertThat(veces(() -> proteccion.consumirConsultaOActivacion(ip))).isEqualTo(30);
        reloj.fijar(INICIO.plusSeconds(60));
        assertThat(veces(() -> proteccion.consumirConsultaOActivacion(ip))).isEqualTo(30);
    }

    @Test
    void cadaGrupoTieneSuPropioContador() {
        reloj.fijar(INICIO);
        ClienteSolicitud ip = cliente("ip4:192.0.2.30");

        assertThat(veces(() -> proteccion.consumirRegistroOReenvio(ip))).isEqualTo(20);
        assertThat(veces(() -> proteccion.consumirConsultaOActivacion(ip))).isEqualTo(30);
        assertThat(veces(() -> proteccion.consumirRegistroOReenvio(cliente("ip4:192.0.2.31")))).isEqualTo(20);
    }

    @Test
    void lasRedesCompartidasDeclaradasTienenDiezVecesMasMargen() {
        reloj.fijar(INICIO);
        ClienteSolicitud compartida = resolutor.resolver(solicitud("198.51.100.40"));
        assertThat(compartida.redCompartida()).isTrue();

        assertThat(veces(() -> proteccion.consumirRegistroOReenvio(compartida))).isEqualTo(200);
        assertThat(veces(() -> proteccion.consumirConsultaOActivacion(compartida))).isEqualTo(300);
    }

    @Test
    void elLimiteGlobalDeCorreosEsDeQuinientosPorHora() {
        reloj.fijar(INICIO.plus(Duration.ofDays(10)));
        int reservados = 0;
        while (proteccion.reservarEnvioCorreo()) {
            reservados++;
            assertThat(reservados).isLessThanOrEqualTo(500);
        }

        assertThat(reservados).isEqualTo(500);
        reloj.avanzar(Duration.ofMinutes(59));
        assertThat(proteccion.reservarEnvioCorreo()).isFalse();
        reloj.avanzar(Duration.ofMinutes(1));
        assertThat(proteccion.reservarEnvioCorreo()).isTrue();
    }

    // ================= presupuesto y máximo de claves por grupo

    // 50 claves: login 25, registro y reenvío 15, consulta y activación 10
    private ProteccionSolicitudes conCincuentaClaves(RelojAjustable relojPropio) {
        return new ProteccionSolicitudes(relojPropio, 50, 10, 30, 200, 20, 100, 30, 3);
    }

    private static String ip6(int i) {
        return "ip6:2001:db8:" + Integer.toHexString(i) + "::/64";
    }

    private static int clientesAdmitidos(java.util.function.Consumer<ClienteSolicitud> accion) {
        int admitidos = 0;
        for (int i = 0; i < 1_000; i++) {
            try {
                accion.accept(cliente(ip6(i)));
                admitidos++;
            } catch (LimiteSolicitudesExcedidoException e) {
                // sin capacidad para un cliente nuevo
            }
        }
        return admitidos;
    }

    @Test
    void elMaximoDeClavesSeRepartePorGrupo() {
        RelojAjustable relojPropio = new RelojAjustable(INICIO, ZoneId.of("America/El_Salvador"));
        ProteccionSolicitudes acotada = conCincuentaClaves(relojPropio);

        assertThat(clientesAdmitidos(acotada::consumirRegistroOReenvio)).isEqualTo(15);
        assertThat(clientesAdmitidos(acotada::consumirConsultaOActivacion)).isEqualTo(10);
        for (int i = 0; i < 1_000; i++) {
            acotada.reservarIntentoLogin(cliente(ip6(i)));
        }
        assertThat(acotada.limitadorLogin().numeroClaves()).isEqualTo(25);
    }

    // Llenar la consulta y activación (o el registro) no quita capacidad a los demás grupos ni al correo
    @Test
    void agotarUnGrupoNoAfectaALosDemasNiAlCorreo() {
        RelojAjustable relojPropio = new RelojAjustable(INICIO, ZoneId.of("America/El_Salvador"));
        ProteccionSolicitudes acotada = conCincuentaClaves(relojPropio);
        ClienteSolicitud insistente = cliente("ip4:192.0.2.90");
        assertThat(veces(() -> acotada.consumirRegistroOReenvio(insistente))).isEqualTo(20);
        for (int i = 0; i < 3; i++) {
            assertThat(acotada.reservarEnvioCorreo()).isTrue();
        }

        assertThat(clientesAdmitidos(acotada::consumirConsultaOActivacion)).isEqualTo(10);

        // El contador lleno sigue lleno, el registro admite clientes nuevos, el login reserva con normalidad
        // y el correo conserva su cuenta
        assertThat(veces(() -> acotada.consumirRegistroOReenvio(insistente))).isZero();
        assertThat(veces(() -> acotada.consumirRegistroOReenvio(cliente("ip4:192.0.2.91")))).isEqualTo(20);
        assertThat(acotada.reservarIntentoLogin(cliente("ip4:192.0.2.92")).resultado().permitido()).isTrue();
        assertThat(acotada.reservarEnvioCorreo()).isFalse();

        ProteccionSolicitudes otra = conCincuentaClaves(relojPropio);
        assertThat(clientesAdmitidos(otra::consumirRegistroOReenvio)).isEqualTo(15);
        assertThat(clientesAdmitidos(otra::consumirConsultaOActivacion)).isEqualTo(10);
    }

    // Sin capacidad, registro y activación responden 429 con el tiempo real hasta que vence un registro
    // (24 horas en registro, aunque su ventana corta sea de 10 minutos)
    @Test
    void sinCapacidadRegistroYActivacionRespondenConElTiempoReal() {
        RelojAjustable relojPropio = new RelojAjustable(INICIO, ZoneId.of("America/El_Salvador"));
        ProteccionSolicitudes acotada = conCincuentaClaves(relojPropio);
        assertThat(clientesAdmitidos(acotada::consumirRegistroOReenvio)).isEqualTo(15);
        assertThat(clientesAdmitidos(acotada::consumirConsultaOActivacion)).isEqualTo(10);

        relojPropio.fijar(INICIO.plus(Duration.ofMinutes(2)));
        LimiteSolicitudesExcedidoException registro = rechazo(
                () -> acotada.consumirRegistroOReenvio(cliente("ip4:192.0.2.93")));
        assertThat(registro.getMessage()).isEqualTo(ProteccionSolicitudes.MENSAJE_SOLICITUDES);
        assertThat(registro.segundosParaReintentar()).isEqualTo(Duration.ofHours(24).minusMinutes(2).toSeconds());

        relojPropio.fijar(INICIO.plusSeconds(20));
        assertThat(rechazo(() -> acotada.consumirConsultaOActivacion(cliente("ip4:192.0.2.94")))
                .segundosParaReintentar()).isEqualTo(40);
        relojPropio.fijar(INICIO.plusSeconds(60));
        acotada.consumirConsultaOActivacion(cliente("ip4:192.0.2.94"));
    }

    // Sin capacidad en el login, el intento sigue sin contarse por IP: no se lanza 429, no se reserva nada
    // y anular esa reserva no tiene efecto
    @Test
    void sinCapacidadEnElLoginElIntentoSigueSinContarse() {
        RelojAjustable relojPropio = new RelojAjustable(INICIO, ZoneId.of("America/El_Salvador"));
        ProteccionSolicitudes acotada = conCincuentaClaves(relojPropio);
        for (int i = 0; i < 25; i++) {
            acotada.reservarIntentoLogin(cliente(ip6(i)));
        }
        LimitadorSolicitudes login = acotada.limitadorLogin();
        long usados = login.bytesUsados();

        ClienteSolicitud nuevo = cliente("ip4:192.0.2.95");
        for (int i = 0; i < 100; i++) {
            Reserva intento = acotada.reservarIntentoLogin(nuevo);
            assertThat(intento.resultado().sinCapacidad()).isTrue();
            acotada.loginCorrecto(intento);
        }

        assertThat(login.bytesUsados()).isEqualTo(usados);
        assertThat(login.numeroClaves()).isEqualTo(25);
        // Un cliente que ya tenía contador sigue limitado con normalidad
        ClienteSolicitud conocido = cliente(ip6(0));
        assertThat(veces(() -> acotada.reservarIntentoLogin(conocido))).isEqualTo(29);
    }

    @Test
    void unMaximoDeClavesMenorQueDiezImpideArrancar() {
        assertThatThrownBy(() -> new ProteccionSolicitudes(Clock.systemUTC(), 9, 10, 30, 200, 20, 100, 30, 500))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SACA_LIMITE_MAX_CLAVES");
    }

    private static LimiteSolicitudesExcedidoException rechazo(Runnable accion) {
        try {
            accion.run();
        } catch (LimiteSolicitudesExcedidoException e) {
            return e;
        }
        throw new AssertionError("Se esperaba un 429");
    }

    @Test
    void unMultiplicadorMenorQueUnoImpideArrancar() {
        assertThatThrownBy(() -> new ProteccionSolicitudes(Clock.systemUTC(), 50_000, 0, 30, 200, 20, 100, 30, 500))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SACA_LIMITE_MULTIPLICADOR_REDES_COMPARTIDAS");
    }

    private static MockHttpServletRequest solicitud(String ip) {
        MockHttpServletRequest solicitud = new MockHttpServletRequest();
        solicitud.setRemoteAddr(ip);
        return solicitud;
    }
}
