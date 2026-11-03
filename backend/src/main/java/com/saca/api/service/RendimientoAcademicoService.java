package com.saca.api.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saca.api.dto.response.ActividadRendimientoResponse;
import com.saca.api.dto.response.RendimientoCicloResponse;
import com.saca.api.dto.response.RendimientoMateriaResponse;
import com.saca.api.entity.ActividadAcademica;
import com.saca.api.entity.CicloAcademico;
import com.saca.api.entity.EstadoCiclo;
import com.saca.api.entity.MateriaInscrita;
import com.saca.api.entity.PerfilAcademico;
import com.saca.api.entity.SituacionRendimiento;
import com.saca.api.exception.RecursoNoEncontradoException;
import com.saca.api.repository.ActividadAcademicaRepository;
import com.saca.api.repository.CicloAcademicoRepository;
import com.saca.api.repository.MateriaInscritaRepository;
import com.saca.api.repository.PerfilAcademicoRepository;

// SDACAS-25. Una actividad está calificada si tiene nota registrada, sin importar su estado.
// Las actividades con porcentaje 0 (registros antiguos) se muestran como «sin ponderación» y no cuentan.
@Service
public class RendimientoAcademicoService {

	private static final BigDecimal CIEN = BigDecimal.valueOf(100);
	private static final BigDecimal NOTA_MAXIMA = BigDecimal.TEN;
	private static final int DECIMALES = 2;

	private final CicloAcademicoRepository cicloAcademicoRepository;
	private final MateriaInscritaRepository materiaInscritaRepository;
	private final ActividadAcademicaRepository actividadAcademicaRepository;
	private final PerfilAcademicoRepository perfilAcademicoRepository;

	public RendimientoAcademicoService(
			CicloAcademicoRepository cicloAcademicoRepository,
			MateriaInscritaRepository materiaInscritaRepository,
			ActividadAcademicaRepository actividadAcademicaRepository,
			PerfilAcademicoRepository perfilAcademicoRepository) {
		this.cicloAcademicoRepository = cicloAcademicoRepository;
		this.materiaInscritaRepository = materiaInscritaRepository;
		this.actividadAcademicaRepository = actividadAcademicaRepository;
		this.perfilAcademicoRepository = perfilAcademicoRepository;
	}

	@Transactional(readOnly = true)
	public RendimientoCicloResponse obtenerPorCiclo(Long cicloAcademicoId, Long estudianteId) {
		BigDecimal notaMinima = perfilAcademicoRepository.findByEstudianteId(estudianteId)
				.map(PerfilAcademico::getNotaMinimaAprobacion)
				.orElse(null);

		CicloAcademico ciclo = cicloAcademicoId != null
				? cicloAcademicoRepository.findByIdAndEstudianteId(cicloAcademicoId, estudianteId)
						.orElseThrow(() -> new RecursoNoEncontradoException("No se encontro el ciclo academico solicitado"))
				: cicloAcademicoRepository
						.findByEstudianteIdAndEstadoOrderByAnioDescFechaInicioDesc(estudianteId, EstadoCiclo.ACTIVO)
						.stream()
						.findFirst()
						.orElse(null);

		if (ciclo == null) {
			return new RendimientoCicloResponse(null, null, notaMinima, List.of());
		}

		Map<Long, List<ActividadAcademica>> actividadesPorMateria = actividadAcademicaRepository
				.findActividadesPorCicloYEstudiante(estudianteId, ciclo.getId())
				.stream()
				.collect(Collectors.groupingBy(actividad -> actividad.getMateriaInscrita().getId()));

		List<RendimientoMateriaResponse> materias = materiaInscritaRepository
				.findByCicloAcademicoIdOrderByNombreAsc(ciclo.getId())
				.stream()
				.map(materia -> calcular(materia, actividadesPorMateria.getOrDefault(materia.getId(), List.of()), notaMinima))
				.toList();

		return new RendimientoCicloResponse(ciclo.getId(), ciclo.getNombre(), notaMinima, materias);
	}

	// Cálculo puro (sin repositorios): recibe la materia, sus actividades y la nota mínima del estudiante
	public RendimientoMateriaResponse calcular(
			MateriaInscrita materia,
			List<ActividadAcademica> actividades,
			BigDecimal notaMinima) {
		BigDecimal pesoCalificado = BigDecimal.ZERO;
		BigDecimal pesoPendiente = BigDecimal.ZERO;
		BigDecimal puntos = BigDecimal.ZERO;
		List<ActividadRendimientoResponse> detalle = new ArrayList<>();

		List<ActividadAcademica> ordenadas = actividades.stream()
				.sorted(Comparator
						.comparing(ActividadAcademica::getFechaEntrega, Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder()))
						.thenComparing(ActividadAcademica::getIdActividad))
				.toList();

		for (ActividadAcademica actividad : ordenadas) {
			BigDecimal peso = BigDecimal.valueOf(actividad.getPorcentajeEvaluacion());
			BigDecimal nota = actividad.getNota() == null ? null : BigDecimal.valueOf(actividad.getNota().getValorObtenido());
			boolean calificada = nota != null;
			boolean sinPonderacion = peso.signum() <= 0;
			BigDecimal aporte = null;

			if (!sinPonderacion && calificada) {
				BigDecimal puntosActividad = nota.multiply(peso);
				pesoCalificado = pesoCalificado.add(peso);
				puntos = puntos.add(puntosActividad);
				aporte = puntosActividad.divide(CIEN, DECIMALES, RoundingMode.HALF_UP);
			} else if (!sinPonderacion) {
				pesoPendiente = pesoPendiente.add(peso);
			}

			detalle.add(new ActividadRendimientoResponse(
					actividad.getIdActividad(),
					actividad.getNombre(),
					actividad.getTipoActividad(),
					actividad.getEstado(),
					actividad.getFechaEntrega(),
					peso,
					nota,
					calificada,
					sinPonderacion,
					aporte));
		}

		BigDecimal pesoSinAsignar = CIEN.subtract(pesoCalificado).subtract(pesoPendiente).max(BigDecimal.ZERO);
		boolean planIncompleto = pesoSinAsignar.signum() > 0;
		boolean sinActividades = pesoCalificado.signum() == 0 && pesoPendiente.signum() == 0;
		boolean sinCalificaciones = pesoCalificado.signum() == 0;

		BigDecimal promedio = sinCalificaciones ? null : puntos.divide(pesoCalificado, DECIMALES, RoundingMode.HALF_UP);
		// Se trunca para no mostrar una nota acumulada o máxima que parezca alcanzar la mínima sin hacerlo
		BigDecimal notaAcumulada = puntos.divide(CIEN, DECIMALES, RoundingMode.DOWN);
		BigDecimal puntosMaximos = puntos.add(NOTA_MAXIMA.multiply(pesoPendiente.add(pesoSinAsignar)));
		BigDecimal notaMaximaAlcanzable = sinActividades ? null : puntosMaximos.divide(CIEN, DECIMALES, RoundingMode.DOWN);

		BigDecimal notaNecesaria = null;
		SituacionRendimiento situacion;

		if (sinActividades) {
			situacion = SituacionRendimiento.SIN_ACTIVIDADES;
		} else {
			// Las comparaciones usan los puntos exactos; solo los valores mostrados se redondean
			BigDecimal puntosMinimos = notaMinima == null ? null : notaMinima.multiply(CIEN);
			boolean aprobada = puntosMinimos != null && puntos.compareTo(puntosMinimos) >= 0;

			if (puntosMinimos != null && !aprobada && pesoPendiente.signum() > 0) {
				// Hacia arriba: nunca se indica una nota menor que la realmente necesaria
				notaNecesaria = puntosMinimos.subtract(puntos).divide(pesoPendiente, DECIMALES, RoundingMode.CEILING);
			}

			if (sinCalificaciones) {
				situacion = SituacionRendimiento.SIN_CALIFICACIONES;
			} else if (puntosMinimos == null) {
				situacion = SituacionRendimiento.NOTA_MINIMA_NO_CONFIGURADA;
			} else if (aprobada) {
				situacion = SituacionRendimiento.APROBADA;
			} else if (pesoPendiente.signum() == 0 && !planIncompleto) {
				situacion = SituacionRendimiento.REPROBADA;
			} else if (puntosMaximos.compareTo(puntosMinimos) < 0) {
				situacion = SituacionRendimiento.IMPOSIBLE_DE_APROBAR;
			} else if (pesoPendiente.signum() > 0
					&& puntosMinimos.subtract(puntos).compareTo(NOTA_MAXIMA.multiply(pesoPendiente)) <= 0) {
				situacion = SituacionRendimiento.RECUPERABLE;
			} else {
				situacion = SituacionRendimiento.DEPENDE_DE_EVALUACIONES_SIN_REGISTRAR;
			}
		}

		return new RendimientoMateriaResponse(
				materia.getId(),
				materia.getCodigo(),
				materia.getNombre(),
				pesoCalificado,
				pesoPendiente,
				pesoSinAsignar,
				promedio,
				notaAcumulada,
				notaMaximaAlcanzable,
				notaMinima,
				notaNecesaria,
				situacion,
				planIncompleto,
				detalle);
	}
}
