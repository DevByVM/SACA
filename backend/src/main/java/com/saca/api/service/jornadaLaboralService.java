package com.saca.api.service;
import com.saca.api.entity.JornadaLaboral;
import com.saca.api.repository.JornadaLaboralRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class jornadaLaboralService {

    @Autowired
    private JornadaLaboralRepository repository;

    // 1. Agregar/Guardar jornada laboral
    public JornadaLaboral guardarJornada(JornadaLaboral jornada) {
        return repository.save(jornada);
    }

    // 2. Obtener jornadas por estudiante
    public List<JornadaLaboral> obtenerJornadasEstudiante(Long estudianteId) {
        return repository.findByEstudianteId(estudianteId);
    }

    // 3. Eliminar jornada por ID
    public void eliminarJornada(Long id) {
        repository.deleteById(id);
    }

    // 4. Obtener TODAS las jornadas del sistema
    public List<JornadaLaboral> listarTodo() {
        return repository.findAll();
    }

    // 5. Obtener una sola jornada por su ID (Lanza excepción si no existe)
    public JornadaLaboral obtenerPorId(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Registro de jornada laboral no encontrado con ID: " + id));
    }

    // 6. Actualizar una jornada existente utilizando  getters y setters
    public JornadaLaboral actualizarJornada(Long id, JornadaLaboral detalles) {
        // Buscamos el registro actual en la base de datos
        JornadaLaboral existente = obtenerPorId(id);

        // Sincronizamos los datos con los getters del objeto que viene desde React
        existente.setDiaSemana(detalles.getDiaSemana());
        existente.setHoraInicio(detalles.getHoraInicio());
        existente.setHoraFin(detalles.getHoraFin());
        existente.setEstudianteId(detalles.getEstudianteId());

        // Guardamos los cambios actualizados en la base de datos
        return repository.save(existente);
    }

}
