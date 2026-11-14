package com.saca.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.saca.api.entity.Administrador;
import com.saca.api.repository.AdministradorRepository;
import com.saca.api.repository.EstudianteRepository;

class AdministradorBootstrapTest {

    private final AdministradorRepository administradores = mock(AdministradorRepository.class);
    private final EstudianteRepository estudiantes = mock(EstudianteRepository.class);
    // Carga application.properties para comprobar también los nombres públicos de las variables.
    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withBean(AdministradorRepository.class, () -> administradores)
            .withBean(EstudianteRepository.class, () -> estudiantes)
            .withUserConfiguration(AdministradorBootstrap.class);

    @Test
    void lasVariablesDelEntornoConfiguranLaCuentaInicialConHash() {
        contexto.withPropertyValues("SACA_ADMIN_CORREO= ADMIN@ues.edu.sv ",
                "SACA_ADMIN_NOMBRE= Admin Inicial ", "SACA_ADMIN_CONTRASENIA=ClaveInicial123")
                .run(context -> {
                    context.getBean(AdministradorBootstrap.class).run(null);

                    ArgumentCaptor<Administrador> captor = ArgumentCaptor.forClass(Administrador.class);
                    verify(administradores).save(captor.capture());
                    Administrador creado = captor.getValue();
                    assertThat(creado.getCorreo()).isEqualTo("admin@ues.edu.sv");
                    assertThat(creado.getNombre()).isEqualTo("Admin Inicial");
                    assertThat(creado.isHabilitado()).isTrue();
                    assertThat(new BCryptPasswordEncoder().matches("ClaveInicial123", creado.getContrasenia()))
                            .isTrue();
                });
    }

    @Test
    void sinVariablesNoSeCreaUnaCuenta() {
        contexto.withPropertyValues("SACA_ADMIN_CORREO=", "SACA_ADMIN_NOMBRE=", "SACA_ADMIN_CONTRASENIA=")
                .run(context -> {
                    context.getBean(AdministradorBootstrap.class).run(null);
                    verifyNoInteractions(administradores, estudiantes);
                });
    }

    @Test
    void configuracionIncompletaNoCreaCuentas() {
        contexto.withPropertyValues("SACA_ADMIN_CORREO=admin@ues.edu.sv", "SACA_ADMIN_NOMBRE=",
                "SACA_ADMIN_CONTRASENIA=ClaveInicial123")
                .run(context -> {
                    assertThatThrownBy(() -> context.getBean(AdministradorBootstrap.class).run(null))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("SACA_ADMIN_CORREO");
                    verifyNoInteractions(administradores, estudiantes);
                });
    }

    @Test
    void cambiarVariablesNoModificaLaCuentaExistente() {
        Administrador existente = new Administrador();
        existente.setCorreo("admin@ues.edu.sv");
        existente.setNombre("Nombre original");
        String hash = new BCryptPasswordEncoder().encode("ClaveOriginal123");
        existente.setContrasenia(hash);
        when(administradores.findByCorreoIgnoreCase("admin@ues.edu.sv")).thenReturn(Optional.of(existente));

        contexto.withPropertyValues("SACA_ADMIN_CORREO=admin@ues.edu.sv", "SACA_ADMIN_NOMBRE=Otro nombre",
                "SACA_ADMIN_CONTRASENIA=OtraClave123456")
                .run(context -> {
                    context.getBean(AdministradorBootstrap.class).run(null);
                    assertThat(existente.getNombre()).isEqualTo("Nombre original");
                    assertThat(existente.getContrasenia()).isEqualTo(hash);
                    verify(administradores).findByCorreoIgnoreCase("admin@ues.edu.sv");
                    org.mockito.Mockito.verifyNoMoreInteractions(administradores);
                    verifyNoInteractions(estudiantes);
                });
    }
}
