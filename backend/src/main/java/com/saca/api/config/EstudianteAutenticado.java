package com.saca.api.config;

import org.springframework.security.oauth2.jwt.Jwt;

import com.saca.api.exception.RecursoNoEncontradoException;

// La identidad del estudiante sale solo del sub del token ya validado por Spring Security,
// nunca de un id o un correo enviados por el cliente
public final class EstudianteAutenticado {

    private EstudianteAutenticado() {
    }

    public static Long id(Jwt jwt) {
        if (jwt == null) {
            // Una ruta que use este método sin exigir autenticación es un error de configuración
            throw new IllegalStateException("La ruta requiere un token validado");
        }
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new RecursoNoEncontradoException("Estudiante no encontrado");
        }
    }

    // Para rutas que aún llevan el estudianteId en la URL: solo se admite el del propio token.
    // Un id ajeno responde 404, igual que un recurso inexistente, para no confirmar que exista.
    public static Long idPropio(Jwt jwt, Long estudianteIdSolicitado) {
        Long id = id(jwt);
        if (!id.equals(estudianteIdSolicitado)) {
            throw new RecursoNoEncontradoException("Estudiante no encontrado");
        }
        return id;
    }
}
