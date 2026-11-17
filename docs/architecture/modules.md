# Módulos y dependencias

| Módulo | Responsabilidad | Puede depender de | Esquema BD | Estado |
|---|---|---|---|---|
| `shared` | Núcleo compartido mínimo | — | — | Creado (Etapa 1) |
| `identity` | Usuarios, credenciales, sesión y seguridad | `shared`, `messaging` (api) | `identity` | Completo (Etapa 2) |
| `finance` | Dinero: productos, categorías, movimientos, cuotas, préstamos y dashboard | `shared`, `identity` (api) | `finance` | Completo (Etapa 3) |
| `capture` | Entrada de notificaciones bancarias: cola, análisis y bandeja de revisión | `shared`, `identity` (api), `finance` (api) | `capture` | Completo (Etapa 4) |
| `goals` | Metas de ahorro y metas financiadas (simulador y plan de pagos con pagos, abonos y ajustes de tasa). No crea movimientos | `shared`, `identity` (api) | `goals` | Completo (Metas, 3 entregas) |
| `messaging` | Entrega de correos con Resend (antes `notifications`) | `shared` | `messaging` | Etapa 5 |
| `reports` | Reportes programados y exportaciones | `shared`, `identity` (api), `finance` (api), `messaging` (api) | `reports` | Etapa 5 |
| `garage` | Vehículos | `shared`, `finance` (api) | `garage` | Posterior al MVP |

Los nombres y el orden de etapas están en [ADR-0016](../decisions/0016-modulos-capture-y-messaging.md).

Reglas generales:

- El grafo es acíclico. `finance` no conoce a `capture`, `reports`, `messaging`, `goals` ni `garage`.
- Un módulo solo usa de otro su paquete `api` (`@NamedInterface`). `shared` es un módulo abierto.
- No hay relaciones JPA entre módulos, solo identificadores. Ningún módulo lee las tablas de otro.
- Un módulo se crea cuando llega su primer caso de uso real, nunca como placeholder.

## shared

Contiene solo lo que realmente comparten varios módulos:

- `domain`: `DomainException` (responde 422) y `NotFoundException` (responde 404). Los módulos extienden estas clases con su propio `code`.
- `infrastructure.web`: `GlobalExceptionHandler`, `CorrelationIdFilter` y CORS (`CorsConfig`, `CorsProperties`).
  - `GlobalExceptionHandler` también traduce `RequestValidationException` y las restricciones de parámetros a 400 `validation_error`, y las violaciones de índice único y las ediciones concurrentes (bloqueo optimista) a 409 `conflict`.
- `infrastructure.openapi`: metadatos del documento OpenAPI.

- `domain` también incluye:
  - `UserId`, el identificador del propietario que usan todos los módulos;
  - `ConflictException`, que responde 409.
- `infrastructure.web.ApiProblems` construye los errores con el formato común; también la usan los manejadores de cada módulo.
- `infrastructure.config.ClockConfig` provee el `Clock` en UTC.

- `domain.Money`: monto con moneda y escala 2 (ADR-0006). Rechaza más de 2 decimales o más de 17 dígitos enteros (`money.amount_invalid`). Lo usan `finance` y, más adelante, `capture` y `reports`.

- `domain.finance`: calculadora financiera en `BigDecimal` con 34 dígitos (ADR-0018). La usa `goals` y, en la Etapa 6, `finance`.
  - `AnnualRate`: tasa efectiva anual como fracción `NUMERIC(9,6)`; `monthly()` da la tasa mensual equivalente.
  - `FinancialMath`: raíz n-ésima (Newton; falla si no converge), crecimiento compuesto y redondeo hacia arriba al peso.
  - `Amortization`: cuota fija exacta (sistema francés), tabla de amortización y la siguiente cuota de un crédito en curso (`nextRow`), con el interés de cada periodo redondeado al centavo.
- `infrastructure.web.ApiFormats`: patrones de los montos y porcentajes que viajan como texto. Los usan las validaciones de `finance` y `goals`.

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
- **Tokens de dispositivo (Etapa 4, V10):** `DeviceToken` con permisos `DeviceScope` y secreto `DeviceTokenSecret` (solo se guarda su SHA-256). El caso de uso `DeviceTokens` los crea, lista, revoca y autentica, y `DeviceTokenController` los expone en `/api/v1/me/device-tokens`.
  - **Seguridad:** `SecurityConfig` tiene una segunda cadena para `/api/v1/device/**`, sin sesión ni CSRF. En ella, `DeviceTokenAuthenticationFilter` deja como principal al dueño (`@CurrentUser` funciona igual) con autoridades `SCOPE_<permiso>`.
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
    - Desde la revisión también lo borra o lo convierte: gasto ↔ transferencia, borrando y registrando de nuevo, sin publicar eventos.
  - `TransactionDeleted`: evento que publica `DeleteTransaction` (puerto `TransactionEvents`, adaptador `SpringTransactionEvents`) cuando el usuario borra un movimiento captado. Corre en la misma transacción.
- El dashboard y las consultas financieras viven aquí. `reports` las consumirá a través de `finance.api`.
- Nunca tendrá referencias a vehículos. `garage` guarda el identificador del movimiento que crea mediante `finance.api`.

## capture

Etapa 4, ADR-0017. Depende de `shared`, `identity :: api` y `finance :: api`.

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
- **Ajustes y revisión:**
  - `ManageOwnKeys` gestiona las llaves Bre-B propias, que solo apuntan a cuentas o billeteras activas.
  - `ReviewNotifications` ofrece las acciones de revisión: confirmar, "Es mía", valor en COP (reabre la notificación y la procesa con `ProcessNotification.processWithManualAmount`), descartar y "No es mía".
  - `DiscardDeletedTransaction` responde a `TransactionDeleted` (`FinanceEventListener`).
  - Controladores `OwnKeyController` y `ReviewController` bajo `/api/v1/capture`.
- **Entrada y cola:**
  - **`ReceiveNotification`:** guarda lo que envía el atajo (`CaptureController`, `POST /api/v1/device/capture/notifications`, 202). Ignora los reenvíos exactos por `content_hash`.
  - **`ProcessPendingNotifications`:** toma las pendientes que ya pasaron la espera (bancos antes que Wallet) y procesa cada una en su propia transacción con `ProcessNotification`, que la bloquea con `FOR UPDATE SKIP LOCKED`.
  - **`PurgeRawNotifications`:** borra el título y el texto a los 45 días.
  - **`CaptureJobs`:** ejecuta la cola cada 30 segundos y la limpieza a las 3:30 (hora de Bogotá). Se desactiva con `app.capture.jobs.enabled=false`, como hacen las pruebas.
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

## goals

Metas del usuario (ADR-0018). Entrega 1: metas de ahorro. Entrega 2: metas financiadas (simulador y tabla de amortización). Entrega 3: pagos, abonos extraordinarios y ajustes de tasa. No depende de `finance`: los abonos son marcas dentro de la meta y una meta financiada no crea deudas.

### Metas de ahorro

- **Dominio:**
  - `SavingsGoal`: nombre, objetivo y plan. `SavingsPlan` es una interfaz sellada (`ByTargetDate`, `ByMonthlyAmount`) y cada plan conoce su `SavingsPlanKind`.
  - `SavingsContribution` (abono) y `SavingsRateChange` (rentabilidad desde una fecha).
  - `SavingsProjection`: lo ahorrado, lo que falta, el avance y, según el plan, el aporte mensual recomendado o la fecha estimada. Recibe la fecha de hoy como parámetro.
  - Excepciones con un código por regla: `InvalidSavingsGoalException` e `InvalidContributionException`.
- **Casos de uso** (lo único público del paquete):
  - `ManageSavingsGoals`: listar, consultar, crear, editar y borrar.
  - `RecordSavingsContributions`: marcar y borrar abonos.
  - `AdjustSavingsRate`: activar, cambiar o apagar la rentabilidad.
  - Colaboradores no públicos: `GoalCalendar` (qué día es hoy para el usuario) y `SavingsGoalReader` (la meta con sus abonos, tasas y proyección).
- **Puertos de salida:** `SavingsGoalRepository` (metas, abonos y tasas; todo filtrado por propietario) y `GoalOwners` (zona horaria).
- **Adaptadores:**
  - `persistence`: `SavingsGoalPersistenceAdapter` sobre el esquema `goals` (V11 y V12).
  - `integration`: `IdentityGoalOwnersAdapter`, con `identity :: api`.
  - `web`: `SavingsGoalController` en `/api/v1/goals/savings`.
- **Reglas con fecha:** la fecha objetivo va desde mañana hasta 100 años; un abono no puede ser futuro ni tener más de 100 años. "Hoy" es el del perfil del usuario.
- **Pruebas:**
  - dominio y simulación mes a mes (`SavingsProjectionSimulationTests`);
  - casos de uso con dobles (`GoalsFakes`) y reloj controlado;
  - adaptador de persistencia contra PostgreSQL;
  - API (`SavingsGoalIntegrationTests`, `SavingsGoalApiEdgeCaseTests`);
  - arquitectura del módulo (`GoalsModuleArchitectureTests`).
- **Auditoría de la entrega 1:** [docs/revisiones/2026-11-16-metas-entrega-1.md](../revisiones/2026-11-16-metas-entrega-1.md).

### Metas financiadas

- **Dominio:**
  - `FinancedGoal`: nombre y condiciones de la financiación.
  - `FinancedTerms`: valor del bien, cuota inicial, tasa, plazo, fecha de la primera cuota y cuota real opcional. Valida sus reglas y calcula la cuota recomendada.
  - `FinancedRecord` (interfaz sellada): `InstallmentPayment`, `Prepayment` y `RateAdjustment`. Lo que pasa en el crédito después de crearlo.
  - `CreditPlan`: el estado del crédito. Repasa los registros en orden sobre las condiciones originales, llevando el saldo, la tasa y la cuota, y ahí valida cada regla. Es un cálculo puro: las mismas condiciones y registros dan siempre el mismo plan.
  - `PaymentSchedule`: la tabla de amortización, con las cuotas pagadas y las pendientes, y sus totales. La produce `CreditPlan` con `shared.domain.finance.Amortization` y **no se guarda**.
  - `InvalidFinancedGoalException` e `InvalidFinancedRecordException`, con un código por regla.
- **Casos de uso:**
  - `ManageFinancedGoals`: listar, consultar, crear, editar y borrar. Calcula el plan antes de guardar: si un total no cabe en un monto, la meta no llega a existir. Con registros, solo deja cambiar el nombre.
  - `SimulateFinancing`: calcula el plan de unas condiciones sin guardar nada (la vista previa del formulario).
  - `RecordCreditPayments`: pagar la siguiente cuota, marcar pagadas varias cuotas de una vez y abonar a capital.
  - `AdjustCreditRate`: ajuste de tasa desde la siguiente cuota.
  - `UndoCreditRecord`: deshacer el último registro.
  - Colaborador no público: `FinancedGoalReader`, que lee la meta con sus registros y, al agregar registros, repasa el plan completo antes de guardarlos: un registro inválido nunca llega a la base.
- **Puerto de salida:** `FinancedGoalRepository` (metas y sus registros), con su adaptador `FinancedGoalPersistenceAdapter` sobre `goals.financed_goals` (V13) y `goals.financed_goal_records` (V14). El orden de los registros es único por meta: dos altas simultáneas chocan y la API responde 409.
- **Web:** `FinancedGoalController` en `/api/v1/goals/financed` y `CreditActivityController` en `/api/v1/goals/financed/{id}` (pagos, abonos, ajustes y deshacer). El listado trae el resumen y el estado; el detalle y la simulación, la tabla completa. `GoalAmounts` convierte los montos y tasas de los dos controladores de Metas.
- **Pruebas:** `AmortizationTests` (valores calculados aparte e invariantes de la tabla), `FinancedTermsTests`, `CreditPlanTests` (el plan recalculado contra valores calculados aparte), `ManageFinancedGoalsTests` y `CreditActivityTests` (con dobles), `FinancedGoalPersistenceAdapterTests`, `FinancedGoalIntegrationTests` y `CreditActivityIntegrationTests`.

## messaging, reports y garage

Se documentarán al crearse.
