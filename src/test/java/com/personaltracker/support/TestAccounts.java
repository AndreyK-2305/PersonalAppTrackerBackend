package com.personaltracker.support;

import com.personaltracker.identity.application.port.out.PasswordHasher;
import com.personaltracker.identity.application.port.out.UserRepository;
import com.personaltracker.identity.domain.model.Email;
import com.personaltracker.identity.domain.model.User;
import com.personaltracker.shared.domain.UserId;
import java.time.Instant;
import java.util.UUID;

/**
 * Crea cuentas de prueba directamente en la base de datos, porque el registro está cerrado por
 * defecto. Cada cuenta usa un correo único para que las pruebas no interfieran entre sí.
 */
public final class TestAccounts {

    public static final String PASSWORD = "contraseña-de-prueba-1";

    private final UserRepository users;
    private final PasswordHasher passwordHasher;

    public TestAccounts(UserRepository users, PasswordHasher passwordHasher) {
        this.users = users;
        this.passwordHasher = passwordHasher;
    }

    public User create() {
        User user = User.register(UserId.newId(), new Email(uniqueEmail()), passwordHasher.hash(PASSWORD),
                "Usuario de prueba", Instant.now());
        users.save(user);
        return user;
    }

    public static String uniqueEmail() {
        return "usuario-" + UUID.randomUUID() + "@example.com";
    }
}
