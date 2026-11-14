package com.saca.api.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saca.api.dto.request.FranjaPlantillaRequest;
import com.saca.api.dto.request.PlantillaPlanificacionRequest;
import com.saca.api.dto.response.FranjaPlantillaResponse;
import com.saca.api.dto.response.PlantillaPlanificacionResponse;
import com.saca.api.entity.FranjaPlantillaPlanificacion;
import com.saca.api.entity.PerfilPlantillaPlanificacion;
import com.saca.api.entity.PlantillaPlanificacion;
import com.saca.api.exception.RecursoNoEncontradoException;
import com.saca.api.exception.ValidacionNegocioException;
import com.saca.api.repository.JornadaLaboralRepository;
import com.saca.api.repository.PlantillaPlanificacionRepository;

@Service
public class PlantillaPlanificacionService {

    private final PlantillaPlanificacionRepository repository;
    private final JornadaLaboralRepository jornadaLaboralRepository;

    public PlantillaPlanificacionService(
            PlantillaPlanificacionRepository repository,
            JornadaLaboralRepository jornadaLaboralRepository) {
        this.repository = repository;
        this.jornadaLaboralRepository = jornadaLaboralRepository;
    }

    @Transactional(readOnly = true)
    public List<PlantillaPlanificacionResponse> listar() {
        return repository.findAllByOrderByPerfilAscNombreAsc().stream().map(this::respuesta).toList();
    }

    @Transactional
    public PlantillaPlanificacionResponse crear(PlantillaPlanificacionRequest request) {
        validarFranjas(request.franjas());
        PlantillaPlanificacion plantilla = new PlantillaPlanificacion();
        actualizar(plantilla, request);
        return respuesta(repository.save(plantilla));
    }

    @Transactional
    public PlantillaPlanificacionResponse actualizar(Long id, PlantillaPlanificacionRequest request) {
        PlantillaPlanificacion plantilla = buscar(id);
        validarFranjas(request.franjas());
        actualizar(plantilla, request);
        return respuesta(plantilla);
    }

    @Transactional(readOnly = true)
    public PlantillaPlanificacionResponse vigenteParaEstudiante(Long estudianteId) {
        PerfilPlantillaPlanificacion perfil = jornadaLaboralRepository.existsByEstudianteId(estudianteId)
                ? PerfilPlantillaPlanificacion.CON_JORNADA_LABORAL
                : PerfilPlantillaPlanificacion.SIN_JORNADA_LABORAL;
        return repository.findByPerfilAndActivaTrue(perfil).stream()
                .findFirst()
                .map(this::respuesta)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No hay una plantilla activa para el perfil " + perfil));
    }

    private void actualizar(PlantillaPlanificacion plantilla, PlantillaPlanificacionRequest request) {
        if (request.activa()) {
            repository.findByPerfilAndActivaTrue(request.perfil()).stream()
                    .filter(otra -> !otra.getId().equals(plantilla.getId()))
                    .forEach(otra -> otra.setActiva(false));
        }
        plantilla.setNombre(request.nombre().trim());
        plantilla.setPerfil(request.perfil());
        plantilla.setActiva(request.activa());
        List<FranjaPlantillaPlanificacion> franjas = request.franjas().stream()
                .map(this::crearFranja)
                .toList();
        plantilla.reemplazarFranjas(franjas);
    }

    private PlantillaPlanificacion buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la plantilla de planificación"));
    }

    private void validarFranjas(List<FranjaPlantillaRequest> franjas) {
        for (int i = 0; i < franjas.size(); i++) {
            FranjaPlantillaRequest actual = franjas.get(i);
            if (!actual.horaInicio().isBefore(actual.horaFin())) {
                throw new ValidacionNegocioException("La hora de inicio debe ser anterior a la hora de fin");
            }
            for (int j = i + 1; j < franjas.size(); j++) {
                FranjaPlantillaRequest otra = franjas.get(j);
                boolean mismoDia = actual.diaSemana() == otra.diaSemana();
                boolean seCruzan = actual.horaInicio().isBefore(otra.horaFin())
                        && otra.horaInicio().isBefore(actual.horaFin());
                if (mismoDia && seCruzan) {
                    throw new ValidacionNegocioException("Las franjas de un mismo día no pueden superponerse");
                }
            }
        }
    }

    private FranjaPlantillaPlanificacion crearFranja(FranjaPlantillaRequest request) {
        FranjaPlantillaPlanificacion franja = new FranjaPlantillaPlanificacion();
        franja.setDiaSemana(request.diaSemana());
        franja.setHoraInicio(request.horaInicio());
        franja.setHoraFin(request.horaFin());
        franja.setTipoBloque(request.tipoBloque());
        return franja;
    }

    private PlantillaPlanificacionResponse respuesta(PlantillaPlanificacion plantilla) {
        List<FranjaPlantillaResponse> franjas = plantilla.getFranjas().stream()
                .sorted(Comparator.comparing(FranjaPlantillaPlanificacion::getDiaSemana)
                        .thenComparing(FranjaPlantillaPlanificacion::getHoraInicio))
                .map(franja -> new FranjaPlantillaResponse(franja.getId(), franja.getDiaSemana(),
                        franja.getHoraInicio(), franja.getHoraFin(), franja.getTipoBloque()))
                .toList();
        return new PlantillaPlanificacionResponse(plantilla.getId(), plantilla.getNombre(),
                plantilla.getPerfil(), plantilla.isActiva(), franjas);
    }
}
