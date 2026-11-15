package com.saca.api.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.saca.api.dto.response.MotivoSinAsignar;
import com.saca.api.dto.response.HorarioSugeridoResponse;
import com.saca.api.dto.response.PlantillaHorarioSugeridaResponse;
import com.saca.api.service.IntervalosMinutos.Intervalo;
import com.saca.api.service.RestriccionesDelDia.VentanasDisponibles;
import com.saca.api.service.SugerenciaHorarioService.ActividadPendiente;
import com.saca.api.service.SugerenciaHorarioService.MateriaActiva;

import static org.assertj.core.api.Assertions.assertThat;

class SugerenciaHorarioServiceTest {

	private static final Clock RELOJ = Clock.fixed(
			Instant.parse("2026-11-14T18:00:00Z"),
			ZoneId.of("America/El_Salvador"));
	private static final LocalDate LUNES = LocalDate.of(2026, 11, 16);

	private final SugerenciaHorarioService service = new SugerenciaHorarioService(RELOJ);

	@Test
	void agrupaPorMateriaYUsaPrimeroLaPlantillaAntesDeLasAlternativas() {
		var respuesta = generar(
				List.of(materia(16L, "Matemática")),
				List.of(
						actividad(16L, 4.0, "2026-11-28T12:00:00"),
						actividad(16L, 2.0, "2026-11-28T12:00:00")),
				Map.of(LUNES, new VentanasDisponibles(
						List.of(i(8, 0, 10, 0)),
						List.of(i(10, 30, 11, 30)))));

		assertThat(respuesta.dias()).hasSize(7);
		assertThat(respuesta.periodo().desde()).isEqualTo(LUNES);
		assertThat(respuesta.periodo().hasta()).isEqualTo(LUNES.plusDays(6));
		assertThat(respuesta.dias().get(0).fecha()).isEqualTo(LUNES);
		assertThat(respuesta.dias().get(0).diaSemana()).isEqualTo("LUNES");
		assertThat(respuesta.dias().get(0).bloques())
				.extracting(bloque -> bloque.materia())
				.containsExactly("Matemática", "Matemática");
		assertThat(respuesta.dias().get(0).bloques())
				.extracting(bloque -> bloque.minutos())
				.containsExactly(120, 60);
		assertThat(respuesta.dias().get(0).bloques().get(0).inicio()).isEqualTo(LocalTime.of(8, 0));
		assertThat(respuesta.dias().get(0).bloques().get(1).inicio()).isEqualTo(LocalTime.of(10, 30));
		assertThat(respuesta.resumen().minutosSugeridosSemanales()).isEqualTo(180);
		assertThat(respuesta.advertencias()).contains("FUERA_DE_PLANTILLA");
	}

	@Test
	void resumePorMateriaLasHorasSemanalesQueNoCaben() {
		var respuesta = generar(
				List.of(materia(16L, "Matemática")),
				List.of(actividad(16L, 4.0, "2026-11-21T12:00:00")),
				Map.of(LUNES, new VentanasDisponibles(
						List.of(i(8, 0, 9, 0)),
						List.of())));

		assertThat(respuesta.dias().get(0).minutosTotales()).isEqualTo(60);
		assertThat(respuesta.sinAsignar()).singleElement().satisfies(materia -> {
			assertThat(materia.materiaInscritaId()).isEqualTo(16L);
			assertThat(materia.motivo()).isEqualTo(MotivoSinAsignar.SIN_ESPACIO);
			assertThat(materia.horasSemanalesProgramadas()).isEqualTo(1.0);
			assertThat(materia.horasSemanalesSinProgramar()).isEqualTo(3.0);
		});
		assertThat(respuesta.resumen().minutosNoProgramadosSemanales()).isEqualTo(180);
	}

	@Test
	void asignaUnaFranjaMinimaALasMateriasActivasSinCargaEstimada() {
		var respuesta = generar(
				List.of(
						materia(16L, "Matemática"),
						materia(17L, "Sociales")),
				List.of(),
				Map.of(LUNES, new VentanasDisponibles(
						List.of(i(8, 0, 9, 30)),
						List.of())));

		assertThat(respuesta.dias().get(0).bloques())
				.extracting(bloque -> bloque.materia())
				.containsExactly("Matemática", "Sociales");
		assertThat(respuesta.dias().get(0).bloques())
				.extracting(bloque -> bloque.minutos())
				.containsExactly(30, 30);
		assertThat(respuesta.resumen().materiasConsideradas()).isEqualTo(2);
		assertThat(respuesta.sinAsignar()).isEmpty();
	}

	@Test
	void noProgramaUnaMateriaDespuesDelInicioDeSuParcial() {
		ActividadPendiente parcial = new ActividadPendiente(
				16L,
				0,
				LocalDateTime.of(2026, 11, 16, 19, 0),
				LocalDateTime.of(2026, 11, 16, 20, 0),
				1,
				30.0);
		var respuesta = generar(
				List.of(materia(16L, "Matemática")),
				List.of(parcial),
				Map.of(LUNES, new VentanasDisponibles(
						List.of(i(18, 0, 21, 0)),
						List.of())));

		assertThat(respuesta.dias().get(0).bloques()).hasSize(1);
		assertThat(respuesta.dias().get(0).bloques().get(0).inicio()).isEqualTo(LocalTime.of(18, 0));
		assertThat(respuesta.dias().get(0).bloques().get(0).fin()).isBeforeOrEqualTo(LocalTime.of(19, 0));
	}

	@Test
	void limitaLaCargaDiariaYDejaDescansoEntreSesiones() {
		var respuesta = generar(
				List.of(materia(16L, "Matemática")),
				List.of(actividad(16L, 8.0, "2026-11-21T12:00:00")),
				Map.of(LUNES, new VentanasDisponibles(
						List.of(i(8, 0, 16, 0)),
						List.of())));

		var bloques = respuesta.dias().get(0).bloques();
		assertThat(respuesta.dias().get(0).minutosTotales()).isEqualTo(360);
		assertThat(bloques).hasSize(3);
		assertThat(bloques).allSatisfy(bloque -> assertThat(bloque.minutos()).isLessThanOrEqualTo(120));
		for (int i = 1; i < bloques.size(); i++) {
			assertThat(bloques.get(i).inicio())
					.isAfterOrEqualTo(bloques.get(i - 1).fin().plusMinutes(15));
		}
	}

	private HorarioSugeridoResponse generar(
			List<MateriaActiva> materias,
			List<ActividadPendiente> actividades,
			Map<LocalDate, VentanasDisponibles> ventanas) {
		return service.generar(
				LUNES,
				materias,
				actividades,
				ventanas,
				new PlantillaHorarioSugeridaResponse(3L, false),
				null,
				List.of());
	}

	private static MateriaActiva materia(Long id, String nombre) {
		return new MateriaActiva(
				id,
				nombre,
				LocalDate.of(2026, 11, 1),
				LocalDate.of(2026, 12, 31));
	}

	private static ActividadPendiente actividad(Long materiaId, double horas, String fechaLimite) {
		return new ActividadPendiente(
				materiaId,
				horas,
				null,
				LocalDateTime.parse(fechaLimite),
				3,
				10.0);
	}

	private static Intervalo i(int inicioHora, int inicioMinuto, int finHora, int finMinuto) {
		return new Intervalo(inicioHora * 60 + inicioMinuto, finHora * 60 + finMinuto);
	}
}
