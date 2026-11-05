package com.saca.api.proteccion;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Limitador de solicitudes en memoria con ventanas deslizantes exactas y presupuesto de memoria.
//
// Ventanas: por cada política y clave se guardan los instantes (en milisegundos) de las solicitudes contadas,
// y cada una cuenta durante toda la duración de cada ventana: deja de contar justo al cumplirse.
//
// Varias claves: una solicitud puede tener que pasar varios contadores a la vez (por ejemplo, un prefijo IPv6
// /64 y su bloque /48). Se comprueban todos y, solo si todos la admiten y caben, se cuenta en todos; si no,
// en ninguno. Anular la reserva la quita de todos. Con reservarEnExistentesSiFaltaCapacidad, si lo único que
// falta es sitio para crear alguna clave, la solicitud se cuenta solo en las que ya existen (todas deben
// admitirla y tener sitio), sin crear ninguna.
//
// Memoria: cada limitador tiene un máximo de claves y un presupuesto en bytes. Lo contabilizado es una
// estimación conservadora (64 bits sin referencias comprimidas, ver BYTES_*) de todo lo que guarda: la tabla
// del mapa, preasignada para maxClaves, y por cada registro su nodo del mapa, su nodo del índice de
// vencimientos, la clave, el objeto y su arreglo con toda su capacidad. Nunca se asigna memoria sin haberla
// contabilizado antes, y una solicitud rechazada no asigna nada.
//
// Sin capacidad: una clave nueva solo se crea si cabe en el máximo de claves y deja libre al menos el
// RESERVA_CRECIMIENTO_PORCENTAJE del presupuesto, que queda para que crezcan las claves existentes. Si no
// cabe, o si una clave existente no puede crecer, se compactan antes hasta CLAVES_POR_BARRIDO registros y, si
// sigue sin caber, la solicitud se rechaza con sinCapacidad = true. Nunca se retira un contador con
// solicitudes vigentes para hacer sitio.
//
// Recuperación: un registro se retira, y su memoria vuelve al presupuesto, justo cuando su última solicitud
// sale de la ventana más larga. Además, los arreglos se reducen al descartar instantes vencidos (al usar la
// clave o en un barrido). El tiempo de reintento de un rechazo sin capacidad es el que falta para que, sin
// otras solicitudes, venzan registros suficientes; puede haber capacidad antes por esas reducciones, y otra
// solicitud puede ocuparla antes.
//
// Limitación: el estado vive en la memoria de esta instancia. Con varias instancias del backend cada una
// tendría sus propios contadores (los límites se multiplicarían) y un reinicio los pone a cero. Para más de
// una instancia haría falta un almacén compartido.
public final class LimitadorSolicitudes {

    private static final Logger log = LoggerFactory.getLogger(LimitadorSolicitudes.class);

    static final Duration DURACION_MAXIMA = Duration.ofDays(7);
    static final int CAPACIDAD_INICIAL = 4;
    static final int CLAVES_POR_BARRIDO = 64;
    static final int RESERVA_CRECIMIENTO_PORCENTAJE = 10;
    private static final long INTERVALO_AVISOS_MS = 60_000;

    // Estimación por objeto: cabeceras de 16 bytes (24 en arreglos), referencias de 8 y alineación a 8
    static final long BYTES_ESTRUCTURAS = 256;
    static final long BYTES_CUBETA = 8;
    static final long BYTES_NODO_MAPA = 48;
    static final long BYTES_NODO_INDICE = 64;
    static final long BYTES_REGISTRO = 96;
    static final long BYTES_CABECERA_ARREGLO = 24;
    static final long BYTES_TEXTO = 32;
    static final int BYTES_POR_INSTANTE = 4;

    public record Ventana(Duration duracion, int limite) {
        public Ventana {
            if (duracion.toMillis() <= 0 || duracion.compareTo(DURACION_MAXIMA) > 0 || limite <= 0) {
                throw new IllegalArgumentException("Ventana de límite no válida");
            }
        }
    }

    public record Politica(String nombre, List<Ventana> ventanas) {
        public Politica {
            ventanas = List.copyOf(ventanas);
            if (nombre.isBlank() || ventanas.isEmpty()) {
                throw new IllegalArgumentException("Política de límite no válida");
            }
        }
    }

    // Uno de los contadores que debe admitir una solicitud
    public record Peticion(Politica politica, String clave, int multiplicador) {
    }

    // sinCapacidad: rechazada por falta de memoria del limitador, no por el límite del cliente
    public record Resultado(boolean permitido, Duration reintentarEn, boolean sinCapacidad) {
        static final Resultado PERMITIDO = new Resultado(true, Duration.ZERO, false);
    }

    // Solicitud admitida y ya contada que puede anularse después, por ejemplo cuando un inicio de sesión
    // resulta correcto. Anular un rechazo, anular más de una vez o anular tras retirarse el registro no tiene
    // efecto
    public final class Reserva {
        private final Resultado resultado;
        private final Registro[] contados;
        private final long[] instantes;
        private boolean anulada;

        private Reserva(Resultado resultado, Registro[] contados, long[] instantes) {
            this.resultado = resultado;
            this.contados = contados;
            this.instantes = instantes;
        }

        public Resultado resultado() {
            return resultado;
        }

        public void anular() {
            synchronized (LimitadorSolicitudes.this) {
                if (!anulada) {
                    for (int i = 0; i < contados.length; i++) {
                        contados[i].quitar(instantes[i]);
                    }
                }
                anulada = true;
            }
        }
    }

    private Reserva rechazo(Resultado resultado) {
        return new Reserva(resultado, new Registro[0], new long[0]);
    }

    private final String nombre;
    private final Clock reloj;
    private final int maxClaves;
    private final long presupuesto;
    private final long umbralClavesNuevas;
    private final long bytesFijos;
    private final Map<String, Registro> registros;
    private final TreeSet<Registro> porVencimiento = new TreeSet<>(
            Comparator.comparingLong((Registro registro) -> registro.venceEn)
                    .thenComparingLong(registro -> registro.secuencia));
    private long usado;
    private long siguienteSecuencia;
    private long ultimoAviso;
    private boolean avisado;

    public LimitadorSolicitudes(String nombre, Clock reloj, int maxClaves, long presupuesto) {
        if (maxClaves <= 0) {
            throw new IllegalArgumentException("El máximo de claves debe ser positivo");
        }
        // Con esta capacidad inicial el mapa nunca se redimensiona por debajo de maxClaves
        int capacidadMapa = (int) Math.min(1 << 30, (long) Math.ceil(maxClaves / 0.75) + 1);
        this.nombre = nombre;
        this.reloj = reloj;
        this.maxClaves = maxClaves;
        this.presupuesto = presupuesto;
        this.umbralClavesNuevas = presupuesto - presupuesto * RESERVA_CRECIMIENTO_PORCENTAJE / 100;
        this.bytesFijos = BYTES_ESTRUCTURAS + BYTES_CUBETA * potenciaDeDosDesde(capacidadMapa);
        if (bytesFijos >= umbralClavesNuevas) {
            throw new IllegalArgumentException("Presupuesto insuficiente para el limitador " + nombre);
        }
        this.registros = new HashMap<>(capacidadMapa);
        this.usado = bytesFijos;
    }

    // Cuenta la solicitud solo si todas las ventanas la admiten y hay capacidad; si no, no cuenta en ninguna
    public Resultado consumir(Politica politica, String clave, int multiplicador) {
        return reservar(politica, clave, multiplicador).resultado();
    }

    // Como consumir, pero la solicitud contada puede anularse después
    public Reserva reservar(Politica politica, String clave, int multiplicador) {
        return reservar(List.of(new Peticion(politica, clave, multiplicador)));
    }

    // Comprobación, reserva de memoria y conteo en todas las claves son una sola operación atómica: varias
    // solicitudes simultáneas nunca superan ningún límite, y una rechazada no cuenta ni asigna nada
    public Reserva reservar(List<Peticion> peticiones) {
        return reservar(peticiones, false);
    }

    // Como reservar, pero si solo falta sitio para crear claves nuevas cuenta la solicitud en las existentes, en
    // la misma operación atómica. Si no existe ninguna o alguna no tiene sitio, el resultado es sinCapacidad y no
    // cuenta en ninguna. Si alguna existente está en su límite, se rechaza como siempre
    public Reserva reservarEnExistentesSiFaltaCapacidad(List<Peticion> peticiones) {
        return reservar(peticiones, true);
    }

    private synchronized Reserva reservar(List<Peticion> peticiones, boolean enExistentesSiFaltaCapacidad) {
        long ahora = reloj.millis();
        retirarVencidos(ahora);
        int total = peticiones.size();
        String[] claves = new String[total];
        int[] factores = new int[total];
        Registro[] existentes = new Registro[total];
        Duration espera = Duration.ZERO;
        for (int i = 0; i < total; i++) {
            Peticion peticion = peticiones.get(i);
            claves[i] = peticion.politica().nombre() + "|" + peticion.clave();
            for (int j = 0; j < i; j++) {
                if (claves[j].equals(claves[i])) {
                    throw new IllegalArgumentException("Clave repetida en una misma solicitud");
                }
            }
            factores[i] = Math.max(1, peticion.multiplicador());
            existentes[i] = registros.get(claves[i]);
            if (existentes[i] != null) {
                existentes[i].multiplicador = factores[i];
                Resultado resultado = existentes[i].evaluar(ahora);
                if (resultado.reintentarEn().compareTo(espera) > 0) {
                    espera = resultado.reintentarEn();
                }
            }
        }
        if (!espera.isZero()) {
            return rechazo(new Resultado(false, espera, false));
        }

        int nuevas = 0;
        long costeNuevas = 0;
        int llenas = 0;
        for (int i = 0; i < total; i++) {
            if (existentes[i] == null) {
                nuevas++;
                costeNuevas += costeRegistro(claves[i], capacidadMinima(peticiones.get(i).politica(), factores[i]));
            } else if (existentes[i].lleno()) {
                llenas++;
            }
        }
        if (costeNuevas > umbralClavesNuevas - bytesFijos) {
            throw new IllegalArgumentException("La solicitud no cabe en el presupuesto del limitador " + nombre);
        }
        if (!cabe(nuevas, costeNuevas, llenas)) {
            barrer(ahora, existentes);
            if (!cabe(nuevas, costeNuevas, llenas)) {
                if (enExistentesSiFaltaCapacidad && nuevas > 0 && nuevas < total && cabe(0, 0, llenas)) {
                    avisarSinCapacidad(ahora);
                    return contar(Arrays.stream(existentes).filter(registro -> registro != null)
                            .toArray(Registro[]::new), llenas, ahora);
                }
                return sinCapacidad(ahora, nuevas, costeNuevas, llenas);
            }
        }

        Registro[] contados = new Registro[total];
        for (int i = 0; i < total; i++) {
            if (existentes[i] == null) {
                contados[i] = new Registro(claves[i], peticiones.get(i).politica(), factores[i]);
                registros.put(claves[i], contados[i]);
                usado += contados[i].coste();
            } else {
                contados[i] = existentes[i];
            }
        }
        return contar(contados, llenas, ahora);
    }

    // Cuenta la solicitud en todos esos registros, ampliando los llenos. Quien llama ya comprobó límites y sitio
    private Reserva contar(Registro[] contados, int llenas, long ahora) {
        int total = contados.length;
        int porAmpliar = llenas;
        for (Registro registro : contados) {
            if (registro.lleno()) {
                porAmpliar--;
                ampliar(registro, porAmpliar);
            }
        }
        long[] instantes = new long[total];
        for (int i = 0; i < total; i++) {
            instantes[i] = contados[i].agregar(ahora);
        }
        return new Reserva(Resultado.PERMITIDO, contados, instantes);
    }

    // ================= consultas para pruebas; no alteran nada

    synchronized int numeroClaves() {
        return registros.size();
    }

    synchronized boolean contiene(Politica politica, String clave) {
        return registros.containsKey(politica.nombre() + "|" + clave);
    }

    synchronized int mayorCapacidad() {
        return registros.values().stream().mapToInt(registro -> registro.instantes.length).max().orElse(0);
    }

    synchronized long capacidadTotal() {
        return registros.values().stream().mapToLong(registro -> registro.instantes.length).sum();
    }

    synchronized long bytesUsados() {
        return usado;
    }

    // Lo que debería valer bytesUsados, recalculado desde cero
    synchronized long bytesRecalculados() {
        return bytesFijos + registros.values().stream().mapToLong(Registro::coste).sum();
    }

    synchronized long presupuesto() {
        return presupuesto;
    }

    // Instante (ms) en que la clave deja de tener solicitudes vigentes, o -1 si no está
    synchronized long venceEn(Politica politica, String clave) {
        Registro registro = registros.get(politica.nombre() + "|" + clave);
        return registro == null ? -1 : registro.venceEn;
    }

    synchronized int solicitudesGuardadas(Politica politica, String clave) {
        Registro registro = registros.get(politica.nombre() + "|" + clave);
        return registro == null ? 0 : registro.tamanio;
    }

    synchronized boolean indiceCoherente() {
        return porVencimiento.size() == registros.size()
                && porVencimiento.stream().allMatch(registro -> registros.get(registro.clave) == registro);
    }

    // ================= capacidad

    // Las claves nuevas caben en el máximo y dejan libre la reserva de crecimiento; cada registro lleno tiene
    // sitio al menos para un instante más
    private boolean cabe(int nuevas, long costeNuevas, int llenas) {
        return registros.size() + nuevas <= maxClaves
                && (nuevas == 0 || usado + costeNuevas <= umbralClavesNuevas)
                && usado + costeNuevas + (long) BYTES_POR_INSTANTE * llenas <= presupuesto;
    }

    // Amplía un arreglo lleno al doble, sin pasar de lo que admite su límite ni del presupuesto (dejando un
    // instante para cada uno de los demás registros llenos de la misma solicitud); si el doble no cabe, lo que
    // quepa. cabe() ya garantizó al menos un instante
    private void ampliar(Registro registro, int otrosLlenos) {
        int actual = registro.instantes.length;
        long deseada = Math.min(2L * actual, registro.capacidadMaxima());
        long libres = (presupuesto - usado) / BYTES_POR_INSTANTE - otrosLlenos;
        registro.redimensionar((int) Math.min(deseada, actual + libres));
    }

    private void retirarVencidos(long ahora) {
        while (!porVencimiento.isEmpty() && porVencimiento.first().venceEn <= ahora) {
            Registro registro = porVencimiento.pollFirst();
            registros.remove(registro.clave);
            registro.retirado = true;
            usado -= registro.coste();
        }
    }

    // Compacta los registros que vencen antes (los que llevan más tiempo sin solicitudes nuevas): descarta solo
    // instantes que ya salieron de todas sus ventanas y ajusta el arreglo a los vigentes. No cambia ningún
    // instante vigente ni el vencimiento, así que el orden del índice se mantiene
    private void barrer(long ahora, Registro[] excluidos) {
        int revisados = 0;
        for (Registro registro : porVencimiento) {
            if (revisados++ == CLAVES_POR_BARRIDO) {
                return;
            }
            if (!Arrays.asList(excluidos).contains(registro)) {
                registro.compactar(ahora);
            }
        }
    }

    private Reserva sinCapacidad(long ahora, int nuevas, long costeNuevas, int llenas) {
        avisarSinCapacidad(ahora);
        return rechazo(new Resultado(false, tiempoHastaCapacidad(ahora, nuevas, costeNuevas, llenas), true));
    }

    private void avisarSinCapacidad(long ahora) {
        if (!avisado || ahora - ultimoAviso >= INTERVALO_AVISOS_MS) {
            avisado = true;
            ultimoAviso = ahora;
            log.warn("Limitador {} sin capacidad: {} de {} bytes estimados y {} de {} claves; no se crean claves nuevas",
                    nombre, usado, presupuesto, registros.size(), maxClaves);
        }
    }

    // Recorre los registros en orden de vencimiento hasta liberar lo necesario. Siempre termina: si vencieran
    // todos, el limitador quedaría vacío y la solicitud cabe (se comprueba antes de llegar aquí)
    private Duration tiempoHastaCapacidad(long ahora, int nuevas, long costeNuevas, int llenas) {
        long faltanBytes = usado + costeNuevas + (long) BYTES_POR_INSTANTE * llenas - presupuesto;
        if (nuevas > 0) {
            faltanBytes = Math.max(faltanBytes, usado + costeNuevas - umbralClavesNuevas);
        }
        long faltanClaves = registros.size() + nuevas - (long) maxClaves;
        long liberados = 0;
        long retirados = 0;
        for (Registro registro : porVencimiento) {
            liberados += registro.coste();
            retirados++;
            if (liberados >= faltanBytes && retirados >= faltanClaves) {
                return Duration.ofMillis(Math.max(1, registro.venceEn - ahora));
            }
        }
        throw new IllegalStateException("El limitador " + nombre + " no puede recuperar capacidad");
    }

    private static long costeRegistro(String clave, int capacidad) {
        return BYTES_NODO_MAPA + BYTES_NODO_INDICE + BYTES_REGISTRO + costeTexto(clave)
                + BYTES_CABECERA_ARREGLO + (long) BYTES_POR_INSTANTE * capacidad;
    }

    // Se supone UTF-16 (2 bytes por carácter) aunque las claves sean ASCII
    private static long costeTexto(String texto) {
        return BYTES_TEXTO + alinear(BYTES_CABECERA_ARREGLO + 2L * texto.length());
    }

    private static long alinear(long bytes) {
        return (bytes + 7) & ~7L;
    }

    private static long potenciaDeDosDesde(int valor) {
        long potencia = 1;
        while (potencia < valor) {
            potencia <<= 1;
        }
        return potencia;
    }

    private static long capacidadMaxima(Politica politica, int multiplicador) {
        int mayorLimite = politica.ventanas().stream().mapToInt(Ventana::limite).max().orElseThrow();
        return (long) mayorLimite * multiplicador;
    }

    private static int capacidadMinima(Politica politica, int multiplicador) {
        return (int) Math.min(CAPACIDAD_INICIAL, capacidadMaxima(politica, multiplicador));
    }

    // Instantes en orden creciente en instantes[primero, primero + tamanio), como desplazamientos desde base.
    // venceEn = último instante + ventana más larga: desde entonces el registro no tiene información vigente.
    // Mientras está en el índice, venceEn solo cambia sacándolo y volviéndolo a meter
    private final class Registro {
        private final String clave;
        private final Politica politica;
        private final long duracionMaxima;
        private final long secuencia;
        private int multiplicador;
        private long base;
        private int[] instantes;
        private int primero;
        private int tamanio;
        private long venceEn;
        private boolean retirado;

        Registro(String clave, Politica politica, int multiplicador) {
            this.clave = clave;
            this.politica = politica;
            this.multiplicador = multiplicador;
            this.secuencia = siguienteSecuencia++;
            this.duracionMaxima = politica.ventanas().stream()
                    .mapToLong(ventana -> ventana.duracion().toMillis()).max().orElseThrow();
            this.instantes = new int[capacidadMinima(politica, multiplicador)];
        }

        long coste() {
            return costeRegistro(clave, instantes.length);
        }

        long capacidadMaxima() {
            return LimitadorSolicitudes.capacidadMaxima(politica, multiplicador);
        }

        boolean lleno() {
            return tamanio == instantes.length;
        }

        Resultado evaluar(long ahora) {
            descartarVencidos(ahora);
            reducirSiSobra();
            Duration espera = Duration.ZERO;
            for (Ventana ventana : politica.ventanas()) {
                long duracion = ventana.duracion().toMillis();
                long limite = (long) ventana.limite() * multiplicador;
                int desde = primeroVigente(ahora, duracion);
                long enVentana = tamanio - desde;
                if (enVentana >= limite) {
                    // Para admitir otra debe salir la que deja limite - 1 dentro de la ventana
                    long sale = instante(desde + (int) (enVentana - limite));
                    Duration hastaLiberar = Duration.ofMillis(Math.max(1, sale + duracion - ahora));
                    if (hastaLiberar.compareTo(espera) > 0) {
                        espera = hastaLiberar;
                    }
                }
            }
            return espera.isZero() ? Resultado.PERMITIDO : new Resultado(false, espera, false);
        }

        // Si el reloj retrocede, la solicitud se guarda con el último instante registrado: así se mantiene el
        // orden y ninguna deja de contar antes de tiempo. Quien llama ya comprobó límite y capacidad
        long agregar(long ahora) {
            if (tamanio >= capacidadMaxima() || lleno()) {
                throw new IllegalStateException("Registro de límite sin espacio");
            }
            long instante = tamanio == 0 ? ahora : Math.max(ahora, instante(tamanio - 1));
            if (tamanio == 0) {
                base = instante;
                primero = 0;
            } else if (instante - base > Integer.MAX_VALUE) {
                cambiarBase(instante(0));
            }
            if (primero + tamanio == instantes.length) {
                redimensionar(instantes.length);
            }
            instantes[primero + tamanio] = (int) (instante - base);
            tamanio++;
            cambiarVencimiento(instante + duracionMaxima);
            return instante;
        }

        void quitar(long instante) {
            if (retirado) {
                return;
            }
            int posicion = buscar(instante);
            if (posicion < 0) {
                return;
            }
            int absoluta = primero + posicion;
            System.arraycopy(instantes, absoluta + 1, instantes, absoluta, tamanio - posicion - 1);
            tamanio--;
            // Sin instantes, vence ya y se retira en la siguiente operación
            cambiarVencimiento(tamanio == 0 ? Long.MIN_VALUE : instante(tamanio - 1) + duracionMaxima);
        }

        void compactar(long ahora) {
            descartarVencidos(ahora);
            int ajustada = Math.max(capacidadMinima(politica, multiplicador), tamanio);
            if (ajustada < instantes.length) {
                redimensionar(ajustada);
            }
        }

        // Copia los instantes vigentes al principio de un arreglo de esa capacidad y ajusta lo contabilizado
        void redimensionar(int capacidad) {
            int[] nuevos = capacidad == instantes.length ? instantes : new int[capacidad];
            System.arraycopy(instantes, primero, nuevos, 0, tamanio);
            usado += (long) BYTES_POR_INSTANTE * (capacidad - instantes.length);
            instantes = nuevos;
            primero = 0;
        }

        private void cambiarVencimiento(long nuevo) {
            porVencimiento.remove(this);
            venceEn = nuevo;
            porVencimiento.add(this);
        }

        // Solo descarta instantes que ya salieron de la ventana más larga, que no cuentan en ninguna
        private void descartarVencidos(long ahora) {
            int vencidos = primeroVigente(ahora, duracionMaxima);
            primero += vencidos;
            tamanio -= vencidos;
            if (tamanio == 0) {
                primero = 0;
            }
        }

        // A la mitad cuando queda ocupado en una cuarta parte o menos
        private void reducirSiSobra() {
            int minima = capacidadMinima(politica, multiplicador);
            if (instantes.length > minima && tamanio * 4 <= instantes.length) {
                redimensionar(Math.max(minima, instantes.length / 2));
            }
        }

        // Índice relativo de la primera solicitud que sigue contando en una ventana de esa duración
        private int primeroVigente(long ahora, long duracion) {
            long limiteInferior = ahora - duracion;
            int bajo = 0;
            int alto = tamanio;
            while (bajo < alto) {
                int medio = (bajo + alto) >>> 1;
                if (instante(medio) > limiteInferior) {
                    alto = medio;
                } else {
                    bajo = medio + 1;
                }
            }
            return bajo;
        }

        private int buscar(long instante) {
            for (int i = tamanio - 1; i >= 0; i--) {
                long actual = instante(i);
                if (actual == instante) {
                    return i;
                }
                if (actual < instante) {
                    return -1;
                }
            }
            return -1;
        }

        private long instante(int indice) {
            return base + instantes[primero + indice];
        }

        private void cambiarBase(long nuevaBase) {
            int diferencia = (int) (nuevaBase - base);
            for (int i = 0; i < tamanio; i++) {
                instantes[primero + i] -= diferencia;
            }
            base = nuevaBase;
        }
    }
}
