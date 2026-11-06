# Convenciones de la API

## Rutas y versionado

- Todas las rutas de negocio van bajo `/api/v1/...`, con recursos en plural y en inglés (`/api/v1/accounts`).
- Un cambio incompatible exige una nueva versión o un cambio coordinado con el frontend, que vive en otro repositorio.
- El health check está en `/actuator/health`, con las sondas `/actuator/health/liveness` y `/actuator/health/readiness`.

## Formato

- **JSON:** cuerpos en JSON con propiedades en `camelCase`.
- **Montos:** se envían como **cadenas decimales**, por ejemplo `"amount": "150000.00"`, nunca como número, para evitar errores de precisión en JavaScript. La moneda va aparte en código ISO 4217 (`"currency": "COP"`). El backend hace todos los cálculos monetarios.
- **Fechas de negocio:** `YYYY-MM-DD` (`"2026-11-06"`).
- **Instantes:** ISO 8601 en UTC (`"2026-11-06T13:45:00Z"`).
- **Identificadores:** UUID.

## Errores (RFC 9457)

Toda respuesta de error usa `Content-Type: application/problem+json`:

```json
{
  "type": "about:blank",
  "title": "Recurso no encontrado",
  "status": 404,
  "detail": "La cuenta no existe.",
  "instance": "/api/v1/accounts/7d3f...",
  "code": "finance.account.not_found",
  "correlationId": "b1c2d3e4-..."
}
```

- **`code`:** es estable y es lo que el frontend debe usar para decidir qué hacer. Los textos pueden cambiar; el código no.
- **`detail`:** está en español y se puede mostrar al usuario. Nunca contiene detalles internos.
- **`correlationId`:** coincide con la cabecera `X-Correlation-Id`. Sirve para buscar la petición en los logs.

### Códigos genéricos

| Estado | `code` | Cuándo |
|---|---|---|
| 400 | `validation_error` | Fallan las validaciones del cuerpo. Incluye `errors: [{ "field", "message" }]`. |
| 400 | `bad_request` | JSON mal formado o parámetros inválidos. |
| 401 | `unauthorized` | No hay sesión válida. |
| 403 | `forbidden` | Hay sesión, pero no hay permiso. |
| 404 | `not_found` | La ruta no existe. |
| 405 | `method_not_allowed` | Método HTTP no soportado. Incluye la cabecera `Allow`. |
| 415 | `unsupported_media_type` | El `Content-Type` no está soportado. |
| 422 | `business_rule_violation` | Valor genérico; en la práctica se usa el código específico de la regla. |
| 500 | `internal_error` | Error inesperado. Se registra en los logs con su `correlationId`. |

### Códigos de dominio

Siguen el formato `<modulo>.<entidad>.<motivo>`, por ejemplo `finance.account.not_found` o `finance.transfer.same_account`:

- Se definen en excepciones que extienden `NotFoundException` (404) o `DomainException` (422).
- Un recurso de otro usuario responde **404**, igual que uno inexistente, para no revelar su existencia.

Ejemplo de error de validación:

```json
{
  "title": "Solicitud inválida",
  "status": 400,
  "detail": "La solicitud contiene datos inválidos.",
  "code": "validation_error",
  "errors": [
    { "field": "name", "message": "no debe estar vacío" },
    { "field": "amount", "message": "debe ser mayor que 0" }
  ]
}
```

## Cabeceras

| Cabecera | Dirección | Uso |
|---|---|---|
| `X-Correlation-Id` | Petición y respuesta | Opcional en la petición; solo se acepta `[A-Za-z0-9-]{1,64}`. Siempre está en la respuesta. |
| `X-XSRF-TOKEN` | Petición | Token CSRF para las operaciones que modifican datos (Etapa 2). |

## Autenticación

La sesión está en el servidor y se identifica con la cookie `SESSION` (`HttpOnly; Secure; SameSite=Lax`).

**CSRF:**

- Cada respuesta incluye la cookie `XSRF-TOKEN`, que el frontend puede leer.
- Las operaciones que modifican datos (`POST`, `PUT`, `PATCH`, `DELETE`), incluido el inicio de sesión, deben enviar su valor en la cabecera `X-XSRF-TOKEN`.
- Si falta o no es válido, la respuesta es `403 csrf_invalid`; el cliente puede reintentar con el token de la última respuesta.

Ver [ADR-0004](../decisions/0004-autenticacion-por-sesion.md) y [ADR-0011](../decisions/0011-autenticacion-bloqueo-y-contrasenas.md).

| Método | Ruta | Acceso | Respuesta |
|---|---|---|---|
| GET | `/api/v1/auth/registration` | Público | `{ "enabled": boolean }` |
| POST | `/api/v1/auth/register` | Público, si `enabled` | 201 con el perfil; inicia sesión |
| POST | `/api/v1/auth/login` | Público | 200 con el perfil; inicia sesión |
| POST | `/api/v1/auth/logout` | Con o sin sesión | 204 |
| GET | `/api/v1/me` | Con sesión | Perfil (401 si no hay sesión) |
| PATCH | `/api/v1/me` | Con sesión | Actualización parcial: `displayName` y/o `timeZone` |
| PUT | `/api/v1/me/password` | Con sesión | 204; cierra las demás sesiones |

Códigos de identity:

| Estado | `code` | Cuándo |
|---|---|---|
| 401 | `identity.invalid_credentials` | Correo o contraseña incorrectos (mismo error en ambos casos) |
| 429 | `identity.too_many_attempts` | Cuenta bloqueada 15 minutos tras 5 intentos fallidos |
| 429 | `too_many_requests` | Límite por IP de nginx en el inicio de sesión y el registro |
| 403 | `identity.registration_closed` | El registro está cerrado |
| 409 | `identity.email_already_registered` | El correo ya tiene una cuenta |
| 422 | `identity.email_invalid`, `identity.password_policy`, `identity.display_name_invalid`, `identity.time_zone_invalid`, `identity.current_password_incorrect` | Reglas de la cuenta |

## Documento OpenAPI y tipos del frontend

- El frontend genera sus tipos con `npm run api:types` a partir de `/v3/api-docs`, que solo está disponible en el perfil `local`.
- **Campos obligatorios en las respuestas:** como springdoc no los deduce, los DTO de respuesta los declaran con `@Schema(requiredProperties = {...})`. Si no se declaran, los tipos generados quedan opcionales.
- **El propietario no se documenta:** el parámetro `@CurrentUser UserId` se excluye del documento, porque sale de la sesión y no es un parámetro de la petición.

## Paginación

Se definirá en la Etapa 3, con los primeros listados.
