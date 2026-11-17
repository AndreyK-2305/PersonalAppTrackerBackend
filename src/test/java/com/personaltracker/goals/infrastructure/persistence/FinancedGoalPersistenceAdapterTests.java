package com.personaltracker.goals.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.goals.application.port.out.FinancedGoalRepository;
import com.personaltracker.goals.domain.model.FinancedGoal;
import com.personaltracker.goals.domain.model.FinancedGoalId;
import com.personaltracker.goals.domain.model.FinancedRecord;
import com.personaltracker.goals.domain.model.FinancedRecord.InstallmentPayment;
import com.personaltracker.goals.domain.model.FinancedRecord.Prepayment;
import com.personaltracker.goals.domain.model.FinancedRecord.RateAdjustment;
import com.personaltracker.goals.domain.model.FinancedTerms;
import com.personaltracker.goals.domain.model.PrepaymentMode;
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
import java.util.List;
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

    private static FinancedRecord payment(String amount) {
        return new InstallmentPayment(UUID.randomUUID(), FIRST, cop(amount), PrepaymentMode.REDUCE_INSTALLMENT, NOW);
    }

    private static FinancedRecord prepayment(String amount) {
        return new Prepayment(UUID.randomUUID(), FIRST.plusDays(3), cop(amount), PrepaymentMode.REDUCE_TERM, NOW);
    }

    private static FinancedRecord adjustment(String actualInstallment) {
        return new RateAdjustment(UUID.randomUUID(), FIRST.plusDays(5),
                AnnualRate.ofPercent(new BigDecimal("20.1234")),
                actualInstallment == null ? null : cop(actualInstallment), NOW.plusSeconds(9));
    }

    @Test
    void theRecordsSurviveTheRoundTripInTheOrderTheyWereAdded() {
        FinancedGoal goal = saved(owner, "Carro", terms("0", null), NOW);
        FinancedRecord first = payment("2100000.50");
        FinancedRecord second = prepayment("5000000");
        FinancedRecord third = adjustment("2300000.25");
        FinancedRecord fourth = adjustment(null);

        repository.appendRecords(goal, List.of(first, second));
        repository.appendRecords(goal, List.of(third, fourth));

        assertThat(repository.records(goal.id(), owner)).containsExactly(first, second, third, fourth);
        assertThat(jdbc.queryForList("select sequence from goals.financed_goal_records where goal_id = ?"
                + " order by sequence", Integer.class, goal.id().value())).containsExactly(1, 2, 3, 4);
    }

    @Test
    void theRecordsBelongToTheirGoalAndOwner() {
        FinancedGoal goal = saved(owner, "Carro", terms("0", null), NOW);
        FinancedGoal other = saved(owner, "Casa", terms("0", null), NOW);
        FinancedRecord ofGoal = payment("2100000");
        FinancedRecord ofOther = payment("2200000");
        repository.appendRecords(goal, List.of(ofGoal));
        repository.appendRecords(other, List.of(ofOther));

        assertThat(repository.records(goal.id(), owner)).containsExactly(ofGoal);
        assertThat(repository.records(other.id(), owner)).containsExactly(ofOther);
        assertThat(repository.records(goal.id(), stranger)).isEmpty();

        // Un registro no se borra a través de otra meta.
        repository.deleteRecord(other, ofGoal.id());
        assertThat(repository.records(goal.id(), owner)).containsExactly(ofGoal);
    }

    @Test
    void deletingTheLastRecordLetsTheNextOneReuseItsPlace() {
        FinancedGoal goal = saved(owner, "Carro", terms("0", null), NOW);
        FinancedRecord first = payment("2100000");
        FinancedRecord second = prepayment("1000");
        repository.appendRecords(goal, List.of(first, second));

        repository.deleteRecord(goal, second.id());
        FinancedRecord replacement = adjustment(null);
        repository.appendRecords(goal, List.of(replacement));

        assertThat(repository.records(goal.id(), owner)).containsExactly(first, replacement);
    }

    @Test
    void deletingAGoalCascadesToItsRecords() {
        FinancedGoal goal = saved(owner, "Carro", terms("0", null), NOW);
        repository.appendRecords(goal, List.of(payment("2100000"), prepayment("1000")));

        repository.delete(goal);

        assertThat(jdbc.queryForObject("select count(*) from goals.financed_goal_records where goal_id = ?",
                Integer.class, goal.id().value())).isZero();
    }

    @Test
    void theSchemaRejectsRecordsThatBreakTheRules() {
        FinancedGoal goal = saved(owner, "Carro", terms("0", null), NOW);
        String insert = "insert into goals.financed_goal_records (id, goal_id, user_id, sequence, kind, record_date,"
                + " currency, amount, prepayment_mode, annual_rate, actual_installment, created_at)"
                + " values (?, ?, ?, ?, ?, ?, 'COP', ?, ?, ?, ?, now())";
        UUID goalId = goal.id().value();
        UUID user = owner.value();
        BigDecimal amount = new BigDecimal("1000");
        BigDecimal rate = new BigDecimal("0.2");
        Object[][] invalid = {
            // Un pago sin valor, en cero o sin modo (los nulos deben fallar, no darse por buenos).
            {2, "PAYMENT", null, "REDUCE_TERM", null, null},
            {2, "PAYMENT", BigDecimal.ZERO, "REDUCE_TERM", null, null},
            {2, "PAYMENT", amount, null, null, null},
            {2, "PAYMENT", amount, "REDUCE_TERM", rate, null},
            {2, "PREPAYMENT", amount, "OTHER", null, null},
            // Un ajuste sin tasa, con tasa fuera de rango, con valor o con cuota real en cero.
            {2, "RATE_ADJUSTMENT", null, null, null, null},
            {2, "RATE_ADJUSTMENT", null, null, new BigDecimal("10.000001"), null},
            {2, "RATE_ADJUSTMENT", amount, null, rate, null},
            {2, "RATE_ADJUSTMENT", null, null, rate, BigDecimal.ZERO},
            {2, "OTHER", amount, "REDUCE_TERM", null, null},
            {0, "PAYMENT", amount, "REDUCE_TERM", null, null},
            // El orden es único por meta.
            {1, "PAYMENT", amount, "REDUCE_TERM", null, null},
        };

        jdbc.update(insert, UUID.randomUUID(), goalId, user, 1, "PAYMENT", FIRST, amount, "REDUCE_TERM", null, null);
        for (Object[] row : invalid) {
            assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), goalId, user, row[0], row[1], FIRST,
                    row[2], row[3], row[4], row[5])).as("%s %s", row[1], row[0])
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), UUID.randomUUID(), user, 1, "PAYMENT", FIRST,
                amount, "REDUCE_TERM", null, null)).isInstanceOf(DataIntegrityViolationException.class);
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
