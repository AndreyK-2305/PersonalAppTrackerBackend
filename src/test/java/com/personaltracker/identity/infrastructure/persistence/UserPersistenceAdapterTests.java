package com.personaltracker.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.personaltracker.identity.application.port.out.UserRepository;
import com.personaltracker.identity.domain.model.Email;
import com.personaltracker.identity.domain.model.User;
import com.personaltracker.shared.domain.UserId;
import com.personaltracker.support.IntegrationTest;
import com.personaltracker.support.TestAccounts;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class UserPersistenceAdapterTests {

    // PostgreSQL guarda microsegundos: se trunca para comparar valores idénticos.
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    UserRepository users;

    @Test
    void savesAndRestoresEveryField() {
        User user = User.register(UserId.newId(), new Email(TestAccounts.uniqueEmail()), "hash", "Kevin", NOW);
        user.changeTimeZone(ZoneId.of("Europe/Madrid"), NOW);
        for (int attempt = 0; attempt < User.MAX_FAILED_ATTEMPTS; attempt++) {
            user.recordFailedLogin(NOW);
        }

        users.save(user);
        User restored = users.findByEmail(user.email()).orElseThrow();

        assertThat(restored).usingRecursiveComparison().isEqualTo(user);
        assertThat(users.existsByEmail(user.email())).isTrue();
    }

    @Test
    void updatesAnExistingAccount() {
        User user = User.register(UserId.newId(), new Email(TestAccounts.uniqueEmail()), "hash", "Kevin", NOW);
        users.save(user);

        User loaded = users.findById(user.id()).orElseThrow();
        loaded.rename("Kevin Jaimes", NOW);
        users.save(loaded);

        assertThat(users.findById(user.id()).orElseThrow().displayName()).isEqualTo("Kevin Jaimes");
    }
}
