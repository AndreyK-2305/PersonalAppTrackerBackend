# ADR-0001: Monolito modular con arquitectura hexagonal

- Estado: Aceptada (la lista de módulos se actualizó en [ADR-0016](0016-modulos-capture-y-messaging.md))
- Fecha: 2026-11-06

## Contexto

Personal Tracker empieza con finanzas y luego incorporará el garaje, reportes y notificaciones. Lo desarrolla una sola persona de forma incremental. Hay tres exigencias: integridad financiera, aislamiento por usuario y facilidad para agregar módulos sin tocar los existentes. Al mismo tiempo, hay que evitar la complejidad operativa de un sistema distribuido.

## Decisión

- **Un monolito:** una sola aplicación Spring Boot desplegable y una sola base de datos PostgreSQL.
- **Modularidad por dominio:** `shared`, `identity`, `finance` y, más adelante, `notifications`, `reports` y `garage`. Cada módulo declara sus dependencias permitidas en `package-info.java` (`@ApplicationModule`) y expone a los demás solo su paquete `api` (`@NamedInterface`).
- **Arquitectura hexagonal dentro de cada módulo:** `domain`, `application` e `infrastructure`, con dependencias de afuera hacia adentro.
- **Comunicación entre módulos:**
  - llamadas síncronas en proceso a través de `api`;
  - eventos de aplicación de Spring solo cuando un módulo debe reaccionar sin acoplarse.
  - No hay brokers. El registro persistente de eventos de Spring Modulith se agregará solo si se necesita entrega garantizada.
- **Verificación automática:** Spring Modulith (`ApplicationModules.verify()`) valida los límites entre módulos y ArchUnit valida las capas internas. Ambas pruebas son parte del build.
- **Creación de módulos:** un módulo se crea cuando llega su primer caso de uso real; nunca como placeholder.

## Consecuencias

- **A favor:**
  - Despliegue, transacciones y depuración simples.
  - Los límites se pueden comprobar y una violación rompe el build.
  - Se puede agregar `garage` sin modificar `finance`, porque `garage` depende de `finance.api` y no al revés.
- **En contra:**
  - Más código de mapeo, porque hay tres representaciones de cada entidad: dominio, JPA y DTO.
  - Se acepta este costo por la riqueza de las reglas financieras. Para mantenerlo bajo, los mappers son manuales y pequeños.
- **Escalado:** un solo proceso escala verticalmente. Las tareas programadas (Etapa 5) asumen una sola instancia.

## Alternativas consideradas

- **Microservicios:** descartados por el contexto del proyecto; son complejidad operativa sin beneficio para el MVP.
- **Arquitectura por capas técnicas** (controllers, services, repositories globales): no deja límites claros entre dominios ni facilita agregar módulos.
- **Solo hexagonal, sin verificación automática:** los límites se erosionan con el tiempo.
