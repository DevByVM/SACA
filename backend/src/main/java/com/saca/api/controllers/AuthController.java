package com.saca.api.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saca.api.config.EstudianteAutenticado;
import com.saca.api.dto.ActualizarPerfilRequest;
import com.saca.api.dto.LoginRequest;
import com.saca.api.dto.LoginResponse;
import com.saca.api.dto.PerfilResponse;
import com.saca.api.dto.RegistroRequest;
import com.saca.api.service.EstudianteService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private EstudianteService service;

    @PostMapping("/registro")
    public String registrar(@RequestBody RegistroRequest request) {
        return service.registrar(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return service.login(request);
    }

    // El perfil siempre es el del estudiante del token; no hay rutas por correo ni por id
    @GetMapping("/perfil")
    public PerfilResponse obtenerPerfil(@AuthenticationPrincipal Jwt jwt) {
        return service.obtenerPerfil(EstudianteAutenticado.id(jwt));
    }

    @PutMapping("/perfil")
    public String actualizarPerfil(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody ActualizarPerfilRequest request) {

        return service.actualizarPerfil(EstudianteAutenticado.id(jwt), request);
    }
}
