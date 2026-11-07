# ADR-0014: Productos, producto "Otro" y categorías

- Estado: Aceptada
- Fecha: 2026-11-06

## Contexto

El usuario registra sus productos financieros (efectivo, cuentas, billeteras, tarjetas). A partir de la Etapa 4, las notificaciones bancarias deben asociarse a uno de ellos: por ejemplo, "tarjeta Bancolombia *5403". Lo que no coincida con ningún producto registrado va a "Otro" (requisito 3 del usuario).

Los movimientos se clasifican en categorías para los reportes. La Etapa 3 recibe las decisiones D20 y D23.

## Decisión

### Productos de la Etapa 3

| Tipo | Datos propios |
|---|---|
| Efectivo | Saldo inicial (no negativo) |
| Cuenta de ahorros, cuenta corriente o billetera digital | Entidad, últimos 4 dígitos (opcionales), saldo inicial |
| Tarjeta de crédito | Entidad, últimos 4 dígitos (opcionales), cupo, deuda inicial |
| Otro | Ninguno: lo crea el sistema |

- **Nombre:** el nombre de cada producto es único por usuario, sin distinguir mayúsculas.
- **Tarjeta de crédito:** en esta etapa es solo un producto de pasivo. Las fechas de corte y de pago, los extractos y las cuotas llegan en la Etapa 6.

### Identificación del producto

- **Datos:** la entidad (`Institution`, texto escrito por el usuario) y los últimos 4 dígitos (`LastFourDigits`, opcionales).
- **Sugerencias:** el frontend sugiere entidades frecuentes, como Bancolombia o Nequi, pero no restringe el texto.
- **Datos que nunca se guardan:** el número completo de una tarjeta o cuenta.
- **Etapa 4:** la captura identificará el producto por entidad y últimos 4 dígitos.

### Producto "Otro" (D20)

- **Existencia:** uno por usuario, creado por el sistema, que no se puede editar, archivar ni borrar.
- **Uso:** recibe los movimientos cuyo producto no se identifica.
- **Saldo:** no lleva saldo ni entra en el patrimonio; su saldo inicial es cero.
- **Movimientos permitidos:** solo ingresos y gastos, que sí cuentan en los reportes. Las transferencias y los ajustes exigen productos con saldo.
- **Reasignación:** un movimiento sale de "Otro" editándolo y eligiendo el producto real.

### Bienvenida de Finanzas

La primera vez que el usuario entra a Finanzas, el caso de uso `SetUpFinance` crea "Otro" y, si el usuario acepta, el catálogo de categorías sugerido.

- **Idempotente:** volver a ejecutarlo no duplica nada.
- **Cuentas anteriores:** funciona también con cuentas creadas antes de la Etapa 3.
- **Sin acoplamiento:** no requiere eventos entre `identity` y `finance`.

### Categorías

- **Niveles:** dos, categoría y subcategoría. Una subcategoría hereda el tipo (ingreso o gasto) de su categoría y no puede tener subcategorías propias.
- **Asignación:** un movimiento puede asignarse a una categoría o a una subcategoría. Los reportes agrupan por la categoría principal.
- **Unicidad:** el nombre es único entre las categorías hermanas del mismo tipo.
- **Catálogo sugerido (`DefaultCategoryCatalog`):** colombiano y editable.
  - Gastos: Vivienda, Alimentación, Transporte, Salud, Educación, Entretenimiento, Compras, Servicios financieros (4x1000, cuota de manejo, intereses y comisiones), Impuestos y Otros gastos.
  - Ingresos: Salario, Honorarios, Ventas, Rendimientos y Otros ingresos.
- **4x1000 (GMF):** se registra a mano con su categoría, sin cálculo automático, porque las exenciones dependen de cada cuenta.
- **Categorías de sistema:** en esta etapa todas son editables. Las categorías de sistema (por ejemplo, "Intereses" para los extractos) llegan en la Etapa 6, cuando el código las necesite.

### Archivar y borrar

- **Con movimientos:** un producto o una categoría que tiene movimientos no se borra, se archiva.
  - Lo archivado no aparece en los selectores y no recibe movimientos nuevos.
  - Conserva su historial y se puede reactivar.
- **Sin movimientos:** se puede borrar.
- **Categoría principal:** al archivar una categoría principal se archivan también sus subcategorías.

## Consecuencias

- **A favor:**
  - "Otro" garantiza que una notificación con un producto desconocido nunca se pierda ni descuadre un saldo.
  - El patrimonio solo incluye productos con saldo conocido.
- **En contra:**
  - El saldo de "Otro" no tiene significado, así que la interfaz no lo muestra.
  - Si una entidad se escribe de formas distintas ("Bancolombia" y "bancolombia"), la Etapa 4 tendrá que normalizarla al comparar.

## Alternativas consideradas

- **Dejar sin producto lo no identificado:** rompería la regla de que todo movimiento pertenece a un producto y complicaría los reportes por producto.
- **Catálogo cerrado de entidades:** facilita la comparación, pero exige mantener una lista y bloquea entidades nuevas.
- **Crear el catálogo al registrarse, con un evento de `identity`:** obliga a publicar eventos desde `identity` y no cubre las cuentas existentes.
- **Categorías de profundidad libre:** complican los reportes y los selectores sin una necesidad real.
