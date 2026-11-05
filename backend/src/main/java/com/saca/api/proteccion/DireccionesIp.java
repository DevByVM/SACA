package com.saca.api.proteccion;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

// Análisis de direcciones IP literales y de rangos CIDR. Solo se aceptan literales IPv4 o IPv6:
// nunca nombres de host, para que una cabecera no pueda provocar consultas DNS.
final class DireccionesIp {

    static final int PREFIJO_BLOQUE = 48;

    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

    private DireccionesIp() {
    }

    // Devuelve la dirección si el texto es un literal IP válido. Una IPv4 dentro de IPv6 (::ffff:a.b.c.d)
    // se trata como IPv4
    static Optional<InetAddress> analizar(String texto) {
        if (texto == null) {
            return Optional.empty();
        }
        String limpio = texto.trim();
        if (limpio.startsWith("[") && limpio.endsWith("]")) {
            limpio = limpio.substring(1, limpio.length() - 1);
        }
        var ipv4 = IPV4.matcher(limpio);
        if (ipv4.matches()) {
            byte[] bytes = new byte[4];
            for (int i = 0; i < 4; i++) {
                int octeto = Integer.parseInt(ipv4.group(i + 1));
                if (octeto > 255) {
                    return Optional.empty();
                }
                bytes[i] = (byte) octeto;
            }
            return porBytes(bytes);
        }
        // Entre corchetes Java exige un literal IPv6 y, si no lo es, falla sin consultar DNS
        if (limpio.indexOf(':') >= 0 && IPV6.matcher(limpio).matches()) {
            try {
                return Optional.of(InetAddress.getByName("[" + limpio + "]"));
            } catch (UnknownHostException | SecurityException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    // Clave de agrupación: IPv4 completa; IPv6 por su prefijo /64, que suele asignarse a un solo cliente
    static String clave(InetAddress direccion) {
        if (direccion instanceof Inet4Address) {
            return "ip4:" + direccion.getHostAddress();
        }
        return prefijoIpv6(direccion, 4);
    }

    // Clave del bloque /48 que contiene una dirección IPv6; null para IPv4, que no se agrega
    static String claveAgregada(InetAddress direccion) {
        return direccion instanceof Inet4Address ? null : prefijoIpv6(direccion, 3);
    }

    // Si el bloque /48 de una dirección IPv6 tiene alguna dirección en común con alguno de los rangos, aunque la
    // propia dirección quede fuera (un /56 declarado dentro del bloque, o un /32 que lo contiene). false en IPv4
    static boolean bloqueIntersecta(InetAddress direccion, List<Cidr> rangos) {
        if (direccion instanceof Inet4Address) {
            return false;
        }
        for (Cidr rango : rangos) {
            if (rango.intersecta(direccion.getAddress(), PREFIJO_BLOQUE)) {
                return true;
            }
        }
        return false;
    }

    // Si a y b coinciden en sus primeros bits
    private static boolean mismosBits(byte[] a, byte[] b, int bits) {
        int completos = bits / 8;
        for (int i = 0; i < completos; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        int resto = bits % 8;
        if (resto == 0) {
            return true;
        }
        int mascara = (0xff << (8 - resto)) & 0xff;
        return (a[completos] & mascara) == (b[completos] & mascara);
    }

    private static String prefijoIpv6(InetAddress direccion, int grupos) {
        byte[] bytes = direccion.getAddress();
        StringBuilder prefijo = new StringBuilder("ip6:");
        for (int i = 0; i < grupos * 2; i += 2) {
            prefijo.append(Integer.toHexString(((bytes[i] & 0xff) << 8) | (bytes[i + 1] & 0xff))).append(':');
        }
        return prefijo.append(":/").append(grupos * 16).toString();
    }

    static List<Cidr> analizarRangos(String lista, String variable) {
        List<Cidr> rangos = new ArrayList<>();
        if (lista == null || lista.isBlank()) {
            return rangos;
        }
        for (String parte : lista.split(",")) {
            if (!parte.isBlank()) {
                rangos.add(Cidr.analizar(parte.trim(), variable));
            }
        }
        return List.copyOf(rangos);
    }

    static boolean contenida(InetAddress direccion, List<Cidr> rangos) {
        for (Cidr rango : rangos) {
            if (rango.contiene(direccion)) {
                return true;
            }
        }
        return false;
    }

    private static Optional<InetAddress> porBytes(byte[] bytes) {
        try {
            return Optional.of(InetAddress.getByAddress(bytes));
        } catch (UnknownHostException e) {
            return Optional.empty();
        }
    }

    // Rango CIDR (a.b.c.d/n o x:y::/n); sin prefijo es una sola dirección
    record Cidr(byte[] red, int prefijo) {

        static Cidr analizar(String texto, String variable) {
            String[] partes = texto.split("/", -1);
            Optional<InetAddress> direccion = partes.length <= 2 ? DireccionesIp.analizar(partes[0]) : Optional.empty();
            if (direccion.isEmpty()) {
                throw new IllegalStateException(variable + " contiene un rango no válido: " + texto);
            }
            byte[] bytes = direccion.get().getAddress();
            int maximo = bytes.length * 8;
            int prefijo = maximo;
            if (partes.length == 2) {
                try {
                    prefijo = Integer.parseInt(partes[1]);
                } catch (NumberFormatException e) {
                    prefijo = -1;
                }
            }
            if (prefijo < 0 || prefijo > maximo) {
                throw new IllegalStateException(variable + " contiene un rango no válido: " + texto);
            }
            return new Cidr(bytes, prefijo);
        }

        boolean contiene(InetAddress direccion) {
            byte[] bytes = direccion.getAddress();
            return bytes.length == red.length && mismosBits(bytes, red, prefijo);
        }

        // Dos rangos CIDR se solapan si y solo si coinciden en los bits del prefijo más corto: entonces uno
        // contiene al otro
        boolean intersecta(byte[] otraRed, int otroPrefijo) {
            return otraRed.length == red.length && mismosBits(otraRed, red, Math.min(prefijo, otroPrefijo));
        }
    }
}
