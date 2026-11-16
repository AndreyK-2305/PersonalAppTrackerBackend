package com.personaltracker.goals.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.goals.application.port.out.FinancedGoalRepository;
import com.personaltracker.goals.domain.model.FinancedGoal;
import com.personaltracker.goals.domain.model.FinancedGoalId;
import com.personaltracker.goals.domain.model.FinancedTerms;
import com.personaltracker.identity.application.port.out.PasswordHasher;
import com.personaltracker.identity.application.port.out.UserRepository;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import com.personaltracker.shared.domain.finance.AnnualRate;
import com.personaltracker.support.IntegrationTest;
import com.personaltracker.support.TestAccounts;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * El adaptador JPA cumple el contrato de {@link FinancedGoalRepository} contra PostgreSQL real: ida y
 * vuelta, filtro por propietario, orden, edición y las restricciones de la migración V13.
 */
@IntegrationTest
class FinancedGoalPersistenceAdapterTests {

    private static final Currency COP = Currency.getInstance("COP");
    private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");
    private static final LocalDate FIRST = LocalDate.of(2026, 2, 15);
    private static final String INSERT = "insert into goals.financed_goals (id, user_id, name, asset_value,"
            + " down_payment, currency, annual_rate, installments, first_due_date, actual_installment, created_at,"
            + " updated_at) values (?, ?, 'x', ?, ?, ?, ?, ?, ?, ?, now(), now())";

    @Autowired
    FinancedGoalRepository repository;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordHasher passwordHasher;

    @Autowired
    JdbcTemplate jdbc;

    private UserId owner;
    private UserId stranger;

    @BeforeEach
    void setUp() {
        TestAccounts accounts = new TestAccounts(users, passwordHasher);
        owner = accounts.create().id();
        stranger = accounts.create().id();
    }

    private static Money cop(String amount) {
        return new Money(new BigDecimal(amount), COP);
    }

    private static FinancedTerms terms(String down, String actual) {
        return new FinancedTerms(cop("70000000.50"), cop(down), AnnualRate.ofPercent(new BigDecimal("18.1234")), 48,
                FIRST, actual == null ? null : cop(actual));
    }

    private FinancedGoal saved(UserId user, String name, FinancedTerms terms, Instant createdAt) {
        FinancedGoal goal = FinancedGoal.create(FinancedGoalId.newId(), user, name, terms, createdAt);
        repository.save(goal);
        return goal;
    }

    @Test
    void aGoalWithAllItsDataSurvivesTheRoundTrip() {
        FinancedTerms terms = terms("10000000.25", "1800000.75");
        FinancedGoal goal = saved(owner, "Carro", terms, NOW);

        FinancedGoal loaded = repository.findByIdAndOwner(goal.id(), owner).orElseThrow();

        assertThat(loaded.id()).isEqualTo(goal.id());
        assertThat(loaded.owner()).isEqualTo(owner);
        assertThat(loaded.name()).isEqualTo("Carro");
        assertThat(loaded.terms()).isEqualTo(terms);
        assertThat(loaded.createdAt()).isEqualTo(NOW);
        assertThat(loaded.updatedAt()).isEqualTo(NOW);
        assertThat(loaded.terms().schedule()).isEqualTo(terms.schedule());
    }

    @Test
    void aGoalWithoutDownPaymentOrActualInstallmentSurvivesTheRoundTrip() {
        FinancedTerms terms = terms("0", null);
        FinancedGoal goal = saved(owner, "Casa", terms, NOW);

        FinancedGoal loaded = repository.findByIdAndOwner(goal.id(), owner).orElseThrow();

        assertThat(loaded.terms()).isEqualTo(terms);
        assertThat(loaded.terms().actualInstallment()).isNull();
        assertThat(loaded.terms().downPayment()).isEqualTo(cop("0"));
    }

    @Test
    void savingAgainUpdatesTheSameRow() {
        FinancedGoal goal = saved(owner, "Carro", terms("0", "2100000"), NOW);
        FinancedTerms newTerms = new FinancedTerms(cop("50000000"), cop("5000000"), AnnualRate.ZERO, 600,
                FIRST.minusYears(3), null);

        goal.update("Moto", newTerms, NOW.plusSeconds(60));
        repository.save(goal);

        FinancedGoal loaded = repository.findByIdAndOwner(goal.id(), owner).orElseThrow();
        assertThat(loaded.name()).isEqualTo("Moto");
        assertThat(loaded.terms()).isEqualTo(newTerms);
        assertThat(loaded.createdAt()).isEqualTo(NOW);
        assertThat(loaded.updatedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(jdbc.queryForObject("select actual_installment from goals.financed_goals where id = ?",
                BigDecimal.class, goal.id().value())).isNull();
        assertThat(repository.findAllByOwner(owner)).hasSize(1);
    }

    @Test
    void everyQueryFiltersByOwnerAndListsNewestFirst() {
        FinancedGoal first = saved(owner, "Primera", terms("0", null), NOW);
        saved(owner, "Tercera", terms("0", null), NOW.plusSeconds(120));
        saved(owner, "Segunda", terms("0", null), NOW.plusSeconds(60));
        saved(stranger, "Ajena", terms("0", null), NOW.plusSeconds(600));

        assertThat(repository.findAllByOwner(owner)).extracting(FinancedGoal::name)
                .containsExactly("Tercera", "Segunda", "Primera");
        assertThat(repository.findByIdAndOwner(first.id(), stranger)).isEmpty();
        assertThat(repository.findAllByOwner(stranger)).extracting(FinancedGoal::name).containsExactly("Ajena");
    }

    @Test
    void deletesOnlyThatGoal() {
        FinancedGoal goal = saved(owner, "Carro", terms("0", null), NOW);
        FinancedGoal other = saved(owner, "Casa", terms("0", null), NOW);

        repository.delete(goal);

        assertThat(repository.findByIdAndOwner(goal.id(), owner)).isEmpty();
        assertThat(repository.findByIdAndOwner(other.id(), owner)).isPresent();
    }

    private void assertRejected(Object asset, Object down, String currency, Object rate, Object installments,
            Object firstDueDate, Object actual, UUID user) {
        assertThatThrownBy(() -> jdbc.update(INSERT, UUID.randomUUID(), user, asset, down, currency, rate,
                installments, firstDueDate, actual)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theSchemaRejectsRowsThatBreakTheRules() {
        BigDecimal asset = new BigDecimal("1000");
        BigDecimal zero = BigDecimal.ZERO;
        BigDecimal rate = new BigDecimal("0.18");
        UUID user = owner.value();

        // Una fila válida entra: así las siguientes fallan por su restricción y no por otra cosa.
        jdbc.update(INSERT, UUID.randomUUID(), user, asset, zero, "COP", rate, 12, FIRST, null);

        assertRejected(zero, zero, "COP", rate, 12, FIRST, null, user); // valor del bien en cero
        assertRejected(asset, asset, "COP", rate, 12, FIRST, null, user); // cuota inicial igual al valor
        assertRejected(asset, new BigDecimal("-1"), "COP", rate, 12, FIRST, null, user); // cuota inicial negativa
        assertRejected(asset, zero, "cop", rate, 12, FIRST, null, user); // moneda mal escrita
        assertRejected(asset, zero, "COP", new BigDecimal("-0.01"), 12, FIRST, null, user); // tasa negativa
        assertRejected(asset, zero, "COP", new BigDecimal("10.000001"), 12, FIRST, null, user); // tasa > 1000 %
        assertRejected(asset, zero, "COP", rate, 0, FIRST, null, user); // sin cuotas
        assertRejected(asset, zero, "COP", rate, 601, FIRST, null, user); // más de 600 cuotas
        assertRejected(asset, zero, "COP", rate, 12, FIRST, zero, user); // cuota real en cero
        assertRejected(asset, zero, "COP", rate, 12, FIRST, null, UUID.randomUUID()); // usuario inexistente
        // Los campos obligatorios no admiten nulos (el caso que falló en V11 con el CHECK del plan).
        assertRejected(asset, null, "COP", rate, 12, FIRST, null, user);
        assertRejected(asset, zero, "COP", null, 12, FIRST, null, user);
        assertRejected(asset, zero, "COP", rate, null, FIRST, null, user);
        assertRejected(asset, zero, "COP", rate, 12, null, null, user);
    }
}
