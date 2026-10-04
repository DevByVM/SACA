package com.saca.api.repository;

import com.saca.api.entity.Estudiante;
import com.saca.api.entity.PreferenciaNotificacion;
import com.saca.api.entity.ActividadAcademica;
import com.saca.api.entity.CicloAcademico;
import com.saca.api.entity.EstadoCiclo;
import com.saca.api.entity.MateriaInscrita;
import com.saca.api.entity.TipoActividadAcademica;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
class PreferenciaNotificacionRepositoryTest {

    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private PreferenciaNotificacionRepository preferenciaRepository;

    @Autowired
    private ActividadAcademicaRepository actividadRepository;

    @Autowired
    private CicloAcademicoRepository cicloRepository;

    @Autowired
    private MateriaInscritaRepository materiaRepository;

    @Test
    void guardaYRecuperaLosTiposHabilitadosDelEstudiante() {
        Estudiante estudiante = guardarEstudiante("UP23006", "edgardo@ues.edu.sv");

        PreferenciaNotificacion preferencia = new PreferenciaNotificacion();
        preferencia.setEstudiante(estudiante);
        preferencia.setTiposActividadHabilitados(
                Set.of(TipoActividadAcademica.PARCIAL, TipoActividadAcademica.PROYECTO)
        );
        preferenciaRepository.saveAndFlush(preferencia);

        PreferenciaNotificacion recuperada = preferenciaRepository
                .findByEstudianteId(estudiante.getId())
                .orElseThrow();

        assertThat(recuperada.getTiposActividadHabilitados())
                .containsExactlyInAnyOrder(
                        TipoActividadAcademica.PARCIAL,
                        TipoActividadAcademica.PROYECTO
                );
    }

    @Test
    void recuperaLasPreferenciasDelEstudianteSolicitadoSinMezclarTipos() {
        Estudiante primerEstudiante = guardarEstudiante("UP23007", "primero@ues.edu.sv");
        Estudiante segundoEstudiante = guardarEstudiante("UP23008", "segundo@ues.edu.sv");

        guardarPreferencia(primerEstudiante, Set.of(TipoActividadAcademica.LABORATORIO));
        guardarPreferencia(segundoEstudiante, Set.of(TipoActividadAcademica.INVESTIGACION));

        PreferenciaNotificacion recuperada = preferenciaRepository
                .findByEstudianteId(segundoEstudiante.getId())
                .orElseThrow();

        assertThat(recuperada.getTiposActividadHabilitados())
                .containsExactly(TipoActividadAcademica.INVESTIGACION);
    }

    @Test
    void filtraNotificacionesPorVentanaTemporalEstadoYTipoHabilitado() {
        LocalDateTime inicio = LocalDateTime.of(2026, 9, 30, 0, 0);
        LocalDateTime fin = inicio.plusDays(8);
        Estudiante estudiante = guardarEstudiante("UP23009", "ventana@ues.edu.sv");
        MateriaInscrita materia = guardarMateriaActiva(estudiante);

        guardarActividad(materia, "Entrega al inicio", inicio, TipoActividadAcademica.PARCIAL, "ACTIVO");
        guardarActividad(materia, "Entrega antes del límite", fin.minusNanos(1_000), TipoActividadAcademica.PARCIAL, "ACTIVO");
        guardarActividad(materia, "Entrega en el límite", fin, TipoActividadAcademica.PARCIAL, "ACTIVO");
        guardarActividad(materia, "Entrega vencida", inicio.minusNanos(1_000), TipoActividadAcademica.PARCIAL, "ACTIVO");
        guardarActividad(materia, "Actividad completada", inicio.plusDays(2), TipoActividadAcademica.PARCIAL, "COMPLETADA");
        guardarActividad(materia, "Tipo deshabilitado", inicio.plusDays(2), TipoActividadAcademica.PROYECTO, "ACTIVO");

        List<ActividadAcademica> notificaciones = actividadRepository.findNotificacionesProximasPorEstudiante(
                estudiante.getId(),
                inicio,
                fin,
                Set.of(TipoActividadAcademica.PARCIAL)
        );

        assertThat(notificaciones)
                .extracting(ActividadAcademica::getNombre)
                .containsExactly("Entrega al inicio", "Entrega antes del límite");
    }

    private Estudiante guardarEstudiante(String carnet, String correo) {
        Estudiante estudiante = new Estudiante();
        estudiante.setCarnet(carnet);
        estudiante.setNombre("Estudiante de prueba");
        estudiante.setCorreoInstitucional(correo);
        estudiante.setContrasenia("hash-de-prueba");
        estudiante.setCuentaActiva(true);
        estudiante.setIntentosFallidos(0);
        return estudianteRepository.saveAndFlush(estudiante);
    }

    private void guardarPreferencia(
            Estudiante estudiante,
            Set<TipoActividadAcademica> tiposActividad
    ) {
        PreferenciaNotificacion preferencia = new PreferenciaNotificacion();
        preferencia.setEstudiante(estudiante);
        preferencia.setTiposActividadHabilitados(tiposActividad);
        preferenciaRepository.saveAndFlush(preferencia);
    }

    private MateriaInscrita guardarMateriaActiva(Estudiante estudiante) {
        CicloAcademico ciclo = new CicloAcademico();
        ciclo.setEstudiante(estudiante);
        ciclo.setNombre("Ciclo de prueba");
        ciclo.setAnio(2026);
        ciclo.setFechaInicio(LocalDate.of(2026, 1, 1));
        ciclo.setFechaFin(LocalDate.of(2026, 12, 31));
        ciclo.setEstado(EstadoCiclo.ACTIVO);
        CicloAcademico cicloGuardado = cicloRepository.saveAndFlush(ciclo);

        MateriaInscrita materia = new MateriaInscrita();
        materia.setCicloAcademico(cicloGuardado);
        materia.setNombre("Diseño de Sistemas II");
        materia.setCodigo("DSI215");
        materia.setGrupoTeorico("01");
        return materiaRepository.saveAndFlush(materia);
    }

    private void guardarActividad(
            MateriaInscrita materia,
            String nombre,
            LocalDateTime fechaEntrega,
            TipoActividadAcademica tipoActividad,
            String estado
    ) {
        ActividadAcademica actividad = new ActividadAcademica();
        actividad.setNombre(nombre);
        actividad.setFechaInicio(fechaEntrega.minusDays(1));
        actividad.setFechaEntrega(fechaEntrega);
        actividad.setPorcentajeEvaluacion(10);
        actividad.setTiempoEstimadoHoras(2);
        actividad.setEstado(estado);
        actividad.setTipoActividad(tipoActividad);
        actividad.setMateriaInscrita(materia);
        actividadRepository.saveAndFlush(actividad);
    }
}
