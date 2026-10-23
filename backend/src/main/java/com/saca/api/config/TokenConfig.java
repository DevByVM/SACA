package com.saca.api.config;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

@Configuration
public class TokenConfig {

    public static final String EMISOR = "saca-api";

    // HS256 exige una clave de al menos 256 bits
    private static final int LONGITUD_MINIMA_BYTES = 32;

    private static final Logger log = LoggerFactory.getLogger(TokenConfig.class);

    @Bean
    public SecretKey claveFirmaToken(
            @Value("${saca.seguridad.token.secreto:}") String secreto,
            @Value("${saca.seguridad.token.clave-temporal-permitida:false}") boolean claveTemporalPermitida) {
        return crearClave(secreto, claveTemporalPermitida);
    }

    @Bean
    public JwtEncoder codificadorToken(SecretKey claveFirmaToken) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(claveFirmaToken));
    }

    @Bean
    public JwtDecoder decodificadorToken(SecretKey claveFirmaToken) {
        NimbusJwtDecoder decodificador = NimbusJwtDecoder.withSecretKey(claveFirmaToken)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decodificador.setJwtValidator(JwtValidators.createDefaultWithIssuer(EMISOR));
        return decodificador;
    }

    // Sin SACA_TOKEN_SECRETO el backend no arranca. Solo en desarrollo
    // (SACA_TOKEN_CLAVE_TEMPORAL_PERMITIDA=true) y en las pruebas se usa una clave aleatoria,
    // sin guardar secretos en el repositorio; los tokens emitidos dejan de valer al reiniciar.
    static SecretKey crearClave(String secreto, boolean claveTemporalPermitida) {
        if (secreto == null || secreto.isBlank()) {
            if (!claveTemporalPermitida) {
                throw new IllegalStateException(
                        "SACA_TOKEN_SECRETO no está definido. Defínelo con una clave de al menos "
                                + LONGITUD_MINIMA_BYTES + " bytes; la clave temporal solo se admite en desarrollo "
                                + "(SACA_TOKEN_CLAVE_TEMPORAL_PERMITIDA=true).");
            }
            log.warn("SACA_TOKEN_SECRETO no está definido: se usa una clave temporal (solo desarrollo) y los "
                    + "tokens dejarán de ser válidos al reiniciar el backend.");
            byte[] aleatoria = new byte[LONGITUD_MINIMA_BYTES];
            new SecureRandom().nextBytes(aleatoria);
            return new SecretKeySpec(aleatoria, "HmacSHA256");
        }

        byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < LONGITUD_MINIMA_BYTES) {
            throw new IllegalStateException(
                    "SACA_TOKEN_SECRETO debe tener al menos " + LONGITUD_MINIMA_BYTES + " bytes para firmar con HS256.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
