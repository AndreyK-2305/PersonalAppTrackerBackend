# ADR-0003: Aislamiento de datos por propietario

- Estado: Aceptada
- Fecha: 2026-11-06

## Contexto

Varias personas pueden registrarse y cada una debe ver solo su información. El aislamiento tiene que existir en el backend y en la persistencia, nunca solo en el frontend, y no debe confiar en identificadores de usuario enviados por el cliente.

## Decisión

1. **Origen del dueño:**
   - Los casos de uso reciben el propietario explícitamente (`UserId owner`) en su comando.
   - El adaptador web lo obtiene de la sesión con `@CurrentUser`, publicado por `identity.api` en la Etapa 2.
   - Nunca se toma del cuerpo, de la URL ni de cabeceras.
2. **Puertos de salida:** filtran siempre por propietario, por ejemplo `findByIdAndOwner(id, owner)`. No existen métodos `findById` sin dueño para recursos de usuario.
3. **Respuesta ante un recurso ajeno:** un recurso de otro usuario responde **404**, igual que uno inexistente, para no revelar su existencia.
4. **Llaves foráneas compuestas:** toda tabla de recursos de usuario tiene `user_id`, y las relaciones entre recursos del mismo usuario se declaran así. Por ejemplo:

   ```sql
   FOREIGN KEY (account_id, user_id) REFERENCES finance.accounts (id, user_id)
   ```

   De esta forma la base de datos impide asociar un movimiento con la cuenta de otro usuario, aun con un error en la aplicación.
5. **Pruebas:** cada endpoint protegido tiene una prueba de integración de acceso cruzado, en la que el usuario A intenta leer o modificar un recurso del usuario B y recibe 404.

## Consecuencias

- **A favor:** defensa en profundidad (aplicación más restricciones de la base de datos) y comportamiento verificable.
- **Costos:**
  - Cada tabla de recurso necesita una restricción `UNIQUE (id, user_id)` para poder ser referenciada.
  - Las firmas de los puertos son algo más largas.

## Alternativas consideradas

- **Filtros automáticos de Hibernate (`@Filter` / `@TenantId`):** ocultan la propiedad del recurso al dominio y acoplan la persistencia al contexto de seguridad.
- **Row-Level Security de PostgreSQL:** es la opción más fuerte, pero complica el pool de conexiones y la depuración. Se puede reconsiderar como endurecimiento futuro.
- **Comprobaciones solo en los controladores:** son fáciles de olvidar y no protegen la persistencia.
