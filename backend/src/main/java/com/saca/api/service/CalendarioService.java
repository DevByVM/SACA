package com.saca.api.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saca.api.dto.CalendarioDTO;
import com.saca.api.dto.response.CalendarioPeriodoResponse;
import com.saca.api.dto.response.CalendarioSemanaResponse;
import com.saca.api.dto.response.EventoCalendarioResponse;
import com.saca.api.dto.response.ResumenDiaCalendarioResponse;
import com.saca.api.entity.ActividadAcademica;
import com.saca.api.entity.CicloAcademico;
import com.saca.api.entity.EstadoCiclo;
import com.saca.api.entity.HorarioClase;
import com.saca.api.entity.MateriaInscrita;
import com.saca.api.entity.TipoEventoCalendario;
import com.saca.api.exception.ValidacionNegocioException;
import com.saca.api.repository.ActividadAcademicaRepository;
import com.saca.api.repository.HorarioClaseRepository;

@Service
public class CalendarioService {

    public static final int MAXIMO_DIAS_PERIODO = 42;

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

    // Rango de las consultas por periodo del calendario (eventos y franjas): mismo límite y mismos mensajes
    public static void validarPeriodo(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new ValidacionNegocioException("La fecha inicial no puede ser posterior a la fecha final");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) + 1 > MAXIMO_DIAS_PERIODO) {
            throw new ValidacionNegocioException("El periodo no puede superar " + MAXIMO_DIAS_PERIODO + " días");
        }
    }

    // SDACAS-21: semana de lunes a domingo que contiene la fecha indicada (sin fecha, la semana actual)
    @Transactional(readOnly = true)
    public CalendarioSemanaResponse obtenerSemana(Long estudianteId, LocalDate fecha) {
        return calcularSemana(
                fecha != null ? fecha : LocalDate.now(relojAplicacion),
                repository.findParaCalendario(estudianteId),
                actividadAcademicaRepository.findParaCalendario(estudianteId));
    }

    // Vista mensual: el frontend pide la cuadrícula completa (del lunes anterior al día 1 al domingo
    // posterior al último día), por eso el límite es de 42 días
    @Transactional(readOnly = true)
    public CalendarioPeriodoResponse obtenerPeriodo(Long estudianteId, LocalDate desde, LocalDate hasta) {
        validarPeriodo(desde, hasta);
        return calcularPeriodo(
                desde,
                hasta,
                repository.findParaCalendario(estudianteId),
                actividadAcademicaRepository.findParaCalendario(estudianteId));
    }

    // Cálculo puro de la semana de lunes a domingo que contiene la fecha
    public CalendarioSemanaResponse calcularSemana(
            LocalDate fecha,
            List<HorarioClase> horarios,
            List<ActividadAcademica> actividades) {
        LocalDate lunes = fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        CalendarioPeriodoResponse periodo = calcularPeriodo(lunes, lunes.plusDays(6), horarios, actividades);
        return new CalendarioSemanaResponse(periodo.desde(), periodo.hasta(), periodo.eventos(), periodo.actividadesSinFecha());
    }

    // Cálculo puro: recibe los horarios y las actividades del estudiante y arma los eventos y el resumen
    // de cada día del periodo [desde, hasta]
    public CalendarioPeriodoResponse calcularPeriodo(
            LocalDate desde,
            LocalDate hasta,
            List<HorarioClase> horarios,
            List<ActividadAcademica> actividades) {
        List<EventoCalendarioResponse> eventos = new ArrayList<>();

        // Las clases son recurrentes: cada horario se ubica en la fecha de su día, solo si ese día cae
        // dentro de las fechas de un ciclo ACTIVO
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            for (HorarioClase horario : horarios) {
                CicloAcademico ciclo = horario.getMateriaInscrita().getCicloAcademico();
                if (horario.getDiaSemana().ordinal() == dia.getDayOfWeek().getValue() - 1
                        && ciclo.getEstado() == EstadoCiclo.ACTIVO
                        && !dia.isBefore(ciclo.getFechaInicio())
                        && !dia.isAfter(ciclo.getFechaFin())) {
                    eventos.add(clase(horario, dia));
                }
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
            if (!dia.isBefore(desde) && !dia.isAfter(hasta)) {
                eventos.add(actividad(actividad, referencia));
            }
        }

        eventos.sort(CRONOLOGICO);
        return new CalendarioPeriodoResponse(desde, hasta, eventos, resumirDias(desde, hasta, eventos), sinFecha);
    }

    // Horas pendientes y concentración por día con las reglas y umbrales de la carga semanal (SDACAS-20)
    private static List<ResumenDiaCalendarioResponse> resumirDias(
            LocalDate desde,
            LocalDate hasta,
            List<EventoCalendarioResponse> eventos) {
        Map<LocalDate, List<EventoCalendarioResponse>> porDia = eventos.stream()
                .collect(Collectors.groupingBy(EventoCalendarioResponse::fecha));
        List<ResumenDiaCalendarioResponse> dias = new ArrayList<>();

        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            List<EventoCalendarioResponse> delDia = porDia.getOrDefault(dia, List.of());
            int clases = 0;
            int actividades = 0;
            int altoPeso = 0;
            BigDecimal horas = BigDecimal.ZERO;
            for (EventoCalendarioResponse evento : delDia) {
                if (evento.tipo() == TipoEventoCalendario.CLASE) {
                    clases++;
                    continue;
                }
                actividades++;
                if (evento.pendiente()) {
                    horas = horas.add(evento.tiempoEstimadoHoras());
                    if (evento.porcentajeEvaluacion().compareTo(CargaAcademicaService.PESO_ALTO) >= 0) {
                        altoPeso++;
                    }
                }
            }
            dias.add(new ResumenDiaCalendarioResponse(dia, clases, actividades, horas, altoPeso,
                    altoPeso >= CargaAcademicaService.EVALUACIONES_ALTO_PESO_CONCENTRACION));
        }
        return dias;
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
                null, null, null, null, null, false, false, false, null, false);
    }

    private static EventoCalendarioResponse actividad(ActividadAcademica actividad, LocalDateTime referencia) {
        MateriaInscrita materia = actividad.getMateriaInscrita();
        boolean calificada = actividad.getNota() != null;
        boolean completada = "COMPLETADA".equals(actividad.getEstado());
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
                completada,
                calificada,
                actividad.getFechaEntrega() == null,
                BigDecimal.valueOf(actividad.getTiempoEstimadoHoras()),
                !completada && !calificada);
    }
}
