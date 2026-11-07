# Glosario de dominio

El código, los identificadores y la API están en inglés; la interfaz y la documentación, en español ([ADR-0002](../decisions/0002-convenciones-de-capas-y-puertos.md)). Este glosario fija la traducción de cada término para que el código sea consistente. Cada etapa revisa y amplía sus términos en el ADR correspondiente.

## Identidad

| Español | Código | Definición |
|---|---|---|
| Usuario | `User` | Persona registrada; dueña de todos sus recursos. |
| Propietario | `owner` / `UserId` | Usuario dueño de un recurso. Se obtiene siempre de la sesión. |
| Perfil | `Profile` | Datos personales y preferencias del usuario, como la zona horaria. |

## Finanzas

| Español | Código | Definición |
|---|---|---|
| Producto (cuenta) | `Account` | Lugar donde está el dinero propio. Puede ser de activo (efectivo, cuenta, billetera) o de pasivo (tarjeta de crédito, préstamo). Jerarquía sellada ([ADR-0013](../decisions/0013-modelo-contable.md)). |
| Tipo de producto | `AccountKind` | `CASH`, `SAVINGS`, `CHECKING`, `DIGITAL_WALLET`, `CREDIT_CARD` u `OTHER`. |
| Efectivo | `CashAccount` | Dinero en mano. Su saldo inicial no puede ser negativo. |
| Cuenta de ahorros, corriente o billetera digital | `DepositAccount` | Producto de depósito de una entidad (Bancolombia, Nequi, …). |
| Producto "Otro" | `OtherAccount` | Producto de sistema que recibe lo que no se identifica. Sin saldo y fuera del patrimonio ([ADR-0014](../decisions/0014-productos-otro-y-categorias.md)). |
| Entidad | `Institution` | Banco o empresa que emite el producto, tal como la escribe el usuario. |
| Últimos 4 dígitos | `LastFourDigits` | Identifican una tarjeta o cuenta sin guardar el número completo. |
| Saldo inicial | `openingBalance` | Saldo del producto antes del primer movimiento registrado. En una tarjeta es la deuda inicial con signo negativo. |
| Saldo | `balance` | Saldo inicial más los efectos de los movimientos. Positivo en activos, negativo en deudas. |
| Patrimonio | `netWorth` | Suma de los saldos de los productos que llevan saldo. |
| Archivar | `archive` | Ocultar un producto o categoría con movimientos, sin borrarlo; se puede reactivar. |
| Movimiento | `Transaction` | Registro de dinero que entra, sale o se mueve entre productos. Jerarquía sellada. |
| Tipo de movimiento | `TransactionType` | `INCOME`, `EXPENSE`, `TRANSFER` o `ADJUSTMENT`. |
| Ingreso | `Income` | Dinero que entra a un producto propio desde afuera. |
| Gasto | `Expense` | Dinero que sale de un producto propio hacia afuera. Una compra con tarjeta es un gasto. |
| Transferencia | `Transfer` | Movimiento entre productos propios. No es ingreso ni gasto; el pago de una tarjeta y depositar el propio efectivo son transferencias. |
| Ajuste de saldo | `Adjustment` | Corrección para cuadrar un saldo con el banco. No es ingreso ni gasto. |
| Comercio | `merchant` | Establecimiento o contraparte del movimiento, si se conoce. |
| Medio de pago | `PaymentChannel` | Cómo se movió el dinero: tarjeta, transferencia, PSE, efectivo, consignación, retiro, débito automático u otro. Solo describe. |
| Efecto en reportes | `ReportEffect` | Cómo cuenta un movimiento: ingreso, gasto o neutro. |
| Categoría / subcategoría | `Category` | Clasificación de ingresos y gastos, en dos niveles. Una subcategoría es una `Category` con `parentId`. |
| Periodo | `Period` | Día, semana (de lunes a domingo) o mes que agrupa los reportes. |
| Tarjeta de crédito | `CreditCard` | Cuenta de pasivo con cupo y ciclo de facturación. Nunca se guarda el número completo. |
| Cupo | `creditLimit` | Monto máximo de crédito de la tarjeta. |
| Cupo disponible | `availableCredit` | Cupo menos la deuda vigente. |
| Fecha de corte | `statementClosingDate` | Fecha en que cierra el periodo facturado. |
| Fecha límite de pago | `paymentDueDate` | Último día para pagar el extracto sin mora. |
| Extracto | `Statement` | Resumen del periodo facturado de una tarjeta. |
| Compra a cuotas (diferida) | `InstallmentPurchase` | Compra pagada en varias cuotas; conserva el valor original y el número de cuotas. |
| Cuota | `Installment` | Parte periódica de una compra a cuotas o de un préstamo. |
| Pago de tarjeta | `CardPayment` | Transferencia que reduce la deuda de la tarjeta. |
| Préstamo | `Loan` | Obligación con capital, tasa y plan de amortización. |
| Obligación | `Liability` | Deuda vigente: tarjeta o préstamo. |
| Capital | `principal` | Parte de una deuda o cuota que no es interés. |
| Interés | `interest` | Costo financiero; siempre se registra separado del capital. |
| Tasa efectiva anual (EA) | `effectiveAnnualRate` | Tasa anual compuesta, la forma habitual de expresarla en Colombia. |
| Tasa mensual vencida (MV) | `monthlyRate` | Tasa equivalente mensual. |
| Amortización | `Amortization` | Plan de pagos que separa capital e intereses. |
| Devolución | `Refund` | Reintegro vinculado a una compra original. |
| Reversión | `Reversal` | Reverso de una operación hecho por el banco. Su manejo se define en la Etapa 6. |
| Rechazada | `REJECTED` | Estado de una operación que no se contabiliza. |
| Presupuesto | `Budget` | Límite de gasto mensual por categoría. Posterior al MVP. |
| Meta de ahorro | `SavingsGoal` | Objetivo de ahorro. Posterior al MVP. |

## Captura (Etapa 4)

| Español | Código | Definición |
|---|---|---|
| Notificación bancaria | `BankNotification` | Texto recibido del iPhone. Es el origen de un movimiento, no un movimiento. |

## Garaje (futuro)

| Español | Código | Definición |
|---|---|---|
| Vehículo | `Vehicle` | Vehículo del usuario. |
| Kilometraje | `odometerReading` | Lectura del odómetro en una fecha. |
| Mantenimiento | `Maintenance` | Servicio realizado o programado. |
| Documento / vencimiento | `VehicleDocument` / `expirationDate` | SOAT, revisión técnico-mecánica, etc. |
