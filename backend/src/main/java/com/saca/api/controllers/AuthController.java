package com.saca.api.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saca.api.dto.ActualizarPerfilRequest;
import com.saca.api.dto.LoginRequest;
import com.saca.api.dto.LoginResponse;
import com.saca.api.dto.RegistroRequest;
import com.saca.api.entity.Estudiante;
import com.saca.api.service.EstudianteService;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:5173")
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

    @GetMapping("/perfil/correo/{correo}")
    public Estudiante obtenerPorCorreo(@PathVariable String correo) {
        return service.obtenerPorCorreo(correo);
    }

    @PutMapping("/perfil/{id}")
    public String actualizarPerfil(
            @PathVariable Long id,
            @RequestBody ActualizarPerfilRequest request) {

        return service.actualizarPerfil(id, request);
    }
}
