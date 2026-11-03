package com.saca.api.service;

import com.saca.api.dto.response.CalendarioPeriodoResponse;
import com.saca.api.dto.response.CargaSemanalResponse;
import com.saca.api.dto.response.EventoCalendarioResponse;
import com.saca.api.dto.response.ResumenDiaCalendarioResponse;
import com.saca.api.entity.ActividadAcademica;
import com.saca.api.entity.CicloAcademico;
import com.saca.api.entity.DiaSemana;
import com.saca.api.entity.EstadoCiclo;
import com.saca.api.entity.HorarioClase;
import com.saca.api.entity.MateriaInscrita;
import com.saca.api.entity.ModalidadClase;
import com.saca.api.entity.Nota;
import com.saca.api.entity.TipoActividadAcademica;
import com.saca.api.entity.TipoEventoCalendario;
import com.saca.api.mapper.ActividadAcademicaMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// SDACAS-21 (vista mensual): periodo arbitrario con resumen diario calculado con las reglas de SDACAS-20
class CalendarioPeriodoCalculoTest {

    // Cuadrícula de noviembre de 2026: del lunes 26 de octubre al domingo 6 de diciembre
    private static final LocalDate DESDE = LocalDate.of(2026, 10, 26);
    private static final LocalDate HASTA = LocalDate.of(2026, 12, 6);
    private static final LocalDate MIERCOLES = LocalDate.of(2026, 11, 4);

    private final CalendarioService servicio = new CalendarioService();
    private final MateriaInscrita materia = materia();
    private long siguienteId = 1;

    private MateriaInscrita materia() {
        CicloAcademico ciclo = new CicloAcademico();
        ciclo.setId(99L);
        ciclo.setEstado(EstadoCiclo.ACTIVO);
        ciclo.setFechaInicio(LocalDate.of(2026, 8, 1));
        ciclo.setFechaFin(LocalDate.of(2026, 12, 15));
        MateriaInscrita nueva = new MateriaInscrita();
        nueva.setId(50L);
        nueva.setCicloAcademico(ciclo);
        nueva.setCodigo("PRN315");
        nueva.setNombre("Programación III");
        return nueva;
    }

    private HorarioClase horario(DiaSemana dia, int hora) {
        HorarioClase horario = new HorarioClase();
        horario.setId(siguienteId++);
        horario.setMateriaInscrita(materia);
        horario.setDiaSemana(dia);
        horario.setHoraInicio(LocalTime.of(hora, 0));
        horario.setHoraFin(LocalTime.of(hora + 1, 40));
        horario.setModalidad(ModalidadClase.PRESENCIAL);
        return horario;
    }

    private ActividadAcademica actividad(String nombre, double peso, double horas, LocalDateTime entrega) {
        ActividadAcademica actividad = new ActividadAcademica();
        actividad.setIdActividad(siguienteId++);
        actividad.setMateriaInscrita(materia);
        actividad.setNombre(nombre);
        actividad.setTipoActividad(TipoActividadAcademica.PARCIAL);
        actividad.setPorcentajeEvaluacion(peso);
        actividad.setTiempoEstimadoHoras(horas);
        actividad.setEstado("ACTIVO");
        actividad.setFechaEntrega(entrega);
        actividad.setFechaInicio(entrega == null ? null : entrega.minusDays(5));
        return actividad;
    }

    private static ResumenDiaCalendarioResponse resumen(CalendarioPeriodoResponse periodo, LocalDate fecha) {
        return periodo.dias().stream().filter(dia -> dia.fecha().equals(fecha)).findFirst().orElseThrow();
    }

    @Test
    void elPeriodoTieneUnResumenPorCadaDiaEnOrden() {
        CalendarioPeriodoResponse periodo = servicio.calcularPeriodo(DESDE, HASTA, List.of(), List.of());

        assertThat(periodo.dias()).hasSize(42);
        assertThat(periodo.dias().get(0).fecha()).isEqualTo(DESDE);
        assertThat(periodo.dias().get(41).fecha()).isEqualTo(HASTA);
        for (int i = 1; i < periodo.dias().size(); i++) {
            assertThat(periodo.dias().get(i).fecha()).isEqualTo(periodo.dias().get(i - 1).fecha().plusDays(1));
        }
        assertThat(periodo.eventos()).isEmpty();
    }

    @Test
    void lasClasesSeRepitenCadaSemanaDelPeriodoDentroDelCiclo() {
        CalendarioPeriodoResponse periodo = servicio.calcularPeriodo(DESDE, HASTA,
                List.of(horario(DiaSemana.LUNES, 7), horario(DiaSemana.MIERCOLES, 9)), List.of());

        // 6 lunes; los miércoles 26/10..2/12 son 6, pero el ciclo termina el 15/12, así que entran todos
        assertThat(periodo.eventos()).filteredOn(e -> e.fecha().getDayOfWeek().getValue() == 1).hasSize(6);
        assertThat(periodo.eventos()).filteredOn(e -> e.fecha().getDayOfWeek().getValue() == 3).hasSize(6);
        assertThat(resumen(periodo, LocalDate.of(2026, 11, 2)).clases()).isEqualTo(1);
        assertThat(resumen(periodo, LocalDate.of(2026, 11, 3)).clases()).isZero();
    }

    @Test
    void unaSemanaDelPeriodoProduceExactamenteLosMismosEventosQueLaVistaSemanal() {
        List<HorarioClase> horarios = List.of(horario(DiaSemana.LUNES, 7), horario(DiaSemana.SABADO, 8));
        List<ActividadAcademica> actividades = List.of(
                actividad("Parcial", 30, 4, MIERCOLES.atTime(9, 0)),
                actividad("Otra semana", 10, 2, MIERCOLES.plusDays(7).atTime(9, 0)),
                actividad("Sin fecha", 10, 2, null));

        var semana = servicio.calcularSemana(MIERCOLES, horarios, actividades);
        var periodo = servicio.calcularPeriodo(semana.inicio(), semana.fin(), horarios, actividades);

        assertThat(periodo.eventos()).isEqualTo(semana.eventos());
        assertThat(periodo.actividadesSinFecha()).isEqualTo(semana.actividadesSinFecha());
        assertThat(periodo.desde()).isEqualTo(semana.inicio());
        assertThat(periodo.hasta()).isEqualTo(semana.fin());
    }

    @Test
    void elResumenSumaHorasPendientesYDetectaConcentracionSoloConActividadesPendientes() {
        ActividadAcademica completada = actividad("Completada", 40, 4, MIERCOLES.atTime(7, 0));
        completada.setEstado("COMPLETADA");
        CalendarioPeriodoResponse periodo = servicio.calcularPeriodo(DESDE, HASTA,
                List.of(horario(DiaSemana.MIERCOLES, 13)),
                List.of(actividad("Parcial A", 30, 3, MIERCOLES.atTime(9, 0)),
                        actividad("Parcial B", 35, 2, MIERCOLES.atTime(15, 0)),
                        completada));

        ResumenDiaCalendarioResponse miercoles = resumen(periodo, MIERCOLES);
        assertThat(miercoles.clases()).isEqualTo(1);
        assertThat(miercoles.actividades()).isEqualTo(3);
        assertThat(miercoles.horasPendientes()).isEqualByComparingTo("5");
        assertThat(miercoles.evaluacionesAltoPeso()).isEqualTo(2);
        assertThat(miercoles.concentracion()).isTrue();
    }

    @Test
    void lasCompletadasYLasCalificadasNoCuentanParaHorasNiConcentracion() {
        ActividadAcademica completada = actividad("Completada", 30, 4, MIERCOLES.atTime(7, 0));
        completada.setEstado("COMPLETADA");
        ActividadAcademica calificada = actividad("Calificada", 30, 6, MIERCOLES.atTime(8, 0));
        Nota nota = new Nota();
        nota.setValorObtenido(9);
        calificada.setNota(nota);

        CalendarioPeriodoResponse periodo = servicio.calcularPeriodo(DESDE, HASTA, List.of(),
                List.of(completada, calificada, actividad("Pendiente", 30, 1, MIERCOLES.atTime(9, 0))));

        ResumenDiaCalendarioResponse miercoles = resumen(periodo, MIERCOLES);
        assertThat(miercoles.actividades()).isEqualTo(3);
        assertThat(miercoles.horasPendientes()).isEqualByComparingTo("1");
        assertThat(miercoles.evaluacionesAltoPeso()).isEqualTo(1);
        assertThat(miercoles.concentracion()).isFalse();
    }

    @Test
    void elLimiteDeAltoPesoEsElDeLaCargaSemanal() {
        CalendarioPeriodoResponse periodo = servicio.calcularPeriodo(DESDE, HASTA, List.of(),
                List.of(actividad("Treinta", 30, 1, MIERCOLES.atTime(9, 0)),
                        actividad("Casi treinta", 29.9, 1, MIERCOLES.atTime(10, 0))));

        assertThat(resumen(periodo, MIERCOLES).evaluacionesAltoPeso()).isEqualTo(1);
        assertThat(resumen(periodo, MIERCOLES).concentracion()).isFalse();
    }

    @Test
    void laConcentracionDiariaCoincideConLaDeLaCargaSemanal() {
        LocalDateTime ahora = LocalDateTime.of(2026, 11, 2, 8, 0);
        List<ActividadAcademica> actividades = List.of(
                actividad("Parcial A", 30, 2, ahora.plusDays(2).withHour(9)),
                actividad("Parcial B", 30, 2, ahora.plusDays(2).withHour(14)),
                actividad("Parcial C", 40, 2, ahora.plusDays(4).withHour(9)),
                actividad("Tarea", 5, 1, ahora.plusDays(4).withHour(15)));

        CargaSemanalResponse carga = new CargaAcademicaService(null, new ActividadAcademicaMapper(), null)
                .calcular(actividades, ahora, ahora.plusDays(7));
        CalendarioPeriodoResponse periodo = servicio.calcularPeriodo(ahora.toLocalDate(), ahora.toLocalDate().plusDays(6),
                List.of(), actividades);

        List<LocalDate> diasConConcentracion = periodo.dias().stream()
                .filter(ResumenDiaCalendarioResponse::concentracion)
                .map(ResumenDiaCalendarioResponse::fecha)
                .toList();
        assertThat(diasConConcentracion).isEqualTo(carga.exigencia().diasConcentracion());
        assertThat(periodo.dias().stream().mapToInt(ResumenDiaCalendarioResponse::evaluacionesAltoPeso).sum())
                .isEqualTo(carga.exigencia().evaluacionesAltoPeso());
    }

    @Test
    void cadaActividadIndicaSusHorasYSiEstaPendiente() {
        ActividadAcademica completada = actividad("Completada", 10, 4, MIERCOLES.atTime(7, 0));
        completada.setEstado("COMPLETADA");

        List<EventoCalendarioResponse> eventos = servicio.calcularPeriodo(DESDE, HASTA, List.of(horario(DiaSemana.MIERCOLES, 13)),
                List.of(completada, actividad("Pendiente", 10, 2.5, MIERCOLES.atTime(9, 0)))).eventos();

        EventoCalendarioResponse eventoCompletada = eventos.stream().filter(e -> "Completada".equals(e.titulo())).findFirst().orElseThrow();
        EventoCalendarioResponse eventoPendiente = eventos.stream().filter(e -> "Pendiente".equals(e.titulo())).findFirst().orElseThrow();
        EventoCalendarioResponse clase = eventos.stream().filter(e -> e.tipo() == TipoEventoCalendario.CLASE).findFirst().orElseThrow();
        assertThat(eventoCompletada.pendiente()).isFalse();
        assertThat(eventoPendiente.pendiente()).isTrue();
        assertThat(eventoPendiente.tiempoEstimadoHoras()).isEqualByComparingTo("2.5");
        assertThat(clase.pendiente()).isFalse();
        assertThat(clase.tiempoEstimadoHoras()).isNull();
    }
}
