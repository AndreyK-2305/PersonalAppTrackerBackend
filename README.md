# Personal Tracker — Backend

API de **Personal Tracker**, una plataforma web de gestión personal enfocada inicialmente en finanzas.
Es un **monolito modular con arquitectura hexagonal** construido con Java 21, Spring Boot 4.1 y PostgreSQL 18.

El frontend vive en un repositorio aparte: [PersonalAppTrackerFront](https://github.com/AndreyK-2305/PersonalAppTrackerFront).

## Requisitos

- **JDK 21** (Eclipse Temurin recomendado).
- **Docker Desktop**, para PostgreSQL en desarrollo y para las pruebas de integración (Testcontainers).
- No hace falta instalar Maven: se usa el wrapper (`./mvnw` o `.\mvnw.cmd`).

## Inicio rápido

```bash
cp .env.example .env          # opcional: los valores por defecto sirven para desarrollo
docker compose up -d          # PostgreSQL en localhost:5434
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

En PowerShell: `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"`.

| URL | Descripción |
|---|---|
| http://localhost:8090/actuator/health | Health check (público) |
| http://localhost:8090/swagger-ui.html | Swagger UI (solo perfil `local`) |
| http://localhost:8090/v3/api-docs | Documento OpenAPI (solo perfil `local`) |

Los puertos locales son **5434** (PostgreSQL) y **8090** (API), y no los estándar 5432/8080, para convivir con otros proyectos locales. Se cambian con `DB_PORT` y `BACKEND_PORT`. Dentro del contenedor, el backend siempre escucha en 8080.

## Comandos

| Comando | Qué hace |
|---|---|
| `./mvnw verify` | Compila y ejecuta todas las pruebas: unitarias, de arquitectura e integración. Requiere Docker. |
| `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` | Ejecuta la API en el puerto 8090. |
| `docker compose up -d` | Levanta solo PostgreSQL para desarrollo. |
| `docker compose --profile full up -d --build --wait` | Levanta PostgreSQL, el backend (perfil `prod`) y el frontend en contenedores; la aplicación queda en http://localhost:8091. Requiere el repositorio del frontend clonado en `../frontend`. |
| `docker compose down` | Detiene los contenedores. Agrega `-v` para borrar también los datos locales. |

## Perfiles y configuración

| Perfil | Uso |
|---|---|
| `local` | Desarrollo. Valores por defecto que apuntan al PostgreSQL de Docker Compose, Swagger habilitado y logs de depuración. |
| `prod` | Producción. Las credenciales son obligatorias por variables de entorno, los logs son estructurados (ECS) y Swagger está deshabilitado. |
| (pruebas) | Sin perfil. Testcontainers provee PostgreSQL. |

Variables de entorno (ver `.env.example`):

| Variable | Descripción | Por defecto en `local` |
|---|---|---|
| `DB_URL` | URL JDBC completa | `jdbc:postgresql://localhost:${DB_PORT}/${DB_NAME}` |
| `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `DB_PORT` | Base de datos | `personal_tracker` / `personal_tracker` / `personal_tracker` / `5434` |
| `BACKEND_PORT` | Puerto de la API en el host (perfil `local` y perfil `full` de Compose) | `8090` |
| `FRONTEND_PORT` | Puerto del frontend (nginx) con el perfil `full` de Compose | `8091` |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos, separados por comas | vacío (ninguno) |

Nunca subas `.env` ni secretos al repositorio.

## Estructura

```text
src/main/java/com/personaltracker/
├── PersonalTrackerApplication.java
├── shared/          núcleo compartido: excepciones de dominio, manejo de errores, CORS, OpenAPI
└── identity/        seguridad (Etapa 1); usuarios y sesión (Etapa 2)
src/main/resources/
├── application.yml, application-local.yml, application-prod.yml
└── db/migration/    migraciones Flyway: V{n}__{modulo}_{descripcion}.sql
src/test/java/com/personaltracker/
├── architecture/    ModularityTests (Spring Modulith) y HexagonalArchitectureTests (ArchUnit)
└── support/         Testcontainers
docs/
├── architecture/    visión general, módulos y glosario de dominio
├── api/             convenciones de la API
└── decisions/       ADR
```

Cada módulo sigue la estructura `domain/`, `application/` (`usecase/`, `port/out/`, `dto/`), `infrastructure/` (`web/`, `persistence/`, `config/`) y `api/`. Los módulos se crean cuando llega su primer caso de uso real.

## Pruebas

- **Arquitectura:**
  - Spring Modulith verifica que no haya ciclos ni accesos a paquetes internos de otros módulos.
  - ArchUnit verifica las reglas hexagonales: dominio sin frameworks, aplicación sin infraestructura y sin `float`/`double` en el núcleo.
- **Integración:**
  - La aplicación completa corre contra PostgreSQL real y se comprueban migraciones, health check, seguridad por defecto, CORS y documentación deshabilitada.
- **Web:**
  - Formato de errores (RFC 9457) y filtro de correlación.

`./mvnw verify` también genera los diagramas de módulos en `target/spring-modulith-docs/`.

## Documentación

- [Visión general de la arquitectura](docs/architecture/overview.md)
- [Módulos y dependencias](docs/architecture/modules.md)
- [Glosario de dominio](docs/architecture/domain-glossary.md)
- [Convenciones de la API](docs/api/conventions.md)
- [Decisiones de arquitectura (ADR)](docs/decisions/README.md)

## Contribución

- Commits con Conventional Commits en español: `feat:`, `fix:`, `chore:`, `docs:`, `test:`, `refactor:`.
- Cada decisión de arquitectura relevante se registra como ADR en `docs/decisions/`.
- `./mvnw verify` debe pasar antes de cada push. CI lo ejecuta en cada push y pull request.
