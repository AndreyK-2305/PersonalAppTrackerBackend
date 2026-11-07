# ADR-0013: Modelo contable: libro de movimientos sobre productos propios

- Estado: Aceptada
- Fecha: 2026-11-06

## Contexto

Las reglas financieras del proyecto exigen:

- que el pago de una tarjeta no sea un segundo gasto;
- que las transferencias entre productos propios no sean ingreso ni gasto;
- que los intereses se separen del capital;
- que las devoluciones se liguen a su compra.

El usuario agregó requisitos centrales:

- registrar movimientos a mano y, desde la Etapa 4, a partir de notificaciones bancarias del iPhone;
- asignar cada movimiento a un producto (por ejemplo, la tarjeta Bancolombia *5403) o a "Otro" si no se identifica;
- reportes por producto con comparación entre periodos;
- borrar lo registrado por error.

El usuario propuso una superclase común para notificaciones, ingresos y gastos, porque comparten monto, fecha y comercio y solo cambia su comportamiento en los reportes. También propuso una jerarquía para los medios de pago.

Hace falta un modelo que garantice las reglas por construcción, sin casos especiales repartidos por los reportes.

## Decisión

### Regla central

Un movimiento es **ingreso** o **gasto** solo si el dinero cruza el límite de los productos propios del usuario. Lo que ocurre entre productos propios es una **transferencia**. Por ejemplo, depositar en el banco el propio efectivo es una transferencia, no un ingreso.

### Productos: jerarquía sellada `Account`

| Clase | Tipos (`AccountKind`) | Naturaleza | Reglas propias |
|---|---|---|---|
| `CashAccount` | `CASH` | Activo | Saldo inicial no negativo |
| `DepositAccount` | `SAVINGS`, `CHECKING`, `DIGITAL_WALLET` | Activo | Entidad obligatoria; últimos 4 dígitos opcionales |
| `CreditCardAccount` | `CREDIT_CARD` | Pasivo | Cupo mayor que cero; deuda inicial no negativa; entidad y últimos 4 dígitos |
| `OtherAccount` | `OTHER` | — | Producto de sistema; ver [ADR-0014](0014-productos-otro-y-categorias.md) |

Todos comparten identificador, propietario, nombre, moneda, saldo inicial, archivado y fechas de auditoría.

### Movimientos: jerarquía sellada `Transaction`

| Clase | Productos | Categoría | Efecto en saldos | En reportes |
|---|---|---|---|---|
| `Income` | Destino | De ingreso, opcional | `+monto` en el destino | Ingreso |
| `Expense` | Origen | De gasto, opcional | `−monto` en el origen | Gasto |
| `Transfer` | Origen y destino distintos | — | `−monto` en el origen y `+monto` en el destino | Neutro |
| `Adjustment` | Uno, con dirección | — | `±monto` en el producto | Neutro |

- **Datos comunes:** monto positivo (`Money`), fecha de negocio (`LocalDate`), descripción, comercio y medio de pago (`TransactionDetails`).
- **Comportamiento por subtipo:** cada clase define qué productos afecta y con qué signo (`balanceEffects()`), cómo cuenta en los reportes (`reportEffect()`) y qué tipo de categoría acepta.
- **Validación entre agregados:** las fábricas (`record`) y la edición (`revise`) reciben los productos y las categorías ya cargados del mismo propietario, y validan en el dominio:
  - la moneda del monto y la del producto coinciden;
  - el producto acepta el tipo de movimiento;
  - un producto archivado no recibe movimientos nuevos;
  - la categoría es del tipo correcto y no está archivada.
  - El movimiento solo guarda los identificadores.
- **Por qué `sealed`:** al agregar un subtipo, el compilador obliga a tratarlo en cada `switch` sobre la jerarquía (mappers, API y reportes).

### Medio de pago: `enum`, no jerarquía

`PaymentChannel` toma los valores tarjeta, transferencia, PSE, efectivo, consignación, retiro, débito automático y otro. Solo describe el movimiento: no cambia saldos ni reportes. Si fuera una jerarquía de clases, serían clases sin comportamiento.

La regla general es esta: **herencia donde cambia el comportamiento y `enum` donde solo se describe**.

### La notificación bancaria es el origen, no la superclase

No todo movimiento viene de una notificación (efectivo, registros manuales), ni toda notificación es un movimiento (promociones, códigos de verificación). Además, sus ciclos de vida son distintos:

- **Notificación:** recibida → analizada → registrada, descartada, en revisión o duplicada.
- **Movimiento:** registrado → editado → borrado.

En la Etapa 4, el módulo `capture` guardará la notificación y creará el movimiento a través de `finance.api` ([ADR-0016](0016-modulos-capture-y-messaging.md)). El movimiento recibirá entonces una referencia a su origen, con una migración. No se agrega antes porque hoy solo tendría el valor "manual".

### Saldos derivados

- **Cálculo:** saldo = saldo inicial + suma de los efectos de los movimientos. No hay columna de saldo mutable.
- **Signo:** los activos quedan positivos y los pasivos negativos (la deuda), de modo que **patrimonio = suma de los saldos** de los productos que llevan saldo.
- **Tarjeta de crédito:**
  - deuda = −saldo cuando el saldo es negativo;
  - cupo disponible = cupo + saldo;
  - la deuda inicial se guarda como saldo inicial negativo.

### Reportes

- **Agrupación:** por la fecha de negocio del movimiento.
- **Totales:** ingresos = suma de `Income` y gastos = suma de `Expense`. Las transferencias, los ajustes y los saldos iniciales nunca cuentan como ingreso ni gasto.
- **Periodos (`Period`):** día, semana (de lunes a domingo) o mes.
- **Comparación:**
  - un periodo cerrado se compara con el periodo anterior completo;
  - un periodo en curso se compara con los mismos días del anterior. Por ejemplo, del 1 al 6 de noviembre se compara con del 1 al 6 de octubre, y si el mes anterior es más corto, se corta en su último día.

### Moneda

Solo COP en el MVP, sin conversiones (D23). La moneda forma parte de `Money` y de cada producto, y `SupportedCurrencies` rechaza cualquier otra. Esto confirma lo pendiente en [ADR-0006](0006-dinero-y-fechas.md).

### Persistencia

- Cada jerarquía se guarda en **una sola tabla** con columna de tipo (`finance.accounts` y `finance.transactions`).
- Restricciones `CHECK` por tipo definen qué columnas llevan valor.
- Llaves foráneas compuestas con `user_id` ([ADR-0003](0003-aislamiento-por-propietario.md)).
- Las entidades JPA son planas y los mappers manuales traducen entre la tabla y la jerarquía del dominio.

## Consecuencias

- **A favor:**
  - Las reglas contables viven en un solo lugar del dominio y el compilador vigila los subtipos.
  - La captura automática de la Etapa 4 y los reportes de la Etapa 5 se conectan sin cambiar el modelo.
  - No hay descuadres de saldo posibles.
- **Riesgo:** las sumas de los reportes se hacen en SQL por rendimiento, y deben coincidir con `reportEffect()` y `balanceEffects()`. Una prueba de integración compara ambos.
- **Rendimiento:** calcular saldos sobre todos los movimientos basta para el volumen personal. Si creciera, se agregarían saldos de cierre mensual sin cambiar el modelo.

## Alternativas consideradas

- **Notificación como superclase de los movimientos:** no cumple la relación "es un" en ninguno de los dos sentidos, y mezclaría dos ciclos de vida y dos módulos.
- **Jerarquía de clases para los medios de pago:** serían clases sin comportamiento.
- **Partida doble completa:** con asientos de débito y crédito. Es correcta, pero expone una complejidad que el uso personal no necesita. Un movimiento nunca afecta más de dos productos propios.
- **Saldo almacenado:** se descuadra y genera conflictos de concurrencia.
- **Montos con signo sin tipo de movimiento:** obligan a adivinar si algo es gasto o transferencia.
- **Herencia JPA (`SINGLE_TABLE`):** acoplaría la jerarquía del dominio a anotaciones de persistencia, que el proyecto mantiene separadas.
