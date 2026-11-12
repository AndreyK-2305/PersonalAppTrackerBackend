# ADR-0017: Captura automática de notificaciones bancarias

- Estado: Aceptada
- Fecha: 2026-11-09

## Contexto

[ADR-0016](0016-modulos-capture-y-messaging.md) creó el módulo `capture` y dejó su diseño para este ADR. El usuario decidió (PROJECT_CONTEXT §16, 2026-11-09):

- **Fuentes:**
  - SMS (hoy solo de Bancolombia);
  - notificaciones push de apps (Rappi);
  - Apple Wallet, sin transferencias.
- **Filtro inicial:** lo hacen las automatizaciones de Atajos del iPhone; el disparador funciona sin confirmación y con el teléfono bloqueado.
- **Datos:** el texto crudo se conserva 45 días. La categoría siempre es manual, y lo captado llega "Por clasificar".
- **Producto:** se identifica por entidad + últimos 4 dígitos.
- **Duplicados:** la propuesta inicial era mandar a revisión el mismo monto, comercio y producto en menos de 5 minutos. El 2026-11-09 el usuario la cambió por la espera y el emparejamiento por fuente (sección "Misma compra avisada por varias fuentes").

Los ejemplos reales están en [formatos-de-notificaciones.md](../capture/formatos-de-notificaciones.md). Muestran tres cosas:

- **Formatos de monto:** Bancolombia usa tres formatos distintos y Rappi un cuarto.
- **Mensajes sin movimiento:** hay SMS que no son movimientos.
- **Datos que faltan:** algunos textos no traen producto ni fecha.

## Decisión

### Entrada

- **Una automatización por fuente:**
  - Mensajes, filtrada por el remitente de Bancolombia;
  - la app de Rappi;
  - Wallet.
- **Un atajo compartido:** todas las automatizaciones llaman al mismo atajo, que hace `POST /api/v1/device/capture/notifications` con:
  - `app`: app de origen ("Mensajes", "Rappi", "Wallet");
  - `title`: título o remitente;
  - `body`;
  - `receivedAt`: la hora del iPhone en ISO 8601 con zona.
- **El endpoint:**
  - solo valida y guarda;
  - responde `202 Accepted`;
  - no analiza nada en la petición, para que el atajo termine rápido y en segundo plano.

### Token de dispositivo

- **Dónde vive:** en `identity`, porque es global a la cuenta. Se envía como `Authorization: Bearer`.
- **Cómo se guarda:** solo su hash SHA-256; el valor se muestra una vez al crearlo.
- **Gestión:** en Ajustes → Conexiones, con nombre, permisos por herramienta (hoy solo `finance:capture`), fecha de último uso y revocación.
- **Formato:** `ptd_` más 32 bytes aleatorios en Base64 URL. Como es aleatorio y largo, basta un SHA-256 sin sal, único en la base de datos.
- **Alcance:**
  - Los endpoints de dispositivo viven bajo `/api/v1/device/**`, en una cadena de seguridad propia: sin sesión, sin cookies y sin CSRF.
  - Cada ruta exige el permiso de su herramienta (`/api/v1/device/capture/**` exige `finance:capture`).
  - El token no abre sesión ni da acceso al resto de la API, y una sesión del navegador no abre los endpoints de dispositivo.
  - El propietario sale del token, nunca del cuerpo.
- **Logs:** no registran el token ni el texto de la notificación.

### Cola y procesamiento

- **Cola:** tabla `capture.notifications` con estado `RECEIVED → RECORDED | REVIEW | PAIRED | DISCARDED`.
- **Espera:** cada notificación se procesa **2 minutos** después de recibirla, para que lleguen los demás avisos de la misma compra. En cada lote, los avisos de los bancos se procesan antes que los de Wallet, que traen menos datos.
- **Trabajador:**
  - Toma lotes con `FOR UPDATE SKIP LOCKED`.
  - No agrega dependencias: usa una tarea programada de Spring.
- **Reenvío exacto:** si llega la misma fuente, el mismo texto y la misma hora, se ignora con una clave única sobre su hash, y el endpoint responde 202 igual.
- **Texto crudo:** se borra a los 45 días; los datos extraídos se conservan.

### Analizadores

- **Plantillas por entidad y fuente:** cada una es una expresión regular anclada, con su formato de monto y de fecha declarados.
- **Sin coincidencia exacta:** el texto va a "Por revisar" con el texto visible; no hay heurísticas genéricas.
- **Mensajes sin movimiento:** las plantillas de descarte (inscripciones, avisos) los marcan `DISCARDED`.
- **Montos y fechas:**
  - Los montos se convierten a `Money` (COP, escala 2).
  - Las fechas se leen en la zona horaria del perfil.
  - Si el texto no trae fecha, se usa `receivedAt`.

### Identificación del producto

1. **Entidad + últimos 4 dígitos:** si hay un único producto activo que coincide, se usa ese.
2. **Entidad + tipo (tarjeta de crédito o depósito), cuando el texto no trae dígitos:** se usa si hay un único producto que coincide (por ejemplo, RappiCard o "tu cuenta AHORROS").
3. **Ningún producto coincide:** "Otro".
4. **Varios productos coinciden:** "Por revisar".

### Registro

- **Movimiento completo y sin conflicto** (monto, tipo, producto y fecha):
  - se crea con `finance.api`, con `origin = CAPTURE`, sin categoría y con el medio de pago de la plantilla;
  - una migración de `finance` agrega la columna `origin` (`MANUAL` o `CAPTURE`).
- **Transferencias propias, moneda extranjera y consignaciones:** ver las secciones siguientes.
- **Revisión:** se hace desde Movimientos, con los filtros "Por revisar" y "Por clasificar". No es una sección aparte.
- **Borrado:** al borrar un movimiento captado, la notificación queda `DISCARDED` ([ADR-0015](0015-edicion-y-borrado-de-movimientos.md)).

### Despliegue (fase 4b)

El iPhone necesita llegar al backend por HTTPS. Dónde se publica sigue pendiente (PROJECT_CONTEXT §16).

- **Fase 4a:** todo lo anterior, probado en local con los textos del catálogo.
- **Fase 4b:** publicación y el atajo real.

### Misma compra avisada por varias fuentes

Decidido por el usuario el 2026-11-09, después de comparar el historial de Wallet con los SMS.

- **Por qué no sirve el comercio:** Wallet usa la ubicación del comercio y el banco el nombre con el que está registrado (`D1 - Antonia Santos` frente a `TIENDA D1 LOS SANTOS`).
- **Qué es la misma compra:** dos avisos de **fuentes distintas** (por ejemplo, SMS de Bancolombia y Wallet) con el mismo producto identificado y el mismo monto en COP, ocurridos con menos de 5 minutos de diferencia.
  - **Monto:** se admite menos de un peso de diferencia, porque Wallet no muestra centavos.
- **Resultado:**
  - El primer aviso registra el movimiento.
  - El otro queda `PAIRED` (emparejado), sin movimiento y enlazado al primero.
  - Si el aviso del banco llega tarde y Wallet ya registró la compra, el del banco se empareja con ella.
- **Uno a uno:** cada aviso se empareja con uno solo, el más cercano en el tiempo.
  - Tres compras iguales seguidas son tres SMS y tres avisos de Wallet: quedan tres movimientos.
  - Dos avisos de la **misma fuente** nunca se emparejan, porque son compras distintas.
- **Producto sin identificar:** no se empareja, porque "Otro" agrupa productos distintos.
- **Producto por candidatos (2026-11-12):** un aviso se empareja si el producto del otro está entre sus candidatos.
  - El aviso de Apple Wallet solo nombra al emisor, así que sus candidatos son todas las tarjetas y cuentas de ese banco (`PAYMENT_CARD`); el SMS dice cuál fue.
  - El aviso emparejado toma el producto del otro.
  - Formato W1 en el catálogo.

### Transferencias a productos propios (B3)

Decidido por el usuario el 2026-11-09.

- **Datos propios, en Ajustes → Perfil:**
  - **Nombres y apellidos**, en campos separados (`identity`).
  - **Mis llaves Bre-B**, cada una con el producto propio al que llega (`capture`). Por ejemplo, la llave `@rp…` va a RappiCuenta.
- **Coincidencia por llave:**
  - Si la llave de destino es una de las tuyas, la operación se registra directamente como **transferencia** desde el producto de origen al producto de la llave.
- **Coincidencia por nombre:**
  - **Regla:** el destinatario coincide si todas sus palabras están entre tus nombres y apellidos, sin importar mayúsculas, tildes ni orden, e incluye **al menos un nombre y al menos un apellido**. Por ejemplo, para "Ana María Pérez Gómez" coinciden "María Pérez" y "Ana María Gómez"; "Ana" sola o "Ana López" no.
  - **Resultado:** el nombre no basta para saber el producto de destino ni para descartar a un homónimo. El movimiento va a "Por revisar" con la casilla **"Es mía (transferencia entre mis productos)"** marcada. Al confirmarlo eliges el producto de destino; si desmarcas la casilla, queda como gasto.
- **Desmarcar después:** los movimientos captados como transferencia ofrecen "No es mía". Esa acción borra la transferencia y vuelve a registrar la misma notificación como gasto. El tipo de un movimiento sigue sin poder cambiarse en `finance` ([ADR-0015](0015-edicion-y-borrado-de-movimientos.md)); la conversión la hace `capture` a partir de la notificación.
- **Contraparte:** si el producto de destino también avisa que recibió el dinero (por ejemplo, Rappi), ese aviso se marca como **parte de la transferencia** y no crea un ingreso. Para eso debe tener el mismo monto, llegar al mismo producto y estar dentro de la ventana de 5 minutos. Queda pendiente hasta tener un ejemplo del aviso de Rappi de dinero recibido.

### Compras en moneda extranjera (B1c)

Decidido por el usuario el 2026-11-09: la app sigue trabajando solo en COP y la conversión es automática.

- **Tasa:**
  - Se usa la **TRM oficial** del día de la compra, del conjunto de datos abierto de la Superintendencia Financiera (`https://www.datos.gov.co/resource/32sa-8pi3.json`).
  - Es pública y no pide clave. La consulta solo envía la fecha, ningún dato del usuario.
  - Se llama con el `RestClient` de Spring, sin dependencias nuevas.
- **Registro:**
  - El gasto se registra en COP: monto en USD × TRM, redondeado a 2 decimales con `HALF_EVEN`.
  - La notificación guarda el monto original, la moneda y la tasa usada.
  - El movimiento se marca como **"Monto estimado"**, para que lo ajustes con el valor del extracto si lo deseas.
- **Por qué es una estimación:**
  - El banco aplica su propia tasa, que suele ser algo mayor que la TRM.
  - Las tarjetas de crédito colombianas llevan aparte la deuda en dólares y la convierten al pagar.
  - El tratamiento exacto de la deuda en dólares queda para la Etapa 6 (tarjetas).
- **Si la consulta falla o no hay TRM para esa fecha:**
  - Se usa la vigente más reciente anterior a la compra.
  - Si tampoco la hay, el movimiento queda "Por revisar".

### Consignaciones (B7)

Decidido por el usuario el 2026-11-09: toda consignación se registra como **ingreso**, con medio `DEPOSIT`.

- **Producto:** como el texto no trae los últimos 4 dígitos, se usa la regla de entidad + tipo de producto. Por ejemplo, la única cuenta de depósito de Bancolombia.
- **Si fue tu propio efectivo:** borras el ingreso y registras la transferencia.

## Consecuencias

- **A favor:**
  - Un texto mal leído nunca se convierte en un monto equivocado; va a revisión.
  - Agregar un banco es agregar plantillas y una automatización.
  - El atajo es simple.
- **En contra:**
  - Si el banco cambia la redacción, la plantilla deja de coincidir. Los mensajes quedan "Por revisar" (no se pierden) hasta actualizarla.
  - El trabajador de la cola agrega unos segundos de retraso.

## Alternativas consideradas

- **Análisis con un modelo de lenguaje:**
  - envía datos financieros a un tercero;
  - tiene costo por mensaje;
  - sus errores no son predecibles.
- **Análisis en el iPhone (Atajos):** las reglas quedarían repartidas en el teléfono, sin pruebas ni versionado.
- **Analizador genérico de montos:** confunde `$100.00` (cien pesos) con diez mil.
- **Procesar dentro de la petición:** el atajo esperaría al análisis, y un error perdería la notificación.
