-- Nombres y apellidos legales del usuario (ADR-0017), opcionales y distintos del nombre visible.
-- La captura los usa para reconocer transferencias a uno mismo.
ALTER TABLE identity.users
    ADD COLUMN given_names varchar(100),
    ADD COLUMN surnames varchar(100);
