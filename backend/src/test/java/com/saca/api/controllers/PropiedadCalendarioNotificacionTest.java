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
import com.saca.api.entity.PreferenciaNotificacion;
import com.saca.api.entity.TipoActividadAcademica;
import com.saca.api.repository.ActividadAcademicaRepository;
import com.saca.api.repository.CicloAcademicoRepository;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.repository.HorarioClaseRepository;
import com.saca.api.repository.MateriaInscritaRepository;
import com.saca.api.repository.PreferenciaNotificacionRepository;
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

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

// Etapa 5D: con los filtros de seguridad activos y tokens reales, el calendario, las notificaciones
// próximas y las preferencias solo son accesibles para el estudiante del token
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PropiedadCalendarioNotificacionTest {

    private static final String DATOS_PRIVADOS_A = "Materia privada de A";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private CicloAcademicoRepository cicloRepository;

    @Autowired
    private MateriaInscritaRepository materiaRepository;

    @Autowired
    private HorarioClaseRepository horarioRepository;

    @Autowired
    private ActividadAcademicaRepository actividadRepository;

    @Autowired
    private PreferenciaNotificacionRepository preferenciaRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transaccion;

    @Autowired
    private Clock relojAplicacion;

    private Estudiante a;
    private Estudiante b;
    private MateriaInscrita materiaB;
    private ActividadAcademica actividadA;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void prepararDatos() {
        a = crearEstudiante("Estudiante Uno");
        b = crearEstudiante("Estudiante Dos");
        MateriaInscrita materiaA = crearMateria(crearCiclo(a), DATOS_PRIVADOS_A, "PRIV-A");
        materiaB = crearMateria(crearCiclo(b), "Materia de B", "MAT-B");
        crearHorario(materiaA, "Docente de A");
        actividadA = crearActividad(materiaA, "Entrega privada de A");
        crearPreferencias(a, Set.of(TipoActividadAcademica.PARCIAL));
        tokenA = tokenService.emitir(a).valor();
        tokenB = tokenService.emitir(b).valor();
    }

    private Estudiante crearEstudiante(String nombre) {
        String unico = UUID.randomUUID().toString().substring(0, 8);
        Estudiante nuevo = new Estudiante();
        nuevo.setNombre(nombre);
        nuevo.setCarnet("CN" + unico);
        nuevo.setCorreoInstitucional("cal." + unico + "@ues.edu.sv");
        nuevo.setContrasenia(new BCryptPasswordEncoder().encode("Segura123"));
        nuevo.setCuentaActiva(true);
        nuevo.setIntentosFallidos(0);
        return estudianteRepository.save(nuevo);
    }

    private CicloAcademico crearCiclo(Estudiante propietario) {
        CicloAcademico nuevo = new CicloAcademico();
        nuevo.setEstudiante(propietario);
        nuevo.setNombre("Ciclo activo");
        nuevo.setAnio(2026);
        nuevo.setFechaInicio(LocalDate.of(2026, 1, 1));
        nuevo.setFechaFin(LocalDate.of(2026, 12, 31));
        nuevo.setEstado(EstadoCiclo.ACTIVO);
        return cicloRepository.save(nuevo);
    }

    private MateriaInscrita crearMateria(CicloAcademico ciclo, String nombre, String codigo) {
        MateriaInscrita nueva = new MateriaInscrita();
        nueva.setCicloAcademico(ciclo);
        nueva.setNombre(nombre);
        nueva.setCodigo(codigo);
        nueva.setGrupoTeorico("GT01");
        return materiaRepository.save(nueva);
    }

    private void crearHorario(MateriaInscrita materia, String docente) {
        HorarioClase nuevo = new HorarioClase();
        nuevo.setMateriaInscrita(materia);
        nuevo.setDiaSemana(DiaSemana.LUNES);
        nuevo.setHoraInicio(LocalTime.parse("08:00"));
        nuevo.setHoraFin(LocalTime.parse("10:00"));
        nuevo.setModalidad(ModalidadClase.PRESENCIAL);
        nuevo.setDocenteTutor(docente);
        horarioRepository.save(nuevo);
    }

    // Vence dentro de la ventana de 8 días que usa NotificacionService, con el reloj de la aplicación
    private ActividadAcademica crearActividad(MateriaInscrita materia, String nombre) {
        ActividadAcademica nueva = new ActividadAcademica();
        nueva.setNombre(nombre);
        nueva.setFechaInicio(LocalDateTime.now(relojAplicacion));
        nueva.setFechaEntrega(LocalDate.now(relojAplicacion).plusDays(2).atTime(10, 0));
        nueva.setPorcentajeEvaluacion(20);
        nueva.setTiempoEstimadoHoras(4);
        nueva.setEstado("ACTIVO");
        nueva.setTipoActividad(TipoActividadAcademica.PARCIAL);
        nueva.setMateriaInscrita(materia);
        return actividadRepository.save(nueva);
    }

    private void crearPreferencias(Estudiante estudiante, Set<TipoActividadAcademica> tipos) {
        PreferenciaNotificacion preferencia = new PreferenciaNotificacion();
        preferencia.setEstudiante(estudiante);
        preferencia.setTiposActividadHabilitados(new HashSet<>(tipos));
        preferenciaRepository.save(preferencia);
    }

    private MvcResult como(String token, MockHttpServletRequestBuilder peticion) throws Exception {
        return mockMvc.perform(peticion.header("Authorization", "Bearer " + token)).andReturn();
    }

    private MvcResult como(String token, MockHttpServletRequestBuilder peticion, String cuerpo) throws Exception {
        return como(token, peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private JsonNode json(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString());
    }

    private static int estado(MvcResult resultado) {
        return resultado.getResponse().getStatus();
    }

    private void assertSinDatosDeA(MvcResult resultado) throws Exception {
        assertThat(resultado.getResponse().getContentAsString())
                .doesNotContain(DATOS_PRIVADOS_A, "PRIV-A", "Docente de A", "Entrega privada de A");
    }

    private List<String> tiposHabilitadosEnBaseDeDatos(Estudiante estudiante) {
        return transaccion.execute(estado -> preferenciaRepository.findByEstudianteId(estudiante.getId())
                .map(p -> p.getTiposActividadHabilitados().stream().map(Enum::name).sorted().toList())
                .orElse(List.of()));
    }

    // ================= Operaciones legítimas de A

    @Test
    void aConsultaSuCalendarioSusNotificacionesYSusPreferencias() throws Exception {
        MvcResult calendario = como(tokenA, get("/api/calendario/semanal/" + a.getId()));
        assertThat(estado(calendario)).isEqualTo(200);
        assertThat(json(calendario).get(0).get("materia").asText()).isEqualTo(DATOS_PRIVADOS_A);
        assertThat(json(calendario).get(0).get("docenteTutor").asText()).isEqualTo("Docente de A");

        MvcResult proximas = como(tokenA, get("/api/notificaciones/estudiante/" + a.getId() + "/proximas"));
        assertThat(estado(proximas)).isEqualTo(200);
        List<Long> ids = new ArrayList<>();
        json(proximas).forEach(n -> ids.add(n.get("idActividad").asLong()));
        assertThat(ids).containsExactly(actividadA.getIdActividad());

        MvcResult preferencias = como(tokenA, get("/api/notificaciones/preferencias/" + a.getId()));
        assertThat(estado(preferencias)).isEqualTo(200);
        assertThat(json(preferencias).get("estudianteId").asLong()).isEqualTo(a.getId());
        assertThat(json(preferencias).get("tiposActividadHabilitados").toString()).isEqualTo("[\"PARCIAL\"]");

        MvcResult guardadas = como(tokenA, put("/api/notificaciones/preferencias/" + a.getId()),
                "{\"tiposActividadHabilitados\":[\"PROYECTO\",\"PARCIAL\"]}");
        assertThat(estado(guardadas)).isEqualTo(200);
        assertThat(tiposHabilitadosEnBaseDeDatos(a)).containsExactly("PARCIAL", "PROYECTO");
    }

    // ================= B con el estudianteId de A: todas las rutas responden 404

    @Test
    void bNoPuedeConsultarNiModificarNadaDeAConSuId() throws Exception {
        List<MvcResult> intentos = List.of(
                como(tokenB, get("/api/calendario/semanal/" + a.getId())),
                como(tokenB, get("/api/notificaciones/estudiante/" + a.getId() + "/proximas")),
                como(tokenB, get("/api/notificaciones/preferencias/" + a.getId())),
                como(tokenB, put("/api/notificaciones/preferencias/" + a.getId()), "{\"tiposActividadHabilitados\":[]}"));

        for (MvcResult intento : intentos) {
            assertThat(estado(intento)).as(intento.getRequest().getMethod() + " " + intento.getRequest().getRequestURI()).isEqualTo(404);
            assertSinDatosDeA(intento);
        }
        assertThat(tiposHabilitadosEnBaseDeDatos(a)).containsExactly("PARCIAL");
    }

    @Test
    void unEstudianteIdInexistenteDistintoDelTokenTambienResponde404() throws Exception {
        assertThat(estado(como(tokenB, get("/api/calendario/semanal/987654321")))).isEqualTo(404);
        assertThat(estado(como(tokenB, get("/api/notificaciones/preferencias/987654321")))).isEqualTo(404);
        assertThat(estado(como(tokenB, put("/api/notificaciones/preferencias/987654321"),
                "{\"tiposActividadHabilitados\":[\"PARCIAL\"]}"))).isEqualTo(404);
        assertThat(preferenciaRepository.findByEstudianteId(987654321L)).isEmpty();
    }

    // ================= Listados de B aislados

    @Test
    void elCalendarioYLasNotificacionesDeBNoIncluyenNadaDeA() throws Exception {
        // B habilita todos los tipos: aun así solo puede recibir sus propias actividades
        como(tokenB, put("/api/notificaciones/preferencias/" + b.getId()),
                "{\"tiposActividadHabilitados\":[\"PARCIAL\",\"LABORATORIO\",\"PROYECTO\",\"INVESTIGACION\"]}");
        ActividadAcademica actividadB = crearActividad(materiaB, "Entrega de B");

        MvcResult calendario = como(tokenB, get("/api/calendario/semanal/" + b.getId()));
        MvcResult proximas = como(tokenB, get("/api/notificaciones/estudiante/" + b.getId() + "/proximas"));

        assertThat(estado(calendario)).isEqualTo(200);
        assertThat(json(calendario)).isEmpty();
        assertThat(estado(proximas)).isEqualTo(200);
        List<Long> ids = new ArrayList<>();
        json(proximas).forEach(n -> ids.add(n.get("idActividad").asLong()));
        assertThat(ids).containsExactly(actividadB.getIdActividad());
        assertSinDatosDeA(calendario);
        assertSinDatosDeA(proximas);
    }

    // ================= Preferencias: el cuerpo no permite eludir la propiedad

    @Test
    void lasPreferenciasDeBSonIndependientesYUnEstudianteIdEnElCuerpoNoTieneEfecto() throws Exception {
        MvcResult guardadas = como(tokenB, put("/api/notificaciones/preferencias/" + b.getId()),
                "{\"estudianteId\":%d,\"tiposActividadHabilitados\":[\"LABORATORIO\"]}".formatted(a.getId()));

        assertThat(estado(guardadas)).isEqualTo(200);
        assertThat(json(guardadas).get("estudianteId").asLong()).isEqualTo(b.getId());
        assertThat(tiposHabilitadosEnBaseDeDatos(b)).containsExactly("LABORATORIO");
        assertThat(tiposHabilitadosEnBaseDeDatos(a)).containsExactly("PARCIAL");
    }

    @Test
    void lasValidacionesDePreferenciasSeConservan() throws Exception {
        MvcResult sinTipos = como(tokenA, put("/api/notificaciones/preferencias/" + a.getId()), "{}");

        assertThat(estado(sinTipos)).isEqualTo(400);
        assertThat(json(sinTipos).get("message").asText())
                .isEqualTo("tiposActividadHabilitados: Debe indicar los tipos de actividad para las notificaciones");
        assertThat(tiposHabilitadosEnBaseDeDatos(a)).containsExactly("PARCIAL");
    }
}
