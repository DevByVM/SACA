package com.saca.api.proteccion;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.saca.api.proteccion.LimitadorSolicitudes.Peticion;
import com.saca.api.proteccion.LimitadorSolicitudes.Politica;
import com.saca.api.proteccion.LimitadorSolicitudes.Reserva;
import com.saca.api.proteccion.LimitadorSolicitudes.Resultado;
import com.saca.api.proteccion.LimitadorSolicitudes.Ventana;
import com.saca.api.proteccion.ResolutorIpCliente.ClienteSolicitud;

// Límites por cliente (IP o prefijo IPv6 /64) frente al abuso. Las redes compartidas declaradas
// (SACA_IP_REDES_COMPARTIDAS) multiplican los límites, sin quedar exentas. Todos los valores se configuran
// en application.properties mediante variables de entorno.
//
// IPv6: además del contador del prefijo /64, cada solicitud cuenta en el único contador de su bloque /48, con
// los mismos límites multiplicados por SACA_LIMITE_MULTIPLICADOR_REDES_COMPARTIDAS (×10 con el valor por
// defecto), y por su cuadrado (×100) si el bloque se solapa con algún rango declarado compartido: entonces
// todo el bloque comparte esa cuota, esté o no cada dirección dentro del rango. El /64 usa el multiplicador de
// su propia dirección (×1 o ×10). Los dos se reservan a la vez: si uno rechaza, no cuenta en ninguno. Así,
// repartir solicitudes entre muchos /64 de un mismo /48 no amplía su límite, y un /48 no puede ocupar más
// claves que solicitudes le admite su contador. Las claves de bloque ocupan el mismo presupuesto y el mismo
// máximo de claves de su grupo. IPv4 solo tiene el contador por dirección. Queda el riesgo de quien reparte
// entre muchos bloques /48 distintos.
//
// - Login: solo cuentan los intentos fallidos, con umbrales altos, para que una IP compartida no deje fuera
//   a quien escribe bien su contraseña. Es independiente del bloqueo de 3 fallos por cuenta durante 5 minutos.
//   Cada intento reserva su fallo antes de comparar la contraseña y lo devuelve si resulta correcto: así los
//   intentos simultáneos no pueden pasar todos la comprobación antes de que se cuente ninguno.
// - Registro y reenvío de activación comparten contador; consulta y activación tienen el suyo.
// - El límite global de correos protege el SMTP aunque el abuso llegue desde muchas IP: quien envíe debe
//   reservar antes cada correo y no enviarlo si la reserva falla.
//
// Memoria: cada grupo tiene su propio limitador, con su presupuesto y su parte de SACA_LIMITE_MAX_CLAVES
// (login 50 %, registro y reenvío 30 %, consulta y activación el resto), de modo que agotar uno no afecta a
// los demás. En total, como mucho 56 MiB estimados. El de correo es aparte y solo tiene una clave.
// Sin capacidad:
// - Login: si ya existen contadores para el cliente (por ejemplo, el de su /48 aunque su /64 sea nuevo), el
//   intento se cuenta en ellos, sin crear claves, y respetan su límite. Si no hay ninguno, el intento sigue sin
//   contarse por IP mientras dure; el bloqueo por cuenta se aplica igual. Un 429 general dejaría sin acceso a
//   quien escribe bien su contraseña, y el bloqueo por cuenta es la defensa principal frente a quien prueba
//   contraseñas.
// - Registro, reenvío, consulta y activación: 429 con el tiempo que falta para recuperar capacidad.
//
// Estado en memoria: válido solo con una instancia del backend (ver LimitadorSolicitudes).
@Service
public class ProteccionSolicitudes {

    static final String MENSAJE_LOGIN = "Demasiados intentos de inicio de sesión desde tu red. Inténtalo de nuevo más tarde.";
    static final String MENSAJE_SOLICITUDES = "Demasiadas solicitudes desde tu red. Inténtalo de nuevo en unos minutos.";
    private static final String CLAVE_GLOBAL = "global";

    static final long PRESUPUESTO_LOGIN = 32L << 20;
    static final long PRESUPUESTO_REGISTRO = 16L << 20;
    static final long PRESUPUESTO_ACTIVACION = 8L << 20;
    static final long PRESUPUESTO_CORREO = 64L << 10;
    static final int MINIMO_MAX_CLAVES = 10;

    private final LimitadorSolicitudes limitadorLogin;
    private final LimitadorSolicitudes limitadorRegistro;
    private final LimitadorSolicitudes limitadorActivacion;
    private final LimitadorSolicitudes limitadorCorreo;
    private final int multiplicadorRedesCompartidas;
    private final Politica fallosLogin;
    private final Politica registroYReenvio;
    private final Politica consultaYActivacion;
    private final Politica fallosLoginBloque;
    private final Politica registroYReenvioBloque;
    private final Politica consultaYActivacionBloque;
    private final Politica correoGlobal;

    @Autowired
    public ProteccionSolicitudes(
            Clock relojAplicacion,
            @Value("${saca.limites.max-claves:50000}") int maxClaves,
            @Value("${saca.limites.multiplicador-redes-compartidas:10}") int multiplicadorRedesCompartidas,
            @Value("${saca.limites.login.fallos-por-10-minutos:30}") int fallosLogin10Minutos,
            @Value("${saca.limites.login.fallos-por-24-horas:200}") int fallosLogin24Horas,
            @Value("${saca.limites.registro.por-10-minutos:20}") int registro10Minutos,
            @Value("${saca.limites.registro.por-24-horas:100}") int registro24Horas,
            @Value("${saca.limites.activacion.por-minuto:30}") int activacionPorMinuto,
            @Value("${saca.limites.correo.por-hora:500}") int correosPorHora) {
        this(relojAplicacion, maxClaves, multiplicadorRedesCompartidas, fallosLogin10Minutos, fallosLogin24Horas,
                registro10Minutos, registro24Horas, activacionPorMinuto, correosPorHora,
                PRESUPUESTO_LOGIN, PRESUPUESTO_REGISTRO, PRESUPUESTO_ACTIVACION);
    }

    // Presupuestos a medida: solo para pruebas
    ProteccionSolicitudes(Clock relojAplicacion, int maxClaves, int multiplicadorRedesCompartidas,
            int fallosLogin10Minutos, int fallosLogin24Horas, int registro10Minutos, int registro24Horas,
            int activacionPorMinuto, int correosPorHora,
            long presupuestoLogin, long presupuestoRegistro, long presupuestoActivacion) {
        if (multiplicadorRedesCompartidas < 1) {
            throw new IllegalStateException("SACA_LIMITE_MULTIPLICADOR_REDES_COMPARTIDAS debe ser al menos 1");
        }
        if (maxClaves < MINIMO_MAX_CLAVES) {
            throw new IllegalStateException("SACA_LIMITE_MAX_CLAVES debe ser al menos " + MINIMO_MAX_CLAVES);
        }
        int clavesLogin = maxClaves / 2;
        int clavesRegistro = (int) (maxClaves * 3L / 10);
        int clavesActivacion = maxClaves - clavesLogin - clavesRegistro;
        this.limitadorLogin = new LimitadorSolicitudes("login", relojAplicacion, clavesLogin, presupuestoLogin);
        this.limitadorRegistro = new LimitadorSolicitudes("registro-reenvio", relojAplicacion, clavesRegistro,
                presupuestoRegistro);
        this.limitadorActivacion = new LimitadorSolicitudes("consulta-activacion", relojAplicacion,
                clavesActivacion, presupuestoActivacion);
        this.limitadorCorreo = new LimitadorSolicitudes("correo-global", relojAplicacion, 1, PRESUPUESTO_CORREO);
        this.multiplicadorRedesCompartidas = multiplicadorRedesCompartidas;
        this.fallosLogin = new Politica("login-fallos", List.of(
                new Ventana(Duration.ofMinutes(10), fallosLogin10Minutos),
                new Ventana(Duration.ofHours(24), fallosLogin24Horas)));
        this.registroYReenvio = new Politica("registro-reenvio", List.of(
                new Ventana(Duration.ofMinutes(10), registro10Minutos),
                new Ventana(Duration.ofHours(24), registro24Horas)));
        this.consultaYActivacion = new Politica("consulta-activacion", List.of(
                new Ventana(Duration.ofMinutes(1), activacionPorMinuto)));
        this.fallosLoginBloque = deBloque(fallosLogin);
        this.registroYReenvioBloque = deBloque(registroYReenvio);
        this.consultaYActivacionBloque = deBloque(consultaYActivacion);
        this.correoGlobal = new Politica("correo-global", List.of(
                new Ventana(Duration.ofHours(1), correosPorHora)));
    }

    // ================= login

    // Antes de comprobar credenciales: con demasiados fallos recientes desde este cliente se responde 429
    // sin tocar la cuenta (ni su contador de intentos ni su bloqueo). Si no, el intento queda contado como
    // fallo hasta que se confirme que fue correcto con loginCorrecto. Sin capacidad, el intento sigue sin
    // contarse: la reserva devuelta no guarda nada
    public Reserva reservarIntentoLogin(ClienteSolicitud cliente) {
        Reserva reserva = limitadorLogin.reservarEnExistentesSiFaltaCapacidad(
                peticiones(fallosLogin, fallosLoginBloque, cliente));
        if (!reserva.resultado().sinCapacidad()) {
            exigir(reserva.resultado(), MENSAJE_LOGIN);
        }
        return reserva;
    }

    public void loginCorrecto(Reserva intento) {
        intento.anular();
    }

    // ================= registro, reenvío, consulta y activación de cuentas, y envío de correos

    public void consumirRegistroOReenvio(ClienteSolicitud cliente) {
        exigir(limitadorRegistro.reservar(peticiones(registroYReenvio, registroYReenvioBloque, cliente)).resultado(),
                MENSAJE_SOLICITUDES);
    }

    public void consumirConsultaOActivacion(ClienteSolicitud cliente) {
        exigir(limitadorActivacion.reservar(peticiones(consultaYActivacion, consultaYActivacionBloque, cliente))
                .resultado(), MENSAJE_SOLICITUDES);
    }

    // true si queda cupo y lo reserva; false si se alcanzó el máximo de correos por hora (no debe enviarse)
    public boolean reservarEnvioCorreo() {
        return limitadorCorreo.consumir(correoGlobal, CLAVE_GLOBAL, 1).permitido();
    }

    // ================= para pruebas

    LimitadorSolicitudes limitadorLogin() {
        return limitadorLogin;
    }

    LimitadorSolicitudes limitadorRegistro() {
        return limitadorRegistro;
    }

    LimitadorSolicitudes limitadorActivacion() {
        return limitadorActivacion;
    }

    // El contador del cliente y, en IPv6, el de su bloque /48
    private List<Peticion> peticiones(Politica individual, Politica bloque, ClienteSolicitud cliente) {
        Peticion propia = new Peticion(individual, cliente.clave(),
                cliente.redCompartida() ? multiplicadorRedesCompartidas : 1);
        if (cliente.claveAgregada() == null) {
            return List.of(propia);
        }
        int factorBloque = (cliente.bloqueCompartido() ? multiplicadorRedesCompartidas : 1) * multiplicadorRedesCompartidas;
        return List.of(propia, new Peticion(bloque, cliente.claveAgregada(), factorBloque));
    }

    private static Politica deBloque(Politica individual) {
        return new Politica(individual.nombre() + "/48", individual.ventanas());
    }

    private static void exigir(Resultado resultado, String mensaje) {
        if (!resultado.permitido()) {
            throw new LimiteSolicitudesExcedidoException(mensaje, resultado.reintentarEn());
        }
    }
}
