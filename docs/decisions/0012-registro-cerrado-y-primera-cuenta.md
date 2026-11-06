# ADR-0012: Registro cerrado y alta de la primera cuenta

- Estado: Aceptada
- Fecha: 2026-11-06

## Contexto

La aplicación es de uso personal, pero debe admitir otras cuentas en el futuro. Todavía no hay envío de correos (llega en la Etapa 5), así que no hay recuperación de contraseña ni verificación del email. Abrir el registro al público implicaría además cumplir la Ley 1581 de 2012 (habeas data). El usuario decidió mantener el registro cerrado (D11) y crear la primera cuenta activando temporalmente el registro (D12).

## Decisión

- **Registro cerrado por defecto:**
  - `app.identity.registration-enabled`, que corresponde a la variable `REGISTRATION_ENABLED`, vale `false` por defecto.
  - Con el registro cerrado, `POST /api/v1/auth/register` responde `403 identity.registration_closed`.
  - `GET /api/v1/auth/registration` informa el estado al frontend, que oculta el enlace "Crear una cuenta".
- **Procedimiento para crear la primera cuenta:**
  1. Arrancar el backend con `REGISTRATION_ENABLED=true`, ya sea en `.env` o en la variable de entorno.
  2. Entrar a `/registro` y crear la cuenta. Se inicia sesión automáticamente.
  3. Volver a `REGISTRATION_ENABLED=false` y reiniciar el backend.
- **Despliegue público futuro:** el primer registro debe hacerse **antes** de exponer el servicio en Internet, por ejemplo con acceso solo local, para no dejar una ventana en la que otra persona pueda registrarse.
- **Sin recuperación de contraseña hasta la Etapa 5.** Si se olvida la contraseña, solo el administrador del servidor puede restablecerla directamente en la base de datos.

## Consecuencias

- **A favor:** no hace falta más código que la bandera, y no hay superficie de registro expuesta en el uso normal.
- **En contra:** es un paso manual, y olvidar la contraseña exige intervenir la base de datos.
- **Antes de abrir el registro a terceros hay que resolver:**
  - la Ley 1581 (política de tratamiento, autorización y eliminación de la cuenta);
  - la recuperación de contraseña;
  - la verificación del correo;
  - la enumeración de cuentas (el 409 por email duplicado).

## Alternativas consideradas

- **Pantalla de configuración inicial con un token de un solo uso:** elimina el paso manual, pero agrega código y una pantalla que solo se usa una vez.
- **Registro abierto:** se descartó por la falta de correo y por las obligaciones legales.
