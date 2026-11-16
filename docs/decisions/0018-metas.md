# ADR-0018: Metas de ahorro y metas financiadas

- Estado: Aceptada
- Fecha: 2026-11-15

## Contexto

El usuario priorizó la herramienta **Metas** antes que Garaje y que las Etapas 5 y 6 (2026-11-14). Metas sirve para dos cosas:

- **ahorrar** para una suma o un bien (por ejemplo, una consola): cuánto apartar cada mes y cuánto se lleva;
- **simular la financiación** de compras grandes a largo plazo (vehículo, vivienda): cuota, tabla de amortización, pagos, abonos a capital y ahorro en intereses.

Las reglas se definieron con el usuario el 2026-11-14 y el 2026-11-15.

## Decisión

### Módulo

- **`goals` (nuevo):** depende de `shared` e `identity :: api` (zona horaria del usuario, para saber qué día es "hoy").
- **No depende de `finance`.** Los abonos y pagos son marcas dentro de la meta, no movimientos. Ligarlos al libro de movimientos obligaría a emparejar y revisar a mano, con más errores (decisión del usuario).
- **Metas independientes:** una meta de ahorro no se convierte en financiada ni se relaciona con ella.
- **Calculadora financiera en `shared`** (`com.personaltracker.shared.domain.finance`): conversión de tasas, cuota fija y amortización, en Java puro con `BigDecimal`. Está en `shared` para que `finance` la reutilice en la Etapa 6 (compras a cuotas y préstamos) sin depender de `goals`.

### Precisión

- Los cálculos internos usan `MathContext.DECIMAL128` (34 dígitos).
- Las tasas se reciben como **efectivas anuales (EA)** en porcentaje con hasta 4 decimales (por ejemplo, `24.5` es 24,5 % EA) y se guardan como fracción `NUMERIC(9,6)`.
- **Tasa mensual:** `(1 + EA)^(1/12) − 1`, con raíz duodécima por el método de Newton en `BigDecimal`.
- Los montos se guardan y se devuelven con 2 decimales (ADR-0006).
- Los valores recomendados se redondean **hacia arriba al peso**, para no quedarse cortos.

### Metas de ahorro

- **Definición:** nombre, valor objetivo y un plan, que es **fecha objetivo** o **aporte mensual**. La app calcula el dato que falta.
- **Ahorrado:** suma de los abonos marcados (fecha y valor positivo). Los abonos se pueden borrar.
- **Meses restantes:** meses completos entre hoy y la fecha objetivo, con un mínimo de 1. Si la fecha ya pasó, la meta aparece vencida y el aporte recomendado es todo lo que falta.
- **Aporte mensual recomendado** (plan por fecha):
  - sin rentabilidad: `(objetivo − ahorrado) ÷ meses`;
  - con rentabilidad: `(objetivo − ahorrado·(1+i)^n) · i ÷ ((1+i)^n − 1)`, con `i` la tasa mensual y `n` los meses.
- **Fecha estimada** (plan por aporte): el primer mes en que lo ahorrado, más los aportes y la rentabilidad, alcanza el objetivo. Se calcula mes a mes, hasta 100 años; si no alcanza, la meta se marca como "no alcanza".
- **Rentabilidad:** apagada por defecto. Se activa o se cambia con una tasa EA que aplica **desde el día siguiente**; la app guarda el historial de tasas. Solo afecta la **proyección**: la app no calcula los intereses ganados, que los conoce el banco.
- **Meta cumplida:** cuando lo ahorrado llega al objetivo.
- **Límites** (aprobados por el usuario el 2026-11-16, tras la auditoría):
  - la fecha objetivo va desde mañana hasta 100 años; una meta vencida puede conservar la suya al editarla;
  - un abono no puede tener fecha futura ni de hace más de 100 años;
  - el aporte recomendado nunca supera lo que falta, así que con menos de un peso pendiente es ese valor exacto.

### Metas financiadas

- **Datos de entrada:** valor del bien, cuota inicial (opcional), tasa EA, número de cuotas y fecha de la primera cuota. El monto financiado es el valor menos la cuota inicial.
- **Cuota fija** (sistema francés): `P · i ÷ (1 − (1+i)^−n)`. Es la **cuota recomendada**.
- **Cuota real:** opcional. Si se registra, la diferencia con la recomendada se muestra como **"seguros y otros"**: un valor fijo por cuota que no amortiza capital.
- **Tabla de amortización:** por cuota, la fecha, la cuota, el interés, el capital, los seguros y otros, y el saldo. El interés es mensual sobre el saldo al inicio del periodo, sin interés diario. La última cuota ajusta el residuo para que el saldo quede en 0.
- **Pagos:** cada cuota se marca como pagada con fecha y valor. Lo pagado de más es un abono extraordinario.
- **Abono extraordinario:** va completo a capital. **Por defecto reduce el plazo**; en cada abono se puede elegir reducir la cuota.
- **Ajuste de tasa:** acción manual para créditos de tasa variable. Registra la nueva tasa EA, que aplica **desde la siguiente cuota**, y recalcula la cuota recomendada sobre el saldo y las cuotas restantes. Si hay cuota real, el usuario la actualiza en el mismo ajuste.
- **Ahorro en intereses:** intereses del plan original − (intereses ya pagados + intereses pendientes según el plan actual).
- Es un **simulador y plan de pagos**, no una obligación de Finanzas. Las compras a cuotas con tarjeta se tratarán en la Etapa 6.

### Entregas

1. Calculadora financiera y metas de ahorro.
2. Metas financiadas: simulador y tabla de amortización.
3. Pagos, abonos extraordinarios y ajustes de tasa, con el ahorro en intereses.

### Errores

Cada regla responde con su propio código (`goals.savings_goal.name_invalid`, `.target_invalid`, `.target_date_invalid`, `.monthly_amount_invalid`, `goals.contribution.amount_invalid` y `.date_invalid`), para que el frontend marque el campo que falló.

## Consecuencias

- Metas avanza antes que las Etapas 5 y 6. La calculadora en `shared` adelanta parte de la Etapa 6.
- Los datos de Metas no aparecen en los reportes ni en el dashboard financiero: no son movimientos.
- Un abono real hay que registrarlo en dos sitios si también se quiere ver en Finanzas (por ejemplo, la transferencia a la cuenta de ahorros). Es el costo aceptado de no ligar los módulos.
- Los retiros de una meta de ahorro no existen todavía: si se saca dinero, se borra o corrige el abono.
