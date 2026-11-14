package com.saca.api.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import com.saca.api.entity.TokenRestablecimiento;

public interface TokenRestablecimientoRepository extends JpaRepository<TokenRestablecimiento, Long> {
    @EntityGraph(attributePaths = "estudiante")
    Optional<TokenRestablecimiento> findByHashToken(String hashToken);

    @Modifying(flushAutomatically = true)
    @Query("update TokenRestablecimiento t set t.invalidadoEn = :ahora where t.estudiante.id = :id and t.usadoEn is null and t.invalidadoEn is null")
    int invalidar(@Param("id") Long id, @Param("ahora") LocalDateTime ahora);
}
