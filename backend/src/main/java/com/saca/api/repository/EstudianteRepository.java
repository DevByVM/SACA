package com.saca.api.repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.saca.api.entity.EstadoCorreo;
import com.saca.api.entity.Estudiante;

import jakarta.persistence.LockModeType;

public interface EstudianteRepository extends JpaRepository<Estudiante, Long> {

    boolean existsByCarnet(String carnet);

    boolean existsByCorreoInstitucional(String correoInstitucional);

    Optional<Estudiante> findByCarnet(String carnet);

    Optional<Estudiante> findByCorreoInstitucional(String correoInstitucional);

    // Registro: cuentas con ese carnet (sin distinguir mayúsculas) o ese correo. Solo los id, para que
    // la cuenta se cargue después ya bloqueada y con su estado actual
    @Query("select e.id from Estudiante e where lower(e.carnet) = lower(:carnet) or e.correoInstitucional = :correo")
    List<Long> idsPorCarnetOCorreo(@Param("carnet") String carnet, @Param("correo") String correo);

    // Bloqueo de la fila hasta el fin de la transacción (SELECT ... FOR UPDATE)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Estudiante e where e.id = :id")
    Optional<Estudiante> bloquearPorId(@Param("id") Long id);

    // Activación: nombre, contraseña y confirmación del correo, solo si la cuenta sigue pendiente. No
    // toca intentos_fallidos ni bloqueado_hasta (condición de diseño de compensarIntentoFallido)
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Estudiante e
            set e.nombre = :nombre, e.contrasenia = :hash, e.estadoCorreo = :confirmado, e.correoConfirmadoEn = :ahora
            where e.id = :id and e.estadoCorreo = :pendiente
            """)
    int activar(@Param("id") Long id, @Param("nombre") String nombre, @Param("hash") String hash,
            @Param("ahora") LocalDateTime ahora, @Param("confirmado") EstadoCorreo confirmado,
            @Param("pendiente") EstadoCorreo pendiente);

    default int activar(Long id, String nombre, String hash, LocalDateTime ahora) {
        return activar(id, nombre, hash, ahora, EstadoCorreo.CONFIRMADO, EstadoCorreo.PENDIENTE);
    }

    // El inicio de sesión y el perfil solo escriben sus columnas con sentencias atómicas, nunca la
    // entidad completa leída antes: así una escritura no deshace otra simultánea (por ejemplo, un fallo de login
    // no devuelve la contraseña anterior). Ninguna se ejecuta mientras se compara una contraseña.
    //
    // MySQL evalúa las asignaciones de un UPDATE de izquierda a derecha con los valores ya asignados, y H2 con
    // los originales: por eso bloqueado_hasta, que depende de intentos_fallidos, se asigna antes.

    // Reserva un fallo antes de comparar la contraseña. Solo afecta a la fila si la cuenta está activa y no
    // bloqueada; el tercer fallo (o cualquiera tras uno ya vencido) la bloquea hasta :bloqueoHasta. De varias
    // reservas simultáneas, como mucho tres encuentran la cuenta sin bloquear
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Estudiante e
            set e.bloqueadoHasta = case when coalesce(e.intentosFallidos, 0) + 1 >= 3 then :bloqueoHasta
                                        else e.bloqueadoHasta end,
                e.intentosFallidos = coalesce(e.intentosFallidos, 0) + 1
            where e.id = :id and e.cuentaActiva = true and (e.bloqueadoHasta is null or e.bloqueadoHasta <= :ahora)
            """)
    int reservarIntentoFallido(@Param("id") Long id, @Param("ahora") Timestamp ahora,
            @Param("bloqueoHasta") Timestamp bloqueoHasta);

    // Deshace la reserva de un intento que no debía contar (contraseña correcta de una cuenta pendiente): un
    // fallo menos y sin bloqueo. Retirar cualquier bloqueo es correcto porque, mientras esa reserva está contada,
    // un bloqueo vigente solo puede ser el que puso ella misma o uno al que se llegó gracias a su fallo: si la
    // cuenta ya estaba bloqueada, la reserva no habría afectado a la fila; si con ella se llegó a tres, la
    // bloqueó ella y las demás quedaron fuera; si otra llegó después a tres, sin esta reserva serían dos.
    //
    // Condición de diseño: mientras haya un inicio de sesión en curso para una cuenta pendiente, nada más debe
    // reiniciar su contador ni su bloqueo (hoy solo lo hace reiniciarIntentos, que nunca se aplica a cuentas
    // pendientes). Si otro proceso lo hiciera, esta compensación restaría un fallo posterior al reinicio
    // y retiraría su bloqueo: antes habría que identificar cada reserva (por ejemplo, con una secuencia).
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Estudiante e
            set e.bloqueadoHasta = null,
                e.intentosFallidos = case when coalesce(e.intentosFallidos, 0) > 0 then e.intentosFallidos - 1
                                          else 0 end
            where e.id = :id
            """)
    int compensarIntentoFallido(@Param("id") Long id);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Estudiante e set e.intentosFallidos = 0, e.bloqueadoHasta = null where e.id = :id")
    int reiniciarIntentos(@Param("id") Long id);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Estudiante e set e.nombre = :nombre where e.id = :id")
    int actualizarNombre(@Param("id") Long id, @Param("nombre") String nombre);

    // Solo cambia si la contraseña sigue siendo la que se comprobó: si otra solicitud la cambió entretanto,
    // no la sobrescribe
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Estudiante e set e.nombre = :nombre, e.contrasenia = :nueva
            where e.id = :id and e.contrasenia = :comprobada
            """)
    int actualizarNombreYContrasenia(@Param("id") Long id, @Param("nombre") String nombre,
            @Param("comprobada") String comprobada, @Param("nueva") String nueva);
}
