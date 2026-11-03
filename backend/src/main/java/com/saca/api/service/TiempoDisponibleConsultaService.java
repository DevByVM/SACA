package com.saca.api.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saca.api.dto.response.TiempoDisponibleDiaResponse;
import com.saca.api.dto.response.TiempoDisponibleResponse;
import com.saca.api.entity.EstadoCiclo;
import com.saca.api.repository.CicloAcademicoRepository;
import com.saca.api.repository.DisponibilidadSemanalRepository;
import com.saca.api.repository.HorarioClaseRepository;
import com.saca.api.repository.JornadaLaboralRepository;
import com.saca.api.service.TiempoDisponibleService.Resultado;

// SDACAS-32 (E4.2): carga los datos del estudiante autenticado y delega el cálculo en TiempoDisponibleService.
// Las cuatro consultas filtran por estudiante; un bloque con un cicloId ajeno se descarta en el cálculo
// porque solo cuentan los ciclos activos del propio estudiante.
@Service
public class TiempoDisponibleConsultaService {

	private final TiempoDisponibleService tiempoDisponibleService;
	private final DisponibilidadSemanalRepository disponibilidadSemanalRepository;
	private final CicloAcademicoRepository cicloAcademicoRepository;
	private final HorarioClaseRepository horarioClaseRepository;
	private final JornadaLaboralRepository jornadaLaboralRepository;
	private final Clock relojAplicacion;

	public TiempoDisponibleConsultaService(
			TiempoDisponibleService tiempoDisponibleService,
			DisponibilidadSemanalRepository disponibilidadSemanalRepository,
			CicloAcademicoRepository cicloAcademicoRepository,
			HorarioClaseRepository horarioClaseRepository,
			JornadaLaboralRepository jornadaLaboralRepository,
			Clock relojAplicacion) {
		this.tiempoDisponibleService = tiempoDisponibleService;
		this.disponibilidadSemanalRepository = disponibilidadSemanalRepository;
		this.cicloAcademicoRepository = cicloAcademicoRepository;
		this.horarioClaseRepository = horarioClaseRepository;
		this.jornadaLaboralRepository = jornadaLaboralRepository;
		this.relojAplicacion = relojAplicacion;
	}

	// Mismo rango que el calendario (máximo 42 días). No se cuenta tiempo pasado: el cálculo empieza en el
	// momento actual si el periodo ya empezó, y en el inicio del periodo si es futuro
	@Transactional(readOnly = true)
	public TiempoDisponibleResponse obtener(Long estudianteId, LocalDate desde, LocalDate hasta) {
		CalendarioService.validarPeriodo(desde, hasta);

		LocalDateTime ahora = LocalDateTime.now(relojAplicacion);
		LocalDateTime inicio = desde.atStartOfDay();
		LocalDateTime calculadoDesde = ahora.isAfter(inicio) ? ahora : inicio;

		Resultado resultado = tiempoDisponibleService.calcular(
				calculadoDesde,
				hasta.atTime(LocalTime.MAX),
				disponibilidadSemanalRepository.findByEstudianteId(estudianteId),
				cicloAcademicoRepository.findByEstudianteIdAndEstadoOrderByAnioDescFechaInicioDesc(estudianteId, EstadoCiclo.ACTIVO),
				horarioClaseRepository.findParaCalendario(estudianteId),
				jornadaLaboralRepository.findByEstudianteId(estudianteId));

		return new TiempoDisponibleResponse(
				desde,
				hasta,
				calculadoDesde,
				resultado.minutosReservados(),
				resultado.minutosDescontados(),
				resultado.minutosDisponibles(),
				resultado.dias().stream()
						.map(dia -> new TiempoDisponibleDiaResponse(
								dia.fecha(), dia.minutosReservados(), dia.minutosDescontados(), dia.minutosDisponibles()))
						.toList());
	}
}
