package sv.edu.ues.saca_backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import sv.edu.ues.saca_backend.Service.EstudianteService;
import sv.edu.ues.saca_backend.dto.LoginRequest;
import sv.edu.ues.saca_backend.dto.RegistroRequest;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:5173")
public class AuthController {

    @Autowired
    private EstudianteService service;

    @PostMapping("/registro")
    public String registrar(
            @RequestBody RegistroRequest request) {

        return service.registrar(request);
    }

    @PostMapping("/login")
    public String login(
            @RequestBody LoginRequest request) {

        return service.login(request);
    }
}