package com.saca.api.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

// Comparación de contraseñas del inicio de sesión. Cada intento hace exactamente una comparación
// BCrypt: contra el hash guardado o, si no hay uno que comparar (cuenta inexistente, inactiva, bloqueada o
// con un valor que no es BCrypt), contra un hash ficticio del mismo coste. Así el tiempo de respuesta no
// delata cuál de esos casos se dio. No registra contraseñas ni hashes.
//
// BCrypt solo tiene en cuenta los primeros 72 bytes (UTF-8): al comparar, una entrada más larga coincidiría con
// el hash de sus 72 primeros. Por eso una contraseña de más de 72 bytes nunca coincide (no puede haberse fijado:
// la aplicación las rechaza al fijarlas y BCrypt se niega a cifrarlas), y no se aceptan prolongaciones de una de
// exactamente 72. También se compara con el hash ficticio, para que tarde lo mismo.
@Component
public class VerificadorContrasenias {

    public static final int MAXIMO_BYTES_CONTRASENIA = 72;

    private final BCryptPasswordEncoder passwordEncoder;
    // Hash de un secreto aleatorio que se descarta: ninguna contraseña puede coincidir con él
    private final String hashFicticio;

    public VerificadorContrasenias() {
        this(new BCryptPasswordEncoder());
    }

    // Para pruebas: permite contar las comparaciones
    VerificadorContrasenias(BCryptPasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        byte[] secreto = new byte[32];
        new SecureRandom().nextBytes(secreto);
        this.hashFicticio = passwordEncoder.encode(Base64.getEncoder().encodeToString(secreto));
    }

    // Más bytes de los que BCrypt tiene en cuenta: ni se admite al fijarla ni coincide al comprobarla
    public static boolean excedeLimite(String contrasenia) {
        return contrasenia != null
                && contrasenia.getBytes(StandardCharsets.UTF_8).length > MAXIMO_BYTES_CONTRASENIA;
    }

    public boolean coincide(String contrasenia, String hashGuardado) {
        if (!esHashBCrypt(hashGuardado) || excedeLimite(contrasenia)) {
            compararConFicticio(contrasenia);
            return false;
        }
        return passwordEncoder.matches(texto(contrasenia), hashGuardado);
    }

    // Mismo coste que una comparación real; el resultado se descarta
    public void compararConFicticio(String contrasenia) {
        passwordEncoder.matches(texto(contrasenia), hashFicticio);
    }

    // BCrypt rechaza null: una contraseña ausente se compara como vacía
    private static String texto(String contrasenia) {
        return contrasenia == null ? "" : contrasenia;
    }

    private static boolean esHashBCrypt(String hash) {
        return hash != null && hash.length() >= 60 && hash.startsWith("$2");
    }
}
