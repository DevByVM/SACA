package com.saca.api.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.saca.api.dto.request.ActualizarUsuarioAdminRequest;
import com.saca.api.dto.request.EstadoCuentaRequest;
import com.saca.api.dto.response.UsuarioAdminResponse;
import com.saca.api.entity.Estudiante;
import com.saca.api.exception.RecursoNoEncontradoException;
import com.saca.api.exception.ValidacionNegocioException;
import com.saca.api.repository.AdministradorRepository;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.repository.TokenRestablecimientoRepository;

@Service
public class AdministradorGestionService {

    private final EstudianteRepository repository;
    private final EstadoCuentas estadoCuentas;
    private final AdministradorRepository administradores;
    private final TokenRestablecimientoRepository restablecimientos;
    private final TokenActivacionService activaciones;
    private final CorreoActivacionService correo;
    private final Clock reloj;

    public AdministradorGestionService(EstudianteRepository repository, EstadoCuentas estadoCuentas,
            AdministradorRepository administradores,
            TokenRestablecimientoRepository restablecimientos,
            TokenActivacionService activaciones, CorreoActivacionService correo, Clock reloj) {
        this.repository = repository;
        this.estadoCuentas = estadoCuentas;
        this.administradores = administradores;
        this.restablecimientos = restablecimientos;
        this.activaciones = activaciones;
        this.correo = correo;
        this.reloj = reloj;
    }

    @Transactional
    public UsuarioAdminResponse actualizar(Long id, ActualizarUsuarioAdminRequest request) {
        Estudiante estudiante = repository.bloquearPorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la cuenta solicitada"));
        String nombre = request.nombre() == null ? "" : request.nombre().trim();
        String carnet = EstudianteService.normalizarCarnet(request.carnet());
        String destino = request.correoInstitucional().trim().toLowerCase(Locale.ROOT);
        if ((!estudiante.estaPendienteDeActivacion() || !nombre.isEmpty()) && !EstudianteService.nombreValido(nombre)) {
            throw new ValidacionNegocioException("El nombre es obligatorio y solo admite letras y espacios.");
        }
        if (repository.existsByCarnetIgnoreCaseAndIdNot(carnet, id)
                || repository.existsByCorreoInstitucionalIgnoreCaseAndIdNot(destino, id)
                || administradores.findByCorreoIgnoreCase(destino).isPresent()) {
            throw new ValidacionNegocioException("El carnet o correo ya pertenece a otra cuenta.");
        }
        boolean cambiaCorreo = !destino.equals(estudiante.getCorreoInstitucional());
        estudiante.setNombre(nombre.isEmpty() ? null : nombre);
        estudiante.setCarnet(carnet);
        estudiante.setCorreoInstitucional(destino);
        estudiante.setCuentaActiva(request.habilitada());
        repository.saveAndFlush(estudiante);
        if (cambiaCorreo) {
            restablecimientos.invalidar(id, LocalDateTime.now(reloj));
            if (estudiante.estaPendienteDeActivacion()) {
                TokenActivacionService.TokenGenerado token = activaciones.generar(estudiante);
                correo.enviarActivacion(destino, token.valor(), token.expiraEn());
            }
        }
        actualizarEstadoTrasCommit(id, request.habilitada());
        return toResponse(estudiante);
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
        Estudiante estudiante = repository.bloquearPorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la cuenta solicitada"));
        estudiante.setCuentaActiva(request.habilitada());
        actualizarEstadoTrasCommit(id, request.habilitada());
        return toResponse(estudiante);
    }

    private void actualizarEstadoTrasCommit(Long id, boolean habilitada) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                estadoCuentas.actualizarEstudiante(id, habilitada);
            }
        });
    }

    private UsuarioAdminResponse toResponse(Estudiante estudiante) {
        return new UsuarioAdminResponse(estudiante.getId(), estudiante.getNombre(), estudiante.getCarnet(),
                estudiante.getCorreoInstitucional(), Boolean.TRUE.equals(estudiante.getCuentaActiva()),
                estudiante.getEstadoCorreo() == null ? "ANTERIOR" : estudiante.getEstadoCorreo().name());
    }
}
