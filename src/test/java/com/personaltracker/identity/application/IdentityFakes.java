package com.personaltracker.identity.application;

import com.personaltracker.identity.application.port.out.PasswordHasher;
import com.personaltracker.identity.application.port.out.SessionTerminator;
import com.personaltracker.identity.application.port.out.UserRepository;
import com.personaltracker.identity.domain.model.Email;
import com.personaltracker.identity.domain.model.User;
import com.personaltracker.shared.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Dobles en memoria de los puertos de identity para probar los casos de uso sin Spring. */
public final class IdentityFakes {

    public static final Instant NOW = Instant.parse("2026-11-06T15:00:00Z");
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private IdentityFakes() {}

    public static final class InMemoryUserRepository implements UserRepository {

        private final Map<UserId, User> users = new HashMap<>();

        @Override
        public Optional<User> findById(UserId id) {
            return Optional.ofNullable(users.get(id));
        }

        @Override
        public Optional<User> findByEmail(Email email) {
            return users.values().stream().filter(user -> user.email().equals(email)).findFirst();
        }

        @Override
        public boolean existsByEmail(Email email) {
            return findByEmail(email).isPresent();
        }

        @Override
        public void save(User user) {
            users.put(user.id(), user);
        }

        public int count() {
            return users.size();
        }
    }

    /** Hash reversible y trivial: suficiente para verificar el flujo, nunca para producción. */
    public static final class FakePasswordHasher implements PasswordHasher {

        private int matchesCalls;

        @Override
        public String hash(String rawPassword) {
            return "hashed:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String passwordHash) {
            matchesCalls++;
            return passwordHash.equals(hash(rawPassword));
        }

        public int matchesCalls() {
            return matchesCalls;
        }
    }

    public static final class RecordingSessionTerminator implements SessionTerminator {

        private UserId user;
        private String keptSessionId;
        private int calls;

        @Override
        public void terminateAllExcept(UserId user, String sessionIdToKeep) {
            this.user = user;
            this.keptSessionId = sessionIdToKeep;
            calls++;
        }

        public UserId user() {
            return user;
        }

        public String keptSessionId() {
            return keptSessionId;
        }

        public int calls() {
            return calls;
        }
    }
}
