# Módulos y dependencias

| Módulo | Responsabilidad | Puede depender de | Esquema BD | Estado |
|---|---|---|---|---|
| `shared` | Núcleo compartido mínimo | — | — | Creado (Etapa 1) |
| `identity` | Usuarios, credenciales, sesión y seguridad | `shared`, `notifications` (api) | `identity` | Completo (Etapa 2) |
| `finance` | Dinero: cuentas, movimientos, tarjetas, cuotas, préstamos y dashboard | `shared`, `identity` (api) | `finance` | Etapa 3 |
| `notifications` | Entrega de comunicaciones (correo con Resend) | `shared` | `notifications` | Etapa 5 (o Etapa 2, pendiente) |
| `reports` | Reportes consolidados y programados, exportaciones | `shared`, `finance` (api), `notifications` (api) | `reports` | Etapa 5 |
| `garage` | Vehículos | `shared`, `finance` (api) | `garage` | Posterior al MVP |

Reglas generales:

- El grafo es acíclico. `finance` no conoce a `reports`, `notifications` ni `garage`.
- Un módulo solo usa de otro su paquete `api` (`@NamedInterface`). `shared` es un módulo abierto.
- No hay relaciones JPA entre módulos, solo identificadores. Ningún módulo lee las tablas de otro.
- Un módulo se crea cuando llega su primer caso de uso real, nunca como placeholder.

## shared

Contiene solo lo que realmente comparten varios módulos:

- `domain`: `DomainException` (responde 422) y `NotFoundException` (responde 404). Los módulos extienden estas clases con su propio `code`.
- `infrastructure.web`: `GlobalExceptionHandler`, `CorrelationIdFilter` y CORS (`CorsConfig`, `CorsProperties`).
- `infrastructure.openapi`: metadatos del documento OpenAPI.

- `domain` también incluye:
  - `UserId`, el identificador del propietario que usan todos los módulos;
  - `ConflictException`, que responde 409.
- `infrastructure.web.ApiProblems` construye los errores con el formato común; también la usan los manejadores de cada módulo.
- `infrastructure.config.ClockConfig` provee el `Clock` en UTC.

Planeado: el objeto de valor `Money` (Etapa 3), cuando lo use el primer caso de uso.

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
- **API publicada (`identity.api`):** `@CurrentUser`, que resuelve el `UserId` de la sesión para los controladores de cualquier módulo. Los otros módulos declaran `allowedDependencies = "identity :: api"`.
- **Planeado:** el evento `UserDeleted`, cuando exista la eliminación de cuentas (D15).

Ver [ADR-0011](../decisions/0011-autenticacion-bloqueo-y-contrasenas.md) y [ADR-0012](../decisions/0012-registro-cerrado-y-primera-cuenta.md).

## finance (Etapa 3)

- Es la única fuente de verdad del dinero.
- Modelo propuesto: libro de movimientos sobre cuentas propias (ver `PROJECT_CONTEXT.md` §9.1). Se formaliza en un ADR al inicio de la Etapa 3.
- El dashboard y las consultas financieras viven aquí. `reports` las consume a través de `finance.api`.
- Nunca tendrá referencias a vehículos. `garage` guarda el identificador del movimiento que crea mediante `finance.api`.

## notifications, reports y garage

Se documentarán al crearse.
