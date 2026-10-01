package com.saca.api.repository;

import com.saca.api.entity.Estudiante;
import com.saca.api.entity.PreferenciaNotificacion;
import com.saca.api.entity.TipoActividadAcademica;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;

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
}
