package com.saca.api.service;

import com.saca.api.config.TokenConfig;
import com.saca.api.entity.Estudiante;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenServiceTest {

    private final TokenConfig config = new TokenConfig();
    private final SecretKey clave = config.claveFirmaToken("clave-de-prueba-solo-para-tests-0123456789", false);

    @Test
    void emiteUnTokenHs256ConElIdComoSujetoYOchoHorasDeVigencia() {
        Instant ahora = Instant.parse("2026-10-21T15:00:00Z");
        Clock reloj = Clock.fixed(ahora, ZoneId.of("America/El_Salvador"));
        TokenService service = new TokenService(config.codificadorToken(clave), reloj, Duration.ofHours(8));

        TokenService.TokenEmitido emitido = service.emitir(new Estudiante(42L));

        // Se verifica la firma sin validar fechas, porque el reloj de la prueba está fijo en el pasado
        NimbusJwtDecoder sinValidarFechas = NimbusJwtDecoder.withSecretKey(clave).macAlgorithm(MacAlgorithm.HS256).build();
        sinValidarFechas.setJwtValidator(token -> OAuth2TokenValidatorResult.success());
        Jwt jwt = sinValidarFechas.decode(emitido.valor());

        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(TokenConfig.EMISOR);
        assertThat(jwt.getIssuedAt()).isEqualTo(ahora);
        assertThat(jwt.getExpiresAt()).isEqualTo(ahora.plus(Duration.ofHours(8)));
        assertThat(emitido.expiraEn()).isEqualTo(jwt.getExpiresAt());
        assertThat(jwt.getClaims()).containsOnlyKeys("iss", "sub", "iat", "exp");
    }

    @Test
    void unTokenCaducadoEsRechazadoPorElDecodificadorDeLaAplicacion() {
        Clock haceNueveHoras = Clock.fixed(Instant.now().minus(Duration.ofHours(9)), ZoneId.of("UTC"));
        TokenService service = new TokenService(config.codificadorToken(clave), haceNueveHoras, Duration.ofHours(8));
        String caducado = service.emitir(new Estudiante(42L)).valor();

        assertThatThrownBy(() -> config.decodificadorToken(clave).decode(caducado))
                .isInstanceOf(JwtException.class);
    }
}
