package com.saca.api.config;

import org.apache.catalina.Container;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.valves.ErrorReportValve;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Las peticiones que Tomcat rechaza antes de llegar a Spring (p. ej. una URL mal codificada) usan su
// propia página de error, que mostraba el detalle y la versión exacta del servidor. Se dejan solo el estado.
@Configuration
public class TomcatConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> paginaDeErrorDeTomcatSinDetalles() {
        return factory -> factory.addContextCustomizers(context -> {
            Container padre = context.getParent();
            if (padre instanceof StandardHost host) {
                ErrorReportValve valvula = new ErrorReportValve();
                valvula.setShowReport(false);
                valvula.setShowServerInfo(false);
                host.getPipeline().addValve(valvula);
            }
        });
    }
}
