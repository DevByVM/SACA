package com.saca.api.service;

import java.util.Locale;
import java.util.Optional;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.saca.api.dto.LoginRequest;
import com.saca.api.dto.LoginResponse;
import com.saca.api.entity.Administrador;
import com.saca.api.repository.AdministradorRepository;

@Service
public class AdministradorService {

    private final AdministradorRepository repository;
    private final TokenService tokenService;
    private final VerificadorContrasenias verificadorContrasenias;

    public AdministradorService(AdministradorRepository repository, TokenService tokenService,
            VerificadorContrasenias verificadorContrasenias) {
        this.repository = repository;
        this.tokenService = tokenService;
        this.verificadorContrasenias = verificadorContrasenias;
    }

    public Optional<LoginResponse> autenticar(LoginRequest request) {
        if (request == null || request.getCorreoInstitucional() == null) {
            return Optional.empty();
        }
        String correo = request.getCorreoInstitucional().trim().toLowerCase(Locale.ROOT);
        Optional<Administrador> encontrado = repository.findByCorreoIgnoreCase(correo);
        if (encontrado.isEmpty()) {
            return Optional.empty();
        }

        Administrador administrador = encontrado.get();
        if (!administrador.isHabilitado()
                || !verificadorContrasenias.coincide(request.getContrasenia(), administrador.getContrasenia())) {
            if (!administrador.isHabilitado()) {
                verificadorContrasenias.compararConFicticio(request.getContrasenia());
            }
            return Optional.of(LoginResponse.error("Credenciales incorrectas"));
        }

        TokenService.TokenEmitido token = tokenService.emitir(administrador);
        return Optional.of(new LoginResponse(true, "LOGIN_OK", administrador.getId(),
                administrador.getNombre(), null, administrador.getCorreo(), token.valor(), token.expiraEn(),
                "ADMINISTRADOR"));
    }
}
