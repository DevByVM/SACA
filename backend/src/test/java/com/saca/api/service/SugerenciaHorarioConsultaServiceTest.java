package com.saca.api.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.saca.api.entity.EstadoCiclo;
import com.saca.api.repository.ActividadAcademicaRepository;
import com.saca.api.repository.CicloAcademicoRepository;
import com.saca.api.repository.DisponibilidadSemanalRepository;
import com.saca.api.repository.HorarioClaseRepository;
import com.saca.api.repository.JornadaLaboralRepository;
import com.saca.api.repository.MateriaInscritaRepository;
import com.saca.api.repository.PlantillaPlanificacionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SugerenciaHorarioConsultaServiceTest {

	private static final Long ESTUDIANTE_ID = 42L;
	private static final Clock RELOJ = Clock.fixed(
			Instant.parse("2026-11-14T18:00:00Z"),
			ZoneId.of("America/El_Salvador"));

	@Mock
	private SugerenciaHorarioService sugerenciaHorarioService;
	@Mock
	private ActividadAcademicaRepository actividadAcademicaRepository;
	@Mock
	private CicloAcademicoRepository cicloAcademicoRepository;
	@Mock
	private DisponibilidadSemanalRepository disponibilidadSemanalRepository;
	@Mock
	private HorarioClaseRepository horarioClaseRepository;
	@Mock
	private JornadaLaboralRepository jornadaLaboralRepository;
	@Mock
	private MateriaInscritaRepository materiaInscritaRepository;
	@Mock
	private PlantillaPlanificacionRepository plantillaPlanificacionRepository;

	private SugerenciaHorarioConsultaService service;

	@BeforeEach
	void prepararServicio() {
		service = new SugerenciaHorarioConsultaService(
				RELOJ,
				sugerenciaHorarioService,
				actividadAcademicaRepository,
				cicloAcademicoRepository,
				disponibilidadSemanalRepository,
				horarioClaseRepository,
				jornadaLaboralRepository,
				materiaInscritaRepository,
				plantillaPlanificacionRepository);
	}

	@Test
	void sinCicloVigenteDevuelveRespuestaVaciaSinConsultarDatosAcademicos() {
		when(cicloAcademicoRepository.findByEstudianteIdAndEstadoOrderByAnioDescFechaInicioDesc(
				ESTUDIANTE_ID, EstadoCiclo.ACTIVO))
				.thenReturn(List.of());

		var respuesta = service.obtener(ESTUDIANTE_ID);

		assertThat(respuesta.dias()).isEmpty();
		assertThat(respuesta.sinAsignar()).isEmpty();
		assertThat(respuesta.mensaje()).isNotBlank();
		assertThat(respuesta.resumen().materiasConsideradas()).isZero();
		assertThat(respuesta.periodo().desde()).isEqualTo(java.time.LocalDate.of(2026, 11, 16));
		verify(cicloAcademicoRepository)
				.findByEstudianteIdAndEstadoOrderByAnioDescFechaInicioDesc(ESTUDIANTE_ID, EstadoCiclo.ACTIVO);
		verifyNoInteractions(
				sugerenciaHorarioService,
				actividadAcademicaRepository,
				disponibilidadSemanalRepository,
				horarioClaseRepository,
				jornadaLaboralRepository,
				materiaInscritaRepository,
				plantillaPlanificacionRepository);
	}
}
