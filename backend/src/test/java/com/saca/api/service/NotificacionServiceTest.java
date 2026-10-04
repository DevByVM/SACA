package com.saca.api.service;

import com.saca.api.entity.Estudiante;
import com.saca.api.entity.PreferenciaNotificacion;
import com.saca.api.entity.TipoActividadAcademica;
import com.saca.api.mapper.ActividadAcademicaMapper;
import com.saca.api.repository.ActividadAcademicaRepository;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.repository.PreferenciaNotificacionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

    @Mock
    private ActividadAcademicaRepository actividadRepository;

    @Mock
    private PreferenciaNotificacionRepository preferenciaRepository;

    @Mock
    private EstudianteRepository estudianteRepository;

    @Mock
    private ActividadAcademicaMapper actividadMapper;

    @Test
    void consultaActividadesDelPeriodoDeOchoDiasConLosTiposHabilitados() {
        Clock relojFijo = Clock.fixed(
                Instant.parse("2026-09-30T12:00:00Z"),
                ZoneId.of("America/El_Salvador")
        );
        NotificacionService service = new NotificacionService(
                actividadRepository,
                preferenciaRepository,
                estudianteRepository,
                actividadMapper,
                relojFijo
        );
        PreferenciaNotificacion preferencia = new PreferenciaNotificacion();
        preferencia.setTiposActividadHabilitados(Set.of(TipoActividadAcademica.PARCIAL));

        when(estudianteRepository.existsById(7L)).thenReturn(true);
        when(preferenciaRepository.findByEstudianteId(7L)).thenReturn(Optional.of(preferencia));
        when(actividadRepository.findNotificacionesProximasPorEstudiante(any(), any(), any(), any()))
                .thenReturn(java.util.List.of());

        service.obtenerProximasPorEstudiante(7L);

        ArgumentCaptor<LocalDateTime> inicioCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> finCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(actividadRepository).findNotificacionesProximasPorEstudiante(
                org.mockito.ArgumentMatchers.eq(7L),
                inicioCaptor.capture(),
                finCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(Set.of(TipoActividadAcademica.PARCIAL))
        );
        assertThat(inicioCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 9, 30, 0, 0));
        assertThat(finCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 10, 8, 0, 0));
    }

    @Test
    void noConsultaActividadesCuandoElEstudianteNoTienePreferencias() {
        NotificacionService service = new NotificacionService(
                actividadRepository,
                preferenciaRepository,
                estudianteRepository,
                actividadMapper,
                Clock.systemUTC()
        );

        when(estudianteRepository.existsById(7L)).thenReturn(true);
        when(preferenciaRepository.findByEstudianteId(7L)).thenReturn(Optional.empty());

        assertThat(service.obtenerProximasPorEstudiante(7L)).isEmpty();
        verify(actividadRepository, never()).findNotificacionesProximasPorEstudiante(any(), any(), any(), any());
    }
}
