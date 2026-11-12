# Despliegue en Oracle Cloud Always Free y Netlify

Fase 4b de la Etapa 4. El iPhone debe llegar al backend por internet con HTTPS.

## Arquitectura

```text
iPhone (atajo) ──HTTPS──▶ api.cuadre.click ─┐
                                            │  VM de Oracle (Docker Compose, deploy/compose.yml)
Navegador ─▶ cuadre.click (Netlify) ─proxy─▶├─ caddy     HTTPS automático (Let's Encrypt)
            frontend estático               ├─ backend   Spring Boot
                                            ├─ postgres  datos
                                            └─ backup    copia diaria de la base
```

- **Netlify** sirve el frontend y reenvía `/api/*` y `/actuator/health` a `api.cuadre.click` (`frontend/netlify.toml`). Para el navegador todo es del mismo origen, así que la sesión y el CSRF funcionan sin cambios (ADR-0007).
- **CORS:** el backend admite el `Origin` `https://cuadre.click` con `CORS_ALLOWED_ORIGINS`, porque el proxy de Netlify lo conserva.
- **Caddy** publica solo la API y el health check. Swagger y el resto de Actuator no salen a internet.
- **El atajo del iPhone** llama directo a `https://api.cuadre.click`, sin pasar por Netlify.

Probado en local el 2026-11-12 con `API_DOMAIN=localhost`:

- health check;
- 404 fuera de la API;
- 401 sin sesión ni token;
- CORS: el origen permitido pasa y uno ajeno recibe 403;
- redirección de HTTP a HTTPS y HSTS;
- copia y restauración de la base.

## 1. Cuenta de Oracle Cloud

1. **Crea la cuenta** en [oracle.com/cloud/free](https://www.oracle.com/cloud/free/). Pide una tarjeta para verificar tu identidad.
2. **Elige la región de origen ("home region") con cuidado:** no se puede cambiar, y las VM gratuitas solo se crean allí. Elige la más cercana con capacidad ARM.
3. **Pasa la cuenta a "Pay As You Go"** (Billing → Upgrade).
   - Sigue costando 0 mientras uses solo recursos "Always Free".
   - Evita que Oracle recupere la VM por baja actividad. Esta app casi no usa CPU, y Oracle considera "inactiva" una VM con menos del 20 % de CPU durante 7 días.
4. **Crea un presupuesto con alerta** (Billing → Budgets) de 1 USD, para enterarte si algo empieza a cobrarse.

## 2. Máquina virtual

En Compute → Instances → Create instance:

| Campo | Valor |
|---|---|
| Imagen | Canonical Ubuntu 24.04 |
| Forma | `VM.Standard.A1.Flex` (Ampere, marcada "Always Free-eligible"): 2 OCPU y 12 GB. Revisa en la página de [Always Free Resources](https://docs.oracle.com/iaas/Content/FreeTier/resourceref.htm) cuánto permite hoy |
| Red | VCN nueva con subred pública y **IP pública** |
| Llave SSH | Sube tu llave pública o descarga la que genera Oracle (guárdala bien) |
| Disco de arranque | 50 GB (el plan gratuito incluye hasta 200 GB en total) |

- **IP fija:** después de crearla, en la VNIC de la instancia convierte la IP pública en **reservada**, para que no cambie si la VM se reinicia.
- **"Out of host capacity":** si aparece este error, no hay máquinas ARM libres en ese momento. Reintenta más tarde o prueba otro "availability domain".

### Puertos

1. **Lista de seguridad de la subred** (Networking → VCN → Security Lists): agrega reglas de entrada TCP 80 y 443 desde `0.0.0.0/0`. Para SSH (22), es mejor permitir solo tu IP.
2. **Firewall interno:** las imágenes Ubuntu de Oracle también traen reglas de `iptables` que bloquean todo menos SSH. Conéctate por SSH y ábrelas:

   ```bash
   ssh ubuntu@<IP_PUBLICA>
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
   sudo netfilter-persistent save
   ```

## 3. Docker en la VM

```bash
sudo apt-get update && sudo apt-get upgrade -y
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
exit   # vuelve a entrar por SSH para que el grupo docker tenga efecto
```

Docker queda activado al arrancar el sistema. Los contenedores usan `restart: unless-stopped`, así que vuelven solos tras un reinicio.

## 4. DNS de cuadre.click

En el proveedor donde administras el DNS del dominio:

| Registro | Nombre | Valor |
|---|---|---|
| A | `api` | IP pública reservada de la VM |
| (el que indique Netlify) | `cuadre.click` y `www` | Netlify (paso 6) |

Espera a que `api.cuadre.click` resuelva a la IP (`nslookup api.cuadre.click`) antes del paso 5. Caddy necesita que el dominio ya apunte a la VM para obtener el certificado.

## 5. Backend

```bash
git clone https://github.com/AndreyK-2305/PersonalAppTrackerBackend.git
cd PersonalAppTrackerBackend/deploy
cp .env.example .env
openssl rand -base64 32      # cópialo como DB_PASSWORD en .env
nano .env                    # revisa API_DOMAIN y CORS_ALLOWED_ORIGINS
docker compose up -d --build --wait
curl https://api.cuadre.click/actuator/health    # {"status":"UP",...}
```

- **Primera construcción:** compila el backend dentro de Docker y tarda varios minutos.
- **Si el repositorio es privado:** crea un token de acceso de solo lectura en GitHub o usa una "deploy key".

## 6. Frontend en Netlify

1. **Crea el sitio:** en Netlify, Add new site → Import an existing project → GitHub → `PersonalAppTrackerFront`. La configuración sale de `netlify.toml` (`npm run build`, carpeta `dist`, Node 24).
2. **Agrega el dominio:** en Domain management, agrega `cuadre.click` y sigue las instrucciones de DNS que te dé Netlify.
3. **Espera el HTTPS:** se activa solo cuando el DNS propague.
4. **Comprueba:** abre `https://cuadre.click/ajustes/sistema`. Debe mostrar el sistema "operativo", con el health check pasando por el proxy de Netlify.

## 7. Primera cuenta

El registro está cerrado (ADR-0012). En la VM:

```bash
cd PersonalAppTrackerBackend/deploy
sed -i 's/^REGISTRATION_ENABLED=.*/REGISTRATION_ENABLED=true/' .env && docker compose up -d --wait backend
# Regístrate en https://cuadre.click/registro
sed -i 's/^REGISTRATION_ENABLED=.*/REGISTRATION_ENABLED=false/' .env && docker compose up -d --wait backend
```

Después, en la app:

1. Completa la bienvenida de Finanzas.
2. Crea tus productos con su entidad y sus últimos 4 dígitos.
3. Escribe tus nombres y apellidos y registra tus llaves Bre-B en **Ajustes → Perfil**.

## 8. Atajo del iPhone

1. **Token:** crea un token en **Ajustes → Conexiones** y cópialo. Solo se muestra una vez.
2. **Atajo "Enviar a Personal Tracker":** recibe la notificación como entrada y usa la acción **Obtener contenido de URL**:
   - **URL:** `https://api.cuadre.click/api/v1/device/capture/notifications`
   - **Método:** POST
   - **Encabezados:**
     - `Authorization`: `Bearer <token>`
     - `Content-Type`: `application/json`
   - **Cuerpo (JSON):**
     - `app`: la app de la notificación ("Mensajes", "Rappi", "Wallet");
     - `title`: el título o remitente;
     - `body`: el texto;
     - `receivedAt`: la fecha actual con formato **ISO 8601** e incluyendo la hora.
3. **Automatizaciones:** una automatización "Al recibir una notificación" por fuente. Cada una ejecuta el atajo inmediatamente, sin confirmación y con "Notificar al ejecutar" desactivado:
   - **Mensajes:** solo los SMS de Bancolombia (remitente 85540);
   - **Rappi;**
   - **Wallet.**
4. **Prueba:** haz una compra pequeña. A los 2 minutos debe aparecer en Movimientos como "Captado".

## Operación

| Tarea | Comando, en `deploy/` |
|---|---|
| Actualizar a la última versión | `git pull && docker compose up -d --build --wait` |
| Ver el estado | `docker compose ps` |
| Ver los registros del backend | `docker compose logs -f backend` |
| Copias de la base | Carpeta `deploy/backups/`: una diaria, se conservan 14 días |
| Restaurar una copia | `docker compose exec backup pg_restore --clean --if-exists -d personal_tracker /backups/<archivo>.dump` |

**Las copias quedan en la misma VM.** Si la VM se pierde, se pierden con ella. Conviene descargarlas de vez en cuando:

```bash
scp ubuntu@<IP>:PersonalAppTrackerBackend/deploy/backups/*.dump .
```

También se puede automatizar la copia a Oracle Object Storage, que incluye 20 GB gratis.

## Pendientes conocidos

- **Límite de intentos por IP en el inicio de sesión (D16):** lo hacía el nginx del frontend en Docker Compose, y con Netlify y Caddy no existe. Sigue activo el bloqueo por cuenta (5 intentos, 15 minutos). Hay que implementarlo en el backend o con un módulo de Caddy.
- **Copias fuera de la VM:** hoy solo existen en la VM.
- **Migración a un equipo propio:** la misma carpeta `deploy/` funciona en cualquier máquina con Docker y los puertos 80 y 443 abiertos.
