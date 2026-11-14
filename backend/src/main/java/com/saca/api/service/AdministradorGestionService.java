package com.saca.api.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.saca.api.dto.request.EstadoCuentaRequest;
import com.saca.api.dto.response.UsuarioAdminResponse;
import com.saca.api.entity.Estudiante;
import com.saca.api.exception.RecursoNoEncontradoException;
import com.saca.api.repository.EstudianteRepository;

@Service
public class AdministradorGestionService {

    private final EstudianteRepository repository;
    private final EstadoCuentas estadoCuentas;

    public AdministradorGestionService(EstudianteRepository repository, EstadoCuentas estadoCuentas) {
        this.repository = repository;
        this.estadoCuentas = estadoCuentas;
    }

    @Transactional(readOnly = true)
    public List<UsuarioAdminResponse> listarUsuarios() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(Estudiante::getCorreoInstitucional,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public UsuarioAdminResponse cambiarEstado(Long id, EstadoCuentaRequest request) {
        Estudiante estudiante = repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la cuenta solicitada"));
        estudiante.setCuentaActiva(request.habilitada());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                estadoCuentas.actualizarEstudiante(id, request.habilitada());
            }
        });
        return toResponse(estudiante);
    }

    private UsuarioAdminResponse toResponse(Estudiante estudiante) {
        return new UsuarioAdminResponse(estudiante.getId(), estudiante.getNombre(), estudiante.getCarnet(),
                estudiante.getCorreoInstitucional(), Boolean.TRUE.equals(estudiante.getCuentaActiva()),
                estudiante.getEstadoCorreo() == null ? "ANTERIOR" : estudiante.getEstadoCorreo().name());
    }
}
