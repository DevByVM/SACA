package com.saca.api.controller;

import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.saca.api.dto.request.ConsultaActivacionRequest;
import com.saca.api.dto.request.RestablecerContraseniaRequest;
import com.saca.api.dto.response.CuentaResponse;
import com.saca.api.proteccion.ProteccionSolicitudes;
import com.saca.api.proteccion.ResolutorIpCliente;
import com.saca.api.service.RestablecimientoContraseniaService;

@RestController
@RequestMapping("/api/auth/restablecimiento")
public class RestablecimientoContraseniaController {
    private final RestablecimientoContraseniaService service;
    private final ProteccionSolicitudes proteccion;
    private final ResolutorIpCliente ips;
    public RestablecimientoContraseniaController(RestablecimientoContraseniaService service,
            ProteccionSolicitudes proteccion, ResolutorIpCliente ips) {
        this.service = service;
        this.proteccion = proteccion;
        this.ips = ips;
    }
    @PostMapping("/consultar")
    public Map<String, String> consultar(@RequestBody ConsultaActivacionRequest request, HttpServletRequest http) {
        proteccion.consumirConsultaOActivacion(ips.resolver(http));
        return Map.of("estado", service.consultar(request.token()));
    }
    @PostMapping
    public CuentaResponse restablecer(@Valid @RequestBody RestablecerContraseniaRequest request, HttpServletRequest http) {
        proteccion.consumirConsultaOActivacion(ips.resolver(http));
        return service.restablecer(request);
    }
}
