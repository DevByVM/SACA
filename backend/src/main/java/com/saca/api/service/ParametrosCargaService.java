package com.saca.api.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saca.api.dto.request.ParametrosCargaRequest;
import com.saca.api.dto.response.ParametrosCargaResponse;
import com.saca.api.entity.ParametrosCarga;
import com.saca.api.exception.ValidacionNegocioException;
import com.saca.api.repository.ParametrosCargaRepository;

@Service
public class ParametrosCargaService implements ApplicationRunner {

    private final ParametrosCargaRepository repository;

    public ParametrosCargaService(ParametrosCargaRepository repository) {
        this.repository = repository;
    }

    // Inicialización aditiva, como las demás configuraciones de arranque del proyecto.
    // Los valores guardados nunca se reemplazan por los iniciales al reiniciar.
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!repository.existsById(ParametrosCarga.CLAVE_GLOBAL)) {
            repository.save(new ParametrosCarga());
        }
    }

    @Transactional(readOnly = true)
    public ParametrosCargaResponse obtener() {
        return toResponse(buscar());
    }

    @Transactional
    public ParametrosCargaResponse guardar(ParametrosCargaRequest request) {
        Double elevada = request.umbralElevada();
        Double critica = request.umbralCritica();
        if (elevada == null || critica == null || !Double.isFinite(elevada) || !Double.isFinite(critica)
                || elevada <= 0 || critica <= 0 || elevada >= critica) {
            throw new ValidacionNegocioException(
                    "Los umbrales deben ser números positivos y el primero debe ser menor que el segundo.");
        }
        ParametrosCarga parametros = buscar();
        parametros.actualizar(elevada, critica);
        return toResponse(parametros);
    }

    private ParametrosCarga buscar() {
        return repository.findById(ParametrosCarga.CLAVE_GLOBAL)
                .orElseThrow(() -> new IllegalStateException("No se inicializaron los parámetros de carga"));
    }

    private ParametrosCargaResponse toResponse(ParametrosCarga parametros) {
        return new ParametrosCargaResponse(parametros.getUmbralElevada(), parametros.getUmbralCritica());
    }
}
