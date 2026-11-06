# ADR-0011: Flujo de autenticación, bloqueo por intentos y política de contraseñas

- Estado: Aceptada
- Fecha: 2026-11-06
- Complementa: [ADR-0004](0004-autenticacion-por-sesion.md)

## Contexto

El ADR-0004 fijó la sesión en el servidor con CSRF. Faltaba concretar cómo se inicia sesión sin perder la arquitectura hexagonal, cómo se frenan los ataques de fuerza bruta sin Redis y qué contraseñas se aceptan. Las decisiones D13, D14, D16 y D17 se aprobaron el 2026-11-06.

## Decisión

**Inicio de sesión**

1. `POST /api/v1/auth/login` llega a `AuthController`, que invoca el caso de uso `AuthenticateUser`.
   - Las reglas de negocio quedan en el hexágono, probadas sin Spring: bloqueo, normalización del email y cuenta inexistente.
   - No se usa el `DaoAuthenticationProvider` de Spring Security.
2. Si las credenciales son válidas, `SessionLogin` abre la sesión:
   - guarda el `SecurityContext` en la sesión, persistida por Spring Session JDBC en `identity.spring_session`;
   - cambia el identificador de la sesión, contra la fijación de sesión;
   - emite un token CSRF nuevo.
3. **Principal de la sesión:** es `AuthenticatedUser(userId)`, cuyo nombre es el id del usuario.
   - Spring Session indexa las sesiones por ese nombre, lo que permite cerrarlas todas.
   - Los controladores de cualquier módulo obtienen el dueño con `@CurrentUser UserId` (`identity.api`).
4. **Cierre de sesión:** `POST /api/v1/auth/logout` lo atiende el filtro de Spring Security. Responde 204, invalida la sesión y borra el token CSRF.

**Protección contra enumeración de cuentas**

- Una cuenta inexistente y una contraseña incorrecta responden lo mismo: `401 identity.invalid_credentials` con un mensaje idéntico.
- Cuando la cuenta no existe, la contraseña se compara igualmente contra un hash ficticio, para que el tiempo de respuesta no delate si el correo está registrado.

**Bloqueo por intentos fallidos (D16)**

- Al quinto intento fallido, la cuenta queda bloqueada **15 minutos**.
- Mientras está bloqueada, cualquier intento, incluso con la contraseña correcta, responde `429 identity.too_many_attempts`.
- El contador vuelve a cero al iniciar sesión con éxito o al vencer el bloqueo.
- El estado se guarda en `identity.users` (`failed_login_attempts`, `locked_until`). El caso de uso usa `noRollbackFor` para que el intento fallido quede registrado aunque la operación termine en error.
- **Límite por IP en nginx:** 10 peticiones por minuto con ráfaga de 5 sobre `/login` y `/register`, respondiendo 429 en formato ProblemDetail. Frena los intentos repartidos entre muchas cuentas desde una misma dirección.

**Política de contraseñas (D14)**

- Entre 12 y 64 caracteres y **72 bytes como máximo** en UTF-8, porque BCrypt ignora lo que sigue.
- Sin reglas de composición, en línea con NIST SP 800-63B: la longitud aporta más que los símbolos obligatorios.
- **Hash:** `DelegatingPasswordEncoder`, con BCrypt por defecto.
- **Entradas largas:** el adaptador rechaza sin calcular el hash cualquier entrada de más de 72 bytes.

**Sesión y cambio de contraseña (D13, D17)**

- La sesión expira tras 60 minutos de inactividad (`SESSION_TIMEOUT`).
- La cookie es `SESSION; HttpOnly; Secure; SameSite=Lax`.
- `PUT /api/v1/me/password` exige la contraseña actual y cierra las demás sesiones del usuario mediante el puerto `SessionTerminator`. La sesión desde la que se pide el cambio sigue abierta.

## Consecuencias

- **A favor:**
  - El bloqueo funciona sin Redis ni dependencias nuevas.
  - Las reglas de seguridad tienen pruebas unitarias (`AuthenticateUserTests`, `UserTests`) y de integración (`AuthenticationFlowTests`).
- **Bloqueo como vía de abuso:** alguien que conozca tu correo puede bloquear tu cuenta 15 minutos a propósito. Se acepta por ser temporal, y el límite de nginx reduce el abuso.
- **Contexto en sesiones antiguas:** el contexto de seguridad se serializa con Java en la tabla de sesiones. Si cambia la clase `AuthenticatedUser`, las sesiones anteriores dejan de ser válidas y obligan a iniciar sesión de nuevo.
- **Desarrollo desde otro dispositivo:** la cookie `Secure` no viaja por HTTP fuera de `localhost`. Para probar desde un teléfono en la red local hace falta HTTPS.

## Alternativas consideradas

- **`DaoAuthenticationProvider` con un `UserDetailsService`:** deja el bloqueo y las reglas en la configuración de Spring, fuera del dominio.
- **Bloqueo exponencial o CAPTCHA:** más complejos; se reconsiderarán si se abre el registro al público.
- **Argon2id:** requiere BouncyCastle. El encoder delegante permite migrar después sin forzar cambios de contraseña.
