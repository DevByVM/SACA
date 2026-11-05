package com.saca.api.service;

import com.saca.api.dto.response.CalendarioFranjasResponse;
import com.saca.api.dto.response.FranjaCalendarioResponse;
import com.saca.api.dto.response.FranjaCalendarioResponse.Tipo;
import com.saca.api.entity.CicloAcademico;
import com.saca.api.entity.DisponibilidadSemanal;
import com.saca.api.entity.EstadoCiclo;
import com.saca.api.entity.JornadaLaboral;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Proyección de jornada laboral y disponibilidad sobre las fechas del calendario
class FranjasCalendarioCalculoTest {

    // Semana del lunes 2 al domingo 8 de noviembre de 2026
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 2);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 11, 8);

    private final FranjasCalendarioService servicio = new FranjasCalendarioService(null, null, null);
    private long siguienteId = 1;

    private final CicloAcademico activo = ciclo(EstadoCiclo.ACTIVO, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 15));

    private CicloAcademico ciclo(EstadoCiclo estado, LocalDate inicio, LocalDate fin) {
        CicloAcademico ciclo = new CicloAcademico();
        ciclo.setId(siguienteId++);
        ciclo.setEstado(estado);
        ciclo.setFechaInicio(inicio);
        ciclo.setFechaFin(fin);
        return ciclo;
    }

    private JornadaLaboral jornada(String dia, String inicio, String fin) {
        JornadaLaboral jornada = new JornadaLaboral(dia, LocalTime.parse(inicio), LocalTime.parse(fin), 1L);
        jornada.setId(siguienteId++);
        return jornada;
    }

    private DisponibilidadSemanal bloque(String dia, String inicio, String fin, String tipo, CicloAcademico ciclo) {
        DisponibilidadSemanal bloque = new DisponibilidadSemanal(dia, LocalTime.parse(inicio), LocalTime.parse(fin), tipo, ciclo.getId(), 1L);
        bloque.setId(siguienteId++);
        return bloque;
    }

    private List<FranjaCalendarioResponse> semana(List<JornadaLaboral> jornadas, List<CicloAcademico> ciclos, List<DisponibilidadSemanal> bloques) {
        return servicio.calcular(LUNES, DOMINGO, jornadas, ciclos, bloques).franjas();
    }

    @Test
    void laJornadaSeRepiteEnSuDiaDentroDelPeriodo() {
        CalendarioFranjasResponse respuesta = servicio.calcular(LUNES, LUNES.plusDays(20),
                List.of(jornada("Martes", "08:00", "12:00")), List.of(), List.of());

        assertThat(respuesta.franjas()).extracting(FranjaCalendarioResponse::fecha).containsExactly(
                LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 17));
        assertThat(respuesta.franjas()).allMatch(f -> f.tipo() == Tipo.JORNADA_LABORAL
                && f.horaInicio().equals(LocalTime.of(8, 0)) && f.horaFin().equals(LocalTime.of(12, 0)));
        assertThat(respuesta.desde()).isEqualTo(LUNES);
    }

    @Test
    void losDiasSeReconocenEnCualquierFormato() {
        List<FranjaCalendarioResponse> franjas = semana(
                List.of(jornada("Miércoles", "08:00", "09:00"), jornada("SABADO", "08:00", "09:00"), jornada(" domingo ", "08:00", "09:00")),
                List.of(activo),
                List.of(bloque("MIERCOLES", "18:00", "20:00", "ESTUDIO", activo), bloque("Sábado", "18:00", "20:00", "ESTUDIO", activo)));

        assertThat(franjas).extracting(FranjaCalendarioResponse::fecha).containsExactly(
                LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 7), DOMINGO);
    }

    @Test
    void unDiaQueNoSeReconoceSeOmiteSinFallar() {
        assertThat(semana(List.of(jornada("Feriado", "08:00", "12:00")), List.of(activo),
                List.of(bloque("Lun", "08:00", "12:00", "ESTUDIO", activo)))).isEmpty();
    }

    @Test
    void soloSeRepresentanLosBloquesNoDisponibleYEstudio() {
        List<FranjaCalendarioResponse> franjas = semana(List.of(), List.of(activo), List.of(
                bloque("LUNES", "07:00", "09:00", "NO_DISPONIBLE", activo),
                bloque("LUNES", "10:00", "12:00", "ESTUDIO", activo),
                bloque("LUNES", "13:00", "15:00", "CLASE", activo),
                bloque("LUNES", "16:00", "18:00", "LIBRE", activo)));

        assertThat(franjas).extracting(FranjaCalendarioResponse::tipo).containsExactly(Tipo.NO_DISPONIBLE, Tipo.ESTUDIO);
    }

    @Test
    void laDisponibilidadSoloCuentaEnCiclosActivosYDentroDeSusFechas() {
        CicloAcademico finalizado = ciclo(EstadoCiclo.FINALIZADO, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 15));
        CicloAcademico terminaMiercoles = ciclo(EstadoCiclo.ACTIVO, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 11, 4));
        CicloAcademico empiezaViernes = ciclo(EstadoCiclo.ACTIVO, LocalDate.of(2026, 11, 6), LocalDate.of(2027, 2, 1));

        List<FranjaCalendarioResponse> franjas = semana(List.of(), List.of(finalizado, terminaMiercoles, empiezaViernes), List.of(
                bloque("LUNES", "08:00", "10:00", "ESTUDIO", finalizado),
                bloque("JUEVES", "08:00", "10:00", "ESTUDIO", terminaMiercoles),
                bloque("MARTES", "08:00", "10:00", "ESTUDIO", terminaMiercoles),
                bloque("JUEVES", "14:00", "16:00", "NO_DISPONIBLE", empiezaViernes),
                bloque("SABADO", "14:00", "16:00", "NO_DISPONIBLE", empiezaViernes)));

        assertThat(franjas).extracting(FranjaCalendarioResponse::fecha)
                .containsExactly(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 7));
    }

    @Test
    void laJornadaNoDependeDelCicloYSeMuestraSinCicloActivo() {
        assertThat(semana(List.of(jornada("Lunes", "08:00", "12:00")), List.of(), List.of()))
                .extracting(FranjaCalendarioResponse::tipo).containsExactly(Tipo.JORNADA_LABORAL);
    }

    @Test
    void lasFranjasSeOrdenanPorFechaYHoraYConservanSuOrigen() {
        JornadaLaboral manana = jornada("Lunes", "08:00", "12:00");
        DisponibilidadSemanal estudio = bloque("LUNES", "06:00", "07:30", "ESTUDIO", activo);
        DisponibilidadSemanal noDisponible = bloque("DOMINGO", "09:00", "11:00", "NO_DISPONIBLE", activo);

        List<FranjaCalendarioResponse> franjas = semana(List.of(manana), List.of(activo), List.of(noDisponible, estudio));

        assertThat(franjas).extracting(FranjaCalendarioResponse::origenId)
                .containsExactly(estudio.getId(), manana.getId(), noDisponible.getId());
    }

    @Test
    void sinRegistrosNoHayFranjas() {
        assertThat(semana(List.of(), List.of(activo), List.of())).isEmpty();
    }
}
