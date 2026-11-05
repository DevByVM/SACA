package com.saca.api.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// Contraseña de una cuenta pendiente de activación
class ContraseniaPendienteServiceTest {

    private final ContraseniaPendienteService service = new ContraseniaPendienteService();
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void generaUnHashBCryptValido() {
        String hash = service.generarHash();

        // Mismo formato y coste que los hashes que guarda el registro
        assertThat(hash).matches("^\\$2[aby]\\$10\\$[./A-Za-z0-9]{53}$");
    }

    @Test
    void cadaCuentaRecibeUnHashDistinto() {
        Set<String> hashes = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            hashes.add(service.generarHash());
        }

        assertThat(hashes).hasSize(10);
    }

    @Test
    void elHashNoCorrespondeAContraseniasPrevisibles() {
        String hash = service.generarHash();

        for (String intento : new String[] {"", "null", "Segura123", "PENDIENTE", "contrasenia", hash}) {
            assertThat(passwordEncoder.matches(intento, hash)).as(intento).isFalse();
        }
    }
}
