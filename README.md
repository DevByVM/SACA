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

## Gestión de usuarios y restablecimiento

Administración puede editar nombre, carnet, correo institucional y habilitación de las cuentas de estudiante.
El correo debe pertenecer a `@ues.edu.sv` y tanto el carnet como el correo deben ser únicos. El alta sigue
siendo por carnet con activación por correo. Si se cambia el correo de una cuenta pendiente, se envía una
nueva activación al nuevo destino y se invalida la anterior.

Desde **Gestión de usuarios → Restablecer contraseña**, el administrador solicita un enlace que se envía al
correo institucional vigente. El estudiante elige su contraseña; administración nunca la recibe ni la escribe.
El enlace vence en una hora, es de un solo uso y una nueva solicitud sustituye la anterior. Solo se guarda su
hash SHA-256. La contraseña anterior funciona hasta completar el cambio. El proceso conserva la habilitación,
la activación y los contadores/bloqueo de la cuenta. Las cuentas pendientes deben activarse primero.
Cambiar el correo invalida los enlaces de restablecimiento existentes.

El envío usa el mismo SMTP y las mismas variables de correo que la activación. La operación administrativa
confirma la aceptación por SMTP; un fallo devuelve un error y revierte la emisión. No hay reintentos automáticos.
La tabla `tokens_restablecimiento` se crea con el mecanismo JPA `ddl-auto=update` ya utilizado en instalaciones
existentes; el script inicial también la incluye.

Para comprobar el correo de pruebas:

```bash
docker compose -f docker-compose.correo.yml --profile correo up -d
cd backend
SACA_PRUEBA_MAILPIT=true ./mvnw -Dtest=CorreoActivacionMailpitTest test
```

Esta comprobación usa H2 y Mailpit local. El resultado SMTP en producción debe registrarse aparte cuando se
compruebe allí; el éxito local no demuestra entrega en producción.

## Parámetros de carga académica — SDACAS-37

El menú de administración incluye **Parámetros de carga**, con dos umbrales globales de horas inicialmente
en **20 y 35**. Solo administración puede consultar y actualizar `GET/PUT /api/admin/parametros-carga`.
Ambos valores deben ser finitos, positivos y el primero menor que el segundo; se admiten decimales.
Guardar requiere confirmar el alcance global. Cancelar descarta la edición sin enviar una actualización.

La configuración se guarda en `parametros_carga`, bajo la clave de dominio `GLOBAL`, sin relación con
estudiantes o semanas. El arranque crea los valores iniciales únicamente si falta esa configuración.
Las instalaciones existentes crean la nueva tabla mediante el `ddl-auto=update` utilizado por el proyecto,
sin sustituir los datos guardados.

La siguiente consulta de carga semanal o de semanas del ciclo toma los umbrales guardados. El cálculo,
los motivos, los textos y las barras utilizan los mismos valores; no hay configuraciones por estudiante o semana.
Normal incluye el primer umbral, elevada incluye el segundo y crítica comienza por encima del segundo.
Las reglas de porcentajes, peso y cantidad de evaluaciones no cambian.
La consulta del ciclo obtiene los parámetros una sola vez, sin consultas adicionales por cada semana.
Los paneles muestran un error con reintento si no se puede obtener la carga, en lugar de mostrar una clasificación normal.

Comprobaciones automatizadas relevantes: `ParametrosCargaAdminTest`, `ParametrosCargaIntegracionTest`,
`CargaAcademicaCalculoTest`, `CargaSemanasCalculoTest` y `CargaSemanasTest` en el backend;
`parametrosCarga.test.js` en el frontend. La validación del entorno publicado debe ejecutarse allí.

## Plantillas de planificación

Las plantillas son horarios semanales compartidos con franjas `ESTUDIO`, `TRABAJO`, `CLASES` o `DESCANSO`; no se asignan a materias. Administración las gestiona en **Plantillas de planificación**. Solo puede haber una plantilla activa por perfil (`CON_JORNADA_LABORAL` o `SIN_JORNADA_LABORAL`); activar otra del mismo perfil desactiva la anterior.

El endpoint autenticado `GET /api/plantillas-planificacion/vigente` elige el perfil según si el estudiante tiene franjas registradas en `jornadas_laborales` y devuelve la plantilla activa completa. El generador de sugerencias debe usar únicamente sus franjas `ESTUDIO` para proponer materias inscritas; no debe modificar la plantilla. La administración usa `GET/POST /api/admin/plantillas-planificacion` y `PUT /api/admin/plantillas-planificacion/{id}`. Cada franja contiene `diaSemana`, `horaInicio`, `horaFin` y `tipoBloque`; no se aceptan horas invertidas ni franjas superpuestas en el mismo día.
