package com.saca.api.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.saca.api.entity.TokenActivacion;

public interface TokenActivacionRepository extends JpaRepository<TokenActivacion, Long> {

    // El estudiante se carga junto con el token: quien consulta o consume necesita sus datos
    @EntityGraph(attributePaths = "estudiante")
    Optional<TokenActivacion> findByHashToken(String hashToken);

    List<TokenActivacion> findByEstudianteIdOrderByCreadoEnAsc(Long estudianteId);

    boolean existsByEstudianteIdAndUsadoEnIsNullAndInvalidadoEnIsNullAndExpiraEnAfter(Long estudianteId,
            LocalDateTime ahora);

    // Emisiones (correos de activación) de una cuenta posteriores a un instante; usa el índice estudiante_id, creado_en
    long countByEstudianteIdAndCreadoEnAfter(Long estudianteId, LocalDateTime desde);

    // Correos que cuentan para el límite de la cuenta: enviados en las últimas 24 horas, intentados sin resultado
    // en las últimas 24 horas y en cola desde hace menos de 15 minutos. Los descartados no cuentan
    @Query("""
            select count(t) from TokenActivacion t
            where t.estudiante.id = :estudianteId and t.envioDescartadoEn is null and (
                  (t.enviadoEn is not null and t.enviadoEn > :desdeDia)
               or (t.enviadoEn is null and t.envioIntentadoEn is not null and t.envioIntentadoEn > :desdeDia)
               or (t.enviadoEn is null and t.envioIntentadoEn is null and t.creadoEn > :desdeCola))
            """)
    long contarCorreos(@Param("estudianteId") Long estudianteId, @Param("desdeDia") LocalDateTime desdeDia,
            @Param("desdeCola") LocalDateTime desdeCola);

    // Envío: cada marca es una sentencia condicional e idempotente sobre una sola fila de tokens (nunca la cuenta),
    // en su propia transacción, porque se ejecutan en el hilo de envío

    // Marca previa a la llamada al SMTP: solo si no se intentó ni descartó antes y aún no ha caducado. Afecta a
    // una fila como mucho una vez por token, así que el SMTP se llama como mucho una vez
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update TokenActivacion t set t.envioIntentadoEn = :ahora
            where t.id = :id and t.envioIntentadoEn is null and t.envioDescartadoEn is null and t.creadoEn > :caducaSiAntes
            """)
    int marcarIntento(@Param("id") Long id, @Param("ahora") LocalDateTime ahora,
            @Param("caducaSiAntes") LocalDateTime caducaSiAntes);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update TokenActivacion t set t.enviadoEn = :ahora
            where t.id = :id and t.envioIntentadoEn is not null and t.enviadoEn is null and t.envioDescartadoEn is null
            """)
    int marcarEnviado(@Param("id") Long id, @Param("ahora") LocalDateTime ahora);

    // Tras un intento que el SMTP rechazó
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update TokenActivacion t set t.envioDescartadoEn = :ahora
            where t.id = :id and t.envioIntentadoEn is not null and t.enviadoEn is null and t.envioDescartadoEn is null
            """)
    int marcarFallido(@Param("id") Long id, @Param("ahora") LocalDateTime ahora);

    // Antes de intentarlo (cola llena, sin cupo horario o caducado). Nunca afecta a un token ya intentado
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update TokenActivacion t set t.envioDescartadoEn = :ahora
            where t.id = :id and t.envioIntentadoEn is null and t.enviadoEn is null and t.envioDescartadoEn is null
            """)
    int marcarDescartadoSinIntento(@Param("id") Long id, @Param("ahora") LocalDateTime ahora);

    // Marca como invalidados los tokens del estudiante que aún podían usarse; los vencidos se quedan como están
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update TokenActivacion t set t.invalidadoEn = :ahora
            where t.estudiante.id = :estudianteId and t.usadoEn is null and t.invalidadoEn is null
              and t.expiraEn > :ahora
            """)
    int invalidarVigentes(@Param("estudianteId") Long estudianteId, @Param("ahora") LocalDateTime ahora);

    // Consumo atómico: la condición y la marca van en la misma sentencia, así que de dos consumos
    // simultáneos del mismo token solo uno puede afectar a la fila
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update TokenActivacion t set t.usadoEn = :ahora
            where t.hashToken = :hashToken and t.usadoEn is null and t.invalidadoEn is null and t.expiraEn > :ahora
            """)
    int consumir(@Param("hashToken") String hashToken, @Param("ahora") LocalDateTime ahora);
}
