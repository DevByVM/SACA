package com.saca.api.service;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// Casos de BloqueoCuentaCasos sobre la base H2 de las pruebas
@SpringBootTest
@ActiveProfiles("test")
@Import(BloqueoCuentaCasos.Configuracion.class)
class BloqueoCuentaConcurrenteTest extends BloqueoCuentaCasos {
}
