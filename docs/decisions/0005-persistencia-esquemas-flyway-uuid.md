# ADR-0005: Persistencia: esquema por módulo, Flyway y UUID

- Estado: Aceptada
- Fecha: 2026-11-06

## Contexto

Los módulos comparten una base de datos PostgreSQL ([ADR-0001](0001-monolito-modular-hexagonal.md)). Hay que dejar clara la propiedad de cada tabla y versionar el esquema. Además, las entidades de dominio deben tener identidad sin depender de la persistencia.

## Decisión

- **Un esquema PostgreSQL por módulo** (`identity`, `finance`, …), creado por la primera migración del módulo. La tabla de historial de Flyway vive en `public`.
- **Propiedad de las tablas:** cada módulo es dueño de sus tablas. No hay relaciones JPA entre módulos, solo columnas con identificadores. Ningún módulo lee las tablas de otro; se usa su `api`.
- **Las llaves foráneas entre esquemas se permiten** cuando protegen la integridad, por ejemplo `finance.*.user_id → identity.users.id`.
- **Flyway es dueño del esquema:**
  - Hibernate solo valida (`ddl-auto=validate`).
  - Las migraciones viven en `src/main/resources/db/migration` con el nombre `V{n}__{modulo}_{descripcion}.sql`, numeradas en secuencia.
  - Una migración aplicada nunca se modifica; las correcciones van en una migración nueva.
- **Identificadores:** UUID generados en el dominio, de modo que la entidad tiene identidad antes de persistirse y los IDs no se pueden enumerar.
- **Auditoría:** todas las tablas tienen `created_at` y `updated_at` (`timestamptz`).
- **Entidades JPA:** viven en `infrastructure.persistence`, separadas de las de dominio, con mappers manuales.

## Consecuencias

- **A favor:**
  - La propiedad de cada tabla es visible en la base de datos.
  - Hibernate detecta en el arranque cualquier diferencia entre entidades y esquema.
  - Extraer un módulo en el futuro sería más simple.
- **En contra:** las entidades JPA declaran el esquema (`@Table(schema = "finance")`).
- **Numeración de migraciones:** la secuencial basta con un solo desarrollador. Con varias ramas en paralelo habría que pasar a versiones basadas en fecha.

## Alternativas consideradas

- **Todo en `public` con prefijos de tabla:** la propiedad queda solo como convención.
- **`ddl-auto=update`:** no reproducible e inseguro para datos financieros.
- **IDs secuenciales (`bigserial`):** se pueden enumerar y la entidad depende de la base de datos para tener identidad.
