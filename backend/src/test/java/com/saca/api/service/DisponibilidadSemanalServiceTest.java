package com.saca.api.service;

import com.saca.api.entity.DisponibilidadSemanal;
import com.saca.api.exception.ConflictoHorarioException;
import com.saca.api.exception.ValidacionNegocioException;
import com.saca.api.repository.DisponibilidadSemanalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisponibilidadSemanalServiceTest {

    private static final Long ESTUDIANTE_ID = 5L;
    private static final Long CICLO_ID = 3L;

    @Mock
    private DisponibilidadSemanalRepository repository;

    @InjectMocks
    private DisponibilidadSemanalService service;

    private static DisponibilidadSemanal bloque(Long id, String dia, String inicio, String fin, Long cicloId) {
        DisponibilidadSemanal bloque = new DisponibilidadSemanal(
                dia, LocalTime.parse(inicio), LocalTime.parse(fin), "CLASE", cicloId, ESTUDIANTE_ID);
        bloque.setId(id);
        return bloque;
    }

    private void conBloquesExistentes(Long cicloId, DisponibilidadSemanal... bloques) {
        when(repository.findByEstudianteIdAndCicloId(ESTUDIANTE_ID, cicloId)).thenReturn(List.of(bloques));
    }

    // Creación

    @Test
    void crearRechazaUnBloqueQueSeCruzaConOtroDelMismoDia() {
        conBloquesExistentes(CICLO_ID, bloque(1L, "LUNES", "08:00", "10:00", CICLO_ID));

        assertThatThrownBy(() -> service.guardarBloque(bloque(null, "LUNES", "09:00", "11:00", CICLO_ID)))
                .isInstanceOf(ConflictoHorarioException.class)
                .hasMessage("El bloque se cruza con otro registrado el LUNES de 08:00 a 10:00");
        verify(repository, never()).save(any());
    }

    @Test
    void crearRechazaUnBloqueContenidoDentroDeOtro() {
        conBloquesExistentes(CICLO_ID, bloque(1L, "LUNES", "08:00", "12:00", CICLO_ID));

        assertThatThrownBy(() -> service.guardarBloque(bloque(null, "LUNES", "09:00", "10:00", CICLO_ID)))
                .isInstanceOf(ConflictoHorarioException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void crearRechazaUnBloqueDuplicado() {
        conBloquesExistentes(CICLO_ID, bloque(1L, "LUNES", "08:00", "10:00", CICLO_ID));

        assertThatThrownBy(() -> service.guardarBloque(bloque(null, "lunes", "08:00", "10:00", CICLO_ID)))
                .isInstanceOf(ConflictoHorarioException.class)
                .hasMessage("Ya existe un bloque de disponibilidad registrado el LUNES de 08:00 a 10:00");
        verify(repository, never()).save(any());
    }

    @Test
    void crearPermiteBloquesConsecutivos() {
        conBloquesExistentes(CICLO_ID, bloque(1L, "LUNES", "08:00", "09:00", CICLO_ID));
        DisponibilidadSemanal despues = bloque(null, "LUNES", "09:00", "10:00", CICLO_ID);
        DisponibilidadSemanal antes = bloque(null, "LUNES", "07:00", "08:00", CICLO_ID);
        when(repository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        assertThat(service.guardarBloque(despues)).isSameAs(despues);
        assertThat(service.guardarBloque(antes)).isSameAs(antes);
    }

    @Test
    void crearPermiteElMismoHorarioEnOtroDia() {
        conBloquesExistentes(CICLO_ID, bloque(1L, "LUNES", "08:00", "10:00", CICLO_ID));
        DisponibilidadSemanal nuevo = bloque(null, "MARTES", "08:00", "10:00", CICLO_ID);
        when(repository.save(nuevo)).thenReturn(nuevo);

        assertThat(service.guardarBloque(nuevo)).isSameAs(nuevo);
    }

    @Test
    void crearSoloComparaConLosBloquesDelMismoCiclo() {
        Long otroCiclo = 4L;
        conBloquesExistentes(otroCiclo);
        DisponibilidadSemanal nuevo = bloque(null, "LUNES", "08:00", "10:00", otroCiclo);
        when(repository.save(nuevo)).thenReturn(nuevo);

        assertThat(service.guardarBloque(nuevo)).isSameAs(nuevo);
        verify(repository).findByEstudianteIdAndCicloId(ESTUDIANTE_ID, otroCiclo);
    }

    @Test
    void crearMantieneLaValidacionDelIntervaloAntesDeBuscarConflictos() {
        assertThatThrownBy(() -> service.guardarBloque(bloque(null, "LUNES", "09:00", "09:00", CICLO_ID)))
                .isInstanceOf(ValidacionNegocioException.class)
                .isNotInstanceOf(ConflictoHorarioException.class)
                .hasMessage("La hora de inicio debe ser menor que la hora de fin");
        verify(repository, never()).findByEstudianteIdAndCicloId(any(), any());
    }

    // Edición

    @Test
    void editarExcluyeElPropioBloque() {
        DisponibilidadSemanal actual = bloque(1L, "LUNES", "08:00", "10:00", CICLO_ID);
        when(repository.findById(1L)).thenReturn(Optional.of(actual));
        conBloquesExistentes(CICLO_ID, actual);
        when(repository.save(actual)).thenReturn(actual);

        DisponibilidadSemanal resultado = service.actualizarBloque(1L, bloque(null, "LUNES", "08:30", "10:30", CICLO_ID));

        assertThat(resultado.getHoraInicio()).isEqualTo(LocalTime.parse("08:30"));
        assertThat(resultado.getHoraFin()).isEqualTo(LocalTime.parse("10:30"));
    }

    @Test
    void editarSinCambiarElHorarioNoSeConsideraDuplicado() {
        DisponibilidadSemanal actual = bloque(1L, "LUNES", "08:00", "10:00", CICLO_ID);
        when(repository.findById(1L)).thenReturn(Optional.of(actual));
        conBloquesExistentes(CICLO_ID, actual);
        when(repository.save(actual)).thenReturn(actual);

        assertThat(service.actualizarBloque(1L, bloque(null, "LUNES", "08:00", "10:00", CICLO_ID))).isSameAs(actual);
    }

    @Test
    void editarRechazaUnCruceConOtroBloque() {
        DisponibilidadSemanal otro = bloque(1L, "LUNES", "08:00", "10:00", CICLO_ID);
        DisponibilidadSemanal actual = bloque(2L, "LUNES", "11:00", "12:00", CICLO_ID);
        when(repository.findById(2L)).thenReturn(Optional.of(actual));
        conBloquesExistentes(CICLO_ID, otro, actual);

        assertThatThrownBy(() -> service.actualizarBloque(2L, bloque(null, "LUNES", "09:30", "11:30", CICLO_ID)))
                .isInstanceOf(ConflictoHorarioException.class)
                .hasMessage("El bloque se cruza con otro registrado el LUNES de 08:00 a 10:00");
        verify(repository, never()).save(any());
        assertThat(actual.getHoraInicio()).isEqualTo(LocalTime.parse("11:00"));
    }

    @Test
    void editarRechazaConvertirseEnDuplicadoDeOtroBloque() {
        DisponibilidadSemanal otro = bloque(1L, "LUNES", "08:00", "10:00", CICLO_ID);
        DisponibilidadSemanal actual = bloque(2L, "LUNES", "11:00", "12:00", CICLO_ID);
        when(repository.findById(2L)).thenReturn(Optional.of(actual));
        conBloquesExistentes(CICLO_ID, otro, actual);

        assertThatThrownBy(() -> service.actualizarBloque(2L, bloque(null, "LUNES", "08:00", "10:00", CICLO_ID)))
                .isInstanceOf(ConflictoHorarioException.class)
                .hasMessage("Ya existe un bloque de disponibilidad registrado el LUNES de 08:00 a 10:00");
        verify(repository, never()).save(any());
    }

    @Test
    void editarPermiteQuedarConsecutivoAOtroBloque() {
        DisponibilidadSemanal otro = bloque(1L, "LUNES", "08:00", "10:00", CICLO_ID);
        DisponibilidadSemanal actual = bloque(2L, "LUNES", "11:00", "12:00", CICLO_ID);
        when(repository.findById(2L)).thenReturn(Optional.of(actual));
        conBloquesExistentes(CICLO_ID, otro, actual);
        when(repository.save(actual)).thenReturn(actual);

        DisponibilidadSemanal resultado = service.actualizarBloque(2L, bloque(null, "LUNES", "10:00", "11:00", CICLO_ID));

        assertThat(resultado.getHoraInicio()).isEqualTo(LocalTime.parse("10:00"));
    }

    @Test
    void editarPermiteMoverElBloqueAOtroDiaSinCruces() {
        DisponibilidadSemanal otro = bloque(1L, "LUNES", "08:00", "10:00", CICLO_ID);
        DisponibilidadSemanal actual = bloque(2L, "LUNES", "11:00", "12:00", CICLO_ID);
        when(repository.findById(2L)).thenReturn(Optional.of(actual));
        conBloquesExistentes(CICLO_ID, otro, actual);
        when(repository.save(actual)).thenReturn(actual);

        DisponibilidadSemanal resultado = service.actualizarBloque(2L, bloque(null, "MARTES", "08:00", "10:00", CICLO_ID));

        assertThat(resultado.getDiaSemana()).isEqualTo("MARTES");
    }

    @Test
    void editarComparaConLosBloquesDelCicloDeDestino() {
        Long otroCiclo = 4L;
        DisponibilidadSemanal enOtroCiclo = bloque(7L, "LUNES", "08:00", "10:00", otroCiclo);
        DisponibilidadSemanal actual = bloque(2L, "LUNES", "08:00", "10:00", CICLO_ID);
        when(repository.findById(2L)).thenReturn(Optional.of(actual));
        conBloquesExistentes(otroCiclo, enOtroCiclo);

        assertThatThrownBy(() -> service.actualizarBloque(2L, bloque(null, "LUNES", "09:00", "11:00", otroCiclo)))
                .isInstanceOf(ConflictoHorarioException.class);
        verify(repository).findByEstudianteIdAndCicloId(ESTUDIANTE_ID, otroCiclo);
    }

    @Test
    void editarMantieneLaValidacionDelIntervalo() {
        assertThatThrownBy(() -> service.actualizarBloque(1L, bloque(null, "LUNES", "10:00", "09:00", CICLO_ID)))
                .isInstanceOf(ValidacionNegocioException.class)
                .hasMessage("La hora de inicio debe ser menor que la hora de fin");
        verify(repository, never()).findById(any());
        verify(repository, never()).save(any());
    }
}
