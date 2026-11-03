package com.saca.api.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saca.api.dto.CalendarioDTO;
import com.saca.api.dto.response.CalendarioSemanaResponse;
import com.saca.api.dto.response.EventoCalendarioResponse;
import com.saca.api.entity.ActividadAcademica;
import com.saca.api.entity.CicloAcademico;
import com.saca.api.entity.EstadoCiclo;
import com.saca.api.entity.HorarioClase;
import com.saca.api.entity.MateriaInscrita;
import com.saca.api.entity.TipoEventoCalendario;
import com.saca.api.repository.ActividadAcademicaRepository;
import com.saca.api.repository.HorarioClaseRepository;

@Service
public class CalendarioService {

    // Los días del enum DiaSemana están declarados de lunes a domingo; la columna en MySQL es un enum
    // en orden alfabético, así que ordenar en la consulta pondría SABADO antes que VIERNES
    private static final Comparator<HorarioClase> POR_DIA_Y_HORA = Comparator
            .comparing((HorarioClase horario) -> horario.getDiaSemana().ordinal())
            .thenComparing(HorarioClase::getHoraInicio);

    private static final Comparator<EventoCalendarioResponse> CRONOLOGICO = Comparator
            .comparing(EventoCalendarioResponse::fecha)
            .thenComparing(EventoCalendarioResponse::horaInicio, Comparator.nullsLast(Comparator.<LocalTime>naturalOrder()))
            .thenComparing(EventoCalendarioResponse::tipo)
            .thenComparing(EventoCalendarioResponse::titulo, Comparator.nullsLast(Comparator.<String>naturalOrder()))
            .thenComparing(EventoCalendarioResponse::id);

    @Autowired
    private HorarioClaseRepository repository;

    @Autowired
    private ActividadAcademicaRepository actividadAcademicaRepository;

    @Autowired
    private Clock relojAplicacion;

    // Endpoint anterior (lista de horarios sin fechas): se conserva por compatibilidad
    @Transactional(readOnly = true)
    public List<CalendarioDTO> obtenerCalendarioSemanal(Long estudianteId) {

        List<HorarioClase> horarios =
                repository.findByMateriaInscritaCicloAcademicoEstudianteIdOrderByDiaSemanaAscHoraInicioAsc(
                        estudianteId);

        return horarios.stream()
                .sorted(POR_DIA_Y_HORA)
                .map(h -> new CalendarioDTO(
                        h.getMateriaInscrita().getNombre(),
                        h.getMateriaInscrita().getCodigo(),
                        h.getDiaSemana().name(),
                        h.getHoraInicio(),
                        h.getHoraFin(),
                        h.getModalidad().name(),
                        h.getDocenteTutor()
                ))
                .toList();
    }

    // SDACAS-21: semana de lunes a domingo que contiene la fecha indicada (sin fecha, la semana actual)
    @Transactional(readOnly = true)
    public CalendarioSemanaResponse obtenerSemana(Long estudianteId, LocalDate fecha) {
        return calcularSemana(
                fecha != null ? fecha : LocalDate.now(relojAplicacion),
                repository.findByMateriaInscritaCicloAcademicoEstudianteIdOrderByDiaSemanaAscHoraInicioAsc(estudianteId),
                actividadAcademicaRepository.findActividadesCicloActivoPorEstudiante(estudianteId));
    }

    // Cálculo puro: recibe los horarios y las actividades del estudiante y arma los eventos de la semana
    public CalendarioSemanaResponse calcularSemana(
            LocalDate fecha,
            List<HorarioClase> horarios,
            List<ActividadAcademica> actividades) {
        LocalDate lunes = fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate domingo = lunes.plusDays(6);
        List<EventoCalendarioResponse> eventos = new ArrayList<>();

        // Las clases son recurrentes: cada horario se ubica en la fecha de su día, solo si ese día cae
        // dentro de las fechas de un ciclo ACTIVO
        for (HorarioClase horario : horarios) {
            CicloAcademico ciclo = horario.getMateriaInscrita().getCicloAcademico();
            LocalDate dia = lunes.plusDays(horario.getDiaSemana().ordinal());
            if (ciclo.getEstado() == EstadoCiclo.ACTIVO
                    && !dia.isBefore(ciclo.getFechaInicio())
                    && !dia.isAfter(ciclo.getFechaFin())) {
                eventos.add(clase(horario, dia));
            }
        }

        int sinFecha = 0;
        for (ActividadAcademica actividad : actividades) {
            if (actividad.getMateriaInscrita().getCicloAcademico().getEstado() != EstadoCiclo.ACTIVO) {
                continue;
            }
            LocalDateTime referencia = actividad.getFechaEntrega() != null
                    ? actividad.getFechaEntrega()
                    : actividad.getFechaInicio();
            if (referencia == null) {
                sinFecha++;
                continue;
            }
            LocalDate dia = referencia.toLocalDate();
            if (!dia.isBefore(lunes) && !dia.isAfter(domingo)) {
                eventos.add(actividad(actividad, referencia));
            }
        }

        eventos.sort(CRONOLOGICO);
        return new CalendarioSemanaResponse(lunes, domingo, eventos, sinFecha);
    }

    private static EventoCalendarioResponse clase(HorarioClase horario, LocalDate dia) {
        MateriaInscrita materia = horario.getMateriaInscrita();
        ActividadAcademica vinculada = horario.getActividadAcademica();
        return new EventoCalendarioResponse(
                "clase-" + horario.getId() + "-" + dia,
                TipoEventoCalendario.CLASE,
                dia,
                horario.getHoraInicio(),
                horario.getHoraFin(),
                materia.getNombre(),
                materia.getId(),
                materia.getCodigo(),
                materia.getNombre(),
                horario.getId(),
                horario.getModalidad(),
                horario.getDocenteTutor(),
                vinculada == null ? null : vinculada.getIdActividad(),
                vinculada == null ? null : vinculada.getNombre(),
                null, null, null, null, null, false, false, false);
    }

    private static EventoCalendarioResponse actividad(ActividadAcademica actividad, LocalDateTime referencia) {
        MateriaInscrita materia = actividad.getMateriaInscrita();
        boolean calificada = actividad.getNota() != null;
        return new EventoCalendarioResponse(
                "actividad-" + actividad.getIdActividad(),
                TipoEventoCalendario.ACTIVIDAD,
                referencia.toLocalDate(),
                referencia.toLocalTime(),
                null,
                actividad.getNombre(),
                materia.getId(),
                materia.getCodigo(),
                materia.getNombre(),
                null, null, null, null, null,
                actividad.getIdActividad(),
                actividad.getTipoActividad(),
                BigDecimal.valueOf(actividad.getPorcentajeEvaluacion()),
                actividad.getEstado(),
                calificada ? actividad.getNota().getValorObtenido() : null,
                "COMPLETADA".equals(actividad.getEstado()),
                calificada,
                actividad.getFechaEntrega() == null);
    }
}
