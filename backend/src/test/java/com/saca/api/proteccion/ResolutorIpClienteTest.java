package com.saca.api.proteccion;

import com.saca.api.proteccion.ResolutorIpCliente.ClienteSolicitud;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// La IP del cliente sale de la conexión; las cabeceras de reenvío solo se aceptan de
// proxies declarados y nunca permiten que el cliente elija su propia IP
class ResolutorIpClienteTest {

    private static final ResolutorIpCliente SIN_PROXIES = new ResolutorIpCliente("", "");
    private static final ResolutorIpCliente CON_PROXY = new ResolutorIpCliente("10.0.0.0/8, 2001:db8:ffff::/48", "198.51.100.0/24");

    private static MockHttpServletRequest solicitud(String remota, String... reenvios) {
        MockHttpServletRequest solicitud = new MockHttpServletRequest();
        solicitud.setRemoteAddr(remota);
        for (String reenvio : reenvios) {
            solicitud.addHeader("X-Forwarded-For", reenvio);
        }
        return solicitud;
    }

    private static String clave(ResolutorIpCliente resolutor, MockHttpServletRequest solicitud) {
        return resolutor.resolver(solicitud).clave();
    }

    @Test
    void usaLaDireccionRemotaDeLaConexion() {
        assertThat(clave(SIN_PROXIES, solicitud("203.0.113.7"))).isEqualTo("ip4:203.0.113.7");
    }

    @Test
    void agrupaIpv6PorPrefijo64() {
        String una = clave(SIN_PROXIES, solicitud("2001:db8:aaaa:1::1"));
        String misma64 = clave(SIN_PROXIES, solicitud("2001:db8:aaaa:1:ffff:eeee:dddd:cccc"));
        String otra64 = clave(SIN_PROXIES, solicitud("2001:db8:aaaa:2::1"));

        assertThat(una).isEqualTo("ip6:2001:db8:aaaa:1::/64").isEqualTo(misma64).isNotEqualTo(otra64);
        // Una IPv4 escrita como IPv6 cuenta como la misma IPv4
        assertThat(clave(SIN_PROXIES, solicitud("::ffff:203.0.113.7"))).isEqualTo("ip4:203.0.113.7");
    }

    // El bloque /48 es común a todos sus /64; IPv4 no tiene clave agregada. Con un rango compartido dentro del
    // bloque, todo el bloque tiene la misma clave agregada y es de red compartida, aunque cada /64 conserva si su
    // propia dirección lo es
    @Test
    void agregaIpv6PorBloque48YNoAgregaIpv4() {
        ResolutorIpCliente conCompartida = new ResolutorIpCliente("", "2001:db8:aaaa:100::/56");
        ClienteSolicitud una = SIN_PROXIES.resolver(solicitud("2001:db8:aaaa:1::1"));
        ClienteSolicitud otra64 = SIN_PROXIES.resolver(solicitud("2001:db8:aaaa:ffff::1"));
        ClienteSolicitud otro48 = SIN_PROXIES.resolver(solicitud("2001:db8:aaab:1::1"));

        assertThat(una.claveAgregada()).isEqualTo("ip6:2001:db8:aaaa::/48").isEqualTo(otra64.claveAgregada());
        assertThat(otro48.claveAgregada()).isEqualTo("ip6:2001:db8:aaab::/48");
        assertThat(una.bloqueCompartido()).isFalse();
        assertThat(SIN_PROXIES.resolver(solicitud("203.0.113.7")).claveAgregada()).isNull();
        assertThat(SIN_PROXIES.resolver(solicitud("::ffff:203.0.113.7")).claveAgregada()).isNull();

        ClienteSolicitud dentro = conCompartida.resolver(solicitud("2001:db8:aaaa:1ab::1"));
        ClienteSolicitud fuera = conCompartida.resolver(solicitud("2001:db8:aaaa:2::1"));
        ClienteSolicitud otroBloque = conCompartida.resolver(solicitud("2001:db8:aaab:1ab::1"));
        assertThat(dentro.redCompartida()).isTrue();
        assertThat(fuera.redCompartida()).isFalse();
        assertThat(dentro.claveAgregada()).isEqualTo("ip6:2001:db8:aaaa::/48").isEqualTo(fuera.claveAgregada());
        assertThat(dentro.bloqueCompartido()).isTrue();
        assertThat(fuera.bloqueCompartido()).isTrue();
        assertThat(otroBloque.bloqueCompartido()).isFalse();
    }

    // Intersección CIDR del bloque /48 de 2001:db8:5:200::1 (2001:db8:5::/48) con distintos rangos, no
    // pertenencia de la dirección: un /56 del bloque que no la contiene también cuenta
    @Test
    void elBloqueEsCompartidoSiSeSolapaConAlgunRango() {
        InetAddress direccion = DireccionesIp.analizar("2001:db8:5:200::1").orElseThrow();
        Map<String, Boolean> casos = new LinkedHashMap<>();
        casos.put("2001:db8:5:100::/56", true);
        casos.put("2001:db8:5:ffff::1/128", true);
        casos.put("2001:db8:5:300::/64", true);
        casos.put("2001:db8:5::/48", true);
        casos.put("2001:db8::/32", true);
        casos.put("2001:db8:4::/47", true);
        casos.put("::/0", true);
        casos.put("2001:db8:6:100::/56", false);
        casos.put("2001:db8:4::/48", false);
        casos.put("2001:db8:6::/47", false);
        casos.put("2001:db9::/32", false);
        casos.put("192.0.2.0/24", false);
        casos.put("0.0.0.0/0", false);
        casos.put("2001:db8:6::/48, 2001:db8:5:abcd::/64", true);
        casos.put("", false);

        casos.forEach((rangos, esperado) -> assertThat(
                DireccionesIp.bloqueIntersecta(direccion, DireccionesIp.analizarRangos(rangos, "PRUEBA")))
                .as(rangos).isEqualTo(esperado));

        // IPv4 no tiene bloque agregado, ni siquiera con un rango que la contiene
        InetAddress ipv4 = DireccionesIp.analizar("192.0.2.10").orElseThrow();
        assertThat(DireccionesIp.bloqueIntersecta(ipv4, DireccionesIp.analizarRangos("192.0.2.0/24", "PRUEBA"))).isFalse();
    }

    @Test
    void ignoraLasCabecerasDeReenvioSiLaConexionNoVieneDeUnProxyConfiable() {
        MockHttpServletRequest falsificada = solicitud("203.0.113.7", "1.2.3.4", "5.6.7.8, 9.9.9.9");
        falsificada.addHeader("X-Real-IP", "1.2.3.4");
        falsificada.addHeader("Forwarded", "for=1.2.3.4");

        assertThat(clave(SIN_PROXIES, falsificada)).isEqualTo("ip4:203.0.113.7");
        // Aunque haya proxies declarados, quien no es uno de ellos no puede elegir su IP
        assertThat(clave(CON_PROXY, falsificada)).isEqualTo("ip4:203.0.113.7");
    }

    @Test
    void conUnProxyConfiableUsaElUltimoSaltoQueNoEsProxy() {
        assertThat(clave(CON_PROXY, solicitud("10.0.0.5", "198.51.100.9"))).isEqualTo("ip4:198.51.100.9");
        // Lo que el cliente añada a la izquierda no se usa
        assertThat(clave(CON_PROXY, solicitud("10.0.0.5", "6.6.6.6, 203.0.113.50"))).isEqualTo("ip4:203.0.113.50");
        // Varios proxies confiables en cadena, también repartidos en varias cabeceras
        assertThat(clave(CON_PROXY, solicitud("10.0.0.5", "203.0.113.50, 10.1.1.1", "10.2.2.2"))).isEqualTo("ip4:203.0.113.50");
        // Proxy IPv6 declarado
        assertThat(clave(CON_PROXY, solicitud("2001:db8:ffff:1::5", "203.0.113.51"))).isEqualTo("ip4:203.0.113.51");
    }

    @Test
    void lasCabecerasEquivalentesSeIgnoranTambienDesdeUnProxyConfiable() {
        MockHttpServletRequest solicitud = solicitud("10.0.0.5");
        solicitud.addHeader("X-Real-IP", "1.2.3.4");
        solicitud.addHeader("Forwarded", "for=1.2.3.4");

        assertThat(clave(CON_PROXY, solicitud)).isEqualTo("ip4:10.0.0.5");
    }

    @Test
    void unValorNoValidoONombreDeHostNoSeUsaNiSeResuelve() {
        for (String valor : new String[] {"no-es-ip", "localhost", "ejemplo.com", "abc:def", "999.1.1.1", "1.2.3.4:5678", ""}) {
            assertThat(clave(CON_PROXY, solicitud("10.0.0.5", valor))).as(valor).isEqualTo("ip4:10.0.0.5");
        }
        // Un valor no válido en medio corta el recorrido en el último salto confiable
        assertThat(clave(CON_PROXY, solicitud("10.0.0.5", "203.0.113.50, basura, 10.1.1.1"))).isEqualTo("ip4:10.1.1.1");
    }

    @Test
    void sinCabeceraElProxyConfiableEsElCliente() {
        assertThat(clave(CON_PROXY, solicitud("10.0.0.5"))).isEqualTo("ip4:10.0.0.5");
    }

    @Test
    void marcaLasRedesCompartidasDeclaradas() {
        ClienteSolicitud compartida = CON_PROXY.resolver(solicitud("198.51.100.20"));
        ClienteSolicitud normal = CON_PROXY.resolver(solicitud("203.0.113.7"));
        ClienteSolicitud trasProxy = CON_PROXY.resolver(solicitud("10.0.0.5", "198.51.100.21"));

        assertThat(compartida.redCompartida()).isTrue();
        assertThat(normal.redCompartida()).isFalse();
        assertThat(trasProxy.redCompartida()).isTrue();
        assertThat(SIN_PROXIES.resolver(solicitud("198.51.100.20")).redCompartida()).isFalse();
    }

    @Test
    void unaDireccionRemotaNoValidaComparteUnaClaveFija() {
        assertThat(clave(SIN_PROXIES, solicitud("no-es-ip"))).isEqualTo(ResolutorIpCliente.CLAVE_DESCONOCIDA);
    }

    @Test
    void unRangoMalConfiguradoImpideArrancar() {
        for (String rango : new String[] {"10.0.0.0/33", "proxy.interno", "10.0.0.0/x", "1.2.3.4/8/2", "2001:db8::/129"}) {
            assertThatThrownBy(() -> new ResolutorIpCliente(rango, "")).as(rango)
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("SACA_PROXIES_CONFIABLES");
        }
        assertThatThrownBy(() -> new ResolutorIpCliente("", "198.51.100.0/40"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SACA_IP_REDES_COMPARTIDAS");
    }
}
