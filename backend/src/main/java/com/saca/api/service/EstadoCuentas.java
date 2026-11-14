package com.saca.api.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.saca.api.repository.AdministradorRepository;
import com.saca.api.repository.EstudianteRepository;

@Component
public class EstadoCuentas implements ApplicationRunner {

    private final EstudianteRepository estudiantes;
    private final AdministradorRepository administradores;
    private final Set<Long> estudiantesInhabilitados = ConcurrentHashMap.newKeySet();
    private final Set<Long> administradoresInhabilitados = ConcurrentHashMap.newKeySet();

    public EstadoCuentas(EstudianteRepository estudiantes, AdministradorRepository administradores) {
        this.estudiantes = estudiantes;
        this.administradores = administradores;
    }

    @Override
    public void run(ApplicationArguments args) {
        estudiantesInhabilitados.addAll(estudiantes.findIdsInhabilitados());
        administradoresInhabilitados.addAll(administradores.findIdsInhabilitados());
    }

    public boolean estaHabilitado(String rol, Long id) {
        return switch (rol) {
            case "ESTUDIANTE" -> !estudiantesInhabilitados.contains(id);
            case "ADMINISTRADOR" -> !administradoresInhabilitados.contains(id);
            default -> false;
        };
    }

    public void actualizarEstudiante(Long id, boolean habilitado) {
        if (habilitado) {
            estudiantesInhabilitados.remove(id);
        } else {
            estudiantesInhabilitados.add(id);
        }
    }
}
