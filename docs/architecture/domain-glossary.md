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
| Cuenta | `Account` | Contenedor de dinero propio. Puede ser de activo (efectivo, banco) o de pasivo (tarjeta de crédito, préstamo). |
| Movimiento | `Transaction` | Registro de dinero que entra, sale o se mueve entre cuentas. |
| Ingreso | `Income` | Dinero que entra a una cuenta propia desde afuera. |
| Gasto | `Expense` | Dinero que sale de una cuenta propia hacia afuera. Una compra con tarjeta es un gasto. |
| Transferencia | `Transfer` | Movimiento entre cuentas propias. No es ingreso ni gasto; el pago de una tarjeta es una transferencia. |
| Categoría / subcategoría | `Category` / `Subcategory` | Clasificación de ingresos y gastos. |
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
| Reversión | `Reversal` | Anulación de un movimiento que conserva la trazabilidad. |
| Rechazada | `REJECTED` | Estado de una operación que no se contabiliza. |
| Presupuesto | `Budget` | Límite de gasto mensual por categoría. Posterior al MVP. |
| Meta de ahorro | `SavingsGoal` | Objetivo de ahorro. Posterior al MVP. |

## Garaje (futuro)

| Español | Código | Definición |
|---|---|---|
| Vehículo | `Vehicle` | Vehículo del usuario. |
| Kilometraje | `odometerReading` | Lectura del odómetro en una fecha. |
| Mantenimiento | `Maintenance` | Servicio realizado o programado. |
| Documento / vencimiento | `VehicleDocument` / `expirationDate` | SOAT, revisión técnico-mecánica, etc. |
