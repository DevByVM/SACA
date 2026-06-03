package sv.edu.ues.saca_backend.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import sv.edu.ues.saca_backend.dto.LoginRequest;
import sv.edu.ues.saca_backend.dto.RegistroRequest;
import sv.edu.ues.saca_backend.entity.Estudiante;
import sv.edu.ues.saca_backend.repository.EstudianteRepository;

@Service
public class EstudianteService {

    @Autowired
    private EstudianteRepository repository;

    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    public String registrar(RegistroRequest request) {

        if (!request.getCorreoInstitucional()
                .endsWith("@ues.edu.sv")) {
            return "Correo institucional inválido";
        }

        if (repository.existsByCarnet(
                request.getCarnet())) {
            return "Carnet ya registrado";
        }

        if (repository.existsByCorreoInstitucional(
                request.getCorreoInstitucional())) {
            return "Correo ya registrado";
        }

        if (!request.getContrasenia()
                .matches("^(?=.*[A-Z])(?=.*\\d).{8,}$")) {
            return "La contraseña debe tener al menos 8 caracteres, una mayúscula y un número";
        }

        Estudiante estudiante = new Estudiante();

        estudiante.setNombre(request.getNombre());
        estudiante.setCarnet(request.getCarnet());
        estudiante.setCorreoInstitucional(
                request.getCorreoInstitucional());

        estudiante.setContrasenia(
                passwordEncoder.encode(
                        request.getContrasenia()));

        estudiante.setCuentaActiva(true);

        estudiante.setIntentosFallidos(0);
        estudiante.setBloqueadoHasta(null);

        repository.save(estudiante);

        return "Usuario registrado correctamente";
    }

    public String login(LoginRequest request) {

        Optional<Estudiante> estudianteOpt =
                repository.findByCorreoInstitucional(
                        request.getCorreoInstitucional());

        if (estudianteOpt.isEmpty()) {
            return "Usuario no encontrado";
        }

        Estudiante estudiante = estudianteOpt.get();

        if (!estudiante.getCuentaActiva()) {
            return "Cuenta no activada";
        }

        if (estudiante.getBloqueadoHasta() != null &&
                estudiante.getBloqueadoHasta()
                        .after(Timestamp.valueOf(
                                LocalDateTime.now()))) {

            return "Cuenta bloqueada temporalmente";
        }

        boolean passwordCorrecta =
                passwordEncoder.matches(
                        request.getContrasenia(),
                        estudiante.getContrasenia());

        if (!passwordCorrecta) {

            estudiante.setIntentosFallidos(
                    estudiante.getIntentosFallidos() + 1);

            if (estudiante.getIntentosFallidos() >= 3) {

                estudiante.setBloqueadoHasta(
                        Timestamp.valueOf(
                                LocalDateTime.now()
                                        .plusMinutes(5)));

                repository.save(estudiante);

                return "Cuenta bloqueada por 5 minutos";
            }

            repository.save(estudiante);

            return "Credenciales incorrectas";
        }

        estudiante.setIntentosFallidos(0);
        estudiante.setBloqueadoHasta(null);

        repository.save(estudiante);

        return "LOGIN_OK";
    }
}