package com.saca.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsUtils;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ManejadorErroresSeguridad manejadorErrores) throws Exception {

        http
            // Aplica la configuración CORS de CorsConfig también a las respuestas 401 de seguridad
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Sin sesión todavía no hay token: login, registro, activación y reenvío deben ser públicos
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/registro",
                        "/api/auth/activacion", "/api/auth/activacion/consultar", "/api/auth/reenviar-activacion",
                        "/api/auth/restablecimiento", "/api/auth/restablecimiento/consultar")
                .permitAll()
                // El navegador nunca envía el token en la consulta previa CORS
                .requestMatchers(CorsUtils::isPreFlightRequest)
                .permitAll()
                // Página de error de Spring: si exigiera token, un error se convertiría en 401
                .requestMatchers("/error")
                .permitAll()
                .requestMatchers("/api/admin/**")
                .hasAuthority("ADMINISTRADOR")
                .requestMatchers(HttpMethod.GET, "/api/catalogo-materias")
                .hasAnyAuthority("ESTUDIANTE", "ADMINISTRADOR")
                // Las funciones académicas pertenecen a los estudiantes.
                .anyRequest()
                .hasAuthority("ESTUDIANTE")
            )
            // Valida el Bearer con el decodificador de TokenConfig (firma HS256, emisor y vigencia)
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                .authenticationEntryPoint(manejadorErrores)
                .accessDeniedHandler(manejadorErrores))
            .exceptionHandling(errores -> errores
                .authenticationEntryPoint(manejadorErrores)
                .accessDeniedHandler(manejadorErrores))
            .formLogin(form -> form.disable())
            .httpBasic(httpBasic -> httpBasic.disable());

        return http.build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String rol = jwt.getClaimAsString("rol");
            return rol == null ? List.of() : List.of(new SimpleGrantedAuthority(rol));
        });
        return converter;
    }
}
