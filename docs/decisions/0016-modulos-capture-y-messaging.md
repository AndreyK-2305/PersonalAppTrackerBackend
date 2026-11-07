# ADR-0016: Módulos `capture` y `messaging`, y orden de etapas

- Estado: Aceptada
- Fecha: 2026-11-06

## Contexto

[ADR-0001](0001-monolito-modular-hexagonal.md) previó un módulo `notifications` para **enviar** correos con Resend. El usuario estableció como requisito central **recibir** las notificaciones bancarias de su iPhone, enviadas mediante automatizaciones de Atajos:

- encolarlas;
- analizarlas para extraer monto, producto, comercio y fecha;
- registrar el movimiento.

Un mismo nombre para los dos sentidos generaría confusión en el código y en la conversación.

La captura automática estaba fuera del MVP; ahora es parte de él (D22).

## Decisión

- **`capture` (nuevo, Etapa 4):** recibe las notificaciones bancarias. Sus responsabilidades:
  - token de dispositivo;
  - endpoint de captura;
  - cola en PostgreSQL;
  - analizadores por banco;
  - identificación del producto;
  - deduplicación;
  - bandeja de revisión.
  - **Dependencias:** `shared`, `identity :: api` y `finance :: api`.
  - Crea los movimientos a través de `finance.api`; `finance` no conoce a `capture`.
- **`messaging` (antes `notifications`, Etapa 5):** solo entrega correos (puerto `EmailSender`, adaptador Resend y registro de envíos). Depende solo de `shared`.
- **`reports` (Etapa 5):** depende de `messaging` en lugar de `notifications`. Depende también de `identity :: api`, para conocer la zona horaria de cada usuario al programar los envíos.
- **`identity`:** podrá depender de `messaging :: api` para la recuperación de contraseña.
- **Orden de etapas (D22):**

  | Etapa | Contenido |
  |---|---|
  | 3 | Finanzas manuales |
  | 4 | Captura automática |
  | 5 | Reportes programados |
  | 6 | Tarjetas y obligaciones (ciclos, cuotas, intereses, devoluciones y préstamos) |

- **Diseño detallado de la captura:** cola, analizadores, seguridad del token y despliegue con HTTPS se deciden en un ADR propio al planificar la Etapa 4.

Este ADR actualiza la lista de módulos de ADR-0001; el resto de ADR-0001 sigue vigente.

## Consecuencias

- **A favor:**
  - Los nombres expresan el sentido: lo que entra (`capture`) y lo que sale (`messaging`).
  - `finance` sigue sin conocer a los módulos que lo usan.
- **Requisito:** la Etapa 4 necesita el backend publicado en internet con HTTPS, porque el iPhone debe alcanzarlo.

## Alternativas consideradas

- **`notifications` para la entrada y otro nombre para el envío:** "notificaciones" en la interfaz suele significar avisos al usuario, lo que reintroduce la ambigüedad.
- **La captura dentro de `finance`:** mezclaría el análisis de textos de bancos y la cola con las reglas contables, y crecería sin límite con cada banco nuevo.
