package com.saca.api.service;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.saca.api.service.CorreoActivacionService.CorreoNoEnviadoException;

import jakarta.annotation.PreDestroy;

// Ejecuta los envíos de correos de activación fuera de la solicitud. Así la respuesta del registro
// tarda lo mismo exista o no la cuenta (el SMTP puede tardar segundos) y un servidor de correo lento no retiene
// hilos de Tomcat. Quien programa un envío ya confirmó su transacción.
//
// La capacidad de la cola (CAPACIDAD_COLA) y el límite global de correos por hora (SACA_LIMITE_CORREO_HORA,
// 500 por defecto) son controles independientes aunque coincidan en el número:
// - La cola solo acota los envíos que esperan en memoria. Si está llena, el envío se descarta con un aviso, sin
//   ejecutarse, así que no consume cupo horario, y se ejecuta la acción de descarte de quien lo programó.
// - El límite horario lo aplica cada envío al ejecutarse, justo antes de hablar con el SMTP.
// En los dos casos la cuenta queda pendiente con su token vigente, a la espera del reenvío.
@Component
public class EnvioCorreoActivacion {

    private static final Logger log = LoggerFactory.getLogger(EnvioCorreoActivacion.class);
    static final int CAPACIDAD_COLA = 500;

    private final ThreadPoolExecutor ejecutor;

    public EnvioCorreoActivacion() {
        this(CAPACIDAD_COLA);
    }

    // Para pruebas: una cola más pequeña
    EnvioCorreoActivacion(int capacidadCola) {
        this.ejecutor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacidadCola), tarea -> {
                    Thread hilo = new Thread(tarea, "correo-activacion");
                    hilo.setDaemon(true);
                    return hilo;
                });
    }

    public void programar(Runnable envio) {
        programar(envio, () -> { });
    }

    // siDescartado se ejecuta en el hilo de quien programa si la cola no admite el envío
    public void programar(Runnable envio, Runnable siDescartado) {
        try {
            ejecutor.execute(() -> ejecutar(envio));
        } catch (RejectedExecutionException e) {
            log.warn("Cola de correos de activación llena: se descarta un envío");
            ejecutar(siDescartado);
        }
    }

    // Ejecuta un envío: un fallo se registra una sola vez y sin datos de la cuenta
    protected void ejecutar(Runnable envio) {
        try {
            envio.run();
        } catch (CorreoNoEnviadoException e) {
            // CorreoActivacionService ya lo registró
        } catch (RuntimeException e) {
            // Solo el tipo: el mensaje podría incluir la dirección o el enlace
            log.warn("Falló un envío de correo de activación ({})", e.getClass().getSimpleName());
        }
    }

    @PreDestroy
    void detener() {
        ejecutor.shutdown();
    }
}
