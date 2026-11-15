package com.saca.api.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saca.api.TipoActividadPrueba;
import com.saca.api.entity.ActividadAcademica;
import com.saca.api.entity.CicloAcademico;
import com.saca.api.entity.DiaSemana;
import com.saca.api.entity.DisponibilidadSemanal;
import com.saca.api.entity.EstadoCiclo;
import com.saca.api.entity.Estudiante;
import com.saca.api.entity.HorarioClase;
import com.saca.api.entity.JornadaLaboral;
import com.saca.api.entity.MateriaInscrita;
import com.saca.api.entity.ModalidadClase;
import com.saca.api.entity.TipoActividadAcademica;
import com.saca.api.repository.ActividadAcademicaRepository;
import com.saca.api.repository.CicloAcademicoRepository;
import com.saca.api.repository.DisponibilidadSemanalRepository;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.repository.HorarioClaseRepository;
import com.saca.api.repository.HorarioEstudioRepository;
import com.saca.api.repository.JornadaLaboralRepository;
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

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

// Confirmación y consulta del horario de estudio definitivo (GET/PUT /api/estudiante/horario-estudio).
// Las fechas parten del día real porque el reloj de la aplicación también lo usa la emisión de tokens.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HorarioEstudioTest {

    private static final String RUTA = "/api/estudiante/horario-estudio";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EstudianteRepository estudianteRepository;
    @Autowired
    private CicloAcademicoRepository cicloAcademicoRepository;
    @Autowired
    private MateriaInscritaRepository materiaInscritaRepository;
    @Autowired
    private HorarioClaseRepository horarioClaseRepository;
    @Autowired
    private DisponibilidadSemanalRepository disponibilidadRepository;
    @Autowired
    private JornadaLaboralRepository jornadaRepository;
    @Autowired
    private HorarioEstudioRepository horarioEstudioRepository;
    @Autowired
    private ActividadAcademicaRepository actividadRepository;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private Clock relojAplicacion;

    private LocalDate lunes;
    private Estudiante a;
    private Estudiante b;
    private MateriaInscrita algebra;
    private MateriaInscrita fisica;
    private MateriaInscrita materiaDeB;
    private String tokenA;
    private String tokenB;
    private String tokenSinCiclo;

    @BeforeEach
    void prepararDatos() {
        LocalDate hoy = LocalDate.now(relojAplicacion);
        lunes = hoy.with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        // A: lunes con disponibilidad de estudio 18-21, clase el martes 08-10 y jornada el miércoles 08-17.
        // Los demás días, sin disponibilidad registrada, quedan libres de 06:00 a 22:00.
        a = crearEstudiante();
        CicloAcademico cicloA = crearCiclo(a, EstadoCiclo.ACTIVO, hoy.minusDays(30), hoy.plusDays(90));
        algebra = crearMateria(cicloA, "Álgebra Lineal");
        fisica = crearMateria(cicloA, "Física II");
        disponibilidadRepository.save(new DisponibilidadSemanal("LUNES", LocalTime.of(18, 0), LocalTime.of(21, 0),
                "ESTUDIO", cicloA.getId(), a.getId()));
        HorarioClase clase = new HorarioClase();
        clase.setMateriaInscrita(algebra);
        clase.setDiaSemana(DiaSemana.MARTES);
        clase.setHoraInicio(LocalTime.of(8, 0));
        clase.setHoraFin(LocalTime.of(10, 0));
        clase.setModalidad(ModalidadClase.PRESENCIAL);
        horarioClaseRepository.save(clase);
        jornadaRepository.save(new JornadaLaboral("MIERCOLES", LocalTime.of(8, 0), LocalTime.of(17, 0), a.getId()));

        b = crearEstudiante();
        materiaDeB = crearMateria(crearCiclo(b, EstadoCiclo.ACTIVO, hoy.minusDays(30), hoy.plusDays(90)), "Química");

        Estudiante sinCiclo = crearEstudiante();
        crearCiclo(sinCiclo, EstadoCiclo.FINALIZADO, hoy.minusDays(30), hoy.plusDays(90));

        tokenA = tokenService.emitir(a).valor();
        tokenB = tokenService.emitir(b).valor();
        tokenSinCiclo = tokenService.emitir(sinCiclo).valor();
    }

    // ================= descartar y confirmar

    @Test
    void consultarLaSugerenciaNoGuardaNingunHorario() throws Exception {
        MvcResult sugerencia = mockMvc.perform(get("/api/estudiante/horario-sugerido")
                .header("Authorization", "Bearer " + tokenA)).andReturn();

        assertThat(sugerencia.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(sugerencia).get("periodo").get("desde").asText()).isEqualTo(lunes.toString());
        assertThat(horarioEstudioRepository.countByEstudianteId(a.getId())).isZero();
        assertThat(consultar(tokenA, lunes).getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    void confirmarGuardaLaVersionEditadaYSePuedeConsultar() throws Exception {
        MvcResult confirmado = confirmar(tokenA, lunes,
                bloque(lunes, "18:00", "20:00", algebra),
                bloque(lunes.plusDays(1), "10:00", "11:00", fisica));

        assertThat(confirmado.getResponse().getStatus()).isEqualTo(200);
        JsonNode cuerpo = json(confirmado);
        assertThat(cuerpo.get("desde").asText()).isEqualTo(lunes.toString());
        assertThat(cuerpo.get("hasta").asText()).isEqualTo(lunes.plusDays(6).toString());
        assertThat(cuerpo.get("minutosTotales").asInt()).isEqualTo(180);
        assertThat(cuerpo.get("bloques")).hasSize(2);
        assertThat(cuerpo.get("bloques").get(0).get("materiaInscritaId").asLong()).isEqualTo(algebra.getId());
        assertThat(cuerpo.get("bloques").get(0).get("materia").asText()).isEqualTo("Álgebra Lineal");
        assertThat(cuerpo.get("bloques").get(0).get("minutos").asInt()).isEqualTo(120);

        MvcResult consultado = consultar(tokenA, lunes);
        assertThat(consultado.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(consultado).get("id").asLong()).isEqualTo(cuerpo.get("id").asLong());
        assertThat(json(consultado).get("bloques").get(1).get("inicio").asText()).startsWith("10:00");
    }

    @Test
    void laSugerenciaSinEditarSePuedeConfirmarTalCual() throws Exception {
        ActividadAcademica proyecto = new ActividadAcademica();
        proyecto.setNombre("Proyecto final");
        proyecto.setFechaInicio(lunes.minusDays(7).atTime(8, 0));
        proyecto.setFechaEntrega(lunes.plusDays(10).atTime(23, 0));
        proyecto.setPorcentajeEvaluacion(30);
        proyecto.setTiempoEstimadoHoras(8);
        proyecto.setEstado("ACTIVO");
        proyecto.setTipoActividad(TipoActividadPrueba.crear(TipoActividadAcademica.PROYECTO));
        proyecto.setMateriaInscrita(fisica);
        actividadRepository.save(proyecto);

        JsonNode sugerencia = json(mockMvc.perform(get("/api/estudiante/horario-sugerido")
                .header("Authorization", "Bearer " + tokenA)).andReturn());
        List<Map<String, Object>> bloques = new ArrayList<>();
        for (JsonNode dia : sugerencia.get("dias")) {
            for (JsonNode sugerido : dia.get("bloques")) {
                Map<String, Object> bloque = new LinkedHashMap<>();
                bloque.put("fecha", dia.get("fecha").asText());
                bloque.put("inicio", sugerido.get("inicio").asText());
                bloque.put("fin", sugerido.get("fin").asText());
                bloque.put("materiaInscritaId", sugerido.get("materiaInscritaId").asLong());
                bloques.add(bloque);
            }
        }
        assertThat(bloques).isNotEmpty();

        @SuppressWarnings("unchecked")
        MvcResult confirmado = confirmar(tokenA, lunes, bloques.toArray(new Map[0]));

        assertThat(confirmado.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(confirmado).get("bloques")).hasSize(bloques.size());
        assertThat(json(confirmado).get("minutosTotales").asInt())
                .isEqualTo(sugerencia.get("resumen").get("minutosSugeridosSemanales").asInt()
                        - sugerencia.get("resumen").get("minutosNoProgramadosSemanales").asInt());
    }

    @Test
    void confirmarOtraVezLaMismaSemanaReemplazaSinDuplicar() throws Exception {
        long primero = json(confirmar(tokenA, lunes, bloque(lunes, "18:00", "20:00", algebra))).get("id").asLong();
        MvcResult segundo = confirmar(tokenA, lunes,
                bloque(lunes.plusDays(3), "14:00", "15:30", fisica),
                bloque(lunes.plusDays(4), "07:00", "08:00", algebra));

        assertThat(segundo.getResponse().getStatus()).isEqualTo(200);
        assertThat(horarioEstudioRepository.countByEstudianteId(a.getId())).isEqualTo(1);
        JsonNode consultado = json(consultar(tokenA, lunes));
        assertThat(consultado.get("id").asLong()).isEqualTo(primero);
        assertThat(consultado.get("bloques")).hasSize(2);
        assertThat(consultado.get("bloques").get(0).get("fecha").asText()).isEqualTo(lunes.plusDays(3).toString());
        assertThat(consultado.get("minutosTotales").asInt()).isEqualTo(150);
    }

    // ================= validaciones

    @Test
    void rechazaBloquesSuperpuestosSinGuardarNada() throws Exception {
        MvcResult resultado = confirmar(tokenA, lunes,
                bloque(lunes.plusDays(3), "14:00", "16:00", algebra),
                bloque(lunes.plusDays(3), "15:30", "17:00", fisica));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(mensaje(resultado)).contains("se superpone");
        assertThat(horarioEstudioRepository.countByEstudianteId(a.getId())).isZero();
    }

    @Test
    void bloquesQueSoloSeTocanSonValidos() throws Exception {
        MvcResult resultado = confirmar(tokenA, lunes,
                bloque(lunes.plusDays(3), "14:00", "15:00", algebra),
                bloque(lunes.plusDays(3), "15:00", "16:00", fisica));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void rechazaChoquesConClasesJornadaYTiempoNoDisponible() throws Exception {
        MvcResult conClase = confirmar(tokenA, lunes, bloque(lunes.plusDays(1), "09:00", "10:30", algebra));
        MvcResult conJornada = confirmar(tokenA, lunes, bloque(lunes.plusDays(2), "12:00", "13:00", algebra));
        MvcResult fueraDeDisponibilidad = confirmar(tokenA, lunes, bloque(lunes, "08:00", "09:00", algebra));

        for (MvcResult resultado : List.of(conClase, conJornada, fueraDeDisponibilidad)) {
            assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
            assertThat(mensaje(resultado)).contains("choca con");
        }
        assertThat(horarioEstudioRepository.countByEstudianteId(a.getId())).isZero();
    }

    @Test
    void rechazaHorasYFechasInvalidas() throws Exception {
        MvcResult invertido = confirmar(tokenA, lunes, bloque(lunes.plusDays(3), "15:00", "14:00", algebra));
        MvcResult corto = confirmar(tokenA, lunes, bloque(lunes.plusDays(3), "14:00", "14:15", algebra));
        MvcResult fueraDeSemana = confirmar(tokenA, lunes, bloque(lunes.plusDays(7), "14:00", "15:00", algebra));
        MvcResult noEsLunes = confirmar(tokenA, lunes.plusDays(1), bloque(lunes.plusDays(3), "14:00", "15:00", algebra));
        MvcResult semanaPasada = confirmar(tokenA, lunes.minusWeeks(2),
                bloque(lunes.minusWeeks(2), "14:00", "15:00", algebra));
        MvcResult sinBloques = confirmar(tokenA, lunes);

        assertThat(mensaje(invertido)).contains("debe terminar después de empezar");
        assertThat(mensaje(corto)).contains("al menos 30 minutos");
        assertThat(mensaje(fueraDeSemana)).contains("fuera de la semana");
        assertThat(mensaje(noEsLunes)).contains("lunes");
        assertThat(mensaje(semanaPasada)).contains("ya terminó");
        assertThat(mensaje(sinBloques)).contains("al menos un bloque");
        for (MvcResult resultado : List.of(invertido, corto, fueraDeSemana, noEsLunes, semanaPasada, sinBloques)) {
            assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        }
        assertThat(horarioEstudioRepository.countByEstudianteId(a.getId())).isZero();
    }

    @Test
    void unBloqueInvalidoImpideGuardarLosValidos() throws Exception {
        MvcResult resultado = confirmar(tokenA, lunes,
                bloque(lunes, "18:00", "20:00", algebra),
                bloque(lunes.plusDays(1), "08:30", "09:30", fisica));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(horarioEstudioRepository.countByEstudianteId(a.getId())).isZero();
    }

    @Test
    void sinCicloActivoNoSePuedeConfirmar() throws Exception {
        MvcResult resultado = confirmar(tokenSinCiclo, lunes, bloque(lunes, "18:00", "20:00", algebra));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(mensaje(resultado)).contains("No hay un ciclo académico activo");
    }

    @Test
    void cicloActivoSinMateriasDaUnaSemanaVaciaYNoPermiteConfirmar() throws Exception {
        // Ciclo activo vacío y materias (con una actividad pendiente) solo en un ciclo finalizado
        LocalDate hoy = LocalDate.now(relojAplicacion);
        Estudiante sinMaterias = crearEstudiante();
        crearCiclo(sinMaterias, EstadoCiclo.ACTIVO, hoy.minusDays(30), hoy.plusDays(90));
        MateriaInscrita anterior = crearMateria(
                crearCiclo(sinMaterias, EstadoCiclo.FINALIZADO, hoy.minusDays(200), hoy.minusDays(60)), "Diseño de Sistemas I");
        ActividadAcademica pendiente = new ActividadAcademica();
        pendiente.setNombre("Actividad del ciclo anterior");
        pendiente.setFechaInicio(lunes.minusDays(7).atTime(8, 0));
        pendiente.setFechaEntrega(lunes.plusDays(10).atTime(23, 0));
        pendiente.setPorcentajeEvaluacion(20);
        pendiente.setTiempoEstimadoHoras(6);
        pendiente.setEstado("ACTIVO");
        pendiente.setTipoActividad(TipoActividadPrueba.crear(TipoActividadAcademica.PROYECTO));
        pendiente.setMateriaInscrita(anterior);
        actividadRepository.save(pendiente);
        String token = tokenService.emitir(sinMaterias).valor();

        MvcResult sugerencia = mockMvc.perform(get("/api/estudiante/horario-sugerido")
                .header("Authorization", "Bearer " + token)).andReturn();
        JsonNode cuerpo = json(sugerencia);
        assertThat(sugerencia.getResponse().getStatus()).isEqualTo(200);
        assertThat(cuerpo.get("mensaje").isNull()).isTrue();
        assertThat(cuerpo.get("dias")).hasSize(7);
        for (JsonNode dia : cuerpo.get("dias")) {
            assertThat(dia.get("bloques")).isEmpty();
        }
        assertThat(cuerpo.get("sinAsignar")).isEmpty();
        assertThat(cuerpo.get("resumen").get("materiasConsideradas").asInt()).isZero();

        MvcResult confirmado = confirmar(token, lunes, bloque(lunes.plusDays(3), "14:00", "15:00", anterior));
        assertThat(confirmado.getResponse().getStatus()).isEqualTo(400);
        assertThat(mensaje(confirmado)).contains("no pertenece a un ciclo activo");
        assertThat(horarioEstudioRepository.countByEstudianteId(sinMaterias.getId())).isZero();
        assertThat(consultar(token, lunes).getResponse().getStatus()).isEqualTo(204);
    }

    // ================= propiedad

    @Test
    void unEstudianteNoVeNiModificaElHorarioDeOtro() throws Exception {
        long idA = json(confirmar(tokenA, lunes, bloque(lunes, "18:00", "20:00", algebra))).get("id").asLong();

        // B no ve el horario de A para la misma semana
        assertThat(consultar(tokenB, lunes).getResponse().getStatus()).isEqualTo(204);
        // B no puede usar una materia de A
        MvcResult conMateriaAjena = confirmar(tokenB, lunes, bloque(lunes, "18:00", "20:00", algebra));
        assertThat(conMateriaAjena.getResponse().getStatus()).isEqualTo(400);
        assertThat(mensaje(conMateriaAjena)).contains("no pertenece");
        // Confirmar su propia semana crea un horario de B y deja intacto el de A
        MvcResult propio = confirmar(tokenB, lunes, bloque(lunes.plusDays(1), "08:00", "09:00", materiaDeB));
        assertThat(propio.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(propio).get("id").asLong()).isNotEqualTo(idA);

        JsonNode deA = json(consultar(tokenA, lunes));
        assertThat(deA.get("id").asLong()).isEqualTo(idA);
        assertThat(deA.get("bloques")).hasSize(1);
        assertThat(deA.get("bloques").get(0).get("materiaInscritaId").asLong()).isEqualTo(algebra.getId());
        assertThat(horarioEstudioRepository.countByEstudianteId(a.getId())).isEqualTo(1);
        assertThat(horarioEstudioRepository.countByEstudianteId(b.getId())).isEqualTo(1);
    }

    @Test
    void sinTokenResponde401() throws Exception {
        assertThat(mockMvc.perform(get(RUTA).param("desde", lunes.toString())).andReturn()
                .getResponse().getStatus()).isEqualTo(401);
        assertThat(mockMvc.perform(put(RUTA).contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn()
                .getResponse().getStatus()).isEqualTo(401);
    }

    // ================= auxiliares

    private Estudiante crearEstudiante() {
        String unico = UUID.randomUUID().toString().substring(0, 8);
        Estudiante nuevo = new Estudiante();
        nuevo.setNombre("Estudiante Horario");
        nuevo.setCarnet("HE" + unico);
        nuevo.setCorreoInstitucional("horario." + unico + "@ues.edu.sv");
        nuevo.setContrasenia(new BCryptPasswordEncoder().encode("Segura123"));
        nuevo.setCuentaActiva(true);
        nuevo.setIntentosFallidos(0);
        return estudianteRepository.save(nuevo);
    }

    private CicloAcademico crearCiclo(Estudiante propietario, EstadoCiclo estado, LocalDate inicio, LocalDate fin) {
        CicloAcademico nuevo = new CicloAcademico();
        nuevo.setEstudiante(propietario);
        nuevo.setNombre("Ciclo de prueba");
        nuevo.setAnio(inicio.getYear());
        nuevo.setFechaInicio(inicio);
        nuevo.setFechaFin(fin);
        nuevo.setEstado(estado);
        return cicloAcademicoRepository.save(nuevo);
    }

    private MateriaInscrita crearMateria(CicloAcademico ciclo, String nombre) {
        MateriaInscrita nueva = new MateriaInscrita();
        nueva.setCicloAcademico(ciclo);
        nueva.setNombre(nombre);
        nueva.setCodigo("COD" + UUID.randomUUID().toString().substring(0, 4));
        nueva.setGrupoTeorico("GT01");
        return materiaInscritaRepository.save(nueva);
    }

    private static Map<String, Object> bloque(LocalDate fecha, String inicio, String fin, MateriaInscrita materia) {
        Map<String, Object> bloque = new LinkedHashMap<>();
        bloque.put("fecha", fecha.toString());
        bloque.put("inicio", inicio);
        bloque.put("fin", fin);
        bloque.put("materiaInscritaId", materia.getId());
        return bloque;
    }

    @SafeVarargs
    private MvcResult confirmar(String token, LocalDate desde, Map<String, Object>... bloques) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("desde", desde.toString());
        cuerpo.put("bloques", new ArrayList<>(List.of(bloques)));
        return mockMvc.perform(put(RUTA)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo))).andReturn();
    }

    private MvcResult consultar(String token, LocalDate desde) throws Exception {
        return mockMvc.perform(get(RUTA).param("desde", desde.toString())
                .header("Authorization", "Bearer " + token)).andReturn();
    }

    private JsonNode json(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String mensaje(MvcResult resultado) throws Exception {
        return json(resultado).get("message").asText();
    }
}
