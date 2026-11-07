# ADR-0006: Representación de dinero y fechas

- Estado: Aceptada
- Fecha: 2026-11-06

## Contexto

Los cálculos monetarios no pueden usar `float` ni `double`. La moneda predeterminada es COP y la interfaz usa el formato `es-CO`. JavaScript representa todos los números como `double`. Los reportes agrupan por mes según la fecha local del usuario.

## Decisión

- **Dinero en el backend:**
  - `BigDecimal` en Java y `NUMERIC(19,2)` en PostgreSQL.
  - Redondeo `HALF_UP`, aplicado de forma explícita en cada cálculo que lo requiera.
- **Tasas de interés:** `BigDecimal`. Su precisión se define en la Etapa 4.
- **Moneda:** forma parte del objeto de valor `Money` (código ISO 4217) desde el inicio. El MVP solo acepta COP y no hace conversiones (confirmado en [ADR-0013](0013-modelo-contable.md)).
- **Montos en la API:** viajan como **cadenas decimales** (`"150000.00"`).
  - El frontend solo formatea montos (COP sin decimales en pantalla) y valida la captura.
  - Los cálculos monetarios (cuotas, intereses, totales y saldos) se hacen exclusivamente en el backend.
- **Fechas de negocio** (fecha del movimiento, de corte, de pago): `LocalDate` en Java y `date` en la base de datos.
- **Instantes** (creación, auditoría, sesión): `Instant` en Java y `timestamptz` en UTC.
- **Zona horaria:** la del usuario está en su perfil (por defecto `America/Bogota`) y define los límites de los periodos ("este mes").
- **Verificación:** `HexagonalArchitectureTests` prohíbe campos `float`, `double`, `Float` y `Double` en dominio, aplicación y API.

## Consecuencias

- **A favor:** no hay pérdida de precisión de punta a punta y el frontend no duplica la lógica de dominio.
- **En contra:**
  - El frontend debe convertir cadenas para mostrarlas.
  - `MoneyInput` debe manejar los separadores de `es-CO` (`.` para miles y `,` para decimales), una fuente clásica de errores de ×1000. Tendrá pruebas dedicadas.

## Alternativas consideradas

- **Enteros en unidades mínimas (centavos):** son seguros, pero menos legibles. En COP los centavos casi no se usan, aunque ISO 4217 define dos decimales.
- **Montos como números JSON:** riesgo de precisión en el cliente.
