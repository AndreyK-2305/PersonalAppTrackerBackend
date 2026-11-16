-- Corrige la restricción del plan de V11 (auditoría del 2026-11-16, H-05). En SQL, NULL > 0 no es
-- falso sino desconocido, y un CHECK desconocido se da por cumplido: la versión anterior aceptaba un
-- plan MONTHLY_AMOUNT sin aporte mensual. Ahora se exige de forma explícita que no sea nulo.
ALTER TABLE goals.savings_goals DROP CONSTRAINT savings_goals_plan;

ALTER TABLE goals.savings_goals ADD CONSTRAINT savings_goals_plan CHECK (
    (plan_kind = 'TARGET_DATE' AND target_date IS NOT NULL AND monthly_amount IS NULL)
    OR (plan_kind = 'MONTHLY_AMOUNT' AND monthly_amount IS NOT NULL AND monthly_amount > 0
        AND target_date IS NULL));
