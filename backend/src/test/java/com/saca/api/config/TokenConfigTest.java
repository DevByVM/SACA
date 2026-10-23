package com.saca.api.config;

import com.saca.api.entity.Estudiante;
import com.saca.api.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenConfigTest {

    private static final String SECRETO = "clave-de-prueba-solo-para-tests-0123456789";

    private final TokenConfig config = new TokenConfig();

    // Contexto mínimo con solo TokenConfig, sin perfiles: equivale a un arranque de producción
    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(TokenConfig.class);

    private static Estudiante estudiante(long id) {
        return new Estudiante(id);
    }

    private SecretKey clave(String secreto) {
        return config.claveFirmaToken(secreto, false);
    }

    @Test
    void enProduccionSinSecretoNoArranca() {
        assertThatThrownBy(() -> TokenConfig.crearClave(null, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SACA_TOKEN_SECRETO no está definido");
        assertThatThrownBy(() -> TokenConfig.crearClave("   ", false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void elContextoDeSpringNoArrancaSinSecretoNiPermisoDeDesarrollo() {
        contexto.run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SACA_TOKEN_SECRETO no está definido");
        });
    }

    @Test
    void elContextoDeSpringArrancaConSecreto() {
        contexto.withPropertyValues("saca.seguridad.token.secreto=" + SECRETO)
                .run(ctx -> assertThat(ctx).hasNotFailed().hasSingleBean(SecretKey.class));
    }

    @Test
    void elContextoDeSpringArrancaSinSecretoSoloConElPermisoDeDesarrollo() {
        contexto.withPropertyValues("saca.seguridad.token.clave-temporal-permitida=true")
                .run(ctx -> assertThat(ctx).hasNotFailed().hasSingleBean(SecretKey.class));
    }

    // Con el application.properties real: el permiso solo llega desde SACA_TOKEN_CLAVE_TEMPORAL_PERMITIDA
    private ApplicationContextRunner contextoConPropiedadesReales() {
        return contexto.withInitializer(new ConfigDataApplicationContextInitializer());
    }

    @Test
    void conLasPropiedadesRealesSinVariablesNoArranca() {
        contextoConPropiedadesReales().run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).rootCause()
                    .hasMessageContaining("SACA_TOKEN_SECRETO no está definido");
        });
    }

    @Test
    void conLasPropiedadesRealesLaVariableDeDesarrolloPermiteLaClaveTemporal() {
        contextoConPropiedadesReales()
                .withSystemProperties("SACA_TOKEN_CLAVE_TEMPORAL_PERMITIDA=true")
                .run(ctx -> assertThat(ctx).hasNotFailed().hasSingleBean(SecretKey.class));
    }

    @Test
    void conLasPropiedadesRealesLaVariableDeDesarrolloEnFalseNoArranca() {
        contextoConPropiedadesReales()
                .withSystemProperties("SACA_TOKEN_CLAVE_TEMPORAL_PERMITIDA=false")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void conLasPropiedadesRealesUnaClaveCortaSeRechazaAunqueSeaDesarrollo() {
        contextoConPropiedadesReales()
                .withSystemProperties("SACA_TOKEN_CLAVE_TEMPORAL_PERMITIDA=true", "SACA_TOKEN_SECRETO=corta")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure()).rootCause().hasMessageContaining("al menos 32 bytes");
                });
    }

    @Test
    void rechazaUnSecretoDemasiadoCortoAunqueSeaDesarrollo() {
        assertThatThrownBy(() -> TokenConfig.crearClave("corta", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("al menos 32 bytes");
        assertThatThrownBy(() -> TokenConfig.crearClave("corta", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("al menos 32 bytes");
    }

    @Test
    void enDesarrolloSinSecretoGeneraUnaClaveTemporalDistintaEnCadaArranque() {
        SecretKey primera = TokenConfig.crearClave("", true);
        SecretKey segunda = TokenConfig.crearClave(null, true);

        assertThat(primera.getEncoded()).hasSize(32);
        assertThat(primera.getEncoded()).isNotEqualTo(segunda.getEncoded());
    }

    @Test
    void conSecretoSeUsaEseSecretoAunqueLaClaveTemporalEstePermitida() {
        assertThat(config.claveFirmaToken(SECRETO, true).getEncoded()).isEqualTo(clave(SECRETO).getEncoded());
    }

    @Test
    void conElMismoSecretoUnTokenSigueSiendoValidoTrasReiniciar() {
        SecretKey claveAntes = clave(SECRETO);
        SecretKey claveDespues = clave(SECRETO);
        TokenService emisor = new TokenService(config.codificadorToken(claveAntes), Clock.systemUTC(), Duration.ofHours(8));

        String token = emisor.emitir(estudiante(5)).valor();
        Jwt jwt = config.decodificadorToken(claveDespues).decode(token);

        assertThat(jwt.getSubject()).isEqualTo("5");
    }

    @Test
    void unTokenFirmadoConOtraClaveEsRechazado() {
        TokenService emisor = new TokenService(
                config.codificadorToken(clave(SECRETO)), Clock.systemUTC(), Duration.ofHours(8));
        String token = emisor.emitir(estudiante(5)).valor();

        SecretKey otraClave = clave("otra-clave-distinta-tambien-de-32-bytes-o-mas");
        assertThatThrownBy(() -> config.decodificadorToken(otraClave).decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void unTokenConElContenidoManipuladoEsRechazado() {
        SecretKey clave = clave(SECRETO);
        TokenService emisor = new TokenService(config.codificadorToken(clave), Clock.systemUTC(), Duration.ofHours(8));
        String[] partes = emisor.emitir(estudiante(5)).valor().split("\\.");

        String otroSujeto = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"iss\":\"saca-api\",\"sub\":\"6\",\"exp\":4102444800}".getBytes());
        String manipulado = partes[0] + "." + otroSujeto + "." + partes[2];

        assertThatThrownBy(() -> config.decodificadorToken(clave).decode(manipulado))
                .isInstanceOf(JwtException.class);
    }
}
