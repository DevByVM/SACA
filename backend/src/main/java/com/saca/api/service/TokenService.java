package com.saca.api.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.saca.api.config.TokenConfig;
import com.saca.api.entity.Estudiante;

@Service
public class TokenService {

    private final JwtEncoder codificadorToken;
    private final Clock relojAplicacion;
    private final Duration duracion;

    public TokenService(
            JwtEncoder codificadorToken,
            Clock relojAplicacion,
            @Value("${saca.seguridad.token.duracion:8h}") Duration duracion) {
        this.codificadorToken = codificadorToken;
        this.relojAplicacion = relojAplicacion;
        this.duracion = duracion;
    }

    // El sujeto del token es el id del estudiante: será la identidad confiable en el servidor
    public TokenEmitido emitir(Estudiante estudiante) {
        // JWT guarda las fechas en segundos; así expiraEn coincide exactamente con el exp del token
        Instant ahora = relojAplicacion.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiraEn = ahora.plus(duracion);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(TokenConfig.EMISOR)
                .subject(String.valueOf(estudiante.getId()))
                .issuedAt(ahora)
                .expiresAt(expiraEn)
                .build();
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();

        String valor = codificadorToken.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
        return new TokenEmitido(valor, expiraEn);
    }

    public record TokenEmitido(String valor, Instant expiraEn) {
    }
}
