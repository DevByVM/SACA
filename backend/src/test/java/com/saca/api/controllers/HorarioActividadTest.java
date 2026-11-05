package com.saca.api.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saca.api.entity.ActividadAcademica;
import com.saca.api.entity.CicloAcademico;
import com.saca.api.entity.DiaSemana;
import com.saca.api.entity.EstadoCiclo;
import com.saca.api.entity.Estudiante;
import com.saca.api.entity.HorarioClase;
import com.saca.api.entity.MateriaInscrita;
import com.saca.api.entity.ModalidadClase;
import com.saca.api.entity.TipoActividadAcademica;
import com.saca.api.repository.ActividadAcademicaRepository;
import com.saca.api.repository.CicloAcademicoRepository;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.repository.HorarioClaseRepository;
import com.saca.api.repository.MateriaInscritaRepository;
import com.saca.api.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

// Un horario de clase puede vincularse opcionalmente con una actividad de su misma materia
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HorarioActividadTest {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private CicloAcademicoRepository cicloAcademicoRepository;

    @Autowired
    private MateriaInscritaRepository materiaInscritaRepository;

    @Autowired
    private ActividadAcademicaRepository actividadRepository;

    @Autowired
    private HorarioClaseRepository horarioRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transaccion;

    private Estudiante a;
    private MateriaInscrita materiaA;
    private MateriaInscrita otraMateriaA;
    private ActividadAcademica laboratorioA;
    private ActividadAcademica parcialA;
    private ActividadAcademica actividadOtraMateriaA;
    private ActividadAcademica actividadB;
    private String tokenA;

    @BeforeEach
    void prepararDatos() {
        a = crearEstudiante("Estudiante Uno");
        Estudiante b = crearEstudiante("Estudiante Dos");
        CicloAcademico cicloA = crearCiclo(a);
        CicloAcademico cicloB = crearCiclo(b);
        materiaA = crearMateria(cicloA, "PRN315");
        otraMateriaA = crearMateria(cicloA, "MAT115");
        MateriaInscrita materiaB = crearMateria(cicloB, "PRN315");

        laboratorioA = crearActividad(materiaA, "Laboratorio 1", TipoActividadAcademica.LABORATORIO);
        parcialA = crearActividad(materiaA, "Parcial 1", TipoActividadAcademica.PARCIAL);
        actividadOtraMateriaA = crearActividad(otraMateriaA, "Tarea de MAT115", TipoActividadAcademica.INVESTIGACION);
        actividadB = crearActividad(materiaB, "Laboratorio privado de B", TipoActividadAcademica.LABORATORIO);

        tokenA = tokenService.emitir(a).valor();
    }

    private Estudiante crearEstudiante(String nombre) {
        String unico = UUID.randomUUID().toString().substring(0, 8);
        Estudiante nuevo = new Estudiante();
        nuevo.setNombre(nombre);
        nuevo.setCarnet("HA" + unico);
        nuevo.setCorreoInstitucional("hor." + unico + "@ues.edu.sv");
        nuevo.setContrasenia(new BCryptPasswordEncoder().encode("Segura123"));
        nuevo.setCuentaActiva(true);
        nuevo.setIntentosFallidos(0);
        return estudianteRepository.save(nuevo);
    }

    private CicloAcademico crearCiclo(Estudiante propietario) {
        CicloAcademico nuevo = new CicloAcademico();
        nuevo.setEstudiante(propietario);
        nuevo.setNombre("Ciclo I");
        nuevo.setAnio(2026);
        nuevo.setFechaInicio(LocalDate.of(2026, 2, 1));
        nuevo.setFechaFin(LocalDate.of(2026, 12, 15));
        nuevo.setEstado(EstadoCiclo.ACTIVO);
        return cicloAcademicoRepository.save(nuevo);
    }

    private MateriaInscrita crearMateria(CicloAcademico ciclo, String codigo) {
        MateriaInscrita nueva = new MateriaInscrita();
        nueva.setCicloAcademico(ciclo);
        nueva.setNombre("Materia " + codigo);
        nueva.setCodigo(codigo);
        nueva.setGrupoTeorico("GT01");
        return materiaInscritaRepository.save(nueva);
    }

    private ActividadAcademica crearActividad(MateriaInscrita materia, String nombre, TipoActividadAcademica tipo) {
        ActividadAcademica nueva = new ActividadAcademica();
        nueva.setNombre(nombre);
        nueva.setFechaInicio(LocalDateTime.of(2026, 3, 1, 8, 0));
        nueva.setFechaEntrega(LocalDateTime.of(2026, 3, 10, 8, 0));
        nueva.setPorcentajeEvaluacion(10);
        nueva.setTiempoEstimadoHoras(4);
        nueva.setEstado("ACTIVO");
        nueva.setTipoActividad(tipo);
        nueva.setMateriaInscrita(materia);
        return actividadRepository.save(nueva);
    }

    private HorarioClase crearHorarioVinculado(ActividadAcademica actividad, DiaSemana dia) {
        HorarioClase nuevo = new HorarioClase();
        nuevo.setMateriaInscrita(actividad.getMateriaInscrita());
        nuevo.setActividadAcademica(actividad);
        nuevo.setDiaSemana(dia);
        nuevo.setHoraInicio(LocalTime.of(13, 0));
        nuevo.setHoraFin(LocalTime.of(14, 40));
        nuevo.setModalidad(ModalidadClase.PRESENCIAL);
        return horarioRepository.save(nuevo);
    }

    private MvcResult como(MockHttpServletRequestBuilder peticion, String cuerpo) throws Exception {
        return mockMvc.perform(peticion
                .header("Authorization", "Bearer " + tokenA)
                .param("estudianteId", String.valueOf(a.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo)).andReturn();
    }

    private MvcResult como(MockHttpServletRequestBuilder peticion) throws Exception {
        return mockMvc.perform(peticion
                .header("Authorization", "Bearer " + tokenA)
                .param("estudianteId", String.valueOf(a.getId()))).andReturn();
    }

    private static String horario(Long materiaId, String dia, String inicio, String fin, Long actividadId) {
        return """
                {"materiaInscritaId":%s,"diaSemana":"%s","horaInicio":"%s","horaFin":"%s",
                 "modalidad":"PRESENCIAL","docenteTutor":"Ing. Pérez","actividadAcademicaId":%s}
                """.formatted(materiaId, dia, inicio, fin, actividadId);
    }

    // Mismos datos de la actividad cargada en prepararDatos, salvo la materia
    private static String actividadEnMateria(ActividadAcademica actividad, Long materiaId, double porcentaje) {
        return """
                {"nombre":"%s","fechaInicio":"%s","fechaEntrega":"%s",
                 "porcentajeEvaluacion":%s,"tiempoEstimadoHoras":4,"estado":"ACTIVO","tipoActividad":"%s",
                 "materiaInscritaId":%s,"notaId":null,"notaObtenida":null}
                """.formatted(actividad.getNombre(), FORMATO_FECHA.format(actividad.getFechaInicio()),
                FORMATO_FECHA.format(actividad.getFechaEntrega()), porcentaje, actividad.getTipoActividad(), materiaId);
    }

    private JsonNode json(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString());
    }

    private String mensaje(MvcResult resultado) throws Exception {
        return json(resultado).get("message").asText();
    }

    private Long actividadVinculada(Long horarioId) {
        return transaccion.execute(estado -> {
            ActividadAcademica actividad = horarioRepository.findById(horarioId).orElseThrow().getActividadAcademica();
            return actividad == null ? null : actividad.getIdActividad();
        });
    }

    private Long materiaDeLaActividad(ActividadAcademica actividad) {
        return transaccion.execute(estado ->
                actividadRepository.findById(actividad.getIdActividad()).orElseThrow().getMateriaInscrita().getId());
    }

    // ================= Crear

    @Test
    void seCreaUnHorarioVinculadoAUnaActividadDeLaMismaMateria() throws Exception {
        MvcResult creado = como(post("/api/horarios"),
                horario(materiaA.getId(), "MARTES", "07:00", "08:40", laboratorioA.getIdActividad()));

        assertThat(creado.getResponse().getStatus()).isEqualTo(201);
        JsonNode cuerpo = json(creado);
        assertThat(cuerpo.get("materiaInscritaId").asLong()).isEqualTo(materiaA.getId());
        assertThat(cuerpo.get("actividadAcademicaId").asLong()).isEqualTo(laboratorioA.getIdActividad());
        assertThat(cuerpo.get("actividadNombre").asText()).isEqualTo("Laboratorio 1");
        assertThat(actividadVinculada(cuerpo.get("id").asLong())).isEqualTo(laboratorioA.getIdActividad());
    }

    @Test
    void elHorarioSinActividadSigueSiendoValido() throws Exception {
        MvcResult creado = como(post("/api/horarios"), horario(materiaA.getId(), "LUNES", "07:00", "08:40", null));

        assertThat(creado.getResponse().getStatus()).isEqualTo(201);
        assertThat(json(creado).get("actividadAcademicaId").isNull()).isTrue();
        assertThat(json(creado).get("actividadNombre").isNull()).isTrue();
    }

    @Test
    void unaActividadDeOtraMateriaSeRechazaCon400() throws Exception {
        long horariosAntes = horarioRepository.count();

        MvcResult rechazado = como(post("/api/horarios"),
                horario(materiaA.getId(), "MARTES", "07:00", "08:40", actividadOtraMateriaA.getIdActividad()));

        assertThat(rechazado.getResponse().getStatus()).isEqualTo(400);
        assertThat(mensaje(rechazado)).isEqualTo("La actividad debe pertenecer a la misma materia del horario");
        assertThat(horarioRepository.count()).isEqualTo(horariosAntes);
    }

    @Test
    void unaActividadDeOtroEstudianteOInexistenteResponde404SinRevelarla() throws Exception {
        long horariosAntes = horarioRepository.count();

        MvcResult ajena = como(post("/api/horarios"),
                horario(materiaA.getId(), "MARTES", "07:00", "08:40", actividadB.getIdActividad()));
        MvcResult inexistente = como(post("/api/horarios"),
                horario(materiaA.getId(), "MARTES", "07:00", "08:40", 999_999L));

        assertThat(ajena.getResponse().getStatus()).isEqualTo(404);
        assertThat(ajena.getResponse().getContentAsString()).doesNotContain("Laboratorio privado de B");
        assertThat(inexistente.getResponse().getStatus()).isEqualTo(404);
        assertThat(horarioRepository.count()).isEqualTo(horariosAntes);
    }

    @Test
    void elVinculoNoEvitaLaValidacionDeConflictos() throws Exception {
        crearHorarioVinculado(laboratorioA, DiaSemana.JUEVES);

        MvcResult cruce = como(post("/api/horarios"),
                horario(materiaA.getId(), "JUEVES", "14:00", "15:00", parcialA.getIdActividad()));

        assertThat(cruce.getResponse().getStatus()).isEqualTo(400);
        assertThat(mensaje(cruce)).startsWith("Existe conflicto con otro horario registrado");
    }

    // ================= Editar

    @Test
    void alEditarSePuedeCambiarLaActividadAsociada() throws Exception {
        HorarioClase existente = crearHorarioVinculado(laboratorioA, DiaSemana.MIERCOLES);

        MvcResult editado = como(put("/api/horarios/" + existente.getId()),
                horario(materiaA.getId(), "MIERCOLES", "13:00", "14:40", parcialA.getIdActividad()));

        assertThat(editado.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(editado).get("actividadNombre").asText()).isEqualTo("Parcial 1");
        assertThat(actividadVinculada(existente.getId())).isEqualTo(parcialA.getIdActividad());
    }

    @Test
    void alEditarSePuedeQuitarLaAsociacion() throws Exception {
        HorarioClase existente = crearHorarioVinculado(laboratorioA, DiaSemana.MIERCOLES);

        MvcResult editado = como(put("/api/horarios/" + existente.getId()),
                horario(materiaA.getId(), "MIERCOLES", "13:00", "14:40", null));

        assertThat(editado.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(editado).get("actividadAcademicaId").isNull()).isTrue();
        assertThat(actividadVinculada(existente.getId())).isNull();
    }

    @Test
    void alEditarTambienSeRechazanActividadesDeOtraMateriaODeOtroEstudiante() throws Exception {
        HorarioClase existente = crearHorarioVinculado(laboratorioA, DiaSemana.MIERCOLES);

        MvcResult otraMateria = como(put("/api/horarios/" + existente.getId()),
                horario(materiaA.getId(), "MIERCOLES", "13:00", "14:40", actividadOtraMateriaA.getIdActividad()));
        MvcResult ajena = como(put("/api/horarios/" + existente.getId()),
                horario(materiaA.getId(), "MIERCOLES", "13:00", "14:40", actividadB.getIdActividad()));

        assertThat(otraMateria.getResponse().getStatus()).isEqualTo(400);
        assertThat(ajena.getResponse().getStatus()).isEqualTo(404);
        assertThat(actividadVinculada(existente.getId())).isEqualTo(laboratorioA.getIdActividad());
    }

    // ================= Listado

    @Test
    void elListadoIncluyeLaActividadCuandoExiste() throws Exception {
        HorarioClase vinculado = crearHorarioVinculado(laboratorioA, DiaSemana.LUNES);
        como(post("/api/horarios"), horario(materiaA.getId(), "VIERNES", "07:00", "08:40", null));

        JsonNode listado = json(como(get("/api/horarios")));

        assertThat(listado).hasSize(2);
        listado.forEach(item -> {
            if (item.get("id").asLong() == vinculado.getId()) {
                assertThat(item.get("actividadAcademicaId").asLong()).isEqualTo(laboratorioA.getIdActividad());
                assertThat(item.get("actividadNombre").asText()).isEqualTo("Laboratorio 1");
            } else {
                assertThat(item.get("actividadAcademicaId").isNull()).isTrue();
            }
        });
    }

    // ================= Eliminar la actividad

    @Test
    void alEliminarUnaActividadSusHorariosSeConservanSinVinculo() throws Exception {
        HorarioClase lunes = crearHorarioVinculado(laboratorioA, DiaSemana.LUNES);
        HorarioClase martes = crearHorarioVinculado(laboratorioA, DiaSemana.MARTES);

        MvcResult eliminado = como(delete("/api/actividades/" + laboratorioA.getIdActividad()));

        assertThat(eliminado.getResponse().getStatus()).isEqualTo(204);
        assertThat(actividadRepository.findById(laboratorioA.getIdActividad())).isEmpty();
        assertThat(actividadVinculada(lunes.getId())).isNull();
        assertThat(actividadVinculada(martes.getId())).isNull();
    }

    // ================= Cambio de materia de una actividad vinculada

    @Test
    void cambiarLaMateriaSinConfirmarSeRechazaYNoModificaNada() throws Exception {
        HorarioClase lunes = crearHorarioVinculado(laboratorioA, DiaSemana.LUNES);
        HorarioClase martes = crearHorarioVinculado(laboratorioA, DiaSemana.MARTES);

        MvcResult rechazado = como(put("/api/actividades/" + laboratorioA.getIdActividad()),
                actividadEnMateria(laboratorioA, otraMateriaA.getId(), 10));

        assertThat(rechazado.getResponse().getStatus()).isEqualTo(400);
        assertThat(mensaje(rechazado))
                .contains("2 horarios de clase vinculados", "PRN315 - Materia PRN315", "Confirma el cambio de materia");
        assertThat(materiaDeLaActividad(laboratorioA)).isEqualTo(materiaA.getId());
        assertThat(actividadVinculada(lunes.getId())).isEqualTo(laboratorioA.getIdActividad());
        assertThat(actividadVinculada(martes.getId())).isEqualTo(laboratorioA.getIdActividad());
    }

    @Test
    void cambiarLaMateriaConfirmandoActualizaLaActividadYDesvinculaSusHorarios() throws Exception {
        HorarioClase lunes = crearHorarioVinculado(laboratorioA, DiaSemana.LUNES);
        HorarioClase otroVinculo = crearHorarioVinculado(parcialA, DiaSemana.MARTES);

        MvcResult confirmado = como(
                put("/api/actividades/" + laboratorioA.getIdActividad()).param("desvincularHorarios", "true"),
                actividadEnMateria(laboratorioA, otraMateriaA.getId(), 10));

        assertThat(confirmado.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(confirmado).get("materiaInscritaId").asLong()).isEqualTo(otraMateriaA.getId());
        assertThat(materiaDeLaActividad(laboratorioA)).isEqualTo(otraMateriaA.getId());
        assertThat(actividadVinculada(lunes.getId())).isNull();
        assertThat(horarioRepository.findById(lunes.getId())).isPresent();
        // Solo se desvinculan los horarios de la actividad modificada
        assertThat(actividadVinculada(otroVinculo.getId())).isEqualTo(parcialA.getIdActividad());
    }

    @Test
    void siLaActualizacionConfirmadaFallaLosHorariosConservanSuVinculo() throws Exception {
        HorarioClase lunes = crearHorarioVinculado(laboratorioA, DiaSemana.LUNES);
        // La materia destino ya tiene 10 %: un 95 % la llevaría por encima de 100 %
        MvcResult fallido = como(
                put("/api/actividades/" + laboratorioA.getIdActividad()).param("desvincularHorarios", "true"),
                actividadEnMateria(laboratorioA, otraMateriaA.getId(), 95));

        assertThat(fallido.getResponse().getStatus()).isEqualTo(400);
        assertThat(materiaDeLaActividad(laboratorioA)).isEqualTo(materiaA.getId());
        assertThat(actividadVinculada(lunes.getId())).isEqualTo(laboratorioA.getIdActividad());
    }

    @Test
    void cambiarLaMateriaDeUnaActividadSinHorariosNoPideConfirmacion() throws Exception {
        MvcResult actualizado = como(put("/api/actividades/" + parcialA.getIdActividad()),
                actividadEnMateria(parcialA, otraMateriaA.getId(), 10));

        assertThat(actualizado.getResponse().getStatus()).isEqualTo(200);
        assertThat(materiaDeLaActividad(parcialA)).isEqualTo(otraMateriaA.getId());
    }

    @Test
    void editarUnaActividadVinculadaSinCambiarDeMateriaConservaElVinculo() throws Exception {
        HorarioClase lunes = crearHorarioVinculado(laboratorioA, DiaSemana.LUNES);

        MvcResult actualizado = como(put("/api/actividades/" + laboratorioA.getIdActividad()),
                actividadEnMateria(laboratorioA, materiaA.getId(), 15));

        assertThat(actualizado.getResponse().getStatus()).isEqualTo(200);
        assertThat(actividadVinculada(lunes.getId())).isEqualTo(laboratorioA.getIdActividad());
    }
}
