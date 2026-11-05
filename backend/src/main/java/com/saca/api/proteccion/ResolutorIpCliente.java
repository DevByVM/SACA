package com.saca.api.proteccion;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.saca.api.proteccion.DireccionesIp.Cidr;

import jakarta.servlet.http.HttpServletRequest;

// Identifica al cliente de una solicitud para los límites. La fuente es la dirección remota de la
// conexión. X-Forwarded-For solo se lee si esa dirección es un proxy declarado en SACA_PROXIES_CONFIABLES;
// entonces se recorre de derecha a izquierda saltando los proxies confiables, de modo que lo que añada el
// cliente a la izquierda nunca se use. Las demás cabeceras equivalentes (Forwarded, X-Real-IP…) se ignoran.
// Ningún proxy se considera confiable si no está declarado.
@Component
public class ResolutorIpCliente {

    static final String CABECERA_REENVIO = "X-Forwarded-For";
    // Dirección remota que no es un literal IP (no debería ocurrir con Tomcat): todas comparten una clave
    static final String CLAVE_DESCONOCIDA = "ip:desconocida";

    private final List<Cidr> proxiesConfiables;
    private final List<Cidr> redesCompartidas;

    public ResolutorIpCliente(
            @Value("${saca.red.proxies-confiables:}") String proxiesConfiables,
            @Value("${saca.red.redes-compartidas:}") String redesCompartidas) {
        this.proxiesConfiables = DireccionesIp.analizarRangos(proxiesConfiables, "SACA_PROXIES_CONFIABLES");
        this.redesCompartidas = DireccionesIp.analizarRangos(redesCompartidas, "SACA_IP_REDES_COMPARTIDAS");
    }

    public ClienteSolicitud resolver(HttpServletRequest solicitud) {
        Optional<InetAddress> remota = DireccionesIp.analizar(solicitud.getRemoteAddr());
        if (remota.isEmpty()) {
            return new ClienteSolicitud(CLAVE_DESCONOCIDA, false);
        }
        InetAddress cliente = remota.get();
        if (DireccionesIp.contenida(cliente, proxiesConfiables)) {
            cliente = desdeCabecera(solicitud, cliente);
        }
        // Cada bloque /48 tiene un único contador agregado. Es de red compartida si el bloque se solapa con algún
        // rango declarado, aunque esta dirección quede fuera de él: así todo el bloque comparte una sola cuota
        return new ClienteSolicitud(DireccionesIp.clave(cliente), DireccionesIp.contenida(cliente, redesCompartidas),
                DireccionesIp.claveAgregada(cliente), DireccionesIp.bloqueIntersecta(cliente, redesCompartidas));
    }

    // El último salto que no es un proxy confiable es el cliente. Un valor no válido corta el recorrido y se
    // usa el último salto confiable conocido, que no puede falsificar el cliente
    private InetAddress desdeCabecera(HttpServletRequest solicitud, InetAddress proxy) {
        List<String> saltos = new ArrayList<>();
        for (String valor : Collections.list(solicitud.getHeaders(CABECERA_REENVIO))) {
            for (String salto : valor.split(",")) {
                saltos.add(salto.trim());
            }
        }
        InetAddress actual = proxy;
        for (int i = saltos.size() - 1; i >= 0; i--) {
            Optional<InetAddress> salto = DireccionesIp.analizar(saltos.get(i));
            if (salto.isEmpty()) {
                return actual;
            }
            actual = salto.get();
            if (!DireccionesIp.contenida(actual, proxiesConfiables)) {
                return actual;
            }
        }
        return actual;
    }

    // clave: IPv4 completa o prefijo /64 de IPv6; redCompartida: la dirección está en un rango declarado;
    // claveAgregada: bloque /48 de IPv6 (null en IPv4); bloqueCompartido: ese bloque se solapa con algún rango
    // declarado. Nunca contienen datos de la solicitud más allá de la IP
    public record ClienteSolicitud(String clave, boolean redCompartida, String claveAgregada, boolean bloqueCompartido) {

        public ClienteSolicitud(String clave, boolean redCompartida) {
            this(clave, redCompartida, null, false);
        }
    }
}
