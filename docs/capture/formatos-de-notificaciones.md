# Formatos de notificaciones bancarias

Catálogo de los textos que recibe el módulo `capture`, tomado de notificaciones reales del usuario (2026-10 y 2026-11). Los datos personales están **anonimizados**: nombres, llaves, números de destino y últimos 4 dígitos. Las pruebas de los analizadores usan estas plantillas, nunca los textos originales.

Notación: `{monto}`, `{comercio}`, `{ultimos4}`, `{fecha}`, `{hora}`. Cada plantilla declara su formato de monto; el analizador **nunca adivina** el formato (ver "Formatos de monto").

## Bancolombia: SMS (remitente 85540)

Todas empiezan con `Bancolombia: `.

| Id | Plantilla (resumida) | Resultado propuesto |
|---|---|---|
| B1 | `Compraste COP{monto_es} en {comercio} con tu T.Cred *{ultimos4}, el {dd/MM/yyyy} a las {HH:mm}. …` | Gasto · tarjeta de crédito Bancolombia `*ultimos4` · medio `CARD` |
| B1b | `Compraste COP{monto_es} en {comercio}, el {dd/MM/yyyy} a las {HH:mm}. Esta compra esta asociada a T.Cred *{ultimos4}. …` | Igual que B1 (compras en línea o recurrentes, por ejemplo APPLE.COM/BILL) |
| B1c | `Compraste USD{monto_es} en {comercio} con tu T.Cred *{ultimos4}, …` | Gasto en COP con la TRM del día de la compra, marcado como "Monto estimado" |
| B2 | `Transferiste ${monto_en} desde tu cuenta *{ultimos4} a la cuenta *{destino} el {dd/MM/yy} a las {HH:mm}. …` | Gasto · cuenta Bancolombia `*ultimos4` · medio `BANK_TRANSFER` · comercio "Cuenta \*{últimos 4 del destino}" |
| B3 | `{NOMBRE}, transferiste ${monto_en} a la llave {llave} desde tu cuenta *{ultimos4} a {destinatario} el {dd/MM/yy} a las {HH:mm}. Con Bre-b …` | Llave propia → transferencia; destinatario con tu nombre → "Por revisar" con "Es mía" marcada; si no → gasto a `{destinatario}` |
| B4 | `{NOMBRE COMPLETO} pagaste ${monto_en} por codigo QR desde tu cuenta *{ultimos4} a la llave {llave} el {dd/MM/yyyy} a las {HH:mm}. …` | Gasto · medio `BANK_TRANSFER` · comercio "Pago QR" (el texto no trae el comercio) |
| B5 | `{Nombre}, recibiste una transferencia de {remitente} por ${monto_en} en tu cuenta *{ultimos4} conectada a la llave {llave} el {dd/MM/yy} a las {HH:mm}. …` | Ingreso · cuenta `*ultimos4` · medio `BANK_TRANSFER` · comercio `{remitente}` |
| B6 | `Recibiste un pago por ${monto_en} de {pagador} a tu cuenta AHORROS, el {HH:mm} a las {dd/MM/yyyy}. …` | Ingreso · cuenta de ahorros Bancolombia (sin últimos 4) · comercio `{pagador}`. **La fecha y la hora vienen invertidas.** |
| B7 | `Recibiste una consignacion por ${monto_entero} desde el corresponsal {corresponsal} en {ciudad}, el {dd/MM/yy} {HH:mm}. …` | Ingreso · cuenta de depósito Bancolombia (por entidad + tipo) · medio `DEPOSIT` · comercio `{corresponsal}` |
| B0 | `Listo. Inscribiste tu Clave Dinamica …`, `Listo. Te inscribiste al servicio …` | **Se descarta:** no es un movimiento |

## Rappi: notificación de la app

| Id | Título | Cuerpo | Resultado propuesto |
|---|---|---|---|
| R1 | `RappiCard \| Compra exitosa` | `Tu compra en {comercio} por $ {monto_es_entero} fue exitosa. Consulta …` | Gasto · tarjeta de crédito Rappi (sin últimos 4) · medio `CARD`. El texto no trae fecha: se usa la hora de recepción que envía el atajo. |

## Apple Wallet

Notificación al pagar con Apple Pay (ejemplos del 2026-11-11):

| Id | App | Título | Cuerpo | Resultado |
|---|---|---|---|---|
| W1 | `Wallet` (o `Cartera`) | Emisor: `BANCOLOMBIA`, `RappiCard` | `{comercio}. {ciudad}, {departamento}` y en otra línea `$ {monto_es_entero}`; la ciudad puede faltar (`Temu\n$ 94.385`) | Gasto · medio `CARD` · producto: cualquier tarjeta o cuenta de ese emisor (`PAYMENT_CARD`) |

- **Lo que no trae:**
  - **Hora:** se usa la de recepción.
  - **Últimos dígitos.**
  - **Crédito o débito:** el nombre de la tarjeta ("Única Virtual") solo aparece al abrir el detalle.
- **Producto:** con un solo producto de ese banco se registra en él. Con varios queda en "Otro" y va a revisión, salvo que se empareje antes.
- **Emparejamiento (ADR-0017):** un aviso W1 se empareja con el SMS del banco si el producto del SMS está entre las tarjetas y cuentas de ese emisor, tienen el mismo monto y llegaron con menos de 5 minutos de diferencia. Como la cola procesa los bancos antes que Wallet, lo normal es que Wallet quede emparejado y el movimiento lleve los datos del SMS.
- **Limitación conocida:** si el SMS llega más de 2 minutos después y el aviso de Wallet ya quedó en "Otro" (porque hay varios productos de ese banco), el SMS no se empareja con él. La compra queda dos veces y el aviso de Wallet aparece "Por revisar" para descartarlo.
- **Emisor sin analizador:** el aviso queda como no reconocido.

El 2026-11-09 el usuario compartió también el **historial** de transacciones de su tarjeta Bancolombia en Wallet, y de él se ven tres cosas:

- **Qué compras aparecen:** todas las de la tarjeta, no solo las hechas con Apple Pay. Las físicas muestran la ciudad, y las de Apple Pay dicen "Apple Pay".
- **Monto:** con punto de miles, sin decimales (`$ 9.980`), es decir, el formato `SPANISH`.
- **Comercio:** viene con otro nombre que en el SMS, así que el comercio no sirve para reconocer que dos avisos son la misma compra:

  | Wallet | SMS de Bancolombia |
  |---|---|
  | `D1 - Antonia Santos` | `TIENDA D1 LOS SANTOS` |
  | `Tiendas Ara` | `TIENDAS ARA` |
  | `Eds Los Libertadores` | `EDS LOS LIBERTADORES` |

Como Wallet notifica las compras con Apple Pay, una compra llega dos veces (SMS y Wallet). Se resuelve con la espera de 2 minutos y el emparejamiento descrito en W1.

## Formatos de monto

Los textos usan cuatro variantes, que se reducen a dos formatos (`AmountFormat`) con decimales opcionales:

| Formato | Ejemplos | Plantillas |
|---|---|---|
| `SPANISH`: punto de miles, coma decimal | `COP25.005,00`, `USD20,00`, `$ 13.300`, `$ 7.900` | B1, B1b, B1c, R1, W1 |
| `ENGLISH`: coma de miles, punto decimal | `$6,500.00`, `$480,000` | B2 a B7 |

- **Grupos de miles:** siempre son de 3 dígitos.
- **Decimales:** si aparecen, son exactamente 2.

Con esas dos reglas, un texto solo encaja en un formato: `100.00` es cien en `ENGLISH` y no es válido en `SPANISH`. Un analizador genérico confundiría `$100.00` (cien pesos, un ejemplo real) con diez mil.

Por eso cada plantilla fija su formato, y un texto que no encaja exactamente en ninguna plantilla va a revisión en lugar de interpretarse:

- un monto con otro formato;
- un monto de cero;
- una fecha imposible;
- un mensaje nuevo del banco.

## Fechas

- Años de 2 o 4 dígitos (`05/11/26`, `03/11/2026`); los de 2 se leen como 20xx.
- Se interpretan en la zona horaria del perfil del usuario.
- Sin fecha en el texto (R1): se usa la hora de recepción en el iPhone.
