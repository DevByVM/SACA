package com.saca.api.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saca.api.dto.response.CargaPorSemanasResponse;
import com.saca.api.dto.response.SemanaCargaResponse;
import com.saca.api.entity.ActividadAcademica;
import com.saca.api.entity.CicloAcademico;
import com.saca.api.entity.EstadoCiclo;
import com.saca.api.repository.ActividadAcademicaRepository;
import com.saca.api.repository.CicloAcademicoRepository;

// SDACAS-26: carga semana a semana (lunes a domingo) desde la semana actual hasta el fin del ciclo activo.
// Cada semana se clasifica con el mismo cálculo híbrido de la carga semanal (SDACAS-20); aquí solo se
// definen las ventanas. La semana actual empieza en el momento de la consulta, así que lo vencido no cuenta.
@Service
public class CargaSemanasService {

	// Un ciclo no debería durar más de un año; el límite evita recorrer rangos absurdos por datos erróneos
	public static final int MAXIMO_SEMANAS = 53;

	private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("d/M");

	private final CargaAcademicaService cargaAcademicaService;
	private final CicloAcademicoRepository cicloAcademicoRepository;
	private final ActividadAcademicaRepository actividadAcademicaRepository;
	private final Clock relojAplicacion;

	public CargaSemanasService(
			CargaAcademicaService cargaAcademicaService,
			CicloAcademicoRepository cicloAcademicoRepository,
			ActividadAcademicaRepository actividadAcademicaRepository,
			Clock relojAplicacion) {
		this.cargaAcademicaService = cargaAcademicaService;
		this.cicloAcademicoRepository = cicloAcademicoRepository;
		this.actividadAcademicaRepository = actividadAcademicaRepository;
		this.relojAplicacion = relojAplicacion;
	}

	// Dos consultas en total: los ciclos activos y sus actividades con materia, ciclo y nota ya cargados
	@Transactional(readOnly = true)
	public CargaPorSemanasResponse obtenerCargaPorSemanas(Long estudianteId) {
		return calcular(
				LocalDateTime.now(relojAplicacion),
				cicloAcademicoRepository.findByEstudianteIdAndEstadoOrderByAnioDescFechaInicioDesc(estudianteId, EstadoCiclo.ACTIVO),
				actividadAcademicaRepository.findParaCalendario(estudianteId));
	}

	// Cálculo puro: recibe el momento actual, los ciclos y las actividades de los ciclos activos
	public CargaPorSemanasResponse calcular(
			LocalDateTime ahora,
			List<CicloAcademico> ciclos,
			List<ActividadAcademica> actividades) {
		List<CicloAcademico> activos = ciclos.stream()
				.filter(ciclo -> ciclo.getEstado() == EstadoCiclo.ACTIVO)
				.toList();
		if (activos.isEmpty()) {
			return new CargaPorSemanasResponse(false, null, null, List.of(), false, 0);
		}

		// Con varios ciclos activos el rango va del inicio más temprano al fin más tardío
		LocalDate inicioCiclo = activos.stream().map(CicloAcademico::getFechaInicio).min(Comparator.naturalOrder()).orElseThrow();
		LocalDate finCiclo = activos.stream().map(CicloAcademico::getFechaFin).max(Comparator.naturalOrder()).orElseThrow();
		LocalDate hoy = ahora.toLocalDate();
		LocalDate primerDia = hoy.isBefore(inicioCiclo) ? inicioCiclo : hoy;

		List<SemanaCargaResponse> semanas = new ArrayList<>();
		boolean limitadas = false;
		// Si el ciclo activo ya terminó no hay semanas por consultar: no se muestran semanas pasadas
		for (LocalDate lunes = primerDia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
				!primerDia.isAfter(finCiclo) && !lunes.isAfter(finCiclo);
				lunes = lunes.plusWeeks(1)) {
			if (semanas.size() == MAXIMO_SEMANAS) {
				limitadas = true;
				break;
			}
			LocalDate domingo = lunes.plusDays(6);
			boolean semanaActual = !hoy.isBefore(lunes) && !hoy.isAfter(domingo);
			LocalDateTime desde = semanaActual ? ahora : lunes.atStartOfDay();
			String descripcion = semanaActual
					? "desde ahora hasta el domingo " + DIA_MES.format(domingo)
					: "en la semana del " + DIA_MES.format(lunes) + " al " + DIA_MES.format(domingo);
			semanas.add(new SemanaCargaResponse(lunes, domingo, semanaActual,
					cargaAcademicaService.calcular(actividades, desde, domingo.atTime(LocalTime.MAX), descripcion)));
		}

		int sinFecha = semanas.isEmpty() ? 0 : semanas.get(0).carga().actividadesSinFecha();
		return new CargaPorSemanasResponse(true, inicioCiclo, finCiclo, semanas, limitadas, sinFecha);
	}
}
