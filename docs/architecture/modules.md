# Módulos y dependencias

| Módulo | Responsabilidad | Puede depender de | Esquema BD | Estado |
|---|---|---|---|---|
| `shared` | Núcleo compartido mínimo | — | — | Creado (Etapa 1) |
| `identity` | Usuarios, credenciales, sesión y seguridad | `shared`, `notifications` (api) | `identity` | Seguridad base (Etapa 1). Usuarios y sesión en la Etapa 2. |
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

Planeado: los objetos de valor `UserId` (Etapa 2) y `Money` (Etapa 3) cuando los use el primer caso de uso.

**Riesgo a vigilar:** que `shared` se convierta en un cajón de utilidades. Antes de agregar algo, comprueba que lo usan al menos dos módulos.

## identity

- **Hoy:** `SecurityConfig` deniega todo salvo las rutas públicas y delega los errores 401/403 al manejador global.
- **Etapa 2:** registro, inicio y cierre de sesión con Spring Session JDBC, CSRF para la SPA, perfil, cambio de contraseña y límite de intentos de login.
- **API publicada (Etapa 2):** `@CurrentUser`, que resuelve el `UserId` de la sesión para los controladores de otros módulos, y el evento `UserDeleted`.

## finance (Etapa 3)

- Es la única fuente de verdad del dinero.
- Modelo propuesto: libro de movimientos sobre cuentas propias (ver `PROJECT_CONTEXT.md` §9.1). Se formaliza en un ADR al inicio de la Etapa 3.
- El dashboard y las consultas financieras viven aquí. `reports` las consume a través de `finance.api`.
- Nunca tendrá referencias a vehículos. `garage` guarda el identificador del movimiento que crea mediante `finance.api`.

## notifications, reports y garage

Se documentarán al crearse.
