# ADR-0015: Edición y borrado de movimientos

- Estado: Aceptada
- Fecha: 2026-11-06

## Contexto

El usuario necesita borrar los movimientos registrados por error y los que vengan de notificaciones no válidas, como una promoción que pasó los filtros (requisito 8). La propuesta inicial era anular sin borrar y editar con historial de cambios. El usuario eligió el borrado definitivo (D21).

## Decisión

- **Edición:**
  - Se pueden editar el monto, la fecha, los productos, la categoría, la descripción, el comercio, el medio de pago y la dirección de un ajuste.
  - **El tipo no se puede cambiar.** Para convertir un gasto en transferencia, por ejemplo, se borra el gasto y se registra la transferencia.
  - Las reglas del modelo ([ADR-0013](0013-modelo-contable.md)) se vuelven a validar al editar.
  - Excepción: un producto o una categoría que el movimiento ya tenía asignados no se vuelven a validar. Así se puede corregir un movimiento antiguo de un producto archivado sin reactivarlo.
- **Borrado:**
  - Es definitivo y la interfaz pide confirmación.
  - No se guarda historial de cambios ni copia de lo borrado; solo `updated_at` indica la última edición.
- **Movimientos de una notificación (Etapa 4):** al borrar el movimiento, la notificación de origen se conserva marcada como descartada. Así no se vuelve a procesar y sirve para mejorar los filtros.
- **Productos y categorías:** se archivan en lugar de borrarse cuando tienen movimientos ([ADR-0014](0014-productos-otro-y-categorias.md)).
- **Etapa 6:** las operaciones encadenadas (cuotas o pagos de extracto) definirán sus propias restricciones de edición y borrado.

## Consecuencias

- **A favor:**
  - Es simple de usar y de implementar.
  - Corregir un error no deja ruido en los reportes.
- **En contra:**
  - No hay trazabilidad de los cambios manuales y lo borrado no se recupera.
  - Para los movimientos que lleguen de notificaciones, la notificación conserva la evidencia.

## Alternativas consideradas

- **Anular en lugar de borrar:** conserva la trazabilidad, pero obliga a filtrar los anulados en cada consulta, y el usuario pidió poder borrar.
- **Historial de revisiones:** requiere una tabla y una interfaz extra para un uso personal.
- **Papelera con recuperación por unos días:** más estados y una tarea de limpieza programada.
