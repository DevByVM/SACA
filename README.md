# SACA - Sistema de Análisis de Carga Académica

MVP desarrollado para la Entrega 3 de Diseño de Sistemas I.

## Tecnologías

- Frontend: React
- Backend: Spring Boot
- Base de datos: MySQL
- Comunicación: API REST con JSON
- Gestión del proyecto: Jira
- Control de versiones: GitHub
- Documentación: Google Drive

## Estructura del repositorio

- `frontend/`: interfaz de usuario desarrollada en React.
- `backend/`: API REST desarrollada con Spring Boot.
- `database/`: scripts SQL para la creación y carga inicial de la base de datos.

## Objetivo del MVP

Permitir que el estudiante registre información académica básica, materias, horarios y actividades, para consultar su carga académica semanal y mejorar su planificación.

## Inicio local

1. Copia `.env.example` como `.env` en la raíz y configura `SACA_TOKEN_SECRETO` con una clave aleatoria de al menos 32 bytes. En PowerShell puedes generar una con:
   ```powershell
   $bytes = New-Object byte[] 32; $rng = [Security.Cryptography.RandomNumberGenerator]::Create(); $rng.GetBytes($bytes); [BitConverter]::ToString($bytes).Replace("-", "").ToLower()
   ```
   El MySQL de Docker se publica en el puerto local `3307` para evitar conflictos con una instalación local de MySQL; puedes cambiarlo con `SACA_MYSQL_PORT`.
2. Define `SACA_ADMIN_CORREO`, `SACA_ADMIN_NOMBRE` y `SACA_ADMIN_CONTRASENIA` en ese `.env`. La contraseña inicial debe tener entre 12 y 72 bytes UTF-8. El correo no debe pertenecer ya a una cuenta de estudiante.
3. Desde la raíz, inicia la base de datos, el backend y el buzón Mailpit:
   ```powershell
   docker compose up -d --build
   ```

El backend crea el administrador inicial una sola vez y guarda su contraseña con BCrypt. Si las tres variables de administrador se dejan vacías, no se crea una cuenta administradora. Cambiar esas variables después no reemplaza la contraseña de un administrador ya existente. Los correos de activación enviados por la API en Docker quedan en `http://localhost:8025`.

El administrador inicia sesión con su correo y contraseña. Desde el menú puede solicitar cuentas de estudiante por carnet (el estudiante debe activar su cuenta por correo), habilitar o inhabilitar cuentas y administrar el catálogo global de materias. Las materias inscritas toman código y nombre del catálogo; el grupo teórico sigue siendo propio de cada inscripción.

Si ejecutas el backend desde el IDE en vez de Docker, configura el SMTP de Mailpit según `.env.example` y arráncalo con `docker compose -f docker-compose.correo.yml --profile correo up -d`.

## Plantillas de planificación

Las plantillas son horarios semanales compartidos con franjas `ESTUDIO`, `TRABAJO`, `CLASES` o `DESCANSO`; no se asignan a materias. Administración las gestiona en **Plantillas de planificación**. Solo puede haber una plantilla activa por perfil (`CON_JORNADA_LABORAL` o `SIN_JORNADA_LABORAL`); activar otra del mismo perfil desactiva la anterior.

El endpoint autenticado `GET /api/plantillas-planificacion/vigente` elige el perfil según si el estudiante tiene franjas registradas en `jornadas_laborales` y devuelve la plantilla activa completa. El generador de sugerencias debe usar únicamente sus franjas `ESTUDIO` para proponer materias inscritas; no debe modificar la plantilla. La administración usa `GET/POST /api/admin/plantillas-planificacion` y `PUT /api/admin/plantillas-planificacion/{id}`. Cada franja contiene `diaSemana`, `horaInicio`, `horaFin` y `tipoBloque`; no se aceptan horas invertidas ni franjas superpuestas en el mismo día.
