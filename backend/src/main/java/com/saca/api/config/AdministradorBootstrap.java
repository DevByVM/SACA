package com.saca.api.config;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.saca.api.entity.Administrador;
import com.saca.api.repository.AdministradorRepository;
import com.saca.api.repository.EstudianteRepository;

@Component
public class AdministradorBootstrap implements ApplicationRunner {

    private final AdministradorRepository administradores;
    private final EstudianteRepository estudiantes;
    private final String correo;
    private final String nombre;
    private final String contrasenia;
    private final BCryptPasswordEncoder codificador = new BCryptPasswordEncoder();

    public AdministradorBootstrap(
            AdministradorRepository administradores,
            EstudianteRepository estudiantes,
            @Value("${saca.admin.correo:}") String correo,
            @Value("${saca.admin.nombre:}") String nombre,
            @Value("${saca.admin.contrasenia:}") String contrasenia) {
        this.administradores = administradores;
        this.estudiantes = estudiantes;
        this.correo = correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
        this.nombre = nombre == null ? "" : nombre.trim();
        this.contrasenia = contrasenia == null ? "" : contrasenia;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean todosVacios = correo.isEmpty() && nombre.isEmpty() && contrasenia.isEmpty();
        if (todosVacios) {
            return;
        }
        if (correo.isEmpty() || nombre.isEmpty() || contrasenia.isEmpty()) {
            throw new IllegalStateException(
                    "Configura SACA_ADMIN_CORREO, SACA_ADMIN_NOMBRE y SACA_ADMIN_CONTRASENIA juntos.");
        }
        if (!correo.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || correo.length() > 120
                || nombre.length() > 120 || contrasenia.getBytes(StandardCharsets.UTF_8).length < 12
                || contrasenia.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException(
                    "El correo del administrador debe ser válido, su nombre debe tener hasta 120 caracteres y "
                            + "la contraseña entre 12 y 72 bytes UTF-8.");
        }
        if (administradores.findByCorreoIgnoreCase(correo).isPresent()) {
            return;
        }
        if (estudiantes.existsByCorreoInstitucional(correo)) {
            throw new IllegalStateException("El correo inicial de administrador ya pertenece a un estudiante.");
        }

        Administrador administrador = new Administrador();
        administrador.setCorreo(correo);
        administrador.setNombre(nombre);
        administrador.setContrasenia(codificador.encode(contrasenia));
        administradores.save(administrador);
    }
}
