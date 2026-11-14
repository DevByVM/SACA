package com.saca.api.service;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saca.api.dto.request.CatalogoMateriaRequest;
import com.saca.api.dto.response.CatalogoMateriaResponse;
import com.saca.api.entity.MateriaCatalogo;
import com.saca.api.exception.RecursoNoEncontradoException;
import com.saca.api.exception.ValidacionNegocioException;
import com.saca.api.repository.MateriaCatalogoRepository;

@Service
public class CatalogoMateriaService {

    private final MateriaCatalogoRepository repository;

    public CatalogoMateriaService(MateriaCatalogoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CatalogoMateriaResponse> listarActivas() {
        return repository.findByActivaTrueOrderByNombreAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogoMateriaResponse> listarTodas() {
        return repository.findAllByOrderByNombreAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public CatalogoMateriaResponse crear(CatalogoMateriaRequest request) {
        String codigo = normalizarCodigo(request.codigo());
        validarCodigoDisponible(codigo, null);
        MateriaCatalogo materia = new MateriaCatalogo();
        materia.setCodigo(codigo);
        materia.setNombre(normalizarNombre(request.nombre()));
        return toResponse(repository.save(materia));
    }

    @Transactional
    public CatalogoMateriaResponse actualizar(Long id, CatalogoMateriaRequest request) {
        MateriaCatalogo materia = buscar(id);
        String codigo = normalizarCodigo(request.codigo());
        validarCodigoDisponible(codigo, id);
        materia.setCodigo(codigo);
        materia.setNombre(normalizarNombre(request.nombre()));
        return toResponse(materia);
    }

    @Transactional
    public CatalogoMateriaResponse cambiarEstado(Long id, boolean activa) {
        MateriaCatalogo materia = buscar(id);
        materia.setActiva(activa);
        return toResponse(materia);
    }

    private MateriaCatalogo buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la materia del catálogo"));
    }

    private void validarCodigoDisponible(String codigo, Long id) {
        boolean existe = id == null
                ? repository.existsByCodigoIgnoreCase(codigo)
                : repository.existsByCodigoIgnoreCaseAndIdNot(codigo, id);
        if (existe) {
            throw new ValidacionNegocioException("Ya existe una materia del catálogo con ese código");
        }
    }

    private static String normalizarCodigo(String codigo) {
        return codigo.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizarNombre(String nombre) {
        return nombre.trim();
    }

    private CatalogoMateriaResponse toResponse(MateriaCatalogo materia) {
        return new CatalogoMateriaResponse(materia.getId(), materia.getCodigo(), materia.getNombre(),
                materia.isActiva());
    }
}
