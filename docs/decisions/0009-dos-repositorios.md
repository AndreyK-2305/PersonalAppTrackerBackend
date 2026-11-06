# ADR-0009: Backend y frontend en repositorios independientes

- Estado: Aceptada
- Fecha: 2026-11-06

## Contexto

La propuesta inicial era un único repositorio con `backend/` y `frontend/`. El usuario decidió usar dos repositorios en GitHub:

- [PersonalAppTrackerBackend](https://github.com/AndreyK-2305/PersonalAppTrackerBackend)
- [PersonalAppTrackerFront](https://github.com/AndreyK-2305/PersonalAppTrackerFront)

Ambos se clonan como carpetas hermanas en un workspace local que no es un repositorio y que contiene `PROJECT_CONTEXT.md` y `AGENTS.md`.

## Decisión

- **Contenido de cada repositorio:** cada uno tiene su propio README, `.env.example`, Dockerfile, CI y `docs/`.
- **Decisiones de sistema:** las que afectan a todo el sistema se documentan en **este** repositorio (`docs/decisions/`), porque el backend es dueño del dominio y del contrato de la API. Las exclusivas del frontend van en `frontend/docs/`.
- **`docker-compose.yml`** vive en el backend:
  - sin perfil, levanta solo PostgreSQL;
  - con el perfil `full`, levanta el stack completo y construye `../frontend`, lo que requiere que los repositorios estén clonados como carpetas hermanas.
- **Contrato de la API:**
  - Se versiona con `/api/v1`.
  - El frontend genera sus tipos desde el OpenAPI del backend con `openapi-typescript` (Etapa 2) y los versiona en su repositorio.

## Consecuencias

- **A favor:** historiales, CI y permisos independientes.
- **En contra:**
  - Los cambios incompatibles de la API requieren coordinar dos repositorios y dos PR.
  - El stack completo con Compose depende de la ubicación relativa de ambos repositorios.
- **Documentos sin versionar:** `PROJECT_CONTEXT.md` y `AGENTS.md` del workspace no están en ningún repositorio; dependen del historial de OneDrive.

## Alternativas consideradas

- **Un solo repositorio:** facilita los cambios atómicos entre backend y frontend, pero no fue la opción elegida.
