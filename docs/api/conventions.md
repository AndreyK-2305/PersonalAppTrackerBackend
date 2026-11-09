# Convenciones de la API

## Rutas y versionado

- Todas las rutas de negocio van bajo `/api/v1/...`, con el módulo como prefijo y recursos en plural y en inglés (`/api/v1/finance/accounts`).
- Un cambio incompatible exige una nueva versión o un cambio coordinado con el frontend, que vive en otro repositorio.
- El health check está en `/actuator/health`, con las sondas `/actuator/health/liveness` y `/actuator/health/readiness`.

## Formato

- **JSON:** cuerpos en JSON con propiedades en `camelCase`.
- **Montos:** se envían como **cadenas decimales**, por ejemplo `"amount": "150000.00"`, nunca como número, para evitar errores de precisión en JavaScript. La moneda va aparte en código ISO 4217 (`"currency": "COP"`). El backend hace todos los cálculos monetarios.
- **Fechas de negocio:** `YYYY-MM-DD` (`"2026-11-06"`).
- **Instantes:** ISO 8601 en UTC (`"2026-11-06T13:45:00Z"`).
- **Identificadores:** UUID.
- **Cuerpos con tipo:** cuando un recurso tiene variantes (productos con `kind`, movimientos con `type`), el cuerpo es un objeto plano con el discriminador.
  - El servidor exige los campos que el tipo necesita y rechaza los que no le aplican: 400 `validation_error`, con los mensajes "es obligatorio para este tipo de …" y "no aplica a este tipo de …".
  - Un texto en blanco cuenta como ausente.
  - Se eligió un objeto plano en lugar de `oneOf` para que el documento OpenAPI y los tipos generados del frontend sean simples. La descripción de cada esquema lista los campos por tipo.
- **Acciones sobre un recurso:** se expresan como `POST /recurso/{id}/accion` cuando no son una edición de datos, por ejemplo `POST /api/v1/finance/accounts/{id}/archive`.

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
| 400 | `validation_error` | Fallan las validaciones del cuerpo o de los parámetros, o faltan o sobran campos según el tipo. Incluye `errors: [{ "field", "message" }]`; en los parámetros, `field` es su nombre (`size`, `sort`). |
| 400 | `bad_request` | JSON mal formado, un valor que no existe en un enum o un parámetro con formato inválido (UUID, fecha). |
| 401 | `unauthorized` | No hay sesión válida. |
| 403 | `forbidden` | Hay sesión, pero no hay permiso. |
| 404 | `not_found` | La ruta no existe. |
| 409 | `conflict` | Un índice único rechazó la operación, por ejemplo dos peticiones simultáneas con el mismo nombre. El cliente puede recargar y reintentar. |
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

Los listados paginados reciben:

| Parámetro | Por defecto | Valores |
|---|---|---|
| `page` | `0` | Número de página, desde 0 |
| `size` | `20` | De 1 a 100 |
| `sort` | `date,desc` | `campo,dirección`; cada listado documenta sus valores (en movimientos: `date,desc`, `date,asc`, `amount,desc` y `amount,asc`) |

Y responden:

```json
{ "items": [ ... ], "page": 0, "size": 20, "totalItems": 57, "totalPages": 3 }
```

- El orden es estable: a igual criterio, los elementos registrados más recientemente van primero.
- Un valor fuera de rango responde 400 `validation_error` con el nombre del parámetro.

## Finanzas

Rutas bajo `/api/v1/finance`, todas con sesión. Las que modifican datos requieren el token CSRF. Ver [ADR-0013](../decisions/0013-modelo-contable.md), [ADR-0014](../decisions/0014-productos-otro-y-categorias.md) y [ADR-0015](../decisions/0015-edicion-y-borrado-de-movimientos.md).

| Método | Ruta | Respuesta |
|---|---|---|
| GET | `/setup` | `{ "setUp": boolean }`: si ya se completó la bienvenida |
| POST | `/setup` | Bienvenida: crea "Otro" y, con `withSuggestedCategories: true`, el catálogo sugerido. Repetirla no duplica nada |
| GET | `/accounts` | `{ accounts, netWorth, currency }`: productos con su saldo (activos, archivados y "Otro" al final) y patrimonio |
| POST | `/accounts` | 201 con el producto |
| PUT | `/accounts/{id}` | Reemplaza los datos editables; el tipo solo cambia entre cuentas de depósito |
| POST | `/accounts/{id}/archive` y `/reactivate` | El producto actualizado |
| DELETE | `/accounts/{id}` | 204; 409 si tiene movimientos |
| GET | `/categories` | Árbol de dos niveles, incluidas las archivadas |
| POST | `/categories` | 201; con `parentId` crea una subcategoría del mismo tipo |
| PATCH | `/categories/{id}` | 204; cambia el nombre |
| POST | `/categories/{id}/archive` y `/reactivate` | 204; aplican también a las subcategorías |
| DELETE | `/categories/{id}` | 204; 409 si tiene movimientos o subcategorías |
| GET | `/transactions` | Página de movimientos. Filtros: `from`, `to`, `accountId` (origen o destino), `categoryId` (incluye subcategorías), `type` y `q` (descripción y comercio) |
| POST | `/transactions` | 201 con el movimiento |
| PUT | `/transactions/{id}` | Reemplaza todos los datos; el tipo no cambia |
| DELETE | `/transactions/{id}` | 204; borrado definitivo |
| GET | `/summary` | Resumen de `period` (`DAY`, `WEEK` o `MONTH`, por defecto `MONTH`) que contiene `date` (por defecto, hoy en la zona horaria del perfil) |

**Origen de los movimientos:** cada movimiento responde `origin` (`MANUAL` o `CAPTURE`) y `amountEstimated`. Este último es verdadero en los movimientos captados con un monto convertido de dólares con la TRM, y deja de serlo cuando el usuario cambia el monto. Los dos campos son de solo lectura: no se envían al crear ni al editar.

**Campos según el tipo de producto (`kind`):**

| Tipo | Campos |
|---|---|
| `CASH` | `name` y `openingBalance` (0 si se omite; no negativo) |
| `SAVINGS`, `CHECKING`, `DIGITAL_WALLET` | `name`, `institution`, `lastFourDigits` (opcional) y `openingBalance` (0 si se omite; admite negativos) |
| `CREDIT_CARD` | `name`, `institution`, `lastFourDigits` (opcional), `creditLimit` e `initialDebt` (0 si se omite) |
| `OTHER` | No se crea por la API: lo crea la bienvenida |

**Campos según el tipo de movimiento (`type`).** Todos llevan `amount` (mayor que cero), `date` y, opcionales, `description`, `merchant` y `channel`.

| Tipo | Campos |
|---|---|
| `INCOME` | `destinationAccountId` y `categoryId` (opcional, de ingreso) |
| `EXPENSE` | `sourceAccountId` y `categoryId` (opcional, de gasto) |
| `TRANSFER` | `sourceAccountId` y `destinationAccountId`, distintos y con saldo |
| `ADJUSTMENT` | `accountId` y `direction` (`INCREASE` o `DECREASE`). En la respuesta, el producto aparece como destino si el ajuste aumenta el saldo y como origen si lo disminuye |

**Dashboard:** combina `GET /summary` (totales del periodo, comparación, detalle por producto y categoría y tendencia), `GET /accounts` (saldos y patrimonio) y `GET /transactions?size=5` (últimos movimientos).

Códigos de finanzas:

| Estado | `code` | Cuándo |
|---|---|---|
| 404 | `finance.account.not_found`, `finance.category.not_found`, `finance.transaction.not_found` | No existe o es de otro usuario |
| 409 | `finance.account.name_taken`, `finance.category.name_taken` | Nombre repetido (productos: por usuario; categorías: entre hermanas) |
| 409 | `finance.account.has_transactions`, `finance.category.in_use` | Se intentó borrar algo con movimientos: hay que archivarlo |
| 422 | `finance.account.system_managed` | Se intentó editar, archivar o borrar "Otro" |
| 422 | `finance.account.kind_change`, `finance.transaction.type_change` | Se intentó cambiar el tipo al editar |
| 422 | `finance.account.archived`, `finance.category.archived` | Producto o categoría archivados |
| 422 | `finance.transaction.account_not_allowed` | Transferencia o ajuste con "Otro" |
| 422 | `finance.transfer.same_account` | Transferencia con el mismo producto como origen y destino |
| 422 | `finance.category.kind_mismatch`, `finance.category.depth_exceeded` | Categoría de otro tipo o tercer nivel |
| 422 | `finance.transaction.amount_invalid`, `finance.account.opening_balance_invalid`, `finance.account.credit_limit_invalid` | Montos fuera de las reglas |
| 422 | `finance.date_range.invalid` | `from` posterior a `to` |
| 422 | `finance.currency.unsupported`, `money.amount_invalid` | Moneda distinta de COP o monto fuera de `NUMERIC(19,2)` |
