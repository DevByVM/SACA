package com.saca.api.service;

import com.saca.api.dto.response.CargaSemanalResponse;
import com.saca.api.entity.ActividadAcademica;
import com.saca.api.entity.MateriaInscrita;
import com.saca.api.entity.NivelCarga;
import com.saca.api.entity.Nota;
import com.saca.api.entity.TipoActividadAcademica;
import com.saca.api.mapper.ActividadAcademicaMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// SDACAS-20: cálculo puro de la carga semanal (horas y exigencia evaluativa) con una ventana fija
class CargaAcademicaCalculoTest {

    private static final LocalDateTime DESDE = LocalDateTime.of(2026, 11, 2, 8, 0);
    private static final LocalDateTime HASTA = DESDE.plusDays(7);

    private final CargaAcademicaService servicio = new CargaAcademicaService(null, new ActividadAcademicaMapper(), null);
    private final MateriaInscrita materia = materia();
    private long siguienteId = 1;

    private static MateriaInscrita materia() {
        MateriaInscrita materia = new MateriaInscrita();
        materia.setId(5L);
        materia.setCodigo("PRN315");
        materia.setNombre("Programación III");
        return materia;
    }

    private ActividadAcademica actividad(String nombre, double horas, double peso, LocalDateTime entrega) {
        ActividadAcademica actividad = new ActividadAcademica();
        actividad.setIdActividad(siguienteId++);
        actividad.setNombre(nombre);
        actividad.setTiempoEstimadoHoras(horas);
        actividad.setPorcentajeEvaluacion(peso);
        actividad.setFechaInicio(entrega == null ? null : entrega.minusDays(3));
        actividad.setFechaEntrega(entrega);
        actividad.setEstado("ACTIVO");
        actividad.setTipoActividad(TipoActividadAcademica.PARCIAL);
        actividad.setMateriaInscrita(materia);
        return actividad;
    }

    private ActividadAcademica enDias(double horas, double peso, int dias) {
        return actividad("Actividad " + siguienteId, horas, peso, DESDE.plusDays(dias));
    }

    private CargaSemanalResponse calcular(ActividadAcademica... actividades) {
        return servicio.calcular(List.of(actividades), DESDE, HASTA);
    }

    private static void assertDecimal(BigDecimal actual, String esperado) {
        assertThat(actual.compareTo(new BigDecimal(esperado))).as("%s debería ser %s", actual, esperado).isZero();
    }

    // ================= Casos del plan de pruebas

    @Test
    void hu020Cp001SumaSoloLasHorasDeLosProximosSieteDias() {
        CargaSemanalResponse r = calcular(enDias(6, 5, 1), enDias(4, 5, 4), enDias(8, 5, 20));

        assertDecimal(r.horas().horasPendientes(), "10");
        assertThat(r.actividadesPendientes()).hasSize(2);
        assertThat(r.horas().nivel()).isEqualTo(NivelCarga.NORMAL);
    }

    @Test
    void hu020Cp002ConLasMismasHorasElPesoCambiaElEstado() {
        CargaSemanalResponse pocoPeso = calcular(enDias(10, 5, 2));
        CargaSemanalResponse altoPeso = calcular(enDias(10, 40, 2));

        assertThat(pocoPeso.horas().nivel()).isEqualTo(NivelCarga.NORMAL);
        assertThat(altoPeso.horas().nivel()).isEqualTo(NivelCarga.NORMAL);
        assertThat(pocoPeso.nivelGeneral()).isEqualTo(NivelCarga.NORMAL);
        assertThat(altoPeso.exigencia().nivel()).isEqualTo(NivelCarga.ELEVADA);
        assertThat(altoPeso.nivelGeneral()).isEqualTo(NivelCarga.ELEVADA);
    }

    @Test
    void hu020Cp003LasHorasPorSiSolasDeterminanNormalElevadaYCritica() {
        assertThat(calcular(enDias(10, 5, 2)).nivelGeneral()).isEqualTo(NivelCarga.NORMAL);
        assertThat(calcular(enDias(25, 5, 2)).nivelGeneral()).isEqualTo(NivelCarga.ELEVADA);
        assertThat(calcular(enDias(40, 5, 2)).nivelGeneral()).isEqualTo(NivelCarga.CRITICA);
    }

    // ================= Límites de la carga horaria

    @Test
    void limitesDeHoras() {
        assertThat(calcular(enDias(20, 5, 1)).horas().nivel()).isEqualTo(NivelCarga.NORMAL);
        assertThat(calcular(enDias(20.1, 5, 1)).horas().nivel()).isEqualTo(NivelCarga.ELEVADA);
        assertThat(calcular(enDias(35, 5, 1)).horas().nivel()).isEqualTo(NivelCarga.ELEVADA);
        assertThat(calcular(enDias(35.1, 5, 1)).horas().nivel()).isEqualTo(NivelCarga.CRITICA);
    }

    @Test
    void lasHorasDeVariasActividadesSeSumanSinErroresDePuntoFlotante() {
        CargaSemanalResponse r = calcular(enDias(6.7, 5, 1), enDias(6.7, 5, 2), enDias(6.6, 5, 3));

        assertDecimal(r.horas().horasPendientes(), "20");
        assertThat(r.horas().nivel()).isEqualTo(NivelCarga.NORMAL);
    }

    // ================= Límites de la exigencia evaluativa

    @Test
    void limitesDelPesoAcumulado() {
        assertThat(calcular(enDias(2, 15, 1), enDias(2, 14.9, 2)).exigencia().nivel()).isEqualTo(NivelCarga.NORMAL);
        assertThat(calcular(enDias(2, 15, 1), enDias(2, 15, 2)).exigencia().nivel()).isEqualTo(NivelCarga.ELEVADA);
        assertThat(calcular(enDias(2, 20, 1), enDias(2, 20, 2), enDias(2, 19.9, 3)).exigencia().nivel())
                .isEqualTo(NivelCarga.ELEVADA);
        assertThat(calcular(enDias(2, 20, 1), enDias(2, 20, 2), enDias(2, 20, 3)).exigencia().nivel())
                .isEqualTo(NivelCarga.CRITICA);
    }

    @Test
    void unaEvaluacionDeExactamenteTreintaPorCientoEsDeAltoPeso() {
        CargaSemanalResponse treinta = calcular(enDias(2, 30, 1));
        CargaSemanalResponse casiTreinta = calcular(enDias(2, 29.9, 1));

        assertThat(treinta.exigencia().evaluacionesAltoPeso()).isEqualTo(1);
        assertThat(treinta.exigencia().nivel()).isEqualTo(NivelCarga.ELEVADA);
        assertThat(casiTreinta.exigencia().evaluacionesAltoPeso()).isZero();
        assertThat(casiTreinta.exigencia().nivel()).isEqualTo(NivelCarga.NORMAL);
        assertDecimal(treinta.exigencia().pesoMaximo(), "30");
    }

    @Test
    void dosEvaluacionesDeAltoPesoElMismoDiaSonConcentracion() {
        LocalDateTime jueves = DESDE.plusDays(3);
        CargaSemanalResponse r = calcular(
                actividad("Parcial A", 2, 30, jueves.withHour(8)),
                actividad("Parcial B", 2, 35, jueves.withHour(14)));

        assertThat(r.exigencia().concentracion()).isTrue();
        assertThat(r.exigencia().diasConcentracion()).containsExactly(LocalDate.of(2026, 11, 5));
        assertThat(r.exigencia().nivel()).isEqualTo(NivelCarga.CRITICA);
        assertThat(r.nivelGeneral()).isEqualTo(NivelCarga.CRITICA);
        assertThat(r.motivos()).anyMatch(m -> m.contains("concentración de 2 evaluaciones"));
    }

    @Test
    void dosEvaluacionesDeAltoPesoEnDiasDistintosNoSonConcentracion() {
        // Con los umbrales aprobados suman 60 %, así que siguen siendo críticas por el peso acumulado
        CargaSemanalResponse r = calcular(enDias(2, 30, 1), enDias(2, 30, 4));

        assertThat(r.exigencia().concentracion()).isFalse();
        assertThat(r.exigencia().diasConcentracion()).isEmpty();
        assertThat(r.exigencia().nivel()).isEqualTo(NivelCarga.CRITICA);
    }

    // ================= Clasificación general

    @Test
    void siAmbosIndicadoresSonElevadosLaCargaGeneralEsCritica() {
        CargaSemanalResponse r = calcular(enDias(25, 30, 2));

        assertThat(r.horas().nivel()).isEqualTo(NivelCarga.ELEVADA);
        assertThat(r.exigencia().nivel()).isEqualTo(NivelCarga.ELEVADA);
        assertThat(r.nivelGeneral()).isEqualTo(NivelCarga.CRITICA);
        assertThat(r.motivos()).contains("Ambos indicadores requieren atención, por eso la carga general es crítica.");
    }

    @Test
    void laExigenciaCriticaPorSiSolaHaceCriticaLaCargaGeneral() {
        CargaSemanalResponse r = calcular(enDias(3, 20, 1), enDias(3, 20, 2), enDias(3, 20, 3));

        assertThat(r.horas().nivel()).isEqualTo(NivelCarga.NORMAL);
        assertThat(r.exigencia().nivel()).isEqualTo(NivelCarga.CRITICA);
        assertThat(r.nivelGeneral()).isEqualTo(NivelCarga.CRITICA);
    }

    @Test
    void losIndicadoresSeInformanPorSeparado() {
        CargaSemanalResponse r = calcular(enDias(40, 5, 2));

        assertThat(r.horas().nivel()).isEqualTo(NivelCarga.CRITICA);
        assertThat(r.exigencia().nivel()).isEqualTo(NivelCarga.NORMAL);
        assertThat(r.nivelGeneral()).isEqualTo(NivelCarga.CRITICA);
        assertThat(r.motivos()).hasSize(2);
        assertThat(r.motivos().get(0)).startsWith("Carga horaria crítica: 40 h");
        assertThat(r.motivos().get(1)).startsWith("Exigencia evaluativa normal: 5 % de peso acumulado");
    }

    @Test
    void losMotivosIdentificanLasEvaluacionesDeAltoPeso() {
        CargaSemanalResponse r = calcular(actividad("Parcial 2", 4, 40, LocalDateTime.of(2026, 11, 5, 9, 0)));

        assertThat(r.motivos().get(1))
                .startsWith("Exigencia evaluativa elevada:")
                .contains("40 % de peso acumulado (30 % o más)")
                .contains("Parcial 2 (PRN315, 40 %, jue 05/11)");
    }

    // ================= Qué actividades cuentan

    @Test
    void lasCompletadasYLasCalificadasNoSumanEnNingunIndicador() {
        ActividadAcademica completada = enDias(10, 40, 1);
        completada.setEstado("COMPLETADA");
        ActividadAcademica calificada = enDias(10, 40, 2);
        Nota nota = new Nota();
        nota.setValorObtenido(8);
        calificada.setNota(nota);

        CargaSemanalResponse r = calcular(completada, calificada, enDias(3, 5, 3));

        assertDecimal(r.horas().horasPendientes(), "3");
        assertDecimal(r.exigencia().pesoAcumulado(), "5");
        assertThat(r.actividadesPendientes()).hasSize(1);
        assertThat(r.actividadesNoPendientes()).hasSize(2);
        assertThat(r.nivelGeneral()).isEqualTo(NivelCarga.NORMAL);
    }

    @Test
    void lasActividadesSinFechaNoEntranPeroSeAvisan() {
        CargaSemanalResponse r = calcular(actividad("Sin fecha", 30, 50, null), enDias(2, 5, 1));

        assertThat(r.actividadesSinFecha()).isEqualTo(1);
        assertDecimal(r.horas().horasPendientes(), "2");
        assertDecimal(r.exigencia().pesoAcumulado(), "5");
        assertThat(r.actividadesPendientes()).hasSize(1);
    }

    @Test
    void unaActividadSinFechaYaCompletadaNoGeneraAviso() {
        ActividadAcademica completada = actividad("Antigua", 3, 10, null);
        completada.setEstado("COMPLETADA");

        assertThat(calcular(completada).actividadesSinFecha()).isZero();
    }

    @Test
    void sinFechaDeEntregaSeUsaLaFechaDeInicio() {
        ActividadAcademica soloInicio = actividad("Solo inicio", 5, 10, null);
        soloInicio.setFechaInicio(DESDE.plusDays(2));

        CargaSemanalResponse r = calcular(soloInicio);

        assertThat(r.actividadesSinFecha()).isZero();
        assertDecimal(r.horas().horasPendientes(), "5");
    }

    @Test
    void lasActividadesSinPorcentajeCuentanHorasPeroNoExigencia() {
        CargaSemanalResponse r = calcular(enDias(8, 0, 1), enDias(2, 10, 2));

        assertDecimal(r.horas().horasPendientes(), "10");
        assertDecimal(r.exigencia().pesoAcumulado(), "10");
        assertThat(r.actividadesSinPorcentaje()).isEqualTo(1);
        assertThat(r.actividadesPendientes()).hasSize(2);
    }

    @Test
    void unaSemanaSinActividadesEsNormalYLoExplica() {
        CargaSemanalResponse r = calcular();

        assertDecimal(r.horas().horasPendientes(), "0");
        assertDecimal(r.exigencia().pesoAcumulado(), "0");
        assertThat(r.nivelGeneral()).isEqualTo(NivelCarga.NORMAL);
        assertThat(r.motivos()).containsExactly(
                "Carga horaria normal: 0 h pendientes en los próximos 7 días (hasta 20 h).",
                "Exigencia evaluativa normal: no hay evaluaciones con porcentaje en los próximos 7 días.");
    }

    @Test
    void losLimitesDeLaVentanaSonInclusivos() {
        CargaSemanalResponse r = calcular(
                actividad("Justo al inicio", 1, 5, DESDE),
                actividad("Justo al final", 1, 5, HASTA),
                actividad("Vencida hoy", 1, 5, DESDE.minusMinutes(1)),
                actividad("Después", 1, 5, HASTA.plusMinutes(1)));

        assertThat(r.actividadesPendientes()).extracting("nombre").containsExactly("Justo al inicio", "Justo al final");
        assertThat(r.desde()).isEqualTo(DESDE);
        assertThat(r.hasta()).isEqualTo(HASTA);
    }

    @Test
    void lasPendientesSeOrdenanPorFechaPesoYHoras() {
        LocalDateTime dia = DESDE.plusDays(2);
        CargaSemanalResponse r = calcular(
                actividad("Tarde", 1, 5, dia.plusDays(1)),
                actividad("Mismo día, menos peso", 9, 10, dia),
                actividad("Mismo día, más peso", 1, 30, dia));

        assertThat(r.actividadesPendientes()).extracting("nombre")
                .containsExactly("Mismo día, más peso", "Mismo día, menos peso", "Tarde");
    }
}
