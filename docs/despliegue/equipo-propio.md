# Publicación desde el equipo propio con Cloudflare Tunnel

Fase 4b de la Etapa 4, decidida por el usuario el 2026-11-12. La app corre en el PC del usuario y se publica en `cuadre.click` con Cloudflare Tunnel, sin abrir puertos en el router. El despliegue en Oracle Cloud y Netlify se conserva fuera del repositorio para el futuro.

## Arquitectura

```text
iPhone (atajo) ─┐
                ├─HTTPS─▶ Cloudflare (cuadre.click) ═túnel saliente═▶ PC del usuario (Docker Compose)
Navegador ──────┘                                                     ├─ tunnel    cloudflared
                                                                      ├─ frontend  nginx: SPA + proxy /api
                                                                      ├─ backend   Spring Boot
                                                                      ├─ postgres  datos
                                                                      └─ backup    copia diaria
```

- **HTTPS:** Cloudflare termina el HTTPS. `cloudflared` llega al nginx del frontend por la red interna de Docker.
- **Mismo origen:** el nginx sirve la SPA y reenvía `/api` al backend, como en desarrollo (ADR-0007).
- **Cabeceras del túnel en nginx** (probado en local el 2026-11-12):
  - **`X-Forwarded-Proto: https`:** nginx lo respeta, así el backend ve el origen `https://cuadre.click` y acepta los POST del navegador. Sin ese ajuste, el login respondía 403.
  - **`CF-Connecting-IP`:** nginx toma de ahí la IP real del visitante. El límite de 10 intentos de inicio de sesión por minuto (D16) se aplica a cada visitante, no al túnel.
  - **Solo de redes internas:** esas cabeceras se aceptan únicamente desde las redes privadas de Docker y del propio equipo.
- **Sin puertos abiertos:** PostgreSQL, el backend y el frontend solo escuchan en `127.0.0.1` del PC.

**Limitación:** el PC tiene que estar encendido, sin suspenderse, con la sesión iniciada y Docker Desktop abierto cuando llegue una notificación.
- El atajo del iPhone no reintenta: lo que llegue con el PC apagado no se captura.
- El SMS sigue en Mensajes y se puede registrar a mano.

## 1. Cloudflare y el dominio

1. **Agrega el dominio:** crea una cuenta gratuita en [cloudflare.com](https://dash.cloudflare.com/sign-up) y agrega el sitio `cuadre.click` en el plan **Free**.
2. **Cambia los nameservers:** en el registrador del dominio, reemplázalos por los dos que te indique Cloudflare. El sitio queda "Active" cuando propaga, en minutos u horas.
3. **No dejes que Cloudflare bloquee el atajo:** el iPhone no puede resolver desafíos de navegador, así que un desafío haría fallar la captura.
   - En **Security → Bots**, deja **Bot Fight Mode** apagado.
   - O, si lo activas, crea una regla en **Security → WAF → Custom rules** que omita las protecciones para la ruta `/api/v1/device/*`.

## 2. Túnel

1. En Cloudflare, ve a **Zero Trust → Networks → Tunnels → Create a tunnel** y elige el tipo **Cloudflared**, con nombre `personal-tracker`.
2. En "Install and run a connector", elige Docker y copia **solo el token**, el texto largo que va después de `--token`. Es un secreto.
3. En **Public Hostname**, agrega una ruta:

   | Campo | Valor |
   |---|---|
   | Subdomain | (vacío) |
   | Domain | `cuadre.click` |
   | Service | `HTTP` → `frontend:8080` |

   `frontend` es el nombre del servicio en `docker-compose.yml`: `cloudflared` lo encuentra por la red interna de Docker.

## 3. Configuración en el PC

En `backend/`, crea o completa `.env` (no se versiona; ver `.env.example`):

```bash
TUNNEL_TOKEN=<el token del túnel>
CORS_ALLOWED_ORIGINS=https://cuadre.click
DB_PASSWORD=<una contraseña larga: openssl rand -base64 32>
BACKUP_DIR=./backups
BACKUP_KEEP_DAYS=14
REGISTRATION_ENABLED=false
```

- **`CORS_ALLOWED_ORIGINS`** es un respaldo. El mismo origen ya funciona gracias al ajuste de nginx, pero si `cloudflared` cambiara la cabecera `Host`, el login seguiría funcionando.
- **`DB_PASSWORD`** solo se aplica al crear la base. Si el volumen `postgres-data` ya existe con la contraseña de desarrollo, no cambia. Para usar una nueva desde cero, haz primero una copia y luego `docker compose down -v`, **que borra los datos**.
- **Copias en OneDrive:** `BACKUP_DIR=./backups` queda dentro de la carpeta del proyecto, que está en OneDrive, así que las copias también se suben a tu OneDrive. Eso sirve como copia fuera del equipo, pero ten presente que contienen tus datos financieros. Si no lo quieres, usa una carpeta fuera de OneDrive.

## 4. Arrancar

Con Docker Desktop encendido, en `backend/`:

```bash
docker compose --profile full --profile tunnel up -d --build --wait
```

Abre `https://cuadre.click/ajustes/sistema`: debe mostrar el sistema "operativo".

### Que siga corriendo

- **Docker Desktop:** Settings → General → **Start Docker Desktop when you sign in**. Los contenedores tienen `restart: unless-stopped` y vuelven solos.
- **Windows:** Configuración → Sistema → Energía → suspender **Nunca** cuando está conectado a la corriente.
- **Tras un reinicio:** Docker Desktop arranca solo cuando inicias sesión en Windows.

## 5. Primera cuenta

El registro está cerrado (ADR-0012):

1. Pon `REGISTRATION_ENABLED=true` en `.env`.
2. Ejecuta `docker compose --profile full up -d --wait backend`.
3. Regístrate en `https://cuadre.click/registro`.
4. Vuelve a poner `false` y ejecuta de nuevo el mismo comando.

Después, en la app:

1. Completa la bienvenida de Finanzas.
2. Crea tus productos con su entidad y sus últimos 4 dígitos.
3. En **Ajustes → Perfil**, escribe tus nombres y apellidos y registra tus llaves Bre-B.

## 6. Atajo del iPhone

1. **Token:** créalo en **Ajustes → Conexiones** y cópialo. Solo se muestra una vez.
2. **Atajo "Enviar a Personal Tracker":** recibe la notificación como entrada y usa la acción **Obtener contenido de URL**:
   - **URL:** `https://cuadre.click/api/v1/device/capture/notifications`
   - **Método:** POST
   - **Encabezados:**
     - `Authorization`: `Bearer <token>`
     - `Content-Type`: `application/json`
   - **Cuerpo (JSON):**
     - `app`: la app de la notificación ("Mensajes", "Rappi", "Wallet");
     - `title`: el título o remitente;
     - `body`: el texto;
     - `receivedAt`: la fecha actual en **ISO 8601** con la hora.
3. **Automatizaciones:** crea una de tipo "Al recibir una notificación" por cada fuente. Cada una debe ejecutar el atajo de inmediato, sin confirmación y con "Notificar al ejecutar" desactivado:
   - Mensajes, solo para el remitente 85540 de Bancolombia;
   - Rappi;
   - Wallet.
4. **Prueba:** haz una compra pequeña. A los 2 minutos debe aparecer en Movimientos como "Captado".

## Operación

| Tarea | Comando, en `backend/` |
|---|---|
| Actualizar | `git pull` en `backend/` y `frontend/`, y luego `docker compose --profile full --profile tunnel up -d --build --wait` |
| Ver el estado | `docker compose --profile full --profile tunnel ps` |
| Ver los registros | `docker compose logs -f backend` (o `tunnel`, `frontend`) |
| Detener la app publicada | `docker compose --profile full --profile tunnel stop` |
| Restaurar una copia | `docker compose exec backup pg_restore --clean --if-exists -d personal_tracker /backups/<archivo>.dump` |

- **Desarrollo y app publicada comparten PostgreSQL y el volumen `postgres-data`.** Para desarrollar con `./mvnw spring-boot:run` (puerto 8090), detén antes el contenedor `backend`, porque usa el mismo puerto. Tus datos reales y los de desarrollo son los mismos.
- **Nunca uses `docker compose down -v`** sin una copia reciente: borra la base.

## Más adelante

- **Más privacidad:** proteger la app con **Cloudflare Access** (Zero Trust, gratis para uso personal), con un código por correo para entrar desde el navegador. Habría que excluir la ruta `/api/v1/device/*` o usar un "service token" para que el atajo siga funcionando.
- **Publicar en la nube:** el despliegue en Oracle Cloud y Netlify se preparó y se conserva solo en local (`backend/deploy/`, `backend/docs/despliegue/oracle-cloud.md`, `frontend/netlify.toml`, ignorados por git).
