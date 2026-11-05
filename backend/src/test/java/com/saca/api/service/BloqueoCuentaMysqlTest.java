package com.saca.api.service;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// Casos de BloqueoCuentaCasos sobre un MySQL real. Solo se ejecuta con SACA_PRUEBA_MYSQL_URL
// definida, apuntando a una base desechable: el esquema se crea y se borra en cada ejecución. Nunca debe
// apuntar a una base con datos
@EnabledIfEnvironmentVariable(named = "SACA_PRUEBA_MYSQL_URL", matches = ".+")
@SpringBootTest(properties = {
        "spring.datasource.url=${SACA_PRUEBA_MYSQL_URL}",
        "spring.datasource.username=${SACA_PRUEBA_MYSQL_USUARIO}",
        "spring.datasource.password=${SACA_PRUEBA_MYSQL_CLAVE}",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
@Import(BloqueoCuentaCasos.Configuracion.class)
class BloqueoCuentaMysqlTest extends BloqueoCuentaCasos {
}
