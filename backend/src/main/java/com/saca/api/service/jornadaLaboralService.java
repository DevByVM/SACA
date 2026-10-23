package com.saca.api.service;
import com.saca.api.entity.JornadaLaboral;
import com.saca.api.exception.RecursoNoEncontradoException;
import com.saca.api.exception.ValidacionNegocioException;
import com.saca.api.repository.JornadaLaboralRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

// Todas las operaciones reciben el id del estudiante autenticado (sub del JWT) y solo
// actúan sobre sus propias jornadas; una jornada ajena responde igual que una inexistente.
@Service
public class jornadaLaboralService {

    @Autowired
    private JornadaLaboralRepository repository;

    // 1. Agregar/Guardar jornada laboral
    public JornadaLaboral guardarJornada(JornadaLaboral jornada, Long estudianteId) {
        validarEstudianteDelCuerpo(jornada.getEstudianteId(), estudianteId);

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
        validarEstudianteDelCuerpo(detalles.getEstudianteId(), estudianteId);

        // Sincronizamos los datos con los getters del objeto que viene desde React;
        // el propietario no se modifica
        existente.setDiaSemana(detalles.getDiaSemana());
        existente.setHoraInicio(detalles.getHoraInicio());
        existente.setHoraFin(detalles.getHoraFin());

        // Guardamos los cambios actualizados en la base de datos
        return repository.save(existente);
    }

    // El frontend envía su propio estudianteId; uno distinto se rechaza en lugar de ignorarse en silencio
    private void validarEstudianteDelCuerpo(Long estudianteIdDelCuerpo, Long estudianteId) {
        if (estudianteIdDelCuerpo != null && !estudianteIdDelCuerpo.equals(estudianteId)) {
            throw new ValidacionNegocioException("El estudiante indicado no corresponde a la sesión iniciada");
        }
    }

}
