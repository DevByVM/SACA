package com.saca.api.service;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.saca.api.dto.request.TipoActividadRequest;
import com.saca.api.entity.TipoActividad;
import com.saca.api.exception.ValidacionNegocioException;
import com.saca.api.repository.ActividadAcademicaRepository;
import com.saca.api.repository.PreferenciaNotificacionRepository;
import com.saca.api.repository.TipoActividadRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TipoActividadServiceTest {

    @Mock
    private TipoActividadRepository tipoRepository;

    @Mock
    private ActividadAcademicaRepository actividadRepository;

    @Mock
    private PreferenciaNotificacionRepository preferenciaRepository;

    private TipoActividadService service;

    @BeforeEach
    void setUp() {
        service = new TipoActividadService(tipoRepository, actividadRepository, preferenciaRepository);
    }

    @Test
    void creaTipoConNombreNormalizado() {
        when(tipoRepository.existsByNombreIgnoreCase("Tarea corta")).thenReturn(false);
        when(tipoRepository.save(any())).thenAnswer(invocacion -> {
            TipoActividad tipo = invocacion.getArgument(0);
            tipo.setIdTipoActividad(8);
            return tipo;
        });

        assertThat(service.crear(new TipoActividadRequest("  Tarea   corta ")))
                .isEqualTo(new com.saca.api.dto.response.TipoActividadResponse(8, "Tarea corta", true));
    }

    @Test
    void rechazaNombreDuplicadoAlCrear() {
        when(tipoRepository.existsByNombreIgnoreCase("Parcial")).thenReturn(true);

        assertThatThrownBy(() -> service.crear(new TipoActividadRequest("Parcial")))
                .isInstanceOf(ValidacionNegocioException.class)
                .hasMessage("Ya existe un tipo de actividad con ese nombre");
        verify(tipoRepository, never()).save(any());
    }

    @Test
    void rechazaNombreDuplicadoAlEditar() {
        TipoActividad tipo = tipo(4, "Laboratorio");
        when(tipoRepository.findById(4)).thenReturn(Optional.of(tipo));
        when(tipoRepository.existsByNombreIgnoreCaseAndIdTipoActividadNot("Parcial", 4)).thenReturn(true);

        assertThatThrownBy(() -> service.actualizar(4, new TipoActividadRequest("Parcial")))
                .isInstanceOf(ValidacionNegocioException.class)
                .hasMessage("Ya existe un tipo de actividad con ese nombre");
    }

    @Test
    void eliminaTipoSinReferencias() {
        TipoActividad tipo = tipo(5, "Tarea corta");
        when(tipoRepository.findById(5)).thenReturn(Optional.of(tipo));
        when(actividadRepository.countByTipoActividadIdTipoActividad(5)).thenReturn(0L);
        when(preferenciaRepository.countByTiposActividadHabilitados_IdTipoActividad(5)).thenReturn(0L);

        service.eliminar(5);

        verify(tipoRepository).delete(tipo);
    }

    @Test
    void impideEliminarTipoUsadoPorActividad() {
        TipoActividad tipo = tipo(5, "Tarea corta");
        when(tipoRepository.findById(5)).thenReturn(Optional.of(tipo));
        when(actividadRepository.countByTipoActividadIdTipoActividad(5)).thenReturn(1L);
        when(preferenciaRepository.countByTiposActividadHabilitados_IdTipoActividad(5)).thenReturn(0L);

        assertThatThrownBy(() -> service.eliminar(5))
                .isInstanceOf(ValidacionNegocioException.class)
                .hasMessageContaining("está asociado");
        verify(tipoRepository, never()).delete(any());
    }

    @Test
    void impideEliminarTipoUsadoPorPreferencia() {
        TipoActividad tipo = tipo(5, "Tarea corta");
        when(tipoRepository.findById(5)).thenReturn(Optional.of(tipo));
        when(actividadRepository.countByTipoActividadIdTipoActividad(5)).thenReturn(0L);
        when(preferenciaRepository.countByTiposActividadHabilitados_IdTipoActividad(5)).thenReturn(1L);

        assertThatThrownBy(() -> service.eliminar(5))
                .isInstanceOf(ValidacionNegocioException.class)
                .hasMessageContaining("está asociado");
        verify(tipoRepository, never()).delete(any());
    }

    private static TipoActividad tipo(int id, String nombre) {
        return new TipoActividad(id, nombre, true);
    }
}
