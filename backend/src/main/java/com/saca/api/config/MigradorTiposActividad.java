package com.saca.api.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.saca.api.entity.TipoActividad;
import com.saca.api.repository.TipoActividadRepository;

@Component
public class MigradorTiposActividad implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigradorTiposActividad.class);
    private static final Map<String, String> TIPOS_LEGACY = Map.of(
            "PARCIAL", "Parcial", "LABORATORIO", "Laboratorio",
            "PROYECTO", "Proyecto", "INVESTIGACION", "Investigación");
    private static final List<String> TIPOS_INICIALES =
            List.of("Parcial", "Laboratorio", "Proyecto", "Investigación");

    private final TipoActividadRepository tipoRepository;
    private final JdbcTemplate jdbcTemplate;

    public MigradorTiposActividad(TipoActividadRepository tipoRepository, JdbcTemplate jdbcTemplate) {
        this.tipoRepository = tipoRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        for (String nombre : TIPOS_INICIALES) {
            tipoRepository.findByNombreIgnoreCase(nombre).orElseGet(() -> {
                TipoActividad tipo = new TipoActividad();
                tipo.setNombre(nombre);
                tipo.setActivo(true);
                return tipoRepository.save(tipo);
            });
        }

        if (tieneColumna("actividades_academicas", "tipo_actividad")) {
            migrarActividades();
        }
        if (tieneColumna("preferencias_notificacion_tipo_actividad", "tipo_actividad")) {
            migrarPreferencias();
        }
    }

    private void migrarActividades() throws Exception {
        if (!tieneColumna("actividades_academicas", "tipo_actividad_id")) {
            throw new IllegalStateException("No se creó la columna tipo_actividad_id para migrar actividades.");
        }
        List<Map<String, Object>> actividades = jdbcTemplate.queryForList(
                "SELECT id_actividad, tipo_actividad FROM actividades_academicas WHERE tipo_actividad_id IS NULL");
        for (Map<String, Object> actividad : actividades) {
            int idTipo = buscarIdLegacy(String.valueOf(actividad.get("tipo_actividad")));
            jdbcTemplate.update("UPDATE actividades_academicas SET tipo_actividad_id = ? WHERE id_actividad = ?",
                    idTipo, actividad.get("id_actividad"));
        }
        if (tieneColumna("actividades_academicas", "tipo_actividad")) {
            jdbcTemplate.execute("ALTER TABLE actividades_academicas DROP COLUMN tipo_actividad");
            marcarTipoActividadObligatorio();
            log.info("Tipos de actividad migrados para {} actividades", actividades.size());
        }
    }

    private void migrarPreferencias() throws Exception {
        if (!tieneTabla("preferencias_notificacion_tipos_catalogo")) {
            throw new IllegalStateException("No se creó la tabla para migrar preferencias de notificación.");
        }
        List<Map<String, Object>> preferencias = jdbcTemplate.queryForList(
                "SELECT preferencia_notificacion_id, tipo_actividad "
                        + "FROM preferencias_notificacion_tipo_actividad");
        for (Map<String, Object> preferencia : preferencias) {
            int idTipo = buscarIdLegacy(String.valueOf(preferencia.get("tipo_actividad")));
            Long yaExiste = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM preferencias_notificacion_tipos_catalogo "
                            + "WHERE preferencia_notificacion_id = ? AND tipo_actividad_id = ?",
                    Long.class, preferencia.get("preferencia_notificacion_id"), idTipo);
            if (yaExiste == null || yaExiste == 0) {
                jdbcTemplate.update(
                        "INSERT INTO preferencias_notificacion_tipos_catalogo "
                                + "(preferencia_notificacion_id, tipo_actividad_id) VALUES (?, ?)",
                        preferencia.get("preferencia_notificacion_id"), idTipo);
            }
        }
        jdbcTemplate.execute("DROP TABLE preferencias_notificacion_tipo_actividad");
        log.info("Tipos de actividad migrados para {} preferencias de notificación", preferencias.size());
    }

    private void marcarTipoActividadObligatorio() throws Exception {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection();
                Statement statement = connection.createStatement()) {
            String producto = connection.getMetaData().getDatabaseProductName();
            if ("MySQL".equalsIgnoreCase(producto) || "MariaDB".equalsIgnoreCase(producto)) {
                statement.execute("ALTER TABLE actividades_academicas "
                        + "MODIFY tipo_actividad_id INT NOT NULL");
            } else if ("H2".equalsIgnoreCase(producto)) {
                statement.execute("ALTER TABLE actividades_academicas "
                        + "ALTER COLUMN tipo_actividad_id SET NOT NULL");
            } else {
                throw new IllegalStateException("Base de datos no admitida para migrar tipos: " + producto);
            }
        }
    }

    private int buscarIdLegacy(String valor) {
        String nombre = TIPOS_LEGACY.get(valor.toUpperCase(Locale.ROOT));
        if (nombre == null) {
            throw new IllegalStateException("Tipo de actividad legado desconocido: " + valor);
        }
        return tipoRepository.findByNombreIgnoreCase(nombre)
                .orElseThrow(() -> new IllegalStateException("No se encontró el tipo migrado: " + nombre))
                .getIdTipoActividad();
    }

    private boolean tieneTabla(String tabla) throws Exception {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet tables = metadata.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    if (tabla.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean tieneColumna(String tabla, String columna) throws Exception {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null, "%", "%")) {
                while (columns.next()) {
                    if (tabla.equalsIgnoreCase(columns.getString("TABLE_NAME"))
                            && columna.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
