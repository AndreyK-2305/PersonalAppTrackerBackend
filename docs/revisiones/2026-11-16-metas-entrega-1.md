# Auditoría de la entrega 1 de Metas (metas de ahorro)

- Fecha: 2026-11-16
- Alcance: módulo `goals`, calculadora `shared.domain.finance`, migración V11 y el feature `goals` del frontend (ADR-0018).
- Pedido del usuario: probar a fondo (simulaciones, casos de uso, integración) y revisar la arquitectura hexagonal y SOLID. Los errores se documentan con su corrección propuesta y **no se reparan**, salvo los casi insignificantes.
- **Estado: los 12 hallazgos se corrigieron el 2026-11-16**, después de que el usuario aprobó aplicarlos todos. Cada uno conserva abajo su análisis original y agrega lo que se hizo. Ver [Correcciones aplicadas](#correcciones-aplicadas).

## Resultado

| | Antes de la auditoría | Al cerrar la auditoría | Con las correcciones |
|---|---|---|---|
| Pruebas del backend (`./mvnw verify`) | 428 | 535: 526 pasan y 9 deshabilitadas | 543: pasan todas |
| Pruebas del frontend (`npm test`) | 111 | 118: 117 pasan y 1 deshabilitada | 118: pasan todas |

- **Ningún hallazgo afectaba los datos que ya había ni el uso normal.** Los defectos aparecían con valores extremos, con ediciones simultáneas o eran de diseño.
- **12 hallazgos:** 5 de severidad media y 7 baja. Cada uno tenía una prueba deshabilitada que lo reproducía; al corregirlo se habilitó, y hoy no queda ninguna deshabilitada.
- **Una corrección se aplicó durante la auditoría** por ser trivial: el Javadoc de `SavingsGoalRepository.saveRateChange` (ver C-01).

## Qué se probó

| Capa | Pruebas nuevas | Qué comprueban |
|---|---|---|
| Calculadora | `FinancialMathTests` (+4) | Raíces de fracciones, índice 1, periodos negativos y números grandes |
| Dominio | `SavingsGoalTests` (13) | Invariantes del agregado: nombre, objetivo, moneda del plan, edición atómica, abonos |
| Dominio, simulación | `SavingsProjectionSimulationTests` (22) | Simulación mes a mes en 11 escenarios: el aporte recomendado alcanza el objetivo y un peso menos no; los dos planes coinciden; valores calculados aparte con 60 dígitos; fin de mes; redondeo del avance |
| Casos de uso | `ManageSavingsGoalsTests`, `RecordSavingsContributionsTests`, `AdjustSavingsRateTests` (22) | Con dobles en memoria (`GoalsFakes`) y reloj controlado: "hoy" según la zona horaria (Bogotá y Tokio), aislamiento entre usuarios, meta vencida, reemplazo de la tasa pendiente, borrado con sus abonos |
| Adaptador de persistencia | `SavingsGoalPersistenceAdapterTests` (15) | Contra PostgreSQL real: ida y vuelta de los dos planes, filtro por propietario en cada consulta, orden, cascada, restricciones de V11 y edición concurrente |
| API | `SavingsGoalApiEdgeCaseTests` (12) | CSRF, peticiones mal formadas, monto máximo, desborde con reversión de la transacción, abono de otra meta, precisión de la tasa, fechas imposibles |
| Arquitectura | `GoalsModuleArchitectureTests` (19 reglas) | Dependencias por capa, un adaptador por puerto, adaptadores no públicos, casos de uso transaccionales, dominio sin reloj, sin ciclos |
| Frontend | `GoalsAudit.test.tsx` (7) | Estados "Vencida" y "No alcanza", errores del servidor y de validación, edición, meta inexistente, borrado |

## Lo que está bien

- **Dominio puro.** `goals.domain` y `shared.domain.finance` solo dependen de Java y del núcleo compartido. No leen el reloj: la fecha de hoy entra como parámetro.
- **Capa de aplicación limpia.** Solo conoce el dominio, sus puertos y las anotaciones permitidas de Spring. Todos los métodos públicos de los casos de uso declaran su transacción.
- **Puertos y adaptadores.** Los dos puertos (`SavingsGoalRepository`, `GoalOwners`) son interfaces y cada uno tiene exactamente un adaptador. Ningún adaptador es público. El adaptador web usa casos de uso, no puertos; los de persistencia e integración no llaman a casos de uso ni a la web; los tres tipos de adaptador no se conocen entre sí.
- **Límites del módulo.** `goals` llega a `identity` solo por su `api` y solo desde el adaptador de integración. No depende de `finance` ni de `capture`. No hay ciclos entre paquetes.
- **Propiedad de los recursos.** Toda consulta del adaptador filtra por propietario; un recurso ajeno responde 404 en los seis endpoints que reciben un identificador y no aparece en el listado; un abono de otra meta del mismo usuario también responde 404.
- **Cálculo.** La fórmula cerrada coincide con la simulación mes a mes y con valores calculados aparte. El redondeo hacia arriba garantiza que el aporte recomendado alcanza y que un peso menos no.
- **Transacciones.** Un abono que desborda la suma se rechaza con 422 y la transacción se revierte: la meta sigue consultable.
- **Inyección.** Todas las dependencias entran por constructor en campos `final`; no hay `@Autowired` en campos.

### SOLID

| Principio | Evaluación |
|---|---|
| Responsabilidad única | Bien en dominio, casos de uso y adaptadores. Excepciones: `SavingsGoalViews` (H-06) y `SavingsProjection` (O-2) |
| Abierto/cerrado | `SavingsPlan` es una interfaz sellada: agregar un plan obliga al compilador a revisar cada `switch`. Dos sitios usan `instanceof` o cadenas y no quedan cubiertos (H-07, O-4) |
| Sustitución de Liskov | Los dobles de prueba cumplen el mismo contrato que el adaptador JPA. El único desajuste era un Javadoc (C-01) |
| Segregación de interfaces | `SavingsGoalRepository` agrupa 11 operaciones de tres conceptos (O-1) |
| Inversión de dependencias | Los casos de uso dependen de puertos; la infraestructura los implementa. Sin dependencias hacia afuera desde el dominio |

## Corrección aplicada durante la auditoría

### C-01. Javadoc del puerto que no coincidía con el adaptador

- **Dónde:** `SavingsGoalRepository.saveRateChange`.
- **Qué pasaba:** decía "reemplazando el que ya hubiera para la misma fecha de inicio". El adaptador no reemplaza: inserta, y la restricción única rechaza un duplicado. Funcionaba porque `AdjustSavingsRate` borra antes el cambio pendiente.
- **Corrección:** se reescribió el comentario para describir el contrato real. Lo comprueban `theDatabaseAllowsOnlyOneRateChangePerGoalAndDay` y el doble `GoalsFakes.Goals`, que ahora rechaza duplicados igual que la base.

## Hallazgos

| # | Severidad | Hallazgo | Capa | Estado |
|---|---|---|---|---|
| H-01 | Media | Una fecha que PostgreSQL no puede guardar responde 500 | Aplicación | Corregido |
| H-02 | Baja | La fecha objetivo no tiene límite superior y descuadra la proyección | Dominio | Corregido |
| H-03 | Media | `FinancialMath.root` devuelve un valor equivocado con números grandes | Calculadora | Corregido |
| H-04 | Media | El redondeo hacia arriba desborda el monto máximo y la meta deja de consultarse | Dominio | Corregido |
| H-05 | Media | El `CHECK` del plan acepta un plan mensual sin aporte | Base de datos | Corregido |
| H-06 | Baja | `SavingsGoalViews` no es un caso de uso y vive en el paquete de casos de uso | Aplicación | Corregido |
| H-07 | Baja | `SavingsGoalResponse` depende del controlador por el enum `PlanKind` | Web | Corregido |
| H-08 | Baja | Borrar cambios de tasa solo funciona dentro de la transacción de quien llama | Persistencia | Corregido |
| H-09 | Media | Una edición concurrente responde 500 en lugar de 409 (todos los módulos) | Web compartida | Corregido |
| H-10 | Baja | Un solo código de error para todas las reglas de la meta y del abono | Dominio y frontend | Corregido |
| H-11 | Baja | Al eliminar una meta aparece un instante la pantalla de error | Frontend | Corregido |
| H-12 | Baja | La documentación de API y arquitectura no menciona Metas | Documentación | Corregido |

Las secciones siguientes conservan el análisis tal como se escribió antes de corregir: describen el defecto y la corrección que se propuso. Lo que se hizo de verdad está en [Correcciones aplicadas](#correcciones-aplicadas).

### H-01. Una fecha que PostgreSQL no puede guardar responde 500

- **Evidencia:** `SavingsGoalApiEdgeCaseTests.datesTheDatabaseCannotStoreAreRejectedAsInvalid`. `targetDate: "+5874898-01-01"` o un abono con `date: "-4800-01-01"` pasan todas las validaciones y la base responde `date out of range`. El usuario recibe un 500 genérico.
- **Causa:** `LocalDate` admite años hasta ±999.999.999 y PostgreSQL solo desde 4713 a. C. hasta 5874897. Ni el controlador ni los casos de uso limitan el rango.
- **Impacto:** no hay pérdida de datos; es un error del servidor provocado por un dato del cliente, que debería ser 422.
- **Corrección propuesta** (junto con H-02):
  - En `ManageSavingsGoals`, reemplazar `requireFutureDate` por una regla con los dos límites:

    ```java
    private static void requireReachableDate(SavingsPlan plan, LocalDate today) {
        if (plan instanceof SavingsPlan.ByTargetDate(LocalDate targetDate)
                && (!targetDate.isAfter(today) || targetDate.isAfter(today.plusMonths(SavingsProjection.MAX_MONTHS)))) {
            throw new InvalidSavingsGoalException("La fecha objetivo debe estar dentro de los próximos 100 años.");
        }
    }
    ```

  - En `RecordSavingsContributions.add`, rechazar también una fecha anterior a `today.minusYears(100)`.
- **Decisión del usuario:** confirmar los dos límites (100 años hacia adelante para la meta y 100 hacia atrás para un abono).
- **Al corregir:** habilitar la prueba citada y `aTargetDateMoreThanAHundredYearsAwayIsRejected`.

### H-02. La fecha objetivo no tiene límite superior y descuadra la proyección

- **Evidencia:** `SavingsProjectionSimulationTests.aTargetDateBeyondAHundredYearsKeepsMonthsAndContributionConsistent` y `SavingsGoalApiEdgeCaseTests.aTargetDateMoreThanAHundredYearsAwayIsRejected`.
- **Causa:** `SavingsProjection` calcula el aporte con un máximo de 1.200 periodos, pero informa `monthsRemaining` sin tope. Con una meta a 200 años responde 2.400 meses y un aporte calculado para 1.200: el doble de lo necesario. Con años extremos, además, el `(int)` de los meses se desborda.
- **Corrección propuesta:** la regla de H-01 impide crear esas metas, y entonces el tope de `SavingsProjection` deja de alcanzarse. Conviene dejarlo como defensa y agregar la misma cota a `monthsRemaining` (`Math.min(months, MAX_MONTHS)`).

### H-03. `FinancialMath.root` devuelve un valor equivocado con números grandes

- **Evidencia:** `FinancialMathTests.rootsOfLargeNumbersConvergeOrFail`: la raíz duodécima de 10^30 es 316,2277… y la función devuelve otro valor, sin error.
- **Causa:** el método de Newton arranca en `1 + (x − 1)/n`, que para un número grande queda lejísimos de la raíz, y cada iteración solo lo reduce a 11/12. A las 200 iteraciones el bucle termina y devuelve la aproximación sin converger.
- **Impacto hoy:** ninguno. `AnnualRate` solo pide raíces de números entre 1 y 11, que convergen en pocas iteraciones (probado hasta 1000 % EA). Es un riesgo latente: la función es pública en `shared` y la Etapa 6 la va a reutilizar.
- **Corrección propuesta:**
  - Arrancar en una potencia de 10 que ya esté cerca y por encima de la raíz.
  - Terminar cuando la sucesión deja de bajar, que no depende de la magnitud.
  - Fallar si no converge, en lugar de devolver un valor.

    ```java
    BigDecimal guess = x.compareTo(BigDecimal.ONE) > 0
            ? BigDecimal.TEN.pow((x.precision() - x.scale() + n - 1) / n)
            : BigDecimal.ONE;
    for (int i = 0; i < MAX_ITERATIONS; i++) {
        BigDecimal next = /* paso de Newton, igual que hoy */;
        if (next.compareTo(guess) >= 0) {
            return guess;
        }
        guess = next;
    }
    throw new ArithmeticException("La raíz no convergió");
    ```

- **Al corregir:** habilitar la prueba; las de `AnnualRateTests` deben seguir pasando sin cambios.

### H-04. El redondeo hacia arriba desborda el monto máximo y la meta deja de consultarse

- **Evidencia:** `SavingsProjectionSimulationTests.anOverdueGoalForTheLargestAmountStillProjects` y `SavingsGoalApiEdgeCaseTests.theLargestAmountWithOneMonthLeftCanStillBeCreated`.
- **Causa:** con un mes o menos, el aporte recomendado es todo lo que falta redondeado hacia arriba al peso. Si lo que falta es 99.999.999.999.999.999,99, el redondeo da un número de 18 dígitos y `Money` lo rechaza con `InvalidMoneyAmountException`.
- **Impacto:** solo con objetivos del orden de 10^17 pesos. Pero el fallo ocurre al **calcular la vista**: cuando a esa meta le quede un mes, `GET /api/v1/goals/savings` responde 422 para **todas** las metas del usuario, y la pantalla no puede listar ninguna.
- **Corrección propuesta:** el aporte recomendado nunca necesita superar lo que falta. En `SavingsProjection.recommendedContribution`:

  ```java
  return FinancialMath.ceilToUnit(value).min(remaining.amount());
  ```

  aplicado a las dos ramas (con y sin rentabilidad).
- **Efecto secundario:** cuando falte menos de un peso, el aporte recomendado será ese valor exacto y no 1 peso. Hay que ajustar `progressIsRoundedDownSoAnUnfinishedGoalNeverShowsAHundredPercent`, que hoy espera `1`.

### H-05. El `CHECK` del plan acepta un plan mensual sin aporte

- **Evidencia:** `SavingsGoalPersistenceAdapterTests.theSchemaRejectsAMonthlyPlanWithoutItsAmount`: la base acepta una fila con `plan_kind = 'MONTHLY_AMOUNT'` y `monthly_amount` nulo.
- **Causa:** en SQL, `NULL > 0` no es falso sino desconocido, y un `CHECK` desconocido se da por cumplido. La restricción `savings_goals_plan` de V11 escribe `monthly_amount > 0` sin exigir antes que no sea nulo.
- **Impacto:** la app nunca escribe esa fila, porque el dominio lo impide. Si llegara por otra vía (un script, una restauración), el adaptador fallaría con `NullPointerException` al leerla y el listado del usuario respondería 500.
- **Corrección propuesta:** una migración nueva; V11 ya está aplicada en la base real y no se edita.

  ```sql
  -- V12__goals_fix_savings_goals_plan_check.sql
  ALTER TABLE goals.savings_goals DROP CONSTRAINT savings_goals_plan;
  ALTER TABLE goals.savings_goals ADD CONSTRAINT savings_goals_plan CHECK (
      (plan_kind = 'TARGET_DATE' AND target_date IS NOT NULL AND monthly_amount IS NULL)
      OR (plan_kind = 'MONTHLY_AMOUNT' AND monthly_amount IS NOT NULL AND monthly_amount > 0
          AND target_date IS NULL));
  ```

- **Nota:** el resto de restricciones de V11 sí rechazan lo que deben (plan por fecha sin fecha, aporte en cero, objetivo en cero, plan desconocido, usuario inexistente, abono en cero, tasa fuera de rango); lo comprueba `theSchemaRejectsRowsThatBreakTheRules`.

### H-06. `SavingsGoalViews` no es un caso de uso y vive en el paquete de casos de uso

- **Evidencia:** regla `theUseCasePackageOnlyHoldsUseCases` de `GoalsModuleArchitectureTests`.
- **Qué pasa:** es un `@Component` público dentro de `application/usecase` con tres responsabilidades distintas: saber qué día es hoy para el usuario (`today`), buscar la meta o responder 404 (`require`) y armar la vista con la proyección (`view`). Es la "clase suelta" del módulo.
- **Corrección propuesta:**
  - Separar la fecha en un colaborador propio, por ejemplo `GoalCalendar.today(UserId)`, que es lo único que usa `GoalOwners` y `Clock`.
  - Dejar `require` y `view` en una clase con un nombre que diga lo que hace (`SavingsGoalReader`), **no pública**, porque solo la usan los tres casos de uso del mismo paquete.
  - Ajustar la regla para que exija `@Service` solo a las clases públicas del paquete.

### H-07. `SavingsGoalResponse` depende del controlador por el enum `PlanKind`

- **Evidencia:** regla `webDtosDoNotDependOnTheirController`.
- **Qué pasa:** `PlanKind` está anidado en `SavingsGoalController` y la respuesta lo usa, así que controlador y respuesta dependen uno del otro. Además, el adaptador de persistencia repite los mismos dos valores como cadenas (`"TARGET_DATE"`, `"MONTHLY_AMOUNT"`): el mismo dato está definido en dos adaptadores.
- **Corrección propuesta:** un enum de dominio `SavingsPlanKind` y un método `SavingsPlan.kind()`. Lo usan la web (petición y respuesta) y la persistencia (`plan_kind`), y desaparecen los `instanceof` de `SavingsGoalResponse` y la comparación de cadenas de `SavingsGoalPersistenceAdapter.toDomain`.

### H-08. Borrar cambios de tasa solo funciona dentro de la transacción de quien llama

- **Evidencia:** `SavingsGoalPersistenceAdapterTests.deletingRateChangesNeedsTheCallersTransaction` (prueba de caracterización: pasa porque hoy **falla** fuera de una transacción).
- **Causa:** `SavingsRateChangeJpaRepository.deleteAfter` es una consulta `@Modifying` sin `@Transactional`. Spring Data la ejecuta en modo solo lectura salvo que quien llama ya tenga una transacción de escritura.
- **Impacto:** ninguno hoy, porque `AdjustSavingsRate.adjust` es transaccional. El contrato del puerto no lo dice, y un caso de uso futuro podría tropezar.
- **Corrección propuesta:** anotar el método del repositorio con `@Transactional`, como recomienda Spring Data para las consultas que modifican. Al hacerlo, la prueba de caracterización debe invertirse.

### H-09. Una edición concurrente responde 500 en lugar de 409

- **Evidencia:** `SavingsGoalPersistenceAdapterTests.aConcurrentEditIsDetectedInsteadOfOverwritingTheFirstOne` comprueba que el bloqueo optimista **sí** evita que una edición pise a la otra. Lo que falta es traducir esa excepción.
- **Causa:** `GlobalExceptionHandler` no tiene un manejador para `OptimisticLockingFailureException`, que cae en el manejador genérico y responde 500.
- **Alcance:** es anterior a Metas y afecta a todos los módulos con `@Version` (`finance`, `capture`, `identity`). En el uso real exige dos peticiones simultáneas sobre el mismo registro, por ejemplo dos pestañas.
- **Corrección propuesta:** en `GlobalExceptionHandler`:

  ```java
  @ExceptionHandler(OptimisticLockingFailureException.class)
  ResponseEntity<ProblemDetail> handleConcurrentEdit(OptimisticLockingFailureException ex) {
      return ApiProblems.response(HttpStatus.CONFLICT, "conflict",
              "Otra sesión cambió este registro. Actualiza la página e intenta de nuevo.");
  }
  ```

  con su caso en `GlobalExceptionHandlerTests` y la mención en `docs/api/conventions.md`.

### H-10. Un solo código de error para todas las reglas de la meta y del abono

- **Qué pasa:** `InvalidSavingsGoalException` responde siempre `goals.savings_goal.invalid`: nombre, objetivo, fecha, aporte mensual, y también el valor y la fecha de un **abono**. Las convenciones de la API piden códigos estables por regla, y `finance` los tiene por campo.
- **Efecto en el frontend:** no puede asignar el error a un campo. "La fecha objetivo debe ser posterior a hoy" aparece como error general del formulario (lo comprueba `GoalsAudit.test.tsx`), no bajo la fecha.
- **Corrección propuesta:**
  - Códigos por regla: `goals.savings_goal.name_invalid`, `.target_invalid`, `.target_date_invalid`, `.monthly_amount_invalid`, `goals.contribution.amount_invalid` y `goals.contribution.date_invalid`.
  - En el frontend, pasarlos como `fieldByCode` a `setServerErrors` en `SavingsGoalFormDialog` y `ContributionDialog`.

### H-11. Al eliminar una meta aparece un instante la pantalla de error

- **Evidencia:** `GoalsAudit.test.tsx`, prueba "al eliminar la meta no muestra el error de carga antes de volver al listado".
- **Causa:** `useDeleteSavingsGoal` invalida todas las consultas de metas, incluida la de la meta recién borrada, que sigue montada. Esa recarga responde 404 y la página pinta "No fue posible cargar la meta" justo antes de navegar al listado.
- **Corrección propuesta:** que el borrado invalide solo el listado:

  ```ts
  export function useDeleteSavingsGoal() {
    const queryClient = useQueryClient()
    return useMutation({
      mutationFn: deleteSavingsGoal,
      onSuccess: () =>
        queryClient.invalidateQueries({ queryKey: goalKeys.savings(), exact: true }),
    })
  }
  ```

  y, tras navegar, `queryClient.removeQueries({ queryKey: goalKeys.savingsGoal(id) })` en la página.

### H-12. La documentación de API y arquitectura no menciona Metas

- **Qué falta:**
  - `docs/api/conventions.md`: la sección de Metas con sus siete endpoints y sus códigos (`goals.savings_goal.not_found`, `goals.contribution.not_found`, `goals.savings_goal.invalid`, `rate.invalid`).
  - `docs/architecture/modules.md`: el módulo `goals` y la calculadora de `shared`.
  - `docs/architecture/domain-glossary.md`: meta de ahorro, abono, plan, rentabilidad y aporte recomendado.
  - Los README de `backend` y `frontend`.
- **Corrección propuesta:** completarlos junto con las correcciones de H-01 y H-10, que cambian reglas y códigos, para no documentar dos veces.

## Observaciones de diseño

No son defectos. Son puntos para decidir antes de la entrega 2, que va a crecer sobre esta base.

- **O-1. Puerto con tres conceptos.** `SavingsGoalRepository` reúne metas, abonos y cambios de tasa (11 operaciones). `AdjustSavingsRate` solo usa tres. Se puede dividir en tres puertos con el mismo adaptador, o dejarlo como repositorio del agregado. Recomendación: dejarlo y decidir al modelar las metas financiadas, que traerán pagos y ajustes de tasa propios.
- **O-2. `SavingsProjection` con campos de doble sentido.** `monthsRemaining` son los meses hasta la fecha en un plan y los meses estimados en el otro; `recommendedMonthly` es el aporte calculado en un plan y el aporte elegido en el otro. Un resultado sellado por plan lo haría explícito y quitaría los `null` que hoy decide `SavingsGoalResponse`.
- **O-3. "Desde el día siguiente" no cambia ningún cálculo.** La proyección usa siempre la última tasa configurada, rija o no todavía. La fecha de inicio es solo historial. Es coherente con el ADR-0018 (la tasa solo afecta la proyección), pero conviene que el usuario lo sepa: la pantalla muestra la tasa nueva desde hoy.
- **O-4. Utilidades web repetidas entre módulos.** El patrón de montos, su mensaje y la validación de campos por tipo existen en `finance.infrastructure.web` (`Amounts`, `TypedFields`) y otra vez en `SavingsGoalController`. La moneda COP también está definida dos veces. Candidatas a `shared.infrastructure.web`.
- **O-5. Consultas de más.** El listado hace dos consultas por meta (abonos y tasas), y cada abono nuevo hace un `SELECT` antes del `INSERT`, porque la entidad tiene identificador asignado y no tiene versión. Irrelevante con pocas metas.
- **O-6. `user_id` sin llave foránea en las tablas hijas.** `savings_contributions` y `savings_rate_changes` guardan `user_id` para filtrar por propietario, pero nada obliga a que coincida con el de la meta. El caso de uso lo garantiza.

## Correcciones aplicadas

Aplicadas el 2026-11-16 en el orden recomendado. El usuario aprobó todas, incluidos los límites de fechas de H-01 y H-02.

| # | Qué se hizo | Prueba que lo comprueba |
|---|---|---|
| H-05 | Migración `V12__goals_fix_savings_goals_plan_check.sql`: la restricción exige `monthly_amount IS NOT NULL` | `theSchemaRejectsAMonthlyPlanWithoutItsAmount` |
| H-04 | `SavingsProjection` limita el aporte recomendado a lo que falta, con y sin rentabilidad | `anOverdueGoalForTheLargestAmountStillProjects`, `theLargestAmountWithOneMonthLeftCanStillBeCreated` |
| H-01 | `ManageSavingsGoals.requireReachableDate` (fecha objetivo desde mañana hasta 100 años) y `RecordSavingsContributions` (abono con máximo 100 años de antigüedad) | `datesTheDatabaseCannotStoreAreRejectedAsInvalid`, `theTargetDateCannotBeMoreThanAHundredYearsAway`, `theDateCannotBeMoreThanAHundredYearsOld` |
| H-02 | La regla de H-01 y el mismo tope de 1.200 meses en `monthsRemaining` | `aTargetDateMoreThanAHundredYearsAwayIsRejected`, `aTargetDateBeyondAHundredYearsKeepsMonthsAndContributionConsistent` |
| H-09 | `GlobalExceptionHandler.handleConcurrentEdit`: `OptimisticLockingFailureException` responde 409 `conflict` | `GlobalExceptionHandlerTests.concurrentEditsAreConflicts` |
| H-03 | `FinancialMath.root` arranca por encima de la raíz (el menor entre `1 + (x − 1)/n` y la potencia de 10 de su magnitud), termina cuando deja de bajar y lanza `ArithmeticException` si no converge | `rootsOfLargeNumbersConverge`, `rootsConvergeForManyMagnitudesAndIndexes` (9 magnitudes × 5 índices) |
| H-07 | Enum de dominio `SavingsPlanKind` y `SavingsPlan.kind()`; lo usan la web y la persistencia, sin `instanceof` ni cadenas repetidas | Regla `webDtosDoNotDependOnTheirController`, `eachPlanKnowsItsKind` |
| H-06 | `SavingsGoalViews` se dividió en `GoalCalendar` (qué día es hoy) y `SavingsGoalReader` (meta con su proyección), los dos no públicos | Reglas `theOnlyPublicClassesOfTheUseCasePackageAreUseCases` y `useCaseCollaboratorsStayInsideTheirPackage` |
| H-10 | Un código por regla en `InvalidSavingsGoalException` y en la nueva `InvalidContributionException`; el frontend los asigna a su campo | `everyRuleHasItsOwnStableCode`, `everyBusinessRuleAnswersWithItsOwnCode`, `GoalsAudit.test.tsx` |
| H-11 | `useDeleteSavingsGoal` invalida solo el listado; la consulta de la meta borrada se descarta al salir de su página | `GoalsAudit.test.tsx`, "al eliminar la meta no muestra el error de carga…" |
| H-08 | `@Transactional` en `SavingsRateChangeJpaRepository.deleteAfter` | `deletingRateChangesWorksWithoutACallersTransaction` |
| H-12 | Sección Metas en `docs/api/conventions.md`, módulo `goals` y calculadora en `docs/architecture/modules.md`, glosario, los dos README y los límites en el ADR-0018 | — |

### Diferencias frente a lo propuesto

- **H-03:** además de la potencia de 10, el punto de partida usa `1 + (x − 1)/n` cuando es menor. Sin eso, una raíz de índice alto de un número cercano a 1 (por ejemplo, una tasa diaria) habría necesitado cientos de iteraciones. El tope defensivo subió a 10.000.
- **H-04:** cambió un resultado visible, como se anticipó: si falta menos de un peso, el aporte recomendado es ese valor exacto y no 1 peso.
- **H-06:** los colaboradores quedaron en el mismo paquete `usecase`, no públicos, en vez de moverse a otro paquete. Los constructores de los casos de uso pasaron a ser de paquete, porque reciben esos colaboradores.
- **H-07:** el tipo de plan en la API conserva los mismos valores (`TARGET_DATE`, `MONTHLY_AMOUNT`), así que los tipos del frontend no cambian.
- **H-10:** el código `goals.savings_goal.invalid` dejó de existir. Solo lo usaba el frontend de esta misma entrega.
- **H-11:** se usó una referencia y la limpieza del efecto de la página para descartar la consulta, en lugar de llamar a `removeQueries` justo después de navegar, que dependía del orden de los renders.

### Observaciones de diseño

Las seis observaciones (O-1 a O-6) siguen abiertas: no eran defectos y se deciden al diseñar la entrega 2. H-07 resolvió la parte de O-4 que tocaba el tipo de plan.
