package com.saca.api.service;

import com.saca.api.dto.response.CalendarioSemanaResponse;
import com.saca.api.dto.response.EventoCalendarioResponse;
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
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Cálculo puro de la semana del calendario (lunes a domingo) sin Spring ni base de datos
class CalendarioSemanaCalculoTest {

    // Semana del lunes 2 al domingo 8 de noviembre de 2026
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 2);
    private static final LocalDate MIERCOLES = LUNES.plusDays(2);
    private static final LocalDate DOMINGO = LUNES.plusDays(6);

    private final CalendarioService servicio = new CalendarioService();
    private long siguienteId = 1;

    private CicloAcademico ciclo(EstadoCiclo estado, LocalDate inicio, LocalDate fin) {
        CicloAcademico ciclo = new CicloAcademico();
        ciclo.setId(siguienteId++);
        ciclo.setEstado(estado);
        ciclo.setFechaInicio(inicio);
        ciclo.setFechaFin(fin);
        return ciclo;
    }

    private CicloAcademico cicloActivo() {
        return ciclo(EstadoCiclo.ACTIVO, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 15));
    }

    private MateriaInscrita materia(CicloAcademico ciclo, String codigo) {
        MateriaInscrita materia = new MateriaInscrita();
        materia.setId(siguienteId++);
        materia.setCicloAcademico(ciclo);
        materia.setCodigo(codigo);
        materia.setNombre("Materia " + codigo);
        return materia;
    }

    private HorarioClase horario(MateriaInscrita materia, DiaSemana dia, int horaInicio) {
        HorarioClase horario = new HorarioClase();
        horario.setId(siguienteId++);
        horario.setMateriaInscrita(materia);
        horario.setDiaSemana(dia);
        horario.setHoraInicio(LocalTime.of(horaInicio, 0));
        horario.setHoraFin(LocalTime.of(horaInicio + 1, 40));
        horario.setModalidad(ModalidadClase.PRESENCIAL);
        horario.setDocenteTutor("Docente " + materia.getCodigo());
        return horario;
    }

    private ActividadAcademica actividad(MateriaInscrita materia, String nombre, TipoActividadAcademica tipo,
                                         double peso, LocalDateTime entrega) {
        ActividadAcademica actividad = new ActividadAcademica();
        actividad.setIdActividad(siguienteId++);
        actividad.setMateriaInscrita(materia);
        actividad.setNombre(nombre);
        actividad.setTipoActividad(tipo);
        actividad.setPorcentajeEvaluacion(peso);
        actividad.setTiempoEstimadoHoras(3);
        actividad.setEstado("ACTIVO");
        actividad.setFechaEntrega(entrega);
        actividad.setFechaInicio(entrega == null ? null : entrega.minusDays(4));
        return actividad;
    }

    private CalendarioSemanaResponse semana(LocalDate fecha, List<HorarioClase> horarios, List<ActividadAcademica> actividades) {
        return servicio.calcularSemana(fecha, horarios, actividades);
    }

    private static List<String> titulos(CalendarioSemanaResponse semana) {
        return semana.eventos().stream().map(EventoCalendarioResponse::titulo).toList();
    }

    // ================= Semana

    @Test
    void laSemanaVaDeLunesADomingoParaCualquierDia() {
        for (LocalDate dia = LUNES; !dia.isAfter(DOMINGO); dia = dia.plusDays(1)) {
            CalendarioSemanaResponse semana = semana(dia, List.of(), List.of());
            assertThat(semana.inicio()).as("inicio para %s", dia).isEqualTo(LUNES);
            assertThat(semana.fin()).as("fin para %s", dia).isEqualTo(DOMINGO);
        }
    }

    @Test
    void unaSemanaSinEventosDevuelveSusFechasYUnaListaVacia() {
        CalendarioSemanaResponse semana = semana(MIERCOLES, List.of(), List.of());

        assertThat(semana.eventos()).isEmpty();
        assertThat(semana.actividadesSinFecha()).isZero();
        assertThat(semana.inicio()).isEqualTo(LUNES);
    }

    // ================= Orden por día y hora

    @Test
    void hu021Cp001LasClasesSeOrdenanDeLunesADomingoYPorHora() {
        MateriaInscrita materia = materia(cicloActivo(), "PRN315");
        List<HorarioClase> horarios = List.of(
                horario(materia, DiaSemana.SABADO, 7),
                horario(materia, DiaSemana.VIERNES, 8),
                horario(materia, DiaSemana.LUNES, 10),
                horario(materia, DiaSemana.DOMINGO, 9),
                horario(materia, DiaSemana.LUNES, 7));

        CalendarioSemanaResponse semana = semana(MIERCOLES, horarios, List.of());

        assertThat(semana.eventos()).extracting(EventoCalendarioResponse::fecha).containsExactly(
                LUNES, LUNES, LUNES.plusDays(4), LUNES.plusDays(5), DOMINGO);
        assertThat(semana.eventos().get(0).horaInicio()).isEqualTo(LocalTime.of(7, 0));
        assertThat(semana.eventos().get(1).horaInicio()).isEqualTo(LocalTime.of(10, 0));
        // Antes, el orden alfabético del enum en MySQL ponía SABADO antes que VIERNES
        assertThat(semana.eventos().get(2).fecha().getDayOfWeek()).hasToString("FRIDAY");
    }

    @Test
    void cadaHorarioApareceUnaSolaVezPorSemanaEnLaFechaDeSuDia() {
        MateriaInscrita materia = materia(cicloActivo(), "PRN315");
        HorarioClase martes = horario(materia, DiaSemana.MARTES, 7);

        CalendarioSemanaResponse semana = semana(DOMINGO, List.of(martes), List.of());

        assertThat(semana.eventos()).hasSize(1);
        EventoCalendarioResponse clase = semana.eventos().get(0);
        assertThat(clase.tipo()).isEqualTo(TipoEventoCalendario.CLASE);
        assertThat(clase.fecha()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(clase.horarioId()).isEqualTo(martes.getId());
        assertThat(clase.modalidad()).isEqualTo(ModalidadClase.PRESENCIAL);
        assertThat(clase.docenteTutor()).isEqualTo("Docente PRN315");
        assertThat(clase.materiaCodigo()).isEqualTo("PRN315");
    }

    // ================= F4: clases solo del ciclo ACTIVO y dentro de sus fechas

    @Test
    void noSeMuestranClasesDeCiclosQueNoEstanActivos() {
        MateriaInscrita finalizada = materia(ciclo(EstadoCiclo.FINALIZADO, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 15)), "FIN115");
        MateriaInscrita planificada = materia(ciclo(EstadoCiclo.PLANIFICADO, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 15)), "PLA115");

        CalendarioSemanaResponse semana = semana(MIERCOLES,
                List.of(horario(finalizada, DiaSemana.LUNES, 7), horario(planificada, DiaSemana.MARTES, 7)), List.of());

        assertThat(semana.eventos()).isEmpty();
    }

    @Test
    void lasClasesSoloAparecenEnLosDiasDentroDeLasFechasDelCiclo() {
        // Ciclo activo que termina el miércoles 4 y otro que empieza el viernes 6
        MateriaInscrita termina = materia(ciclo(EstadoCiclo.ACTIVO, LocalDate.of(2026, 8, 1), MIERCOLES), "FIN115");
        MateriaInscrita empieza = materia(ciclo(EstadoCiclo.ACTIVO, LUNES.plusDays(4), LocalDate.of(2027, 3, 1)), "INI115");
        List<HorarioClase> horarios = List.of(
                horario(termina, DiaSemana.MIERCOLES, 7),
                horario(termina, DiaSemana.JUEVES, 7),
                horario(empieza, DiaSemana.JUEVES, 9),
                horario(empieza, DiaSemana.VIERNES, 9));

        CalendarioSemanaResponse semana = semana(MIERCOLES, horarios, List.of());

        assertThat(semana.eventos()).extracting(e -> e.materiaCodigo() + " " + e.fecha())
                .containsExactly("FIN115 2026-11-04", "INI115 2026-11-06");
    }

    // ================= Clases y actividades diferenciadas

    @Test
    void hu021Cp002ClasesYActividadesSeDiferencianConSuTipoYPorcentaje() {
        MateriaInscrita materia = materia(cicloActivo(), "PRN315");
        HorarioClase clase = horario(materia, DiaSemana.MIERCOLES, 7);
        ActividadAcademica parcial = actividad(materia, "Parcial 1", TipoActividadAcademica.PARCIAL, 30,
                MIERCOLES.atTime(9, 30));
        ActividadAcademica laboratorio = actividad(materia, "Laboratorio 2", TipoActividadAcademica.LABORATORIO, 10,
                MIERCOLES.atTime(6, 0));

        CalendarioSemanaResponse semana = semana(MIERCOLES, List.of(clase), List.of(parcial, laboratorio));

        assertThat(titulos(semana)).containsExactly("Laboratorio 2", "Materia PRN315", "Parcial 1");
        EventoCalendarioResponse eventoParcial = semana.eventos().get(2);
        assertThat(eventoParcial.tipo()).isEqualTo(TipoEventoCalendario.ACTIVIDAD);
        assertThat(eventoParcial.tipoActividad()).isEqualTo(TipoActividadAcademica.PARCIAL);
        assertThat(eventoParcial.porcentajeEvaluacion()).isEqualByComparingTo("30");
        assertThat(eventoParcial.horaInicio()).isEqualTo(LocalTime.of(9, 30));
        assertThat(eventoParcial.horaFin()).isNull();
        assertThat(eventoParcial.modalidad()).isNull();
        assertThat(semana.eventos().get(1).tipo()).isEqualTo(TipoEventoCalendario.CLASE);
        assertThat(semana.eventos().get(1).tipoActividad()).isNull();
    }

    @Test
    void lasActividadesCompletadasOCalificadasSeMarcanYConservanSuEstado() {
        MateriaInscrita materia = materia(cicloActivo(), "PRN315");
        ActividadAcademica completada = actividad(materia, "Completada", TipoActividadAcademica.PROYECTO, 20, MIERCOLES.atTime(8, 0));
        completada.setEstado("COMPLETADA");
        ActividadAcademica calificada = actividad(materia, "Calificada", TipoActividadAcademica.PARCIAL, 25, MIERCOLES.atTime(9, 0));
        Nota nota = new Nota();
        nota.setValorObtenido(8.5);
        calificada.setNota(nota);
        ActividadAcademica pendiente = actividad(materia, "Pendiente", TipoActividadAcademica.INVESTIGACION, 5, MIERCOLES.atTime(10, 0));

        List<EventoCalendarioResponse> eventos = semana(MIERCOLES, List.of(), List.of(completada, calificada, pendiente)).eventos();

        assertThat(eventos.get(0).completada()).isTrue();
        assertThat(eventos.get(0).estado()).isEqualTo("COMPLETADA");
        assertThat(eventos.get(1).calificada()).isTrue();
        assertThat(eventos.get(1).completada()).isFalse();
        assertThat(eventos.get(1).notaObtenida()).isEqualTo(8.5);
        assertThat(eventos.get(2).completada()).isFalse();
        assertThat(eventos.get(2).calificada()).isFalse();
        assertThat(eventos.get(2).estado()).isEqualTo("ACTIVO");
    }

    @Test
    void unaClaseVinculadaMuestraSuActividadSinDuplicarLaEntrega() {
        MateriaInscrita materia = materia(cicloActivo(), "PRN315");
        ActividadAcademica laboratorio = actividad(materia, "Laboratorio 1", TipoActividadAcademica.LABORATORIO, 10,
                MIERCOLES.atTime(13, 0));
        HorarioClase clase = horario(materia, DiaSemana.MIERCOLES, 13);
        clase.setActividadAcademica(laboratorio);

        List<EventoCalendarioResponse> eventos = semana(MIERCOLES, List.of(clase), List.of(laboratorio)).eventos();

        assertThat(eventos).hasSize(2);
        assertThat(eventos).extracting(EventoCalendarioResponse::id).doesNotHaveDuplicates();
        assertThat(eventos.get(0).tipo()).isEqualTo(TipoEventoCalendario.CLASE);
        assertThat(eventos.get(0).actividadVinculadaNombre()).isEqualTo("Laboratorio 1");
        assertThat(eventos.get(1).tipo()).isEqualTo(TipoEventoCalendario.ACTIVIDAD);
        assertThat(eventos.get(1).actividadId()).isEqualTo(laboratorio.getIdActividad());
    }

    // ================= F5: fecha de referencia

    @Test
    void sinFechaDeEntregaSeUbicaPorLaFechaDeInicioYSinNingunaSeCuenta() {
        MateriaInscrita materia = materia(cicloActivo(), "PRN315");
        ActividadAcademica soloInicio = actividad(materia, "Solo inicio", TipoActividadAcademica.PROYECTO, 15, null);
        soloInicio.setFechaInicio(MIERCOLES.atTime(10, 0));
        ActividadAcademica sinFecha = actividad(materia, "Sin fecha", TipoActividadAcademica.PARCIAL, 20, null);

        CalendarioSemanaResponse semana = semana(MIERCOLES, List.of(), List.of(soloInicio, sinFecha));

        assertThat(titulos(semana)).containsExactly("Solo inicio");
        assertThat(semana.eventos().get(0).ubicadaPorFechaInicio()).isTrue();
        assertThat(semana.actividadesSinFecha()).isEqualTo(1);
    }

    @Test
    void losBordesDeLaSemanaSonInclusivos() {
        MateriaInscrita materia = materia(cicloActivo(), "PRN315");
        List<ActividadAcademica> actividades = List.of(
                actividad(materia, "Lunes 00:00", TipoActividadAcademica.PARCIAL, 5, LUNES.atStartOfDay()),
                actividad(materia, "Domingo 23:59", TipoActividadAcademica.PARCIAL, 5, DOMINGO.atTime(23, 59)),
                actividad(materia, "Domingo anterior", TipoActividadAcademica.PARCIAL, 5, LUNES.minusDays(1).atTime(23, 59)),
                actividad(materia, "Lunes siguiente", TipoActividadAcademica.PARCIAL, 5, DOMINGO.plusDays(1).atStartOfDay()));

        assertThat(titulos(semana(MIERCOLES, List.of(), actividades))).containsExactly("Lunes 00:00", "Domingo 23:59");
    }

    @Test
    void lasActividadesDeCiclosNoActivosNoSeMuestran() {
        MateriaInscrita finalizada = materia(ciclo(EstadoCiclo.FINALIZADO, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 12, 1)), "FIN115");
        ActividadAcademica antigua = actividad(finalizada, "Antigua", TipoActividadAcademica.PARCIAL, 30, MIERCOLES.atTime(8, 0));
        ActividadAcademica antiguaSinFecha = actividad(finalizada, "Antigua sin fecha", TipoActividadAcademica.PARCIAL, 30, null);

        CalendarioSemanaResponse semana = semana(MIERCOLES, List.of(), List.of(antigua, antiguaSinFecha));

        assertThat(semana.eventos()).isEmpty();
        assertThat(semana.actividadesSinFecha()).isZero();
    }

    // ================= Cambiar de semana

    @Test
    void hu021Cp003AlCambiarDeSemanaCambianLasFechasYLosEventos() {
        MateriaInscrita materia = materia(cicloActivo(), "PRN315");
        HorarioClase lunes = horario(materia, DiaSemana.LUNES, 7);
        ActividadAcademica estaSemana = actividad(materia, "Esta semana", TipoActividadAcademica.PARCIAL, 20, MIERCOLES.atTime(8, 0));
        ActividadAcademica siguiente = actividad(materia, "Semana siguiente", TipoActividadAcademica.PROYECTO, 25, MIERCOLES.plusDays(7).atTime(8, 0));
        ActividadAcademica anterior = actividad(materia, "Semana anterior", TipoActividadAcademica.LABORATORIO, 10, MIERCOLES.minusDays(7).atTime(8, 0));
        List<HorarioClase> horarios = List.of(lunes);
        List<ActividadAcademica> actividades = List.of(estaSemana, siguiente, anterior);

        CalendarioSemanaResponse actual = semana(MIERCOLES, horarios, actividades);
        CalendarioSemanaResponse proxima = semana(MIERCOLES.plusDays(7), horarios, actividades);
        CalendarioSemanaResponse previa = semana(MIERCOLES.minusDays(7), horarios, actividades);

        assertThat(titulos(actual)).containsExactly("Materia PRN315", "Esta semana");
        assertThat(proxima.inicio()).isEqualTo(LUNES.plusDays(7));
        assertThat(titulos(proxima)).containsExactly("Materia PRN315", "Semana siguiente");
        assertThat(proxima.eventos().get(0).fecha()).isEqualTo(LUNES.plusDays(7));
        assertThat(previa.inicio()).isEqualTo(LUNES.minusDays(7));
        assertThat(titulos(previa)).containsExactly("Materia PRN315", "Semana anterior");
    }

    @Test
    void sePuedeNavegarAUnaSemanaFueraDelCicloYQuedaVacia() {
        MateriaInscrita materia = materia(cicloActivo(), "PRN315");

        CalendarioSemanaResponse semana = semana(LocalDate.of(2027, 6, 9), List.of(horario(materia, DiaSemana.LUNES, 7)), List.of());

        assertThat(semana.inicio()).isEqualTo(LocalDate.of(2027, 6, 7));
        assertThat(semana.eventos()).isEmpty();
    }
}
