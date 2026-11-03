package com.saca.api.service;
import com.saca.api.entity.JornadaLaboral;
import com.saca.api.exception.ConflictoHorarioException;
import com.saca.api.exception.RecursoNoEncontradoException;
import com.saca.api.exception.ValidacionNegocioException;
import com.saca.api.repository.JornadaLaboralRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

// Todas las operaciones reciben el id del estudiante autenticado (sub del JWT) y solo
// actúan sobre sus propias jornadas; una jornada ajena responde igual que una inexistente.
@Service
public class jornadaLaboralService {

    private static final int DURACION_MINIMA_MINUTOS = 30;

    @Autowired
    private JornadaLaboralRepository repository;

    // 1. Agregar/Guardar jornada laboral
    public JornadaLaboral guardarJornada(JornadaLaboral jornada, Long estudianteId) {
        validarHoras(jornada.getHoraInicio(), jornada.getHoraFin());
        jornada.setDiaSemana(nombreDelDia(jornada.getDiaSemana()));
        validarEstudianteDelCuerpo(jornada.getEstudianteId(), estudianteId);
        validarSinConflicto(jornada.getDiaSemana(), jornada.getHoraInicio(), jornada.getHoraFin(), estudianteId, null);

        // Un id en el cuerpo haría que save() sobrescribiera una jornada existente
        jornada.setId(null);
        jornada.setEstudianteId(estudianteId);
        return repository.save(jornada);
    }

    // 2. Obtener jornadas por estudiante
    public List<JornadaLaboral> obtenerJornadasEstudiante(Long estudianteId) {
        return repository.findByEstudianteId(estudianteId);
    }

    // 3. Eliminar jornada por ID
    public void eliminarJornada(Long id, Long estudianteId) {
        repository.delete(obtenerPorId(id, estudianteId));
    }

    // 4. Obtener una sola jornada por su ID (Lanza excepción si no existe o es de otro estudiante)
    public JornadaLaboral obtenerPorId(Long id, Long estudianteId) {
        return repository.findByIdAndEstudianteId(id, estudianteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la jornada laboral solicitada"));
    }

    // 5. Actualizar una jornada existente utilizando  getters y setters
    public JornadaLaboral actualizarJornada(Long id, JornadaLaboral detalles, Long estudianteId) {
        // Buscamos el registro actual en la base de datos
        JornadaLaboral existente = obtenerPorId(id, estudianteId);
        validarHoras(detalles.getHoraInicio(), detalles.getHoraFin());
        String dia = nombreDelDia(detalles.getDiaSemana());
        validarEstudianteDelCuerpo(detalles.getEstudianteId(), estudianteId);
        // La propia jornada se excluye para que no choque consigo misma
        validarSinConflicto(dia, detalles.getHoraInicio(), detalles.getHoraFin(), estudianteId, id);

        // Sincronizamos los datos con los getters del objeto que viene desde React;
        // el propietario no se modifica
        existente.setDiaSemana(dia);
        existente.setHoraInicio(detalles.getHoraInicio());
        existente.setHoraFin(detalles.getHoraFin());

        // Guardamos los cambios actualizados en la base de datos
        return repository.save(existente);
    }

    // Misma regla y mensaje que la disponibilidad semanal; sin horas, la base de datos rechazaría el registro con un 500
    private void validarHoras(LocalTime horaInicio, LocalTime horaFin) {
        if (horaInicio == null || horaFin == null) {
            throw new ValidacionNegocioException("La hora de inicio y la hora de fin son obligatorias");
        }
        if (!horaInicio.isBefore(horaFin)) {
            throw new ValidacionNegocioException("La hora de inicio debe ser menor que la hora de fin");
        }
        if (Duration.between(horaInicio, horaFin).toMinutes() < DURACION_MINIMA_MINUTOS) {
            throw new ValidacionNegocioException("La jornada debe durar al menos " + DURACION_MINIMA_MINUTOS + " minutos");
        }
    }

    // Acepta el día con o sin tilde y en cualquier combinación de mayúsculas («MIERCOLES», «Miércoles»);
    // se guarda tal como llega (sin espacios en los extremos) y cualquier otro valor se rechaza
    private static String nombreDelDia(String dia) {
        if (dia == null || dia.isBlank()) {
            throw new ValidacionNegocioException("El día de la semana es obligatorio");
        }
        if (DiaSemanaTexto.aDia(dia).isEmpty()) {
            throw new ValidacionNegocioException("El día de la semana no es válido: usa un día de lunes a domingo");
        }
        return dia.trim();
    }

    // Mismo criterio que la disponibilidad semanal: mismo estudiante y día, traslape estricto
    // (08:00-12:00 y 12:00-16:00 son consecutivas y se aceptan) y duplicado exacto con su propio mensaje.
    // Las jornadas guardadas antes de esta validación pueden tener el día en otro formato, por eso se normaliza.
    private void validarSinConflicto(String dia, LocalTime inicio, LocalTime fin, Long estudianteId, Long idExcluir) {
        String diaNormalizado = DiaSemanaTexto.normalizar(dia);
        for (JornadaLaboral existente : repository.findByEstudianteId(estudianteId)) {
            if (Objects.equals(existente.getId(), idExcluir)
                    || existente.getDiaSemana() == null
                    || !DiaSemanaTexto.normalizar(existente.getDiaSemana()).equals(diaNormalizado)) {
                continue;
            }
            if (existente.getHoraInicio().equals(inicio) && existente.getHoraFin().equals(fin)) {
                throw new ConflictoHorarioException("Ya tienes registrada una jornada el " + describir(existente));
            }
            if (inicio.isBefore(existente.getHoraFin()) && fin.isAfter(existente.getHoraInicio())) {
                throw new ConflictoHorarioException("La jornada se cruza con otra registrada el " + describir(existente));
            }
        }
    }

    private static String describir(JornadaLaboral jornada) {
        return jornada.getDiaSemana() + " de " + jornada.getHoraInicio() + " a " + jornada.getHoraFin();
    }

    // El frontend envía su propio estudianteId; uno distinto se rechaza en lugar de ignorarse en silencio
    private void validarEstudianteDelCuerpo(Long estudianteIdDelCuerpo, Long estudianteId) {
        if (estudianteIdDelCuerpo != null && !estudianteIdDelCuerpo.equals(estudianteId)) {
            throw new ValidacionNegocioException("El estudiante indicado no corresponde a la sesión iniciada");
        }
    }

}
