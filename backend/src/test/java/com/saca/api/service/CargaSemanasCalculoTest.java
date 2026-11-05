package com.saca.api.service;

import com.saca.api.dto.response.CargaPorSemanasResponse;
import com.saca.api.dto.response.CargaSemanalResponse;
import com.saca.api.dto.response.SemanaCargaResponse;
import com.saca.api.entity.ActividadAcademica;
import com.saca.api.entity.CicloAcademico;
import com.saca.api.entity.EstadoCiclo;
import com.saca.api.entity.MateriaInscrita;
import com.saca.api.entity.NivelCarga;
import com.saca.api.entity.Nota;
import com.saca.api.entity.TipoActividadAcademica;
import com.saca.api.mapper.ActividadAcademicaMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Semanas de lunes a domingo desde la actual hasta el fin del ciclo activo, clasificadas con
// el cálculo de la carga semanal
class CargaSemanasCalculoTest {

    // Miércoles 4 de noviembre de 2026 a las 10:00
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 4, 10, 0);
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 2);

    private final CargaAcademicaService cargaAcademicaService = new CargaAcademicaService(null, new ActividadAcademicaMapper(), null);
    private final CargaSemanasService servicio = new CargaSemanasService(cargaAcademicaService, null, null, null);
    private long siguienteId = 1;

    private CicloAcademico ciclo(EstadoCiclo estado, LocalDate inicio, LocalDate fin) {
        CicloAcademico ciclo = new CicloAcademico();
        ciclo.setId(siguienteId++);
        ciclo.setEstado(estado);
        ciclo.setFechaInicio(inicio);
        ciclo.setFechaFin(fin);
        return ciclo;
    }

    // Termina el domingo 29 de noviembre: cuatro semanas desde la actual
    private final CicloAcademico cicloActivo = ciclo(EstadoCiclo.ACTIVO, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 11, 29));
    private final MateriaInscrita materia = materia(cicloActivo);

    private MateriaInscrita materia(CicloAcademico ciclo) {
        MateriaInscrita nueva = new MateriaInscrita();
        nueva.setId(siguienteId++);
        nueva.setCicloAcademico(ciclo);
        nueva.setCodigo("PRN315");
        nueva.setNombre("Programación III");
        return nueva;
    }

    private ActividadAcademica actividad(String nombre, double horas, double peso, LocalDateTime entrega) {
        return actividad(materia, nombre, horas, peso, entrega);
    }

    private ActividadAcademica actividad(MateriaInscrita materia, String nombre, double horas, double peso, LocalDateTime entrega) {
        ActividadAcademica actividad = new ActividadAcademica();
        actividad.setIdActividad(siguienteId++);
        actividad.setMateriaInscrita(materia);
        actividad.setNombre(nombre);
        actividad.setTipoActividad(TipoActividadAcademica.PARCIAL);
        actividad.setPorcentajeEvaluacion(peso);
        actividad.setTiempoEstimadoHoras(horas);
        actividad.setEstado("ACTIVO");
        actividad.setFechaEntrega(entrega);
        actividad.setFechaInicio(entrega.minusDays(3));
        return actividad;
    }

    private CargaPorSemanasResponse calcular(List<ActividadAcademica> actividades) {
        return servicio.calcular(AHORA, List.of(cicloActivo), actividades);
    }

    private static List<String> nombres(List<?> actividades) {
        return actividades.stream().map(a -> ((com.saca.api.dto.response.ActividadAcademicaResponse) a).nombre()).toList();
    }

    // ================= Semanas

    @Test
    void lasSemanasVanDeLunesADomingoDesdeLaActualHastaElFinDelCiclo() {
        CargaPorSemanasResponse respuesta = calcular(List.of());

        assertThat(respuesta.cicloActivo()).isTrue();
        assertThat(respuesta.semanas()).extracting(SemanaCargaResponse::inicio).containsExactly(
                LUNES, LUNES.plusWeeks(1), LUNES.plusWeeks(2), LUNES.plusWeeks(3));
        assertThat(respuesta.semanas()).extracting(SemanaCargaResponse::fin).containsExactly(
                LUNES.plusDays(6), LUNES.plusDays(13), LUNES.plusDays(20), LocalDate.of(2026, 11, 29));
        assertThat(respuesta.semanas()).extracting(SemanaCargaResponse::semanaActual).containsExactly(true, false, false, false);
        assertThat(respuesta.inicioCiclo()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(respuesta.finCiclo()).isEqualTo(LocalDate.of(2026, 11, 29));
        assertThat(respuesta.semanasLimitadas()).isFalse();
    }

    @Test
    void laSemanaActualSeCalculaDesdeAhoraHastaElDomingoSinActividadesVencidas() {
        CargaPorSemanasResponse respuesta = calcular(List.of(
                actividad("Vencida el martes", 8, 30, LocalDateTime.of(2026, 11, 3, 18, 0)),
                actividad("Vencida esta mañana", 8, 30, LocalDateTime.of(2026, 11, 4, 8, 0)),
                actividad("Esta tarde", 3, 10, LocalDateTime.of(2026, 11, 4, 15, 0)),
                actividad("Domingo noche", 2, 5, LocalDateTime.of(2026, 11, 8, 23, 59))));

        CargaSemanalResponse actual = respuesta.semanas().get(0).carga();
        assertThat(actual.desde()).isEqualTo(AHORA);
        assertThat(actual.hasta()).isEqualTo(LocalDate.of(2026, 11, 8).atTime(LocalTime.MAX));
        assertThat(nombres(actual.actividadesPendientes())).containsExactly("Esta tarde", "Domingo noche");
        assertThat(actual.horas().horasPendientes()).isEqualByComparingTo("5");
        assertThat(actual.motivos().get(0)).contains("desde ahora hasta el domingo 8/11");
    }

    @Test
    void lasSemanasFuturasEmpiezanElLunesALasCeroHoras() {
        CargaPorSemanasResponse respuesta = calcular(List.of(
                actividad("Lunes 00:00", 2, 5, LocalDate.of(2026, 11, 9).atStartOfDay()),
                actividad("Domingo anterior", 2, 5, LocalDateTime.of(2026, 11, 8, 23, 59))));

        CargaSemanalResponse segunda = respuesta.semanas().get(1).carga();
        assertThat(segunda.desde()).isEqualTo(LocalDate.of(2026, 11, 9).atStartOfDay());
        assertThat(nombres(segunda.actividadesPendientes())).containsExactly("Lunes 00:00");
        assertThat(segunda.motivos().get(0)).contains("en la semana del 9/11 al 15/11");
    }

    // ================= Clasificación baja / media / alta (Normal / Elevada / Crítica)

    @Test
    void cadaSemanaSeClasificaConElCalculoDeLaCargaSemanal() {
        CargaPorSemanasResponse respuesta = calcular(List.of(
                actividad("Tarea", 10, 5, LocalDateTime.of(2026, 11, 10, 9, 0)),
                actividad("Proyecto largo", 25, 5, LocalDateTime.of(2026, 11, 18, 9, 0)),
                actividad("Parcial A", 2, 30, LocalDateTime.of(2026, 11, 25, 9, 0)),
                actividad("Parcial B", 2, 30, LocalDateTime.of(2026, 11, 25, 15, 0))));

        assertThat(respuesta.semanas()).extracting(s -> s.carga().nivelGeneral()).containsExactly(
                NivelCarga.NORMAL, NivelCarga.NORMAL, NivelCarga.ELEVADA, NivelCarga.CRITICA);
        assertThat(respuesta.semanas().get(3).carga().exigencia().concentracion()).isTrue();
    }

    @Test
    void elResultadoDeUnaSemanaEsElMismoQueElCalculoDeSdacas20ConEsaVentana() {
        List<ActividadAcademica> actividades = List.of(
                actividad("Proyecto", 22, 35, LocalDateTime.of(2026, 11, 18, 9, 0)),
                actividad("Tarea", 3, 5, LocalDateTime.of(2026, 11, 20, 9, 0)));

        CargaSemanalResponse semana = calcular(actividades).semanas().get(2).carga();
        CargaSemanalResponse directa = cargaAcademicaService.calcular(actividades,
                LocalDate.of(2026, 11, 16).atStartOfDay(), LocalDate.of(2026, 11, 22).atTime(LocalTime.MAX));

        assertThat(semana.nivelGeneral()).isEqualTo(directa.nivelGeneral()).isEqualTo(NivelCarga.CRITICA);
        assertThat(semana.horas()).isEqualTo(directa.horas());
        assertThat(semana.exigencia()).isEqualTo(directa.exigencia());
        assertThat(semana.actividadesPendientes()).isEqualTo(directa.actividadesPendientes());
    }

    @Test
    void unaSemanaSinActividadesEsBaja() {
        CargaSemanalResponse vacia = calcular(List.of()).semanas().get(1).carga();

        assertThat(vacia.nivelGeneral()).isEqualTo(NivelCarga.NORMAL);
        assertThat(vacia.horas().horasPendientes()).isEqualByComparingTo("0");
        assertThat(vacia.actividadesPendientes()).isEmpty();
        assertThat(vacia.motivos()).containsExactly(
                "Carga horaria normal: 0 h pendientes en la semana del 9/11 al 15/11 (hasta 20 h).",
                "Exigencia evaluativa normal: no hay evaluaciones con porcentaje en la semana del 9/11 al 15/11.");
    }

    @Test
    void lasCompletadasYCalificadasSeIncluyenEnElDesglosePeroNoSuman() {
        ActividadAcademica completada = actividad("Completada", 10, 40, LocalDateTime.of(2026, 11, 11, 9, 0));
        completada.setEstado("COMPLETADA");
        ActividadAcademica calificada = actividad("Calificada", 10, 40, LocalDateTime.of(2026, 11, 12, 9, 0));
        Nota nota = new Nota();
        nota.setValorObtenido(8);
        calificada.setNota(nota);

        CargaSemanalResponse semana = calcular(List.of(completada, calificada,
                actividad("Pendiente", 3, 5, LocalDateTime.of(2026, 11, 13, 9, 0)))).semanas().get(1).carga();

        assertThat(nombres(semana.actividadesPendientes())).containsExactly("Pendiente");
        assertThat(nombres(semana.actividadesNoPendientes())).containsExactly("Completada", "Calificada");
        assertThat(semana.horas().horasPendientes()).isEqualByComparingTo("3");
        assertThat(semana.nivelGeneral()).isEqualTo(NivelCarga.NORMAL);
    }

    // ================= Ciclos

    @Test
    void conVariosCiclosActivosElRangoVaDelInicioMasTempranoAlFinMasTardio() {
        CicloAcademico corto = ciclo(EstadoCiclo.ACTIVO, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 15));
        CicloAcademico largo = ciclo(EstadoCiclo.ACTIVO, LocalDate.of(2026, 8, 15), LocalDate.of(2026, 12, 6));
        ActividadAcademica diciembre = actividad(materia(largo), "Final de diciembre", 4, 20, LocalDateTime.of(2026, 12, 2, 9, 0));

        CargaPorSemanasResponse respuesta = servicio.calcular(AHORA, List.of(corto, largo), List.of(diciembre));

        assertThat(respuesta.inicioCiclo()).isEqualTo(LocalDate.of(2026, 8, 15));
        assertThat(respuesta.finCiclo()).isEqualTo(LocalDate.of(2026, 12, 6));
        assertThat(respuesta.semanas()).hasSize(5);
        assertThat(nombres(respuesta.semanas().get(4).carga().actividadesPendientes())).containsExactly("Final de diciembre");
    }

    @Test
    void sinCicloActivoNoHaySemanasNiFechas() {
        CicloAcademico finalizado = ciclo(EstadoCiclo.FINALIZADO, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 15));

        CargaPorSemanasResponse sinCiclos = servicio.calcular(AHORA, List.of(), List.of());
        CargaPorSemanasResponse soloFinalizado = servicio.calcular(AHORA, List.of(finalizado), List.of());

        for (CargaPorSemanasResponse respuesta : List.of(sinCiclos, soloFinalizado)) {
            assertThat(respuesta.cicloActivo()).isFalse();
            assertThat(respuesta.semanas()).isEmpty();
            assertThat(respuesta.inicioCiclo()).isNull();
            assertThat(respuesta.finCiclo()).isNull();
        }
    }

    @Test
    void unCicloActivoQueYaTerminoNoGeneraSemanasPasadas() {
        CicloAcademico terminado = ciclo(EstadoCiclo.ACTIVO, LocalDate.of(2026, 2, 3), LocalDate.of(2026, 6, 20));

        CargaPorSemanasResponse respuesta = servicio.calcular(AHORA, List.of(terminado),
                List.of(actividad(materia(terminado), "De junio", 5, 20, LocalDateTime.of(2026, 6, 10, 9, 0))));

        assertThat(respuesta.cicloActivo()).isTrue();
        assertThat(respuesta.finCiclo()).isEqualTo(LocalDate.of(2026, 6, 20));
        assertThat(respuesta.semanas()).isEmpty();
    }

    @Test
    void unCicloQueAunNoEmpiezaComienzaEnLaSemanaDeSuInicio() {
        CicloAcademico futuro = ciclo(EstadoCiclo.ACTIVO, LocalDate.of(2026, 12, 2), LocalDate.of(2026, 12, 20));

        CargaPorSemanasResponse respuesta = servicio.calcular(AHORA, List.of(futuro), List.of());

        assertThat(respuesta.semanas()).extracting(SemanaCargaResponse::inicio).containsExactly(
                LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 7), LocalDate.of(2026, 12, 14));
        assertThat(respuesta.semanas()).noneMatch(SemanaCargaResponse::semanaActual);
    }

    @Test
    void unCicloDemasiadoLargoSeLimitaA53Semanas() {
        CicloAcademico largo = ciclo(EstadoCiclo.ACTIVO, LocalDate.of(2026, 1, 1), LocalDate.of(2029, 1, 1));

        CargaPorSemanasResponse respuesta = servicio.calcular(AHORA, List.of(largo), List.of());

        assertThat(respuesta.semanas()).hasSize(CargaSemanasService.MAXIMO_SEMANAS);
        assertThat(respuesta.semanasLimitadas()).isTrue();
    }

    @Test
    void elFinDeCicloEnMitadDeSemanaIncluyeEsaSemanaCompleta() {
        CicloAcademico termina = ciclo(EstadoCiclo.ACTIVO, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 11, 11));

        CargaPorSemanasResponse respuesta = servicio.calcular(AHORA, List.of(termina), List.of());

        assertThat(respuesta.semanas()).extracting(SemanaCargaResponse::fin).containsExactly(
                LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 15));
    }
}
