package com.saca.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsUtils;

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
                // Sin sesión todavía no hay token: login y registro deben ser públicos
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/registro")
                .permitAll()
                // El navegador nunca envía el token en la consulta previa CORS
                .requestMatchers(CorsUtils::isPreFlightRequest)
                .permitAll()
                // Página de error de Spring: si exigiera token, un error se convertiría en 401
                .requestMatchers("/error")
                .permitAll()
                // Cualquier otra ruta, incluidas las que se añadan en el futuro, exige un JWT válido
                .anyRequest()
                .authenticated()
            )
            // Valida el Bearer con el decodificador de TokenConfig (firma HS256, emisor y vigencia)
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(Customizer.withDefaults())
                .authenticationEntryPoint(manejadorErrores)
                .accessDeniedHandler(manejadorErrores))
            .exceptionHandling(errores -> errores
                .authenticationEntryPoint(manejadorErrores)
                .accessDeniedHandler(manejadorErrores))
            .formLogin(form -> form.disable())
            .httpBasic(httpBasic -> httpBasic.disable());

        return http.build();
    }
}
