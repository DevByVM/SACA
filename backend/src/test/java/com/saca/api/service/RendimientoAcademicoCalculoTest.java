package com.saca.api.service;

import com.saca.api.dto.response.ActividadRendimientoResponse;
import com.saca.api.dto.response.RendimientoMateriaResponse;
import com.saca.api.entity.ActividadAcademica;
import com.saca.api.entity.MateriaInscrita;
import com.saca.api.entity.Nota;
import com.saca.api.entity.SituacionRendimiento;
import com.saca.api.entity.TipoActividadAcademica;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Cálculo puro del rendimiento, sin Spring ni base de datos
class RendimientoAcademicoCalculoTest {

    private static final BigDecimal SEIS = new BigDecimal("6.00");

    private final RendimientoAcademicoService servicio = new RendimientoAcademicoService(null, null, null, null);
    private final MateriaInscrita materia = materia();
    private long siguienteId = 1;

    private static MateriaInscrita materia() {
        MateriaInscrita materia = new MateriaInscrita();
        materia.setId(10L);
        materia.setCodigo("PRN315");
        materia.setNombre("Programación III");
        return materia;
    }

    private ActividadAcademica actividad(double peso, Double nota, String estado) {
        ActividadAcademica actividad = new ActividadAcademica();
        actividad.setIdActividad(siguienteId++);
        actividad.setNombre("Actividad " + actividad.getIdActividad());
        actividad.setPorcentajeEvaluacion(peso);
        actividad.setEstado(estado);
        actividad.setTipoActividad(TipoActividadAcademica.PARCIAL);
        actividad.setFechaEntrega(LocalDateTime.of(2026, 9, 1, 8, 0).plusDays(actividad.getIdActividad()));
        actividad.setMateriaInscrita(materia);
        if (nota != null) {
            Nota registrada = new Nota();
            registrada.setValorObtenido(nota);
            actividad.setNota(registrada);
        }
        return actividad;
    }

    private ActividadAcademica calificada(double peso, double nota) {
        return actividad(peso, nota, "COMPLETADA");
    }

    private ActividadAcademica pendiente(double peso) {
        return actividad(peso, null, "ACTIVO");
    }

    private RendimientoMateriaResponse calcular(BigDecimal notaMinima, ActividadAcademica... actividades) {
        return servicio.calcular(materia, new ArrayList<>(List.of(actividades)), notaMinima);
    }

    private static void assertDecimal(BigDecimal actual, String esperado) {
        assertThat(actual).isNotNull();
        assertThat(actual.compareTo(new BigDecimal(esperado))).as("%s debería ser %s", actual, esperado).isZero();
    }

    // ================= Promedio ponderado con parte del plan calificado

    @Test
    void promedioPonderadoDelCasoHU025CP001() {
        RendimientoMateriaResponse r = calcular(null, calificada(30, 10), calificada(10, 5));

        assertDecimal(r.promedioPonderado(), "8.75");
        assertDecimal(r.notaAcumulada(), "3.50");
        assertDecimal(r.pesoCalificado(), "40");
        assertDecimal(r.pesoPendiente(), "0");
        assertDecimal(r.pesoSinAsignar(), "60");
        assertThat(r.planIncompleto()).isTrue();
    }

    @Test
    void elDetalleIncluyeElAporteDeCadaActividadALaNotaFinal() {
        RendimientoMateriaResponse r = calcular(null, calificada(30, 10), calificada(10, 5), pendiente(20));

        List<ActividadRendimientoResponse> detalle = r.actividades();
        assertThat(detalle).hasSize(3);
        assertDecimal(detalle.get(0).aporte(), "3.00");
        assertDecimal(detalle.get(1).aporte(), "0.50");
        assertThat(detalle.get(2).calificada()).isFalse();
        assertThat(detalle.get(2).aporte()).isNull();
        assertDecimal(detalle.get(2).porcentajeEvaluacion(), "20");
    }

    // ================= Casos sin datos

    @Test
    void sinActividadesNoHayPromedioNiSituacionDeAprobacion() {
        RendimientoMateriaResponse r = calcular(SEIS);

        assertThat(r.situacion()).isEqualTo(SituacionRendimiento.SIN_ACTIVIDADES);
        assertThat(r.promedioPonderado()).isNull();
        assertThat(r.notaNecesaria()).isNull();
        assertThat(r.notaMaximaAlcanzable()).isNull();
        assertDecimal(r.pesoSinAsignar(), "100");
    }

    @Test
    void sinCalificacionesNoSeDivideEntreCeroPeroSeCalculaLaNotaNecesaria() {
        RendimientoMateriaResponse r = calcular(SEIS, pendiente(60), pendiente(40));

        assertThat(r.situacion()).isEqualTo(SituacionRendimiento.SIN_CALIFICACIONES);
        assertThat(r.promedioPonderado()).isNull();
        assertDecimal(r.notaAcumulada(), "0");
        // Todo el peso pendiente: hace falta exactamente la nota mínima en cada actividad
        assertDecimal(r.notaNecesaria(), "6.00");
    }

    @Test
    void sinCalificacionesNiNotaMinimaSoloSeInformaLaFaltaDeCalificaciones() {
        RendimientoMateriaResponse r = calcular(null, pendiente(50));

        assertThat(r.situacion()).isEqualTo(SituacionRendimiento.SIN_CALIFICACIONES);
        assertThat(r.notaNecesaria()).isNull();
    }

    // ================= Situaciones de aprobación

    @Test
    void unaMateriaQueYaAlcanzaLaNotaMinimaEstaAprobadaAunqueQuedenActividades() {
        RendimientoMateriaResponse r = calcular(SEIS, calificada(40, 10), calificada(30, 8), pendiente(30));

        assertDecimal(r.notaAcumulada(), "6.40");
        assertThat(r.situacion()).isEqualTo(SituacionRendimiento.APROBADA);
        assertThat(r.notaNecesaria()).isNull();
    }

    @Test
    void unaMateriaRecuperableIndicaLaNotaNecesariaEnLoPendiente() {
        RendimientoMateriaResponse r = calcular(SEIS, calificada(50, 4), pendiente(50));

        // (600 − 200) / 50 = 8
        assertThat(r.situacion()).isEqualTo(SituacionRendimiento.RECUPERABLE);
        assertDecimal(r.notaNecesaria(), "8.00");
        assertThat(r.planIncompleto()).isFalse();
    }

    @Test
    void necesitarExactamenteDiezSigueSiendoRecuperable() {
        RendimientoMateriaResponse r = calcular(SEIS, calificada(50, 2), pendiente(50));

        assertThat(r.situacion()).isEqualTo(SituacionRendimiento.RECUPERABLE);
        assertDecimal(r.notaNecesaria(), "10.00");
    }

    @Test
    void unaNotaNecesariaMayorQueDiezConElPlanCompletoEsImposible() {
        RendimientoMateriaResponse r = calcular(SEIS, calificada(80, 3), pendiente(20));

        // (600 − 240) / 20 = 18 y el máximo alcanzable es 4.40
        assertDecimal(r.notaNecesaria(), "18.00");
        assertThat(r.situacion()).isEqualTo(SituacionRendimiento.IMPOSIBLE_DE_APROBAR);
        assertDecimal(r.notaMaximaAlcanzable(), "4.40");
    }

    @Test
    void sinPendientesNiPesoSinAsignarYPorDebajoDeLaMinimaEstaReprobada() {
        RendimientoMateriaResponse r = calcular(SEIS, calificada(60, 5), calificada(40, 6));

        assertThat(r.situacion()).isEqualTo(SituacionRendimiento.REPROBADA);
        assertThat(r.notaNecesaria()).isNull();
        assertDecimal(r.notaAcumulada(), "5.40");
    }

    @Test
    void sinNotaMinimaSeMuestranPromedioYPesosPeroNoSeClasificaNiSeEstima() {
        RendimientoMateriaResponse r = calcular(null, calificada(50, 4), pendiente(50));

        assertThat(r.situacion()).isEqualTo(SituacionRendimiento.NOTA_MINIMA_NO_CONFIGURADA);
        assertThat(r.notaMinima()).isNull();
        assertThat(r.notaNecesaria()).isNull();
        assertDecimal(r.promedioPonderado(), "4.00");
        assertDecimal(r.pesoCalificado(), "50");
        assertDecimal(r.pesoPendiente(), "50");
    }

    // ================= Plan de evaluación incompleto

    @Test
    void conPesosIncompletosLaNotaNecesariaSeCalculaSobreLoPendienteRegistrado() {
        RendimientoMateriaResponse r = calcular(SEIS, calificada(30, 6), pendiente(40));

        // (600 − 180) / 40 = 10.5; el peso sin asignar (30 %) todavía podría permitir aprobar
        assertThat(r.planIncompleto()).isTrue();
        assertDecimal(r.pesoSinAsignar(), "30");
        assertDecimal(r.notaNecesaria(), "10.50");
        assertThat(r.situacion()).isEqualTo(SituacionRendimiento.DEPENDE_DE_EVALUACIONES_SIN_REGISTRAR);
    }

    @Test
    void elPesoSinAsignarEvitaDeclararImposibleUnaMateriaSinPendientesRegistrados() {
        RendimientoMateriaResponse r = calcular(SEIS, calificada(50, 4));

        // Sin el 50 % sin asignar sería reprobada; con él aún puede llegar a 7.00
        assertThat(r.notaNecesaria()).isNull();
        assertDecimal(r.notaMaximaAlcanzable(), "7.00");
        assertThat(r.situacion()).isEqualTo(SituacionRendimiento.DEPENDE_DE_EVALUACIONES_SIN_REGISTRAR);
    }

    @Test
    void conPlanIncompletoSigueSiendoImposibleSiNiConDiezEnTodoAlcanza() {
        RendimientoMateriaResponse r = calcular(SEIS, calificada(70, 2), pendiente(10));

        // 140 + 10 × (10 + 20) = 440 < 600
        assertThat(r.planIncompleto()).isTrue();
        assertThat(r.situacion()).isEqualTo(SituacionRendimiento.IMPOSIBLE_DE_APROBAR);
    }

    @Test
    void conPlanIncompletoYNotaNecesariaPosibleEsRecuperable() {
        RendimientoMateriaResponse r = calcular(SEIS, calificada(30, 6), pendiente(50));

        // (600 − 180) / 50 = 8.4
        assertThat(r.situacion()).isEqualTo(SituacionRendimiento.RECUPERABLE);
        assertThat(r.planIncompleto()).isTrue();
        assertDecimal(r.notaNecesaria(), "8.40");
    }

    // ================= Clasificación de actividades

    @Test
    void unaActividadConPesoCeroQuedaFueraDeLosCalculos() {
        RendimientoMateriaResponse r = calcular(SEIS, calificada(0, 2), calificada(50, 8), pendiente(50));

        assertDecimal(r.promedioPonderado(), "8.00");
        assertDecimal(r.pesoCalificado(), "50");
        ActividadRendimientoResponse sinPeso = r.actividades().get(0);
        assertThat(sinPeso.sinPonderacion()).isTrue();
        assertThat(sinPeso.aporte()).isNull();
    }

    @Test
    void soloActividadesSinPonderacionCuentaComoSinActividades() {
        RendimientoMateriaResponse r = calcular(SEIS, calificada(0, 9));

        assertThat(r.situacion()).isEqualTo(SituacionRendimiento.SIN_ACTIVIDADES);
        assertThat(r.actividades()).hasSize(1);
    }

    @Test
    void unaNotaCeroEsUnaCalificacion() {
        RendimientoMateriaResponse r = calcular(SEIS, calificada(40, 0), pendiente(60));

        assertThat(r.actividades().get(0).calificada()).isTrue();
        assertDecimal(r.pesoCalificado(), "40");
        assertDecimal(r.promedioPonderado(), "0.00");
        assertThat(r.situacion()).isEqualTo(SituacionRendimiento.RECUPERABLE);
        assertDecimal(r.notaNecesaria(), "10.00");
    }

    @Test
    void unaActividadCompletadaSinNotaSiguePendiente() {
        RendimientoMateriaResponse r = calcular(SEIS, actividad(40, null, "COMPLETADA"), calificada(60, 7));

        assertThat(r.actividades().get(0).calificada()).isFalse();
        assertDecimal(r.pesoPendiente(), "40");
        assertDecimal(r.pesoCalificado(), "60");
    }

    @Test
    void unaActividadActivaConNotaEstaCalificada() {
        RendimientoMateriaResponse r = calcular(SEIS, actividad(40, 9.0, "ACTIVO"), pendiente(60));

        assertThat(r.actividades().get(0).calificada()).isTrue();
        assertDecimal(r.pesoCalificado(), "40");
        assertDecimal(r.promedioPonderado(), "9.00");
    }

    // ================= Precisión y redondeo

    @Test
    void laNotaNecesariaSeRedondeaSiempreHaciaArriba() {
        RendimientoMateriaResponse r = calcular(SEIS, calificada(40, 5), pendiente(30), pendiente(30));

        // (600 − 200) / 60 = 6.666…; redondeada hacia arriba = 6.67
        assertDecimal(r.notaNecesaria(), "6.67");
        r = calcular(new BigDecimal("6.01"), calificada(70, 6), pendiente(30));
        // (601 − 420) / 30 = 6.0333…; hacia arriba = 6.04, nunca 6.03
        assertDecimal(r.notaNecesaria(), "6.04");
    }

    @Test
    void laComparacionUsaLosPuntosExactosYNoLosRedondeados() {
        // 5.999 de nota acumulada no alcanza un 6.00 aunque redondeado a dos decimales lo pareciera
        RendimientoMateriaResponse r = calcular(SEIS, calificada(99.99, 6), pendiente(0.01));

        assertThat(r.situacion()).isNotEqualTo(SituacionRendimiento.APROBADA);
        assertDecimal(r.notaAcumulada(), "5.99");
    }

    @Test
    void sumasDecimalesDePesosNoProducenErroresDePuntoFlotante() {
        RendimientoMateriaResponse r = calcular(SEIS, calificada(33.3, 7), calificada(33.3, 7), calificada(33.4, 7));

        assertDecimal(r.pesoCalificado(), "100");
        assertDecimal(r.pesoSinAsignar(), "0");
        assertThat(r.planIncompleto()).isFalse();
        assertDecimal(r.promedioPonderado(), "7.00");
        assertThat(r.situacion()).isEqualTo(SituacionRendimiento.APROBADA);
    }

    @Test
    void unaNotaMinimaDeCeroSeConsideraAlcanzada() {
        RendimientoMateriaResponse r = calcular(BigDecimal.ZERO, calificada(50, 0), pendiente(50));

        assertThat(r.situacion()).isEqualTo(SituacionRendimiento.APROBADA);
        assertThat(r.notaNecesaria()).isNull();
    }
}
