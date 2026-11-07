# Registro de decisiones de arquitectura (ADR)

Cada decisión relevante se registra como un archivo `NNNN-titulo.md` con este formato:

```markdown
# ADR-NNNN: Título

- Estado: Propuesta | Aceptada | Reemplazada por ADR-XXXX
- Fecha: AAAA-MM-DD

## Contexto
## Decisión
## Consecuencias
## Alternativas consideradas
```

Un ADR aceptado no se edita para cambiar la decisión. Se crea uno nuevo que lo reemplaza.

| ADR | Título | Estado |
|---|---|---|
| [0001](0001-monolito-modular-hexagonal.md) | Monolito modular con arquitectura hexagonal | Aceptada |
| [0002](0002-convenciones-de-capas-y-puertos.md) | Convenciones de capas, puertos e idioma del código | Aceptada |
| [0003](0003-aislamiento-por-propietario.md) | Aislamiento de datos por propietario | Aceptada |
| [0004](0004-autenticacion-por-sesion.md) | Autenticación por sesión en el servidor | Aceptada |
| [0005](0005-persistencia-esquemas-flyway-uuid.md) | Persistencia: esquema por módulo, Flyway y UUID | Aceptada |
| [0006](0006-dinero-y-fechas.md) | Representación de dinero y fechas | Aceptada |
| [0007](0007-topologia-mismo-origen-y-cors.md) | Topología de mismo origen y CORS restringido | Aceptada |
| [0008](0008-errores-problem-detail.md) | Formato de errores con ProblemDetail (RFC 9457) | Aceptada |
| [0009](0009-dos-repositorios.md) | Backend y frontend en repositorios independientes | Aceptada |
| [0010](0010-plataforma-base-y-dependencias.md) | Plataforma base, dependencias y CI | Aceptada |
| [0011](0011-autenticacion-bloqueo-y-contrasenas.md) | Flujo de autenticación, bloqueo por intentos y política de contraseñas | Aceptada |
| [0012](0012-registro-cerrado-y-primera-cuenta.md) | Registro cerrado y alta de la primera cuenta | Aceptada |
| [0013](0013-modelo-contable.md) | Modelo contable: libro de movimientos sobre productos propios | Aceptada |
| [0014](0014-productos-otro-y-categorias.md) | Productos, producto "Otro" y categorías | Aceptada |
| [0015](0015-edicion-y-borrado-de-movimientos.md) | Edición y borrado de movimientos | Aceptada |
| [0016](0016-modulos-capture-y-messaging.md) | Módulos `capture` y `messaging`, y orden de etapas | Aceptada |
