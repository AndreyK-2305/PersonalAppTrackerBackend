# ADR-0004: Autenticación por sesión en el servidor

- Estado: Aceptada (se implementa en la Etapa 2)
- Fecha: 2026-11-06

## Contexto

El cliente principal es una SPA servida desde el mismo origen que la API ([ADR-0007](0007-topologia-mismo-origen-y-cors.md)). El contexto exige cookies `HttpOnly`, `Secure` y `SameSite`, contraseñas con hashing seguro y autorización en cada operación. En el futuro habrá una integración con Atajos de iOS que usará tokens de dispositivo.

## Decisión

- **Sesión en el servidor:** se persiste en PostgreSQL con Spring Session JDBC, en el esquema `identity`.
  - La cookie es `HttpOnly; Secure; SameSite=Lax`.
  - La sesión cambia de identificador al iniciar sesión (protección contra fijación de sesión).
  - El logout invalida la sesión en el servidor.
- **CSRF:**
  - Patrón para SPA: cookie `XSRF-TOKEN` legible por JavaScript y cabecera `X-XSRF-TOKEN`.
  - Es obligatorio en `POST`, `PUT`, `PATCH` y `DELETE`.
- **Contraseñas:** `DelegatingPasswordEncoder`, con BCrypt por defecto. Permite migrar a otro algoritmo sin forzar cambios de contraseña.
- **Acceso:**
  - Todo se deniega por defecto; ya está implementado en la Etapa 1.
  - Los errores 401/403 usan el formato estándar de la API.
- **Intentos de login:** se limitan con un contador persistido en la base de datos, sin Redis.
- **Integración con Atajos de iOS (futura):** tendrá su propio `SecurityFilterChain` para `/api/capture/**`, con tokens por dispositivo guardados como hash y revocables.

## Consecuencias

- **A favor:**
  - La revocación es inmediata.
  - No hay tokens accesibles desde JavaScript.
  - Las sesiones sobreviven a reinicios y escalan horizontalmente gracias a la base de datos.
- **En contra:**
  - Requiere el token CSRF en el cliente.
  - Agrega la dependencia `spring-session-jdbc` y sus tablas.

## Alternativas consideradas

- **JWT en una cookie con refresh tokens:** igual necesita una cookie en el navegador; la revocación exige una tabla de refresh tokens y rotación. Es más código y más superficie de ataque, sin beneficio para una SPA de mismo origen.
- **JWT en `localStorage`:** descartado por quedar expuesto a XSS.
- **Sesión en memoria (por defecto en Tomcat):** se pierde al reiniciar y no permite más de una instancia.
