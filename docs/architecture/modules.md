# Módulos y dependencias

| Módulo | Responsabilidad | Puede depender de | Esquema BD | Estado |
|---|---|---|---|---|
| `shared` | Núcleo compartido mínimo | — | — | Creado (Etapa 1) |
| `identity` | Usuarios, credenciales, sesión y seguridad | `shared`, `messaging` (api) | `identity` | Completo (Etapa 2) |
| `finance` | Dinero: productos, categorías, movimientos, cuotas, préstamos y dashboard | `shared`, `identity` (api) | `finance` | Completo (Etapa 3) |
| `capture` | Entrada de notificaciones bancarias: cola, análisis y bandeja de revisión | `shared`, `identity` (api), `finance` (api) | `capture` | En curso (Etapa 4) |
| `messaging` | Entrega de correos con Resend (antes `notifications`) | `shared` | `messaging` | Etapa 5 |
| `reports` | Reportes programados y exportaciones | `shared`, `identity` (api), `finance` (api), `messaging` (api) | `reports` | Etapa 5 |
| `garage` | Vehículos | `shared`, `finance` (api) | `garage` | Posterior al MVP |

Los nombres y el orden de etapas están en [ADR-0016](../decisions/0016-modulos-capture-y-messaging.md).

Reglas generales:

- El grafo es acíclico. `finance` no conoce a `capture`, `reports`, `messaging` ni `garage`.
- Un módulo solo usa de otro su paquete `api` (`@NamedInterface`). `shared` es un módulo abierto.
- No hay relaciones JPA entre módulos, solo identificadores. Ningún módulo lee las tablas de otro.
- Un módulo se crea cuando llega su primer caso de uso real, nunca como placeholder.

## shared

Contiene solo lo que realmente comparten varios módulos:

- `domain`: `DomainException` (responde 422) y `NotFoundException` (responde 404). Los módulos extienden estas clases con su propio `code`.
- `infrastructure.web`: `GlobalExceptionHandler`, `CorrelationIdFilter` y CORS (`CorsConfig`, `CorsProperties`).
  - `GlobalExceptionHandler` también traduce `RequestValidationException` y las restricciones de parámetros a 400 `validation_error`, y las violaciones de índice único a 409 `conflict`.
- `infrastructure.openapi`: metadatos del documento OpenAPI.

- `domain` también incluye:
  - `UserId`, el identificador del propietario que usan todos los módulos;
  - `ConflictException`, que responde 409.
- `infrastructure.web.ApiProblems` construye los errores con el formato común; también la usan los manejadores de cada módulo.
- `infrastructure.config.ClockConfig` provee el `Clock` en UTC.

- `domain.Money`: monto con moneda y escala 2 (ADR-0006). Rechaza más de 2 decimales o más de 17 dígitos enteros (`money.amount_invalid`). Lo usan `finance` y, más adelante, `capture` y `reports`.

**Riesgo a vigilar:** que `shared` se convierta en un cajón de utilidades. Antes de agregar algo, comprueba que lo usan al menos dos módulos.

## identity (Etapa 2)

- **Dominio:**
  - `User`: bloqueo por intentos fallidos, nombre y zona horaria.
  - `Email`: normalizado.
  - `PasswordPolicy`: de 12 a 64 caracteres y 72 bytes como máximo.
  - Excepciones con código `identity.*`.
- **Casos de uso:** `RegisterUser`, `AuthenticateUser`, `GetProfile`, `UpdateProfile` y `ChangePassword`.
- **Puertos de salida y sus adaptadores:**
  - `UserRepository`: JPA sobre `identity.users`.
  - `PasswordHasher`: `DelegatingPasswordEncoder` (BCrypt).
  - `SessionTerminator`: índice por usuario de Spring Session.
  - `RegistrationPolicy`: `REGISTRATION_ENABLED`.
- **Infraestructura:**
  - `SecurityConfig`: sesión, CSRF para SPA y logout.
  - `SessionLogin`: abre la sesión tras autenticar.
  - `AuthController` y `ProfileController`.
  - `IdentityExceptionHandler`: traduce a 401, 403 y 429.
- **API publicada (`identity.api`):**
  - `@CurrentUser`: resuelve el `UserId` de la sesión para los controladores de cualquier módulo.
  - `UserTimeZones`: zona horaria del perfil, para que otros módulos calculen "hoy" y los límites de los periodos. La implementa `ResolveUserTimeZone`.
  - `PersonNames`: nombres y apellidos legales del perfil (`PersonName`, opcionales, migración V8), que `capture` usa para reconocer transferencias a uno mismo. La implementa `ResolvePersonNames`. Su edición desde el perfil llega en el paso 4 de la Etapa 4.
  - Los otros módulos declaran `allowedDependencies = "identity :: api"`.
- **Planeado:** el evento `UserDeleted`, cuando exista la eliminación de cuentas (D15).

Ver [ADR-0011](../decisions/0011-autenticacion-bloqueo-y-contrasenas.md) y [ADR-0012](../decisions/0012-registro-cerrado-y-primera-cuenta.md).

## finance (Etapa 3, en curso)

- Es la única fuente de verdad del dinero.
- **Dominio (`domain.model`):**
  - `Account`, jerarquía sellada de productos: `CashAccount`, `DepositAccount`, `CreditCardAccount` y `OtherAccount`.
  - `Transaction`, jerarquía sellada de movimientos: `Income`, `Expense`, `Transfer` y `Adjustment`. Cada subtipo define sus efectos en saldos (`balanceEffects()`) y en reportes (`reportEffect()`).
  - `TransactionDetails`: fecha, descripción, comercio y `PaymentChannel`.
  - `Category`: dos niveles, de ingreso o de gasto. `DefaultCategoryCatalog` contiene el catálogo sugerido.
  - `Period` y `PeriodComparison`: día, semana o mes, y la comparación con el periodo anterior.
  - `SupportedCurrencies`: solo COP.
  - Excepciones con código `finance.*`.
- **Decisiones:** [ADR-0013](../decisions/0013-modelo-contable.md) (modelo contable), [ADR-0014](../decisions/0014-productos-otro-y-categorias.md) (productos, "Otro" y categorías) y [ADR-0015](../decisions/0015-edicion-y-borrado-de-movimientos.md) (edición y borrado).
- **Casos de uso (`application.usecase`):**
  - Bienvenida: `GetFinanceStatus` y `SetUpFinance` (idempotente).
  - Productos: `ListAccounts` (con saldos y patrimonio), `CreateAccount`, `UpdateAccount`, `ArchiveAccount`, `ReactivateAccount` y `DeleteAccount`.
  - Categorías: `ListCategories` (árbol), `CreateCategory`, `RenameCategory`, `ArchiveCategory`, `ReactivateCategory` y `DeleteCategory`.
  - Movimientos: `RecordTransaction`, `UpdateTransaction`, `DeleteTransaction` y `ListTransactions` (filtros y paginación).
  - Resumen: `GetPeriodSummary` calcula los totales del periodo y del anterior, el detalle por producto y por categoría principal, y la tendencia de 6 periodos. Calcula "hoy" con `identity.api.UserTimeZones`.
- **Puertos de salida:**
  - `AccountRepository`, `CategoryRepository` y `TransactionRepository`. Todos filtran por propietario y su método `require` responde 404 ante un recurso ajeno.
  - `TransactionTotals`: sumas diarias y cambios de saldo calculados en SQL.
- **Persistencia (`infrastructure.persistence`):**
  - Migraciones V4 (productos), V5 (categorías), V6 (movimientos) y V7 (origen y monto estimado de los movimientos) en el esquema `finance`.
  - Una tabla por jerarquía (`accounts`, `transactions`), con columna de tipo y restricciones `CHECK` que fijan qué columnas lleva cada tipo.
  - Llaves foráneas compuestas con `user_id` entre productos, categorías y movimientos (ADR-0003), y hacia `identity.users` (ADR-0005).
  - Índices únicos: nombre de producto por usuario sin distinguir mayúsculas, un solo "Otro" por usuario y nombre de categoría entre hermanas del mismo tipo.
  - Adaptadores JPA con mappers manuales. El listado de movimientos usa `Specification` de Spring Data y ordena de forma estable.
  - `TransactionTotalsAdapter` calcula las sumas diarias y los cambios de saldo en SQL con `JdbcClient`. `TransactionTotalsConsistencyTests` las compara con las reglas del dominio sobre 80 movimientos generados.
- **API REST (`infrastructure.web`):**
  - Controladores de bienvenida, productos, categorías, movimientos y resumen bajo `/api/v1/finance`. Las rutas, campos y códigos están en [convenciones de la API](../api/conventions.md#finanzas).
  - Los cuerpos son planos, con `kind` o `type`. `AccountRequest` y `TransactionRequest` validan los campos según el tipo con `TypedFields` y arman los datos de los casos de uso.
  - Los montos viajan como cadenas (`Amounts`). Los listados usan `PageResponse`.
  - Cada endpoint tiene pruebas de acceso cruzado (`FinanceCrossAccessTests`).
- **Origen de los movimientos (Etapa 4):** `TransactionOrigin` (`MANUAL` o `CAPTURE`) y `amountEstimated`, que marca los montos convertidos de dólares con la TRM. El monto deja de ser estimado cuando el usuario lo cambia. Ambos campos viajan en `TransactionResponse`.
- **API publicada (`finance.api`), sin tipos del dominio:**
  - `FinanceProducts`: productos activos (`ProductSummary`, sin saldos) y el producto "Otro", que se crea si no existe. La implementa `ListFinanceProducts`.
  - `CapturedTransactions`: registra un movimiento captado (`CapturedTransaction`) con las mismas reglas que el registro manual, sin categoría y con origen `CAPTURE`. La implementa `RecordCapturedTransaction`.
- El dashboard y las consultas financieras viven aquí. `reports` las consumirá a través de `finance.api`.
- Nunca tendrá referencias a vehículos. `garage` guarda el identificador del movimiento que crea mediante `finance.api`.

## capture

Etapa 4, ADR-0017. Depende de `shared`, `identity :: api` y `finance :: api`. Falta la entrada (endpoint, token de dispositivo y trabajador de la cola), que llega en el paso 4.

- **`domain.model`:**
  - **Notificación:** `RawNotification` (app, título, texto, hora de recepción).
  - **Resultado del análisis:** `ParsedMovement` y `ParseOutcome`.
  - **Producto:** `ProductHint`, con entidad, tipo y últimos 4 dígitos opcionales.
  - **Nombre del usuario:** `OwnerName`. Coincide si el nombre tiene al menos un nombre y un apellido del usuario, en cualquier orden.
- **`domain.parsing`:**
  - **Analizadores por fuente:** `BancolombiaSmsParser` y `RappiPushParser`, combinados en `NotificationParsers`.
  - **Plantillas:** expresiones ancladas, con formato de monto (`AmountFormat`) y de fecha declarados.
  - **Lo que no encaja exactamente:** es `Unrecognized` y va a revisión.
- **`application`:**
  - **`ProcessNotification`:** analiza una notificación de la cola, identifica el producto (`ProductResolution`), decide el tipo (gasto, ingreso o transferencia a una llave propia), convierte los dólares con la TRM y empareja los avisos de la misma compra que llegan por fuentes distintas (2 minutos de espera; mismo producto y monto en 5 minutos). Después registra el movimiento con el puerto `Ledger` o deja la notificación "Por revisar" (`ReviewReason`).
  - **Puertos de salida:**
    - `UserProducts` y `Ledger` → `finance :: api`;
    - `CaptureOwners` → `identity :: api`;
    - `OwnKeys`;
    - `ExchangeRates` (TRM de datos.gov.co);
    - `CapturedNotificationRepository`.
- **Persistencia (`infrastructure.persistence`, migración V9, esquema `capture`):**
  - `notifications`: cola y resultado de cada notificación. Incluye:
    - el texto crudo, que se borra a los 45 días;
    - `content_hash`, único por usuario, para ignorar los reenvíos exactos;
    - el estado y el motivo de revisión;
    - el movimiento de `finance` (solo el identificador);
    - `paired_with`, con índice único parcial que garantiza el emparejamiento de uno a uno;
    - la huella para emparejar.
  - `own_keys`: llaves Bre-B propias, normalizadas, con el producto al que llegan. Sin llave foránea hacia `finance`: una llave de un producto borrado o archivado se ignora.
- **Integración (`infrastructure.integration`):**
  - `FinanceProductsAdapter` y `FinanceLedgerAdapter` → `finance :: api`.
  - `IdentityOwnersAdapter` → `identity :: api`.
  - `DatosGovExchangeRates`: TRM con `RestClient`, sin dependencias nuevas. Usa tiempos de espera de 3 y 5 segundos y guarda en memoria las tasas ya vigentes. Si la fuente falla, la notificación queda "Por revisar". La URL se configura con `app.capture.exchange-rate-url`.
- **Pruebas:**
  - `CaptureIntegrationTests`: de punta a punta contra PostgreSQL.
  - `DatosGovExchangeRatesTests`: con `MockRestServiceServer`.

## messaging, reports y garage

Se documentarán al crearse.
