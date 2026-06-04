package com.saca.api.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.saca.api.dto.ActualizarPerfilRequest;
import com.saca.api.dto.LoginRequest;
import com.saca.api.dto.LoginResponse;
import com.saca.api.dto.RegistroRequest;
import com.saca.api.entity.Estudiante;
import com.saca.api.repository.EstudianteRepository;

@Service
public class EstudianteService {

    @Autowired
    private EstudianteRepository repository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public String registrar(RegistroRequest request) {
        String correoInstitucional = normalizarCorreo(request.getCorreoInstitucional());
        String carnet = normalizarTexto(request.getCarnet());

        if (!correoInstitucional.endsWith("@ues.edu.sv")) {
            return "Correo institucional inválido";
        }

        if (repository.existsByCarnet(carnet)) {
            return "Carnet ya registrado";
        }

        if (repository.existsByCorreoInstitucional(correoInstitucional)) {
            return "Correo ya registrado";
        }

        if (!contraseniaValida(request.getContrasenia())) {
            return "La contraseña debe tener al menos 8 caracteres, una mayúscula y un número";
        }

        Estudiante estudiante = new Estudiante();
        estudiante.setNombre(normalizarTexto(request.getNombre()));
        estudiante.setCarnet(carnet);
        estudiante.setCorreoInstitucional(correoInstitucional);
        estudiante.setContrasenia(passwordEncoder.encode(request.getContrasenia()));
        estudiante.setCuentaActiva(true);
        estudiante.setIntentosFallidos(0);
        estudiante.setBloqueadoHasta(null);

        repository.save(estudiante);

        return "Usuario registrado correctamente";
    }

    public LoginResponse login(LoginRequest request) {
        String correoInstitucional = normalizarCorreo(request.getCorreoInstitucional());

        Optional<Estudiante> estudianteOpt =
                repository.findByCorreoInstitucional(correoInstitucional);

        if (estudianteOpt.isEmpty()) {
            return LoginResponse.error("Usuario no encontrado");
        }

        Estudiante estudiante = estudianteOpt.get();

        if (!Boolean.TRUE.equals(estudiante.getCuentaActiva())) {
            return LoginResponse.error("Cuenta no activada");
        }

        if (estudiante.getBloqueadoHasta() != null
                && estudiante.getBloqueadoHasta().after(Timestamp.valueOf(LocalDateTime.now()))) {
            return LoginResponse.error("Cuenta bloqueada temporalmente");
        }

        boolean passwordCorrecta =
                esHashBCrypt(estudiante.getContrasenia())
                        && passwordEncoder.matches(
                                request.getContrasenia(),
                                estudiante.getContrasenia());

        if (!passwordCorrecta) {
            int intentosFallidos = estudiante.getIntentosFallidos() == null
                    ? 0
                    : estudiante.getIntentosFallidos();

            estudiante.setIntentosFallidos(intentosFallidos + 1);

            if (estudiante.getIntentosFallidos() >= 3) {
                estudiante.setBloqueadoHasta(Timestamp.valueOf(LocalDateTime.now().plusMinutes(5)));
                repository.save(estudiante);
                return LoginResponse.error("Cuenta bloqueada por 5 minutos");
            }

            repository.save(estudiante);
            return LoginResponse.error("Credenciales incorrectas");
        }

        estudiante.setIntentosFallidos(0);
        estudiante.setBloqueadoHasta(null);
        repository.save(estudiante);

        return new LoginResponse(
                true,
                "LOGIN_OK",
                estudiante.getId(),
                estudiante.getNombre(),
                estudiante.getCarnet(),
                estudiante.getCorreoInstitucional());
    }

    public String actualizarPerfil(Long id, ActualizarPerfilRequest request) {
        Optional<Estudiante> estudianteOpt = repository.findById(id);

        if (estudianteOpt.isEmpty()) {
            return "Usuario no encontrado";
        }

        String nombre = normalizarTexto(request.getNombre());
        String carnet = normalizarTexto(request.getCarnet());
        String correoInstitucional = normalizarCorreo(request.getCorreoInstitucional());

        if (nombre.isBlank()) {
            return "Nombre obligatorio";
        }

        if (!correoInstitucional.endsWith("@ues.edu.sv")) {
            return "Correo institucional inválido";
        }

        Optional<Estudiante> carnetExistente = repository.findByCarnet(carnet);

        if (carnetExistente.isPresent() && !carnetExistente.get().getId().equals(id)) {
            return "Carnet ya registrado";
        }

        Optional<Estudiante> correoExistente =
                repository.findByCorreoInstitucional(correoInstitucional);

        if (correoExistente.isPresent() && !correoExistente.get().getId().equals(id)) {
            return "Correo ya registrado";
        }

        if (!contraseniaValida(request.getContrasenia())) {
            return "La contraseña debe tener al menos 8 caracteres, una mayúscula y un número";
        }

        Estudiante estudiante = estudianteOpt.get();
        estudiante.setNombre(nombre);
        estudiante.setCarnet(carnet);
        estudiante.setCorreoInstitucional(correoInstitucional);
        estudiante.setContrasenia(passwordEncoder.encode(request.getContrasenia()));

        repository.save(estudiante);

        return "Perfil actualizado correctamente";
    }

    public Estudiante obtenerPorCorreo(String correo) {
        return repository.findByCorreoInstitucional(normalizarCorreo(correo)).orElse(null);
    }

    private String normalizarCorreo(String correo) {
        return normalizarTexto(correo).toLowerCase();
    }

    private String normalizarTexto(String texto) {
        return texto == null ? "" : texto.trim();
    }

    private boolean contraseniaValida(String contrasenia) {
        return contrasenia != null && contrasenia.matches("^(?=.*[A-Z])(?=.*\\d).{8,}$");
    }

    private boolean esHashBCrypt(String contraseniaAlmacenada) {
        return contraseniaAlmacenada != null
                && contraseniaAlmacenada.length() >= 60
                && contraseniaAlmacenada.startsWith("$2");
    }
}
