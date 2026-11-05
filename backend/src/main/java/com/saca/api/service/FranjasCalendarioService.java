package com.saca.api.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saca.api.dto.response.CalendarioFranjasResponse;
import com.saca.api.dto.response.FranjaCalendarioResponse;
import com.saca.api.dto.response.FranjaCalendarioResponse.Tipo;
import com.saca.api.entity.CicloAcademico;
import com.saca.api.entity.DisponibilidadSemanal;
import com.saca.api.entity.EstadoCiclo;
import com.saca.api.entity.JornadaLaboral;
import com.saca.api.repository.CicloAcademicoRepository;
import com.saca.api.repository.DisponibilidadSemanalRepository;
import com.saca.api.repository.JornadaLaboralRepository;

// Proyecta sobre las fechas del calendario las restricciones de tiempo del estudiante.
// - Jornada laboral: no pertenece a un ciclo, así que se repite cada semana en su día.
// - Disponibilidad semanal: solo los bloques NO_DISPONIBLE y ESTUDIO de ciclos ACTIVO y dentro de las
//   fechas de su ciclo, igual que las clases del calendario. CLASE y LIBRE conservan su significado y
//   no se representan como franjas.
@Service
public class FranjasCalendarioService {

	private static final Map<String, Tipo> TIPOS_DISPONIBILIDAD = Map.of(
			"NO_DISPONIBLE", Tipo.NO_DISPONIBLE,
			"ESTUDIO", Tipo.ESTUDIO);

	private static final Comparator<FranjaCalendarioResponse> CRONOLOGICO = Comparator
			.comparing(FranjaCalendarioResponse::fecha)
			.thenComparing(FranjaCalendarioResponse::horaInicio)
			.thenComparing(FranjaCalendarioResponse::tipo)
			.thenComparing(FranjaCalendarioResponse::origenId);

	private final JornadaLaboralRepository jornadaLaboralRepository;
	private final DisponibilidadSemanalRepository disponibilidadSemanalRepository;
	private final CicloAcademicoRepository cicloAcademicoRepository;

	public FranjasCalendarioService(
			JornadaLaboralRepository jornadaLaboralRepository,
			DisponibilidadSemanalRepository disponibilidadSemanalRepository,
			CicloAcademicoRepository cicloAcademicoRepository) {
		this.jornadaLaboralRepository = jornadaLaboralRepository;
		this.disponibilidadSemanalRepository = disponibilidadSemanalRepository;
		this.cicloAcademicoRepository = cicloAcademicoRepository;
	}

	// Tres consultas en total: jornadas, ciclos activos y bloques de disponibilidad del estudiante
	@Transactional(readOnly = true)
	public CalendarioFranjasResponse obtenerFranjas(Long estudianteId, LocalDate desde, LocalDate hasta) {
		CalendarioService.validarPeriodo(desde, hasta);
		return calcular(
				desde,
				hasta,
				jornadaLaboralRepository.findByEstudianteId(estudianteId),
				cicloAcademicoRepository.findByEstudianteIdAndEstadoOrderByAnioDescFechaInicioDesc(estudianteId, EstadoCiclo.ACTIVO),
				disponibilidadSemanalRepository.findByEstudianteId(estudianteId));
	}

	// Cálculo puro sobre [desde, hasta]; los registros con un día que no se reconoce se omiten
	public CalendarioFranjasResponse calcular(
			LocalDate desde,
			LocalDate hasta,
			List<JornadaLaboral> jornadas,
			List<CicloAcademico> ciclos,
			List<DisponibilidadSemanal> bloques) {
		Map<Long, CicloAcademico> ciclosActivos = ciclos.stream()
				.filter(ciclo -> ciclo.getEstado() == EstadoCiclo.ACTIVO)
				.collect(Collectors.toMap(CicloAcademico::getId, Function.identity()));

		List<FranjaCalendarioResponse> franjas = new ArrayList<>();
		for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
			DayOfWeek dia = fecha.getDayOfWeek();

			for (JornadaLaboral jornada : jornadas) {
				if (esDelDia(jornada.getDiaSemana(), dia)) {
					franjas.add(new FranjaCalendarioResponse(
							fecha, jornada.getHoraInicio(), jornada.getHoraFin(), Tipo.JORNADA_LABORAL, jornada.getId()));
				}
			}

			for (DisponibilidadSemanal bloque : bloques) {
				Tipo tipo = tipoDe(bloque.getTipoBloque());
				CicloAcademico ciclo = ciclosActivos.get(bloque.getCicloId());
				if (tipo != null && ciclo != null && dentroDelCiclo(fecha, ciclo) && esDelDia(bloque.getDiaSemana(), dia)) {
					franjas.add(new FranjaCalendarioResponse(
							fecha, bloque.getHoraInicio(), bloque.getHoraFin(), tipo, bloque.getId()));
				}
			}
		}

		franjas.sort(CRONOLOGICO);
		return new CalendarioFranjasResponse(desde, hasta, franjas);
	}

	private static boolean esDelDia(String texto, DayOfWeek dia) {
		Optional<DayOfWeek> reconocido = DiaSemanaTexto.aDia(texto);
		return reconocido.isPresent() && reconocido.get() == dia;
	}

	private static Tipo tipoDe(String tipoBloque) {
		return tipoBloque == null ? null : TIPOS_DISPONIBILIDAD.get(tipoBloque.trim().toUpperCase());
	}

	private static boolean dentroDelCiclo(LocalDate fecha, CicloAcademico ciclo) {
		return (ciclo.getFechaInicio() == null || !fecha.isBefore(ciclo.getFechaInicio()))
				&& (ciclo.getFechaFin() == null || !fecha.isAfter(ciclo.getFechaFin()));
	}
}
