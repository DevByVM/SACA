package com.saca.api.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// Límite de 72 bytes UTF-8 de BCrypt. Una entrada más larga nunca coincide (BCrypt la compararía
// solo por sus 72 primeros bytes) y cada intento hace exactamente una comparación BCrypt, real o ficticia
class VerificadorContraseniasTest {

    static final String ASCII_72 = "A1" + "a".repeat(70);
    static final String ASCII_73 = ASCII_72 + "a";
    // ñ (U+00F1) ocupa 2 bytes en UTF-8; el emoji U+1F600, 4
    static final String ENIE_72 = "A1" + "ñ".repeat(35);
    static final String ENIE_74 = "A1" + "ñ".repeat(36);
    static final String EMOJI_72 = "A1" + "a".repeat(66) + "😀";
    static final String EMOJI_73 = EMOJI_72 + "a";

    private final BCryptPasswordEncoder codificador = spy(new BCryptPasswordEncoder(4));
    private final VerificadorContrasenias verificador = new VerificadorContrasenias(codificador);

    private static int bytes(String texto) {
        return texto.getBytes(StandardCharsets.UTF_8).length;
    }

    @Test
    void elLimiteSeCuentaEnBytesUtf8YNoEnCaracteres() {
        assertThat(bytes(ASCII_72)).isEqualTo(72);
        assertThat(bytes(ASCII_73)).isEqualTo(73);
        assertThat(ENIE_72).hasSize(37);
        assertThat(bytes(ENIE_72)).isEqualTo(72);
        assertThat(bytes(ENIE_74)).isEqualTo(74);
        assertThat(bytes(EMOJI_72)).isEqualTo(72);

        assertThat(VerificadorContrasenias.excedeLimite(ASCII_72)).isFalse();
        assertThat(VerificadorContrasenias.excedeLimite(ENIE_72)).isFalse();
        assertThat(VerificadorContrasenias.excedeLimite(EMOJI_72)).isFalse();
        assertThat(VerificadorContrasenias.excedeLimite(ASCII_73)).isTrue();
        assertThat(VerificadorContrasenias.excedeLimite(ENIE_74)).isTrue();
        assertThat(VerificadorContrasenias.excedeLimite(EMOJI_73)).isTrue();
        assertThat(VerificadorContrasenias.excedeLimite(null)).isFalse();
    }

    // Sin el límite, BCrypt aceptaría cualquier prolongación de una contraseña de 72 bytes
    @Test
    void unaProlongacionDeUnaContraseniaDe72BytesNoCoincide() {
        for (String contrasenia : new String[] {ASCII_72, ENIE_72, EMOJI_72}) {
            String hash = codificador.encode(contrasenia);
            assertThat(codificador.matches(contrasenia + "X", hash)).as("BCrypt por sí solo").isTrue();

            assertThat(verificador.coincide(contrasenia, hash)).isTrue();
            assertThat(verificador.coincide(contrasenia + "X", hash)).isFalse();
            assertThat(verificador.coincide(contrasenia + "ñ", hash)).isFalse();
        }
    }

    // Correcta, incorrecta, demasiado larga, hash que no es BCrypt o contraseña ausente: siempre una comparación
    @Test
    void cadaIntentoHaceExactamenteUnaComparacionBCrypt() {
        String hash = codificador.encode(ASCII_72);
        String[][] casos = {
                {ASCII_72, hash}, {"Incorrecta1", hash}, {ASCII_73, hash}, {ENIE_74, hash}, {EMOJI_73, hash},
                {ASCII_72 + "X", hash}, {ASCII_72, "texto-plano"}, {null, hash}};
        for (String[] caso : casos) {
            clearInvocations(codificador);

            verificador.coincide(caso[0], caso[1]);

            verify(codificador, times(1)).matches(any(), anyString());
        }

        clearInvocations(codificador);
        verificador.compararConFicticio(ASCII_73);
        verify(codificador, times(1)).matches(any(), anyString());
    }
}
