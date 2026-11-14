package com.saca.api.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saca.api.dto.RegistroRequest;
import com.saca.api.dto.request.CatalogoMateriaRequest;
import com.saca.api.dto.request.CrearUsuarioAdminRequest;
import com.saca.api.dto.request.EstadoCatalogoMateriaRequest;
import com.saca.api.dto.request.EstadoCuentaRequest;
import com.saca.api.dto.response.CatalogoMateriaResponse;
import com.saca.api.dto.response.CuentaResponse;
import com.saca.api.dto.response.UsuarioAdminResponse;
import com.saca.api.service.AdministradorGestionService;
import com.saca.api.service.CatalogoMateriaService;
import com.saca.api.service.RegistroCuentaService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
public class AdministradorController {

    private final AdministradorGestionService gestion;
    private final CatalogoMateriaService catalogo;
    private final RegistroCuentaService registro;

    public AdministradorController(AdministradorGestionService gestion, CatalogoMateriaService catalogo,
            RegistroCuentaService registro) {
        this.gestion = gestion;
        this.catalogo = catalogo;
        this.registro = registro;
    }

    @GetMapping("/usuarios")
    public List<UsuarioAdminResponse> listarUsuarios() {
        return gestion.listarUsuarios();
    }

    @PostMapping("/usuarios")
    public ResponseEntity<CuentaResponse> crearUsuario(@Valid @RequestBody CrearUsuarioAdminRequest request) {
        RegistroRequest alta = new RegistroRequest();
        alta.setCarnet(request.carnet());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(registro.registrar(alta));
    }

    @PatchMapping("/usuarios/{id}/estado")
    public UsuarioAdminResponse cambiarEstadoUsuario(@PathVariable Long id,
            @Valid @RequestBody EstadoCuentaRequest request) {
        return gestion.cambiarEstado(id, request);
    }

    @GetMapping("/catalogo-materias")
    public List<CatalogoMateriaResponse> listarCatalogo() {
        return catalogo.listarTodas();
    }

    @PostMapping("/catalogo-materias")
    public ResponseEntity<CatalogoMateriaResponse> crearMateria(
            @Valid @RequestBody CatalogoMateriaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogo.crear(request));
    }

    @PutMapping("/catalogo-materias/{id}")
    public CatalogoMateriaResponse actualizarMateria(@PathVariable Long id,
            @Valid @RequestBody CatalogoMateriaRequest request) {
        return catalogo.actualizar(id, request);
    }

    @PatchMapping("/catalogo-materias/{id}/estado")
    public CatalogoMateriaResponse cambiarEstadoMateria(@PathVariable Long id,
            @Valid @RequestBody EstadoCatalogoMateriaRequest request) {
        return catalogo.cambiarEstado(id, request.activa());
    }
}
