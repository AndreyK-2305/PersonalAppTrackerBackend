# ADR-0010: Plataforma base, dependencias y CI

- Estado: Aceptada
- Fecha: 2026-11-06

## Contexto

El contexto fija las tecnologías obligatorias y prohíbe agregar dependencias sin justificarlas. Se necesita una base actual, con soporte y reproducible.

## Decisión

**Plataforma**

| Componente | Versión | Motivo |
|---|---|---|
| Java | 21 LTS (Eclipse Temurin) | Versión exigida; con soporte a largo plazo. |
| Spring Boot | 4.1.2 | Última estable al crear el proyecto. Trae Spring Framework 7, Spring Security 7, Hibernate 7.4 y Jackson 3. |
| Maven | Wrapper (Maven 3.10) | Builds reproducibles sin instalar Maven. |
| PostgreSQL | 18.6 (imagen `postgres:18.6-alpine`) | Versión estable actual; la misma en Compose y en Testcontainers. |
| Spring Modulith | 2.1.1 | Alineado con Boot 4.1. |
| springdoc-openapi | 3.2.0 | Línea compatible con Boot 4. |
| Testcontainers | 2.0.x (gestionado por Boot) | Compatible con Docker Engine 29 o superior. |

**Dependencias que no estaban en la lista obligatoria del contexto, y su justificación**

| Dependencia | Problema que resuelve |
|---|---|
| `spring-boot-starter-actuator` | Health check estándar con verificación de la base de datos y sondas liveness/readiness para Docker y AWS. Solo se expone `health`. |
| `spring-modulith-api`, `-starter-test`, `-docs` | Anotaciones de módulo, verificación de límites y diagramas. No se incluyen el runtime, la observabilidad ni el registro de eventos JPA, que Initializr agrega por defecto. |
| `archunit-junit5` | Verifica las reglas hexagonales dentro de cada módulo. |
| `spring-boot-starter-security-test` | Prueba la configuración de seguridad. |
| `spring-session-jdbc` (Etapa 2) | Sesiones persistentes ([ADR-0004](0004-autenticacion-por-sesion.md)). |

**Excluidas a propósito:** Lombok, MapStruct, el SDK de Resend (se usará `RestClient`), Hibernate Envers, Kafka, RabbitMQ y Redis.

**Ajustes de configuración**

- Se excluye `UserDetailsServiceAutoConfiguration` para que no exista un usuario en memoria con contraseña generada. `identity` proveerá la autenticación real.
- `spring.jpa.open-in-view=false`.

**Imagen Docker**

- Build multi-stage: JDK 21 para compilar y JRE 21 para ejecutar.
- Capas del jar extraídas con `jarmode=tools` para aprovechar la caché.
- Usuario del sistema sin privilegios.

**CI (GitHub Actions)**

- Se ejecuta en cada push a `main` y en cada pull request.
- Corre `./mvnw verify` (incluye Testcontainers con el Docker del runner) y construye la imagen Docker.

## Consecuencias

- **Spring Boot 4 es reciente:** algunas librerías tardan en ser compatibles; cada dependencia nueva debe verificarse contra Boot 4.
- **Las pruebas de integración necesitan Docker** en local y en CI.
- **Actualizaciones:** las versiones se actualizan de forma deliberada, una por cambio, verificando con `./mvnw verify`.

## Alternativas consideradas

- **Spring Boot 3.5:** tiene menos soporte por delante y obligaría a migrar pronto.
- **Gradle:** el contexto exige Maven.
