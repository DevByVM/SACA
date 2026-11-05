package com.saca.api.service;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

// Una cuenta pendiente de activación aún no tiene contraseña elegida por el estudiante, pero la
// columna contrasenia es NOT NULL. Se guarda el hash BCrypt de un secreto aleatorio de 32 bytes, distinto en
// cada cuenta, que se descarta en el acto: nadie lo conoce ni puede obtenerlo. La activación lo sustituye por
// el hash de la contraseña elegida. El estado PENDIENTE es lo que impide la sesión; este hash solo evita
// guardar un valor arbitrario en la columna.
@Service
public class ContraseniaPendienteService {

    private static final int BYTES_SECRETO = 32;

    private final SecureRandom aleatorio = new SecureRandom();
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public String generarHash() {
        byte[] secreto = new byte[BYTES_SECRETO];
        aleatorio.nextBytes(secreto);
        try {
            // 43 caracteres: dentro del límite de 72 bytes que BCrypt tiene en cuenta
            return passwordEncoder.encode(Base64.getUrlEncoder().withoutPadding().encodeToString(secreto));
        } finally {
            Arrays.fill(secreto, (byte) 0);
        }
    }
}
