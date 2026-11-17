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

## Metas

| Español | Código | Definición |
|---|---|---|
| Meta de ahorro | `SavingsGoal` | Objetivo de ahorro para una suma o un bien, con un valor y un plan. |
| Plan | `SavingsPlan` | Cómo se llega: por fecha (`ByTargetDate`) o por aporte mensual (`ByMonthlyAmount`). |
| Abono | `SavingsContribution` | Marca de un ahorro hecho, con fecha y valor. No es un movimiento de Finanzas. |
| Rentabilidad | `SavingsRateChange` / `AnnualRate` | Tasa efectiva anual de donde se ahorra. Rige desde el día siguiente al cambio y solo afecta la proyección. |
| Proyección | `SavingsProjection` | Estado de la meta en un día: ahorrado, faltante, avance y lo que hay que hacer para cumplirla. |
| Aporte recomendado | `recommendedMonthly` | Valor a apartar cada mes para llegar en la fecha; se redondea hacia arriba al peso y nunca supera lo que falta. |
| Meta vencida | `overdue` | La fecha objetivo llegó y la meta no está cumplida. |
| Tasa EA | `AnnualRate` | Tasa efectiva anual. La mensual equivalente es `(1 + EA)^(1/12) − 1`. |
| Meta financiada | `FinancedGoal` | Compra grande que se piensa pagar a crédito. Es un simulador y plan de pagos, no una deuda de Finanzas. |
| Condiciones | `FinancedTerms` | Valor del bien, cuota inicial, tasa EA, número de cuotas, fecha de la primera cuota y cuota real opcional. |
| Monto financiado | `financedAmount` | Valor del bien menos la cuota inicial. |
| Cuota recomendada (calculada) | `recommendedInstallment` | Cuota fija del sistema francés, redondeada hacia arriba al peso. |
| Cuota real | `actualInstallment` | Lo que cobra la entidad cada mes. No puede ser menor que la calculada. |
| Seguros y otros | `extras` | Diferencia entre la cuota real y la calculada. No amortiza capital. |
| Plan de pagos | `PaymentSchedule` | Tabla de amortización con fechas y totales. Se calcula al consultar. |
| Tabla de amortización | `Amortization.schedule` | Por cuota: interés sobre el saldo, capital y saldo restante. La última cuota ajusta el residuo. |
| Registro | `FinancedRecord` | Algo que pasó en el crédito: un pago, un abono o un ajuste de tasa. Se guardan en orden y solo se deshace el último. |
| Pago de cuota | `InstallmentPayment` | Marca la siguiente cuota como pagada. No puede ser menor que la cuota; lo que sobre es un abono. |
| Abono extraordinario | `Prepayment` / `extraPrincipal` | Pago que va completo a capital. Reduce el plazo (por defecto) o la cuota. |
| Ajuste de tasa | `RateAdjustment` | Nueva tasa EA desde la siguiente cuota; recalcula la cuota sobre el saldo y las cuotas que faltan. |
| Estado del crédito | `CreditPlan` / `CreditStatus` | El plan actual tras repasar los registros: al día, con una cuota vencida o pagado. Informativo: no hay mora. |
| Ahorro en intereses | `interestSavings` | Intereses del plan original menos los del plan actual. Negativo si ahora se paga más. |

## Garaje (futuro)

| Español | Código | Definición |
|---|---|---|
| Vehículo | `Vehicle` | Vehículo del usuario. |
| Kilometraje | `odometerReading` | Lectura del odómetro en una fecha. |
| Mantenimiento | `Maintenance` | Servicio realizado o programado. |
| Documento / vencimiento | `VehicleDocument` / `expirationDate` | SOAT, revisión técnico-mecánica, etc. |
