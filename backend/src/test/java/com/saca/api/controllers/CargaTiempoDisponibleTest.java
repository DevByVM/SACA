package com.saca.api.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saca.api.entity.CicloAcademico;
import com.saca.api.entity.DiaSemana;
import com.saca.api.entity.DisponibilidadSemanal;
import com.saca.api.entity.EstadoCiclo;
import com.saca.api.entity.Estudiante;
import com.saca.api.entity.HorarioClase;
import com.saca.api.entity.JornadaLaboral;
import com.saca.api.entity.MateriaInscrita;
import com.saca.api.entity.ModalidadClase;
import com.saca.api.repository.CicloAcademicoRepository;
import com.saca.api.repository.DisponibilidadSemanalRepository;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.repository.HorarioClaseRepository;
import com.saca.api.repository.JornadaLaboralRepository;
import com.saca.api.repository.MateriaInscritaRepository;
import com.saca.api.service.TiempoDisponibleService;
import com.saca.api.service.TiempoDisponibleService.Resultado;
import com.saca.api.service.TokenService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// SDACAS-32 (E4.2): GET /api/carga/tiempo-disponible con el estudiante del token. Las fechas se construyen
// a partir del día real porque el reloj de la aplicación también lo usa la emisión de tokens.
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CargaTiempoDisponibleTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private CicloAcademicoRepository cicloAcademicoRepository;

    @Autowired
    private MateriaInscritaRepository materiaInscritaRepository;

    @Autowired
    private HorarioClaseRepository horarioRepository;

    @Autowired
    private DisponibilidadSemanalRepository disponibilidadRepository;

    @Autowired
    private JornadaLaboralRepository jornadaRepository;

    @Autowired
    private TiempoDisponibleService tiempoDisponibleService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private Clock relojAplicacion;

    private LocalDate hoy;
    private LocalDate lunesProximo;
    private Estudiante a;
    private String tokenA;
    private String tokenB;
    private String tokenSinCicloActivo;
    private String tokenSinEstudio;

    @BeforeEach
    void prepararDatos() {
        hoy = LocalDate.now(relojAplicacion);
        lunesProximo = hoy.with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        // A: dos ciclos activos y uno finalizado
        a = crearEstudiante();
        CicloAcademico activoA = crearCiclo(a, EstadoCiclo.ACTIVO, hoy.minusDays(60), hoy.plusDays(60));
        CicloAcademico activoB = crearCiclo(a, EstadoCiclo.ACTIVO, hoy.minusDays(30), hoy.plusDays(90));
        CicloAcademico finalizado = crearCiclo(a, EstadoCiclo.FINALIZADO, hoy.minusDays(60), hoy.plusDays(60));
        MateriaInscrita materiaB = crearMateria(activoB, "MAT215");
        MateriaInscrita materiaFinalizada = crearMateria(finalizado, "FIN101");

        // Martes: ESTUDIO 08-12, clase de otro ciclo activo 09-10 y jornada 09:30-11 -> 4 h, 2 h, 2 h
        bloque(a, "ESTUDIO", "MARTES", "08:00", "12:00", activoA);
        clase(materiaB, DiaSemana.MARTES, "09:00", "10:00");
        jornada(a, "Martes", "09:30", "11:00");
        // Miércoles: ESTUDIO 08-10 y NO_DISPONIBLE de otro ciclo activo 08-09 -> 2 h, 1 h, 1 h
        bloque(a, "ESTUDIO", "MIERCOLES", "08:00", "10:00", activoA);
        bloque(a, "NO_DISPONIBLE", "MIERCOLES", "08:00", "09:00", activoB);
        // Jueves: los bloques CLASE y LIBRE manuales no descuentan -> 2 h, 0, 2 h
        bloque(a, "ESTUDIO", "JUEVES", "08:00", "10:00", activoA);
        bloque(a, "CLASE", "JUEVES", "08:00", "10:00", activoB);
        bloque(a, "LIBRE", "JUEVES", "09:00", "10:00", activoB);
        // Viernes: el ciclo finalizado no reserva ni descuenta -> 1 h, 0, 1 h
        bloque(a, "ESTUDIO", "VIERNES", "08:00", "10:00", finalizado);
        bloque(a, "ESTUDIO", "VIERNES", "14:00", "15:00", activoA);
        clase(materiaFinalizada, DiaSemana.VIERNES, "14:00", "15:00");
        // Sábado: ESTUDIO solapados de dos ciclos activos se unen -> 3 h, 0, 3 h
        bloque(a, "ESTUDIO", "SABADO", "10:00", "12:00", activoB);
        bloque(a, "ESTUDIO", "SABADO", "11:00", "13:00", activoA);

        // B: otro estudiante con su propio ciclo, bloque y jornada el martes
        Estudiante b = crearEstudiante();
        CicloAcademico cicloB = crearCiclo(b, EstadoCiclo.ACTIVO, hoy.minusDays(60), hoy.plusDays(60));
        bloque(b, "ESTUDIO", "MARTES", "14:00", "16:00", cicloB);
        jornada(b, "Martes", "08:00", "17:00");

        // C: solo un ciclo finalizado (con su bloque) y una jornada
        Estudiante c = crearEstudiante();
        CicloAcademico finalizadoC = crearCiclo(c, EstadoCiclo.FINALIZADO, hoy.minusDays(60), hoy.plusDays(60));
        bloque(c, "ESTUDIO", "MARTES", "08:00", "12:00", finalizadoC);
        jornada(c, "Lunes", "08:00", "12:00");

        // D: ciclo activo con restricciones, pero sin bloques ESTUDIO
        Estudiante d = crearEstudiante();
        CicloAcademico cicloD = crearCiclo(d, EstadoCiclo.ACTIVO, hoy.minusDays(60), hoy.plusDays(60));
        clase(crearMateria(cicloD, "PRN315"), DiaSemana.LUNES, "08:00", "10:00");
        bloque(d, "NO_DISPONIBLE", "MARTES", "08:00", "10:00", cicloD);
        jornada(d, "Miércoles", "08:00", "17:00");

        tokenA = tokenService.emitir(a).valor();
        tokenB = tokenService.emitir(b).valor();
        tokenSinCicloActivo = tokenService.emitir(c).valor();
        tokenSinEstudio = tokenService.emitir(d).valor();
    }

    private Estudiante crearEstudiante() {
        String unico = UUID.randomUUID().toString().substring(0, 8);
        Estudiante nuevo = new Estudiante();
        nuevo.setNombre("Estudiante Tiempo");
        nuevo.setCarnet("TD" + unico);
        nuevo.setCorreoInstitucional("tiempo." + unico + "@ues.edu.sv");
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

    private MateriaInscrita crearMateria(CicloAcademico ciclo, String codigo) {
        MateriaInscrita nueva = new MateriaInscrita();
        nueva.setCicloAcademico(ciclo);
        nueva.setNombre("Materia " + codigo);
        nueva.setCodigo(codigo);
        nueva.setGrupoTeorico("GT01");
        return materiaInscritaRepository.save(nueva);
    }

    // Se guardan con el repositorio para poder preparar solapes que la API rechazaría dentro de un mismo ciclo
    private void bloque(Estudiante propietario, String tipo, String dia, String inicio, String fin, CicloAcademico ciclo) {
        disponibilidadRepository.save(new DisponibilidadSemanal(dia, LocalTime.parse(inicio), LocalTime.parse(fin), tipo, ciclo.getId(), propietario.getId()));
    }

    private void clase(MateriaInscrita materia, DiaSemana dia, String inicio, String fin) {
        HorarioClase nuevo = new HorarioClase();
        nuevo.setMateriaInscrita(materia);
        nuevo.setDiaSemana(dia);
        nuevo.setHoraInicio(LocalTime.parse(inicio));
        nuevo.setHoraFin(LocalTime.parse(fin));
        nuevo.setModalidad(ModalidadClase.PRESENCIAL);
        horarioRepository.save(nuevo);
    }

    private void jornada(Estudiante propietario, String dia, String inicio, String fin) {
        jornadaRepository.save(new JornadaLaboral(dia, LocalTime.parse(inicio), LocalTime.parse(fin), propietario.getId()));
    }

    private MvcResult consultar(String token, Object desde, Object hasta) throws Exception {
        return mockMvc.perform(get("/api/carga/tiempo-disponible")
                .param("desde", String.valueOf(desde)).param("hasta", String.valueOf(hasta))
                .header("Authorization", "Bearer " + token)).andReturn();
    }

    private JsonNode json(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static int[] totales(JsonNode cuerpo) {
        return new int[] {cuerpo.get("minutosReservados").asInt(), cuerpo.get("minutosDescontados").asInt(), cuerpo.get("minutosDisponibles").asInt()};
    }

    private static int[] totalesDia(JsonNode dia) {
        return new int[] {dia.get("minutosReservados").asInt(), dia.get("minutosDescontados").asInt(), dia.get("minutosDisponibles").asInt()};
    }

    // El mismo cálculo de E4.1 con los datos del estudiante, desde el instante que informa la respuesta
    private Resultado calculoDirecto(Long estudianteId, LocalDateTime desde, LocalDate hasta) {
        return tiempoDisponibleService.calcular(desde, hasta.atTime(LocalTime.MAX),
                disponibilidadRepository.findByEstudianteId(estudianteId),
                cicloAcademicoRepository.findByEstudianteIdAndEstadoOrderByAnioDescFechaInicioDesc(estudianteId, EstadoCiclo.ACTIVO),
                horarioRepository.findParaCalendario(estudianteId),
                jornadaRepository.findByEstudianteId(estudianteId));
    }

    // ================= seguridad y rango

    @Test
    void sinTokenResponde401() throws Exception {
        assertThat(mockMvc.perform(get("/api/carga/tiempo-disponible").param("desde", lunesProximo.toString())
                .param("hasta", lunesProximo.plusDays(6).toString())).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void validaElRangoConLasMismasReglasYMensajesQueElCalendario() throws Exception {
        MvcResult invertido = consultar(tokenA, lunesProximo.plusDays(6), lunesProximo);
        MvcResult largo = consultar(tokenA, lunesProximo, lunesProximo.plusDays(42));
        MvcResult justo = consultar(tokenA, lunesProximo, lunesProximo.plusDays(41));
        MvcResult unDia = consultar(tokenA, lunesProximo, lunesProximo);
        MvcResult sinHasta = mockMvc.perform(get("/api/carga/tiempo-disponible").param("desde", lunesProximo.toString())
                .header("Authorization", "Bearer " + tokenA)).andReturn();
        MvcResult formatoInvalido = consultar(tokenA, "02/11/2026", lunesProximo);

        assertThat(invertido.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(invertido).get("message").asText()).isEqualTo("La fecha inicial no puede ser posterior a la fecha final");
        assertThat(largo.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(largo).get("message").asText()).isEqualTo("El periodo no puede superar 42 días");
        assertThat(justo.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(justo).get("dias")).hasSize(42);
        assertThat(json(unDia).get("dias")).hasSize(1);
        assertThat(sinHasta.getResponse().getStatus()).isEqualTo(400);
        assertThat(formatoInvalido.getResponse().getStatus()).isEqualTo(400);
    }

    // ================= cálculo con datos reales

    @Test
    void unaSemanaFuturaSeCalculaCompletaConSuDesgloseDiario() throws Exception {
        MvcResult resultado = consultar(tokenA, lunesProximo, lunesProximo.plusDays(6));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        JsonNode cuerpo = json(resultado);
        assertThat(cuerpo.get("desde").asText()).isEqualTo(lunesProximo.toString());
        assertThat(cuerpo.get("hasta").asText()).isEqualTo(lunesProximo.plusDays(6).toString());
        assertThat(LocalDateTime.parse(cuerpo.get("calculadoDesde").asText())).isEqualTo(lunesProximo.atStartOfDay());

        JsonNode dias = cuerpo.get("dias");
        assertThat(dias).hasSize(7);
        assertThat(dias.get(0).get("fecha").asText()).isEqualTo(lunesProximo.toString());
        assertThat(totalesDia(dias.get(0))).containsExactly(0, 0, 0);
        // Clase de otro ciclo activo y jornada simultáneas: 09:00-11:00 se descuenta una sola vez
        assertThat(totalesDia(dias.get(1))).containsExactly(240, 120, 120);
        assertThat(totalesDia(dias.get(2))).containsExactly(120, 60, 60);
        assertThat(totalesDia(dias.get(3))).containsExactly(120, 0, 120);
        assertThat(totalesDia(dias.get(4))).containsExactly(60, 0, 60);
        assertThat(totalesDia(dias.get(5))).containsExactly(180, 0, 180);
        assertThat(totalesDia(dias.get(6))).containsExactly(0, 0, 0);
        assertThat(totales(cuerpo)).containsExactly(720, 180, 540);
    }

    @Test
    void elEndpointDevuelveLoMismoQueElCalculoDelServicio() throws Exception {
        JsonNode cuerpo = json(consultar(tokenA, lunesProximo, lunesProximo.plusDays(13)));
        Resultado directo = calculoDirecto(a.getId(), lunesProximo.atStartOfDay(), lunesProximo.plusDays(13));

        assertThat(totales(cuerpo)).containsExactly(directo.minutosReservados(), directo.minutosDescontados(), directo.minutosDisponibles());
        assertThat(cuerpo.get("dias")).hasSize(directo.dias().size());
        for (int i = 0; i < directo.dias().size(); i++) {
            assertThat(cuerpo.get("dias").get(i).get("fecha").asText()).isEqualTo(directo.dias().get(i).fecha().toString());
            assertThat(totalesDia(cuerpo.get("dias").get(i))).containsExactly(directo.dias().get(i).minutosReservados(),
                    directo.dias().get(i).minutosDescontados(), directo.dias().get(i).minutosDisponibles());
        }
        // Dos semanas con las mismas franjas semanales
        assertThat(totales(cuerpo)).containsExactly(1440, 360, 1080);
    }

    @Test
    void laSemanaActualEmpiezaEnElMomentoDeLaConsultaSinTiempoPasado() throws Exception {
        LocalDateTime antes = LocalDateTime.now(relojAplicacion);
        JsonNode cuerpo = json(consultar(tokenA, hoy.minusDays(3), hoy.plusDays(3)));
        LocalDateTime despues = LocalDateTime.now(relojAplicacion);

        LocalDateTime calculadoDesde = LocalDateTime.parse(cuerpo.get("calculadoDesde").asText());
        assertThat(calculadoDesde).isBetween(antes, despues);
        // Los tres días anteriores no aparecen: el desglose empieza hoy
        assertThat(cuerpo.get("dias")).hasSize(4);
        assertThat(cuerpo.get("dias").get(0).get("fecha").asText()).isEqualTo(hoy.toString());

        Resultado directo = calculoDirecto(a.getId(), calculadoDesde, hoy.plusDays(3));
        assertThat(totales(cuerpo)).containsExactly(directo.minutosReservados(), directo.minutosDescontados(), directo.minutosDisponibles());
        assertThat(cuerpo.get("minutosReservados").asInt()).isEqualTo(cuerpo.get("minutosDisponibles").asInt() + cuerpo.get("minutosDescontados").asInt());
    }

    @Test
    void unPeriodoYaPasadoNoTieneTiempoDisponible() throws Exception {
        JsonNode cuerpo = json(consultar(tokenA, hoy.minusDays(10), hoy.minusDays(4)));

        assertThat(cuerpo.get("dias")).isEmpty();
        assertThat(totales(cuerpo)).containsExactly(0, 0, 0);
    }

    // ================= estudiantes sin datos y aislamiento

    @Test
    void sinCicloActivoNoHayTiempoReservadoAunqueHayaJornadaYBloquesDeCiclosFinalizados() throws Exception {
        JsonNode cuerpo = json(consultar(tokenSinCicloActivo, lunesProximo, lunesProximo.plusDays(6)));

        assertThat(cuerpo.get("dias")).hasSize(7);
        assertThat(totales(cuerpo)).containsExactly(0, 0, 0);
    }

    @Test
    void sinBloquesEstudioLasRestriccionesNoProducenValoresNegativos() throws Exception {
        JsonNode cuerpo = json(consultar(tokenSinEstudio, lunesProximo, lunesProximo.plusDays(6)));

        assertThat(totales(cuerpo)).containsExactly(0, 0, 0);
        for (JsonNode dia : cuerpo.get("dias")) {
            assertThat(totalesDia(dia)).containsExactly(0, 0, 0);
        }
    }

    @Test
    void cadaEstudianteSoloObtieneSuPropioTiempo() throws Exception {
        JsonNode deA = json(consultar(tokenA, lunesProximo, lunesProximo.plusDays(6)));
        JsonNode deB = json(consultar(tokenB, lunesProximo, lunesProximo.plusDays(6)));

        // B: su bloque del martes 14-16 queda cubierto por su jornada; no ve los bloques ni las clases de A
        assertThat(totales(deB)).containsExactly(120, 120, 0);
        assertThat(totalesDia(deB.get("dias").get(1))).containsExactly(120, 120, 0);
        assertThat(totalesDia(deB.get("dias").get(5))).containsExactly(0, 0, 0);
        // A no se ve afectado por la jornada de 08:00-17:00 de B
        assertThat(totales(deA)).containsExactly(720, 180, 540);
    }

    @Test
    void elPeriodoMaximoSeResuelveConCuatroConsultas() throws Exception {
        Statistics estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estadisticas.clear();

        MvcResult resultado = consultar(tokenA, lunesProximo, lunesProximo.plusDays(41));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        // Bloques, ciclos activos, horarios (con materia y ciclo) y jornadas; no depende del número de días
        assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(4);
    }
}
