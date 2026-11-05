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
import com.saca.api.dto.request.ActivacionCuentaRequest;
import com.saca.api.dto.request.ConsultaActivacionRequest;
import com.saca.api.dto.request.ReenvioActivacionRequest;
import com.saca.api.dto.response.ConsultaActivacionResponse;
import com.saca.api.dto.response.CuentaResponse;
import com.saca.api.proteccion.LimitadorSolicitudes.Reserva;
import com.saca.api.proteccion.ProteccionSolicitudes;
import com.saca.api.proteccion.ResolutorIpCliente;
import com.saca.api.proteccion.ResolutorIpCliente.ClienteSolicitud;
import com.saca.api.service.EstudianteService;
import com.saca.api.service.RegistroCuentaService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private EstudianteService service;

    @Autowired
    private ProteccionSolicitudes proteccion;

    @Autowired
    private ResolutorIpCliente resolutorIp;

    @Autowired
    private RegistroCuentaService registroCuenta;

    // Solo con el carnet; la misma respuesta exista o no la cuenta. El límite por cliente se aplica
    // antes que cualquier validación
    @PostMapping("/registro")
    public CuentaResponse registrar(@RequestBody RegistroRequest request, HttpServletRequest solicitud) {
        proteccion.consumirRegistroOReenvio(resolutorIp.resolver(solicitud));
        return registroCuenta.registrar(request);
    }

    // Con el carnet o el correo institucional; la misma respuesta haya o no cuenta pendiente. Comparte
    // el límite por cliente con el registro
    @PostMapping("/reenviar-activacion")
    public CuentaResponse reenviarActivacion(@RequestBody ReenvioActivacionRequest request, HttpServletRequest solicitud) {
        proteccion.consumirRegistroOReenvio(resolutorIp.resolver(solicitud));
        return registroCuenta.reenviar(request);
    }

    // El token va en el cuerpo, nunca en la URL, para que no quede en registros de acceso
    @PostMapping("/activacion/consultar")
    public ConsultaActivacionResponse consultarActivacion(@RequestBody ConsultaActivacionRequest request,
            HttpServletRequest solicitud) {
        proteccion.consumirConsultaOActivacion(resolutorIp.resolver(solicitud));
        return registroCuenta.consultar(request);
    }

    // Activa la cuenta sin iniciar sesión: el frontend vuelve al login con el correo devuelto
    @PostMapping("/activacion")
    public CuentaResponse activar(@RequestBody ActivacionCuentaRequest request, HttpServletRequest solicitud) {
        proteccion.consumirConsultaOActivacion(resolutorIp.resolver(solicitud));
        return registroCuenta.activar(request);
    }

    // Con demasiados fallos recientes desde el mismo cliente se responde 429 antes de mirar la
    // cuenta. El intento se cuenta como fallo antes de comparar la contraseña y se descuenta si es correcto:
    // quien entra bien no consume el límite de su red. Un error inesperado no es un acceso, así que cuenta
    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request, HttpServletRequest solicitud) {
        ClienteSolicitud cliente = resolutorIp.resolver(solicitud);
        Reserva intento = proteccion.reservarIntentoLogin(cliente);

        LoginResponse respuesta = service.login(request);
        if (respuesta.autenticado()) {
            proteccion.loginCorrecto(intento);
        }
        return respuesta;
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
