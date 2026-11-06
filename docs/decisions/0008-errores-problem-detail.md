# ADR-0008: Formato de errores con ProblemDetail (RFC 9457)

- Estado: Aceptada
- Fecha: 2026-11-06

## Contexto

El contexto exige manejo centralizado de excepciones, mensajes de error claros en español y no exponer información interna. El frontend necesita distinguir los errores de forma estable, sin depender de los textos.

## Decisión

- **Formato único:** todas las respuestas de error usan `application/problem+json` (RFC 9457) con las propiedades adicionales:
  - `code`: identificador estable;
  - `correlationId`;
  - `errors`, solo en los errores de validación.
- **`GlobalExceptionHandler`** en `shared.infrastructure.web`:
  - Extiende `ResponseEntityExceptionHandler` y reemplaza los mensajes estándar de Spring MVC, que están en inglés, por mensajes en español según el estado HTTP.
  - `NotFoundException` responde 404 y `DomainException` responde 422, ambas con su `code` y su mensaje.
  - Los errores de autenticación y autorización responden 401 y 403.
  - Cualquier otra excepción responde 500 con un mensaje genérico; el detalle se registra en el log con el `correlationId`.
- **Spring Security delega sus errores 401/403 en este manejador** mediante `HandlerExceptionResolver`, de modo que un error de seguridad tiene el mismo formato que uno de negocio.
- **Validación en español:** Bean Validation usa el locale fijo `es_CO`.
- **Códigos de dominio:** siguen el formato `<modulo>.<entidad>.<motivo>`.

## Consecuencias

- **A favor:**
  - Un solo formato de error para todo el frontend.
  - Los mensajes para el usuario están en español.
  - No se filtran stack traces ni mensajes internos.
  - El formato está cubierto por `GlobalExceptionHandlerTests`.
- **Obligación para cada módulo:** cada uno define sus excepciones con códigos documentados.
- **`ResponseStatusException`:** no debe usarse, porque su mensaje se reemplaza por el genérico de su estado.

## Alternativas consideradas

- **El formato de error por defecto de Spring Boot:** no es estándar, no tiene código estable y está en inglés.
- **Un sobre de error propio:** reinventa un estándar existente.
