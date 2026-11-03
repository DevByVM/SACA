package com.saca.api.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saca.api.dto.response.CargaSemanalResponse;
import com.saca.api.dto.response.IndicadorExigenciaResponse;
import com.saca.api.dto.response.IndicadorHorasResponse;
import com.saca.api.entity.ActividadAcademica;
import com.saca.api.entity.NivelCarga;
import com.saca.api.mapper.ActividadAcademicaMapper;
import com.saca.api.repository.ActividadAcademicaRepository;

// SDACAS-20. Dos indicadores independientes sobre las actividades pendientes de los próximos 7 días:
// la carga horaria (tiempo estimado) y la exigencia evaluativa (porcentajes). Los porcentajes nunca se
// convierten en horas. Los umbrales son valores iniciales del proyecto, no normas oficiales de la UES.
@Service
public class CargaAcademicaService {

	public static final BigDecimal HORAS_ELEVADA = BigDecimal.valueOf(20);
	public static final BigDecimal HORAS_CRITICA = BigDecimal.valueOf(35);
	// Mismo umbral que la prioridad «Alta» de las actividades
	public static final BigDecimal PESO_ALTO = BigDecimal.valueOf(30);
	public static final BigDecimal PESO_ACUMULADO_ELEVADA = BigDecimal.valueOf(30);
	public static final BigDecimal PESO_ACUMULADO_CRITICA = BigDecimal.valueOf(60);
	public static final int EVALUACIONES_ALTO_PESO_CONCENTRACION = 2;
	public static final int DIAS_VENTANA = 7;

	private static final DateTimeFormatter FORMATO_DIA =
			DateTimeFormatter.ofPattern("EEE dd/MM", Locale.forLanguageTag("es-SV"));

	private final ActividadAcademicaRepository actividadAcademicaRepository;
	private final ActividadAcademicaMapper actividadAcademicaMapper;
	private final Clock relojAplicacion;

	public CargaAcademicaService(
			ActividadAcademicaRepository actividadAcademicaRepository,
			ActividadAcademicaMapper actividadAcademicaMapper,
			Clock relojAplicacion) {
		this.actividadAcademicaRepository = actividadAcademicaRepository;
		this.actividadAcademicaMapper = actividadAcademicaMapper;
		this.relojAplicacion = relojAplicacion;
	}

	@Transactional(readOnly = true)
	public CargaSemanalResponse obtenerCargaSemanal(Long estudianteId) {
		LocalDateTime desde = LocalDateTime.now(relojAplicacion);
		return calcular(
				actividadAcademicaRepository.findActividadesCicloActivoPorEstudiante(estudianteId),
				desde,
				desde.plusDays(DIAS_VENTANA));
	}

	// Cálculo puro: recibe las actividades de los ciclos activos y la ventana [desde, hasta]
	public CargaSemanalResponse calcular(List<ActividadAcademica> actividades, LocalDateTime desde, LocalDateTime hasta) {
		List<ActividadAcademica> pendientes = new ArrayList<>();
		List<ActividadAcademica> noPendientes = new ArrayList<>();
		int sinFecha = 0;

		for (ActividadAcademica actividad : actividades) {
			LocalDateTime referencia = fechaReferencia(actividad);
			if (referencia == null) {
				// Sin fecha no se puede ubicar en la semana; solo se avisa si todavía está pendiente
				if (esPendiente(actividad)) {
					sinFecha++;
				}
				continue;
			}
			if (referencia.isBefore(desde) || referencia.isAfter(hasta)) {
				continue;
			}
			(esPendiente(actividad) ? pendientes : noPendientes).add(actividad);
		}

		Comparator<ActividadAcademica> orden = Comparator
				.comparing(CargaAcademicaService::fechaReferencia)
				.thenComparing(ActividadAcademica::getPorcentajeEvaluacion, Comparator.reverseOrder())
				.thenComparing(ActividadAcademica::getTiempoEstimadoHoras, Comparator.reverseOrder());
		pendientes.sort(orden);
		noPendientes.sort(orden);

		IndicadorHorasResponse horas = calcularHoras(pendientes);
		List<ActividadAcademica> evaluaciones = pendientes.stream()
				.filter(actividad -> actividad.getPorcentajeEvaluacion() > 0)
				.toList();
		IndicadorExigenciaResponse exigencia = calcularExigencia(evaluaciones);
		int sinPorcentaje = pendientes.size() - evaluaciones.size();

		NivelCarga nivelGeneral = mayor(horas.nivel(), exigencia.nivel());
		boolean ambosRequierenAtencion = horas.nivel() != NivelCarga.NORMAL && exigencia.nivel() != NivelCarga.NORMAL;
		if (ambosRequierenAtencion || exigencia.concentracion()) {
			nivelGeneral = NivelCarga.CRITICA;
		}

		return new CargaSemanalResponse(
				desde,
				hasta,
				nivelGeneral,
				horas,
				exigencia,
				explicar(horas, exigencia, evaluaciones, ambosRequierenAtencion),
				pendientes.stream().map(actividadAcademicaMapper::toResponse).toList(),
				noPendientes.stream().map(actividadAcademicaMapper::toResponse).toList(),
				sinFecha,
				sinPorcentaje);
	}

	// Pendiente = ni completada ni calificada: una actividad con nota ya no exige trabajo aunque siga activa
	private static boolean esPendiente(ActividadAcademica actividad) {
		return !"COMPLETADA".equals(actividad.getEstado()) && actividad.getNota() == null;
	}

	// Igual que el cálculo anterior del frontend: la entrega y, si falta, la fecha de inicio
	private static LocalDateTime fechaReferencia(ActividadAcademica actividad) {
		return actividad.getFechaEntrega() != null ? actividad.getFechaEntrega() : actividad.getFechaInicio();
	}

	private static IndicadorHorasResponse calcularHoras(List<ActividadAcademica> pendientes) {
		BigDecimal total = pendientes.stream()
				.map(actividad -> BigDecimal.valueOf(actividad.getTiempoEstimadoHoras()))
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		NivelCarga nivel = total.compareTo(HORAS_CRITICA) > 0
				? NivelCarga.CRITICA
				: total.compareTo(HORAS_ELEVADA) > 0 ? NivelCarga.ELEVADA : NivelCarga.NORMAL;
		return new IndicadorHorasResponse(total, nivel, HORAS_ELEVADA, HORAS_CRITICA);
	}

	private static IndicadorExigenciaResponse calcularExigencia(List<ActividadAcademica> evaluaciones) {
		BigDecimal acumulado = BigDecimal.ZERO;
		BigDecimal maximo = BigDecimal.ZERO;
		int altoPeso = 0;
		Map<LocalDate, Integer> altoPesoPorDia = new TreeMap<>();

		for (ActividadAcademica evaluacion : evaluaciones) {
			BigDecimal peso = BigDecimal.valueOf(evaluacion.getPorcentajeEvaluacion());
			acumulado = acumulado.add(peso);
			maximo = maximo.max(peso);
			if (peso.compareTo(PESO_ALTO) >= 0) {
				altoPeso++;
				altoPesoPorDia.merge(fechaReferencia(evaluacion).toLocalDate(), 1, Integer::sum);
			}
		}

		List<LocalDate> diasConcentracion = altoPesoPorDia.entrySet().stream()
				.filter(dia -> dia.getValue() >= EVALUACIONES_ALTO_PESO_CONCENTRACION)
				.map(Map.Entry::getKey)
				.toList();
		boolean concentracion = !diasConcentracion.isEmpty();

		NivelCarga nivel;
		if (acumulado.compareTo(PESO_ACUMULADO_CRITICA) >= 0 || concentracion) {
			nivel = NivelCarga.CRITICA;
		} else if (acumulado.compareTo(PESO_ACUMULADO_ELEVADA) >= 0 || altoPeso > 0) {
			nivel = NivelCarga.ELEVADA;
		} else {
			nivel = NivelCarga.NORMAL;
		}

		return new IndicadorExigenciaResponse(acumulado, maximo, altoPeso, concentracion, diasConcentracion, nivel,
				PESO_ALTO, PESO_ACUMULADO_ELEVADA, PESO_ACUMULADO_CRITICA);
	}

	private static NivelCarga mayor(NivelCarga a, NivelCarga b) {
		return a.compareTo(b) >= 0 ? a : b;
	}

	private static List<String> explicar(
			IndicadorHorasResponse horas,
			IndicadorExigenciaResponse exigencia,
			List<ActividadAcademica> evaluaciones,
			boolean ambosRequierenAtencion) {
		List<String> motivos = new ArrayList<>();
		String horasTexto = numero(horas.horasPendientes()) + " h pendientes en los próximos 7 días";

		motivos.add(switch (horas.nivel()) {
			case CRITICA -> "Carga horaria crítica: " + horasTexto + " (más de " + numero(HORAS_CRITICA) + " h).";
			case ELEVADA -> "Carga horaria elevada: " + horasTexto + " (más de " + numero(HORAS_ELEVADA) + " h).";
			case NORMAL -> "Carga horaria normal: " + horasTexto + " (hasta " + numero(HORAS_ELEVADA) + " h).";
		});

		String pesoTexto = numero(exigencia.pesoAcumulado()) + " % de peso acumulado";
		if (evaluaciones.isEmpty()) {
			motivos.add("Exigencia evaluativa normal: no hay evaluaciones con porcentaje en los próximos 7 días.");
		} else if (exigencia.nivel() == NivelCarga.NORMAL) {
			motivos.add("Exigencia evaluativa normal: " + pesoTexto + " y ninguna evaluación de "
					+ numero(PESO_ALTO) + " % o más.");
		} else {
			List<String> razones = new ArrayList<>();
			if (exigencia.pesoAcumulado().compareTo(PESO_ACUMULADO_CRITICA) >= 0) {
				razones.add(pesoTexto + " (" + numero(PESO_ACUMULADO_CRITICA) + " % o más)");
			} else if (exigencia.pesoAcumulado().compareTo(PESO_ACUMULADO_ELEVADA) >= 0) {
				razones.add(pesoTexto + " (" + numero(PESO_ACUMULADO_ELEVADA) + " % o más)");
			}
			for (LocalDate dia : exigencia.diasConcentracion()) {
				long cantidad = evaluaciones.stream()
						.filter(evaluacion -> esAltoPeso(evaluacion) && fechaReferencia(evaluacion).toLocalDate().equals(dia))
						.count();
				razones.add("concentración de " + cantidad + " evaluaciones de " + numero(PESO_ALTO)
						+ " % o más el " + FORMATO_DIA.format(dia));
			}
			List<String> altoPeso = evaluaciones.stream()
					.filter(CargaAcademicaService::esAltoPeso)
					.map(evaluacion -> evaluacion.getNombre() + " (" + evaluacion.getMateriaInscrita().getCodigo() + ", "
							+ numero(BigDecimal.valueOf(evaluacion.getPorcentajeEvaluacion())) + " %, "
							+ FORMATO_DIA.format(fechaReferencia(evaluacion)) + ")")
					.toList();
			if (!altoPeso.isEmpty()) {
				razones.add((altoPeso.size() == 1 ? "evaluación de alto peso: " : "evaluaciones de alto peso: ")
						+ String.join("; ", altoPeso));
			}
			String nivel = exigencia.nivel() == NivelCarga.CRITICA ? "crítica" : "elevada";
			motivos.add("Exigencia evaluativa " + nivel + ": " + String.join("; ", razones) + ".");
		}

		if (ambosRequierenAtencion) {
			motivos.add("Ambos indicadores requieren atención, por eso la carga general es crítica.");
		}
		return motivos;
	}

	private static boolean esAltoPeso(ActividadAcademica actividad) {
		return BigDecimal.valueOf(actividad.getPorcentajeEvaluacion()).compareTo(PESO_ALTO) >= 0;
	}

	private static String numero(BigDecimal valor) {
		return valor.stripTrailingZeros().toPlainString();
	}
}
