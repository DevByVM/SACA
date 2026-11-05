package com.saca.api.service;

import com.saca.api.entity.CicloAcademico;
import com.saca.api.entity.DiaSemana;
import com.saca.api.entity.DisponibilidadSemanal;
import com.saca.api.entity.EstadoCiclo;
import com.saca.api.entity.HorarioClase;
import com.saca.api.entity.JornadaLaboral;
import com.saca.api.entity.MateriaInscrita;
import com.saca.api.service.TiempoDisponibleService.Dia;
import com.saca.api.service.TiempoDisponibleService.Resultado;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

// Horas de estudio disponibles = bloques ESTUDIO menos su solape con clases, jornadas y
// bloques NO_DISPONIBLE, sin contar dos veces un mismo minuto. Los casos se numeran (caso01, caso02...) y se agrupan por tema.
class TiempoDisponibleCalculoTest {

    // Semana del lunes 2 al domingo 8 de noviembre de 2026
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 2);

    private final TiempoDisponibleService servicio = new TiempoDisponibleService();
    private long siguienteId = 1;

    private final CicloAcademico activo = ciclo(EstadoCiclo.ACTIVO, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 15));
    private final CicloAcademico otroActivo = ciclo(EstadoCiclo.ACTIVO, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 20));
    private final CicloAcademico finalizado = ciclo(EstadoCiclo.FINALIZADO, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 15));

    private CicloAcademico ciclo(EstadoCiclo estado, LocalDate inicio, LocalDate fin) {
        CicloAcademico ciclo = new CicloAcademico();
        ciclo.setId(siguienteId++);
        ciclo.setEstado(estado);
        ciclo.setFechaInicio(inicio);
        ciclo.setFechaFin(fin);
        return ciclo;
    }

    private DisponibilidadSemanal bloque(String tipo, String dia, String inicio, String fin, CicloAcademico ciclo) {
        DisponibilidadSemanal bloque = new DisponibilidadSemanal(dia, LocalTime.parse(inicio), LocalTime.parse(fin), tipo, ciclo.getId(), 1L);
        bloque.setId(siguienteId++);
        return bloque;
    }

    private DisponibilidadSemanal estudio(String dia, String inicio, String fin) {
        return bloque("ESTUDIO", dia, inicio, fin, activo);
    }

    private HorarioClase clase(DiaSemana dia, String inicio, String fin, CicloAcademico ciclo) {
        MateriaInscrita materia = new MateriaInscrita();
        materia.setId(siguienteId++);
        materia.setCicloAcademico(ciclo);
        HorarioClase horario = new HorarioClase();
        horario.setId(siguienteId++);
        horario.setMateriaInscrita(materia);
        horario.setDiaSemana(dia);
        horario.setHoraInicio(LocalTime.parse(inicio));
        horario.setHoraFin(LocalTime.parse(fin));
        return horario;
    }

    private HorarioClase clase(DiaSemana dia, String inicio, String fin) {
        return clase(dia, inicio, fin, activo);
    }

    private JornadaLaboral jornada(String dia, String inicio, String fin) {
        JornadaLaboral jornada = new JornadaLaboral(dia, LocalTime.parse(inicio), LocalTime.parse(fin), 1L);
        jornada.setId(siguienteId++);
        return jornada;
    }

    private Resultado semanaDel(LocalDate lunes, List<DisponibilidadSemanal> bloques, List<CicloAcademico> ciclos,
            List<HorarioClase> horarios, List<JornadaLaboral> jornadas) {
        Resultado resultado = servicio.calcular(lunes.atStartOfDay(), lunes.plusDays(6).atTime(LocalTime.MAX),
                bloques, ciclos, horarios, jornadas);
        verificarInvariantes(resultado);
        return resultado;
    }

    private Resultado semana(List<DisponibilidadSemanal> bloques, List<HorarioClase> horarios, List<JornadaLaboral> jornadas) {
        return semanaDel(LUNES, bloques, List.of(activo, otroActivo, finalizado), horarios, jornadas);
    }

    // Reservado = disponible + descontado; nada negativo; lo descontado nunca supera lo reservado
    private static void verificarInvariantes(Resultado resultado) {
        for (Dia dia : resultado.dias()) {
            assertThat(dia.minutosDisponibles()).isNotNegative();
            assertThat(dia.minutosDescontados()).isNotNegative().isLessThanOrEqualTo(dia.minutosReservados());
            assertThat(dia.minutosReservados()).isEqualTo(dia.minutosDisponibles() + dia.minutosDescontados())
                    .isLessThanOrEqualTo(IntervalosMinutos.MINUTOS_DIA);
        }
        assertThat(resultado.minutosReservados()).isEqualTo(resultado.dias().stream().mapToInt(Dia::minutosReservados).sum());
        assertThat(resultado.minutosDescontados()).isEqualTo(resultado.dias().stream().mapToInt(Dia::minutosDescontados).sum());
        assertThat(resultado.minutosReservados()).isEqualTo(resultado.minutosDisponibles() + resultado.minutosDescontados());
    }

    private static void totales(Resultado resultado, int reservados, int descontados, int disponibles) {
        assertThat(resultado.minutosReservados()).as("reservados").isEqualTo(reservados);
        assertThat(resultado.minutosDescontados()).as("descontados").isEqualTo(descontados);
        assertThat(resultado.minutosDisponibles()).as("disponibles").isEqualTo(disponibles);
    }

    private static Dia dia(Resultado resultado, LocalDate fecha) {
        return resultado.dias().stream().filter(d -> d.fecha().equals(fecha)).findFirst().orElseThrow();
    }

    // ================= casos 1 a 7: solapes con clases y jornadas

    @Test
    void caso01_unBloqueSinConflictosQuedaDisponibleCompleto() {
        Resultado r = semana(List.of(estudio("LUNES", "18:00", "20:00")), List.of(), List.of());

        totales(r, 120, 0, 120);
        assertThat(dia(r, LUNES).minutosDisponibles()).isEqualTo(120);
        assertThat(r.dias()).hasSize(7);
    }

    @Test
    void caso02_unaClaseQueCoincideEnParteSoloDescuentaElTramoComun() {
        totales(semana(List.of(estudio("MARTES", "08:00", "12:00")), List.of(clase(DiaSemana.MARTES, "11:00", "13:00")), List.of()),
                240, 60, 180);
    }

    @Test
    void caso03_unaClaseQueCubreTodoElBloqueDejaCeroYNoUnValorNegativo() {
        totales(semana(List.of(estudio("MARTES", "09:00", "10:00")), List.of(clase(DiaSemana.MARTES, "08:00", "11:00")), List.of()),
                60, 60, 0);
    }

    @Test
    void caso04_unaJornadaQueCoincideEnParteSoloDescuentaElTramoComun() {
        totales(semana(List.of(estudio("JUEVES", "14:00", "18:00")), List.of(), List.of(jornada("Jueves", "16:00", "20:00"))),
                240, 120, 120);
    }

    @Test
    void caso05_unaJornadaQueCubreTodoElBloqueDejaCero() {
        totales(semana(List.of(estudio("JUEVES", "09:00", "11:00")), List.of(), List.of(jornada("Jueves", "08:00", "17:00"))),
                120, 120, 0);
    }

    @Test
    void caso06_claseYJornadaSimultaneasNoDescuentanDosVecesElTramoComun() {
        Resultado r = semana(
                List.of(estudio("VIERNES", "08:00", "12:00")),
                List.of(clase(DiaSemana.VIERNES, "09:00", "10:00")),
                List.of(jornada("Viernes", "09:30", "11:00")));

        // Las restricciones cubren 09:00-11:00 en conjunto: 2 h, no 2 h 30 min
        totales(r, 240, 120, 120);
    }

    @Test
    void caso07_dosClasesEnPartesDistintasDelMismoBloqueDescuentanAmbosTramos() {
        totales(semana(List.of(estudio("LUNES", "08:00", "14:00")),
                List.of(clase(DiaSemana.LUNES, "08:00", "09:00"), clase(DiaSemana.LUNES, "12:00", "13:00")), List.of()),
                360, 120, 240);
    }

    // ================= casos 8 a 11: uniones, contacto y medianoche

    @Test
    void caso08_bloquesEstudioSuperpuestosDeDosCiclosActivosSeUnifican() {
        totales(semana(List.of(
                        bloque("ESTUDIO", "MIERCOLES", "08:00", "11:00", activo),
                        bloque("ESTUDIO", "MIERCOLES", "10:00", "12:00", otroActivo)),
                List.of(), List.of()), 240, 0, 240);
    }

    @Test
    void caso09_bloquesConsecutivosSumanSinHuecoNiDobleConteo() {
        totales(semana(List.of(estudio("MIERCOLES", "08:00", "10:00"), estudio("MIERCOLES", "10:00", "12:00")), List.of(), List.of()),
                240, 0, 240);
    }

    @Test
    void caso10_unaClaseQueTerminaCuandoEmpiezaElBloqueNoDescuentaNada() {
        totales(semana(List.of(estudio("LUNES", "08:40", "10:00")), List.of(clase(DiaSemana.LUNES, "07:00", "08:40")),
                List.of(jornada("Lunes", "10:00", "12:00"))), 80, 0, 80);
    }

    // El modelo no admite bloques que crucen la medianoche (inicio < fin en los tres módulos). Equivalente:
    // un registro invertido no aporta minutos y el mismo tiempo partido en dos días cuenta por separado
    @Test
    void caso11_sinBloquesQueCrucenLaMedianocheCadaDiaSeCalculaPorSeparado() {
        DisponibilidadSemanal invertido = estudio("LUNES", "08:00", "09:00");
        invertido.setHoraInicio(LocalTime.of(22, 0));
        invertido.setHoraFin(LocalTime.of(2, 0));

        totales(semana(List.of(invertido), List.of(), List.of()), 0, 0, 0);

        Resultado partido = semana(List.of(estudio("LUNES", "22:00", "23:59"), estudio("MARTES", "00:00", "02:00")), List.of(), List.of());
        assertThat(dia(partido, LUNES).minutosReservados()).isEqualTo(119);
        assertThat(dia(partido, LUNES.plusDays(1)).minutosReservados()).isEqualTo(120);
        totales(partido, 239, 0, 239);
    }

    // ================= casos 12 a 16: semanas, ciclos y semana actual

    @Test
    void caso12_unaSemanaSinBloquesEstudioNoTieneTiempoDisponible() {
        Resultado r = semana(List.of(bloque("NO_DISPONIBLE", "LUNES", "08:00", "10:00", activo)),
                List.of(clase(DiaSemana.LUNES, "10:00", "12:00")), List.of(jornada("Martes", "08:00", "17:00")));

        totales(r, 0, 0, 0);
        assertThat(r.dias()).allMatch(d -> d.minutosReservados() == 0);
    }

    @Test
    void caso13_unaSemanaSinClasesNiJornadasConservaTodoLoReservado() {
        totales(semana(List.of(estudio("LUNES", "18:00", "20:00"), estudio("MIERCOLES", "18:00", "20:00"), estudio("SABADO", "09:00", "11:00")),
                List.of(), List.of()), 360, 0, 360);
    }

    @Test
    void caso14_soloCuentanLosDiasDentroDeLasFechasDelCiclo() {
        CicloAcademico terminaMiercoles = ciclo(EstadoCiclo.ACTIVO, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 11, 11));
        LocalDate lunes9 = LocalDate.of(2026, 11, 9);

        Resultado r = semanaDel(lunes9,
                List.of(bloque("ESTUDIO", "LUNES", "18:00", "20:00", terminaMiercoles), bloque("ESTUDIO", "VIERNES", "18:00", "20:00", terminaMiercoles)),
                List.of(terminaMiercoles),
                List.of(clase(DiaSemana.LUNES, "18:30", "19:00", terminaMiercoles), clase(DiaSemana.VIERNES, "18:00", "20:00", terminaMiercoles)),
                List.of());

        totales(r, 120, 30, 90);
        assertThat(dia(r, lunes9.plusDays(4)).minutosReservados()).isZero();
    }

    @Test
    void caso15_losCiclosFinalizadosNoReservanNiDescuentan() {
        Resultado r = semana(
                List.of(bloque("ESTUDIO", "LUNES", "08:00", "10:00", finalizado), estudio("MARTES", "08:00", "10:00"),
                        bloque("NO_DISPONIBLE", "MARTES", "08:00", "09:00", finalizado)),
                List.of(clase(DiaSemana.MARTES, "08:00", "10:00", finalizado)),
                List.of());

        totales(r, 120, 0, 120);
        assertThat(dia(r, LUNES).minutosReservados()).isZero();
    }

    @Test
    void caso15b_unBloqueDeUnCicloQueNoEsDelEstudianteONoExisteSeIgnora() {
        CicloAcademico desconocido = ciclo(EstadoCiclo.ACTIVO, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 15));

        totales(semanaDel(LUNES, List.of(bloque("ESTUDIO", "LUNES", "08:00", "10:00", desconocido)), List.of(activo), List.of(), List.of()),
                0, 0, 0);
    }

    @Test
    void caso16_laSemanaActualSeCalculaDesdeElMomentoPresente() {
        List<DisponibilidadSemanal> bloques = List.of(estudio("LUNES", "08:00", "10:00"), estudio("MIERCOLES", "08:00", "12:00"));
        LocalDateTime domingo = LUNES.plusDays(6).atTime(LocalTime.MAX);

        Resultado desdeLasDiez = servicio.calcular(LocalDateTime.of(2026, 11, 4, 10, 0), domingo, bloques, List.of(activo), List.of(), List.of());
        Resultado conSegundos = servicio.calcular(LocalDateTime.of(2026, 11, 4, 10, 0, 30), domingo, bloques, List.of(activo), List.of(), List.of());
        Resultado trasElBloque = servicio.calcular(LocalDateTime.of(2026, 11, 4, 12, 0), domingo, bloques, List.of(activo), List.of(), List.of());

        verificarInvariantes(desdeLasDiez);
        totales(desdeLasDiez, 120, 0, 120);
        assertThat(desdeLasDiez.dias()).extracting(Dia::fecha).first().isEqualTo(LocalDate.of(2026, 11, 4));
        // El minuto en curso ya empezó: no se cuenta
        totales(conSegundos, 119, 0, 119);
        totales(trasElBloque, 0, 0, 0);
    }

    // ================= casos 17 a 20: datos históricos, formatos, NO_DISPONIBLE y cambio de semana

    @Test
    void caso17_jornadasHistoricasSolapadasNoDescuentanDosVeces() {
        totales(semana(List.of(estudio("MARTES", "08:00", "16:00")), List.of(),
                List.of(jornada("Martes", "08:00", "12:00"), jornada("MARTES", "10:00", "14:00"))), 480, 360, 120);
    }

    @Test
    void caso18_losDiasSeReconocenEnTodosLosFormatosYLosNoValidosSeIgnoran() {
        Resultado r = semana(
                List.of(estudio("miercoles", "08:00", "12:00"), estudio("Lun", "08:00", "10:00")),
                List.of(),
                List.of(jornada("MIÉRCOLES", "08:00", "09:00"), jornada(" Miércoles ", "11:30", "12:00"), jornada("Feriado", "09:00", "12:00")));

        totales(r, 240, 90, 150);
        assertThat(dia(r, LUNES).minutosReservados()).isZero();
    }

    @Test
    void caso19_losBloquesNoDisponibleDescuentanYSeUnenConLasDemasRestricciones() {
        Resultado r = semana(
                List.of(estudio("JUEVES", "08:00", "12:00"),
                        bloque("NO_DISPONIBLE", "JUEVES", "09:00", "10:00", otroActivo),
                        bloque("NO_DISPONIBLE", "JUEVES", "11:00", "12:00", finalizado)),
                List.of(clase(DiaSemana.JUEVES, "09:30", "10:30", otroActivo)),
                List.of());

        // 09:00-10:30 en conjunto; el bloque del ciclo finalizado no cuenta
        totales(r, 240, 90, 150);
    }

    @Test
    void caso20_cadaSemanaSeCalculaSoloConSusFechasAunqueCambieElCiclo() {
        CicloAcademico terminaMiercoles = ciclo(EstadoCiclo.ACTIVO, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 11, 11));
        CicloAcademico empiezaLunes16 = ciclo(EstadoCiclo.ACTIVO, LocalDate.of(2026, 11, 16), LocalDate.of(2027, 3, 1));
        List<DisponibilidadSemanal> bloques = List.of(
                bloque("ESTUDIO", "LUNES", "08:00", "10:00", terminaMiercoles),
                bloque("ESTUDIO", "LUNES", "14:00", "17:00", empiezaLunes16));
        List<CicloAcademico> ciclos = List.of(terminaMiercoles, empiezaLunes16);
        List<JornadaLaboral> jornadas = List.of(jornada("Lunes", "16:00", "18:00"));

        Resultado semana9 = semanaDel(LocalDate.of(2026, 11, 9), bloques, ciclos, List.of(), jornadas);
        Resultado semana16 = semanaDel(LocalDate.of(2026, 11, 16), bloques, ciclos, List.of(), jornadas);

        totales(semana9, 120, 0, 120);
        totales(semana16, 180, 60, 120);
        assertThat(semana9.dias()).extracting(Dia::fecha).allMatch(f -> !f.isBefore(LocalDate.of(2026, 11, 9)) && !f.isAfter(LocalDate.of(2026, 11, 15)));
    }

    // ================= reglas adicionales

    @Test
    void lasRestriccionesFueraDelBloqueYLosBloquesClaseOLibreNoIntervienen() {
        totales(semana(
                        List.of(estudio("SABADO", "09:00", "11:00"),
                                bloque("CLASE", "SABADO", "09:00", "11:00", activo),
                                bloque("LIBRE", "SABADO", "08:00", "12:00", activo)),
                        List.of(clase(DiaSemana.SABADO, "13:00", "15:00")),
                        List.of(jornada("Domingo", "09:00", "11:00"))),
                120, 0, 120);
    }

    @Test
    void elEjemploObligatorioDaDosHorasDisponibles() {
        Resultado r = semana(List.of(estudio("LUNES", "08:00", "12:00")),
                List.of(clase(DiaSemana.LUNES, "09:00", "10:00")),
                List.of(jornada("Lunes", "09:30", "11:00")));

        totales(r, 240, 120, 120);
    }

    @Test
    void unaVentanaInvertidaNoProduceDias() {
        Resultado r = servicio.calcular(LUNES.plusDays(1).atStartOfDay(), LUNES.atStartOfDay(),
                List.of(estudio("LUNES", "08:00", "10:00")), List.of(activo), List.of(), List.of());

        assertThat(r.dias()).isEmpty();
        totales(r, 0, 0, 0);
    }

    @Test
    void elResultadoEsDeterministaYCoincideConUnRecuentoMinutoAMinuto() {
        Random aleatorio = new Random(2026);
        String[] dias = {"LUNES", "MARTES", "MIERCOLES", "JUEVES", "VIERNES", "SABADO", "DOMINGO"};
        String[] tipos = {"ESTUDIO", "ESTUDIO", "NO_DISPONIBLE", "CLASE", "LIBRE"};
        List<CicloAcademico> ciclos = List.of(activo, otroActivo, finalizado);

        for (int iteracion = 0; iteracion < 200; iteracion++) {
            List<DisponibilidadSemanal> bloques = new ArrayList<>();
            List<HorarioClase> horarios = new ArrayList<>();
            List<JornadaLaboral> jornadas = new ArrayList<>();
            for (int k = 0; k < 12; k++) {
                int dia = aleatorio.nextInt(7);
                int inicio = aleatorio.nextInt(23 * 60);
                int fin = inicio + 1 + aleatorio.nextInt(Math.min(300, 24 * 60 - 1 - inicio));
                String hInicio = hora(inicio);
                String hFin = hora(fin);
                switch (aleatorio.nextInt(3)) {
                    case 0 -> bloques.add(bloque(tipos[aleatorio.nextInt(tipos.length)], dias[dia], hInicio, hFin, ciclos.get(aleatorio.nextInt(3))));
                    case 1 -> horarios.add(clase(DiaSemana.values()[dia], hInicio, hFin, ciclos.get(aleatorio.nextInt(3))));
                    default -> jornadas.add(jornada(dias[dia], hInicio, hFin));
                }
            }

            Resultado resultado = semanaDel(LUNES, bloques, ciclos, horarios, jornadas);
            int[] esperado = recuentoMinutoAMinuto(bloques, horarios, jornadas);
            assertThat(resultado.minutosReservados()).isEqualTo(esperado[0]);
            assertThat(resultado.minutosDisponibles()).isEqualTo(esperado[1]);

            Collections.shuffle(bloques, aleatorio);
            Collections.shuffle(horarios, aleatorio);
            Collections.shuffle(jornadas, aleatorio);
            assertThat(semanaDel(LUNES, bloques, ciclos, horarios, jornadas)).isEqualTo(resultado);
        }
    }

    private static String hora(int minuto) {
        return "%02d:%02d".formatted(minuto / 60, minuto % 60);
    }

    // Referencia independiente: marca cada minuto de la semana. Solo valen los ciclos activos (todas sus
    // fechas cubren la semana de prueba) y los días de este test están en el formato del enum.
    private int[] recuentoMinutoAMinuto(List<DisponibilidadSemanal> bloques, List<HorarioClase> horarios, List<JornadaLaboral> jornadas) {
        boolean[][] reservado = new boolean[7][IntervalosMinutos.MINUTOS_DIA];
        boolean[][] restringido = new boolean[7][IntervalosMinutos.MINUTOS_DIA];
        List<Long> activos = List.of(activo.getId(), otroActivo.getId());
        for (DisponibilidadSemanal b : bloques) {
            if (!activos.contains(b.getCicloId())) continue;
            boolean[][] destino = switch (b.getTipoBloque()) {
                case "ESTUDIO" -> reservado;
                case "NO_DISPONIBLE" -> restringido;
                default -> null;
            };
            if (destino != null) marcar(destino[DiaSemana.valueOf(b.getDiaSemana()).ordinal()], b.getHoraInicio(), b.getHoraFin());
        }
        for (HorarioClase h : horarios) {
            if (activos.contains(h.getMateriaInscrita().getCicloAcademico().getId())) {
                marcar(restringido[h.getDiaSemana().ordinal()], h.getHoraInicio(), h.getHoraFin());
            }
        }
        for (JornadaLaboral j : jornadas) {
            marcar(restringido[DiaSemana.valueOf(j.getDiaSemana()).ordinal()], j.getHoraInicio(), j.getHoraFin());
        }
        int reservados = 0;
        int disponibles = 0;
        for (int d = 0; d < 7; d++) {
            for (int m = 0; m < IntervalosMinutos.MINUTOS_DIA; m++) {
                if (reservado[d][m]) {
                    reservados++;
                    if (!restringido[d][m]) disponibles++;
                }
            }
        }
        return new int[] {reservados, disponibles};
    }

    private static void marcar(boolean[] dia, LocalTime inicio, LocalTime fin) {
        for (int m = inicio.getHour() * 60 + inicio.getMinute(); m < fin.getHour() * 60 + fin.getMinute(); m++) {
            dia[m] = true;
        }
    }
}
