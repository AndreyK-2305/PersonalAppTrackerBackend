# ADR-0007: Topología de mismo origen y CORS restringido

- Estado: Aceptada
- Fecha: 2026-11-06

## Contexto

La autenticación usa cookies de sesión ([ADR-0004](0004-autenticacion-por-sesion.md)). Si la SPA y la API estuvieran en orígenes distintos, harían falta cookies `SameSite=None` y un CORS con credenciales, y la superficie de ataque crecería. El contexto exige CORS restringido a los orígenes autorizados.

## Decisión

- **Mismo origen:** el navegador habla con un solo origen.
  - En desarrollo, el proxy de Vite reenvía `/api` y `/actuator/health` al backend local, en `http://localhost:8090` por defecto.
  - En contenedores y producción, nginx sirve el frontend compilado y hace de proxy hacia el backend.
- **CORS:**
  - Lo configura `CorsConfig` y lo aplica Spring Security sobre `/api/**`.
  - Los orígenes permitidos se toman de `CORS_ALLOWED_ORIGINS`, que está **vacía por defecto**, de modo que se rechaza cualquier petición de otro origen.
  - Con credenciales, nunca se permite `*`.
- **Puertos en Docker Compose:** se publican solo en `127.0.0.1`.
- **Cabeceras que el proxy debe conservar:** el origen que ve el navegador (`Host` con su puerto, en nginx `$http_host`) y el protocolo (`X-Forwarded-Proto`).
  - El backend compara ese origen con la cabecera `Origin` de los POST para decidir si son del mismo origen.
  - Si el proxy envía el host sin puerto o su puerto interno, los POST se rechazan como CORS ("Invalid CORS request").
- **Puertos locales:** son 5434 (PostgreSQL) y 8090 (backend), y no 5432/8080, para convivir con otros proyectos de la máquina de desarrollo. Se configuran con `DB_PORT` y `BACKEND_PORT`.

## Consecuencias

- **A favor:**
  - Las cookies funcionan con `SameSite=Lax`.
  - No hay preflights CORS en el uso normal.
  - La configuración es simple.
- **Requisito:** los entornos deben tener un proxy, ya sea Vite, nginx o un balanceador.
- **Si en el futuro hay otro cliente en otro origen**, se agrega explícitamente a `CORS_ALLOWED_ORIGINS`.

## Alternativas consideradas

- **Orígenes separados con CORS y `SameSite=None`:** más configuración y más riesgo.
- **Que Spring Boot sirva los estáticos del frontend:** acoplaría los builds de dos repositorios independientes ([ADR-0009](0009-dos-repositorios.md)).
