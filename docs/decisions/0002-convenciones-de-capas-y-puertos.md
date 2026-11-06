# ADR-0002: Convenciones de capas, puertos e idioma del código

- Estado: Aceptada
- Fecha: 2026-11-06

## Contexto

El contexto del proyecto menciona contratos de repositorio en el dominio "cuando corresponda" y, a la vez, puertos de salida en la capa de aplicación. También exige puertos de entrada y, al mismo tiempo, prohíbe abstracciones sin una justificación concreta (reglas 9 y 12). Hacen falta convenciones únicas y verificables.

## Decisión

1. **Puertos de salida:** todos, incluidos los repositorios, viven en `application/port/out` como interfaces. El dominio contiene solo modelo y reglas.
2. **Puertos de entrada:**
   - cada caso de uso es una **clase concreta** con un método `execute`, por ejemplo `RegisterExpense.execute(RegisterExpenseCommand)`; esa clase es el puerto de entrada;
   - no se crea una interfaz por caso de uso.
3. **Interfaces:** se crean solo en dos casos:
   - el paquete `api` de un módulo, que es su contrato con otros módulos;
   - los puertos de salida, que tienen implementaciones reales (adaptador JPA y dobles de prueba).
4. **Frameworks por capa:**
   - `domain`: Java puro.
   - `application`: de Spring solo `@Service`/`@Component` y `@Transactional`.
   - `infrastructure`: sin restricción.
5. **Controladores:** traducen HTTP ↔ comando/resultado y delegan en un caso de uso. Sin reglas de negocio.
6. **DTO y objetos de valor:** records. Mappers manuales. Sin Lombok ni MapStruct.
7. **Idioma:**
   - Código, identificadores y API en inglés.
   - Interfaz de usuario y documentación en español.
   - Mensajes de error para el usuario en español.
   - La traducción de términos se fija en el [glosario de dominio](../architecture/domain-glossary.md).

Las reglas 1, 3, 4 y 5 se verifican con `HexagonalArchitectureTests`.

## Consecuencias

- **A favor:**
  - Menos archivos y menos indirección.
  - Los controladores se prueban sustituyendo la clase del caso de uso.
  - Las reglas se pueden comprobar automáticamente.
- **`@Transactional` en la capa de aplicación:** es un acoplamiento a Spring limitado a anotaciones; la lógica no depende de Spring. Hacer la capa 100 % libre de Spring obligaría a cablear cada caso de uso a mano y a decorar las transacciones, con más código y sin beneficio real.
- **Si un caso de uso llega a tener una segunda implementación real**, se extrae una interfaz en ese momento.

## Alternativas consideradas

- **Una interfaz más una implementación por caso de uso:** duplica clases sin un segundo implementador.
- **Repositorios en `domain` (estilo DDD clásico):** válido, pero mezclarlo con puertos en `application` crearía dos convenciones.
- **Código en español:** no encaja con el vocabulario técnico de los frameworks y produce nombres mixtos.
