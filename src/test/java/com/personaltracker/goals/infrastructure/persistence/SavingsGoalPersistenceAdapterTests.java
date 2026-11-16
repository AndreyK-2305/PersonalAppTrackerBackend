package com.personaltracker.goals.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.goals.application.port.out.SavingsGoalRepository;
import com.personaltracker.goals.domain.model.SavingsContribution;
import com.personaltracker.goals.domain.model.SavingsGoal;
import com.personaltracker.goals.domain.model.SavingsGoalId;
import com.personaltracker.goals.domain.model.SavingsPlan;
import com.personaltracker.goals.domain.model.SavingsRateChange;
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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * El adaptador JPA cumple el contrato del puerto {@link SavingsGoalRepository} contra PostgreSQL
 * real: ida y vuelta de los dos planes, filtro por propietario, orden, borrado en cascada y las
 * restricciones de la migración V11.
 */
@IntegrationTest
class SavingsGoalPersistenceAdapterTests {

    private static final Currency COP = Currency.getInstance("COP");
    private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");
    private static final LocalDate JAN_15 = LocalDate.of(2026, 1, 15);

    @Autowired
    SavingsGoalRepository repository;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordHasher passwordHasher;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

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

    private SavingsGoal savedGoal(UserId user, String name, SavingsPlan plan, Instant createdAt) {
        SavingsGoal goal = SavingsGoal.create(SavingsGoalId.newId(), user, name, cop("1200000.50"), plan, createdAt);
        repository.save(goal);
        return goal;
    }

    private SavingsGoal savedGoal(String name) {
        return savedGoal(owner, name, new SavingsPlan.ByTargetDate(LocalDate.of(2026, 12, 1)), NOW);
    }

    private SavingsContribution contribution(SavingsGoal goal, LocalDate date, String amount, Instant createdAt) {
        SavingsContribution contribution =
                new SavingsContribution(UUID.randomUUID(), goal.id(), goal.owner(), date, cop(amount), createdAt);
        repository.saveContribution(contribution);
        return contribution;
    }

    private SavingsRateChange rateChange(SavingsGoal goal, LocalDate from, String percent) {
        SavingsRateChange change = new SavingsRateChange(UUID.randomUUID(), goal.id(), goal.owner(), from,
                AnnualRate.ofPercent(new BigDecimal(percent)), NOW);
        repository.saveRateChange(change);
        return change;
    }

    private Integer count(String table, SavingsGoalId goalId) {
        return jdbc.queryForObject("select count(*) from goals." + table + " where goal_id = ?", Integer.class,
                goalId.value());
    }

    @Test
    void aGoalByDateSurvivesTheRoundTrip() {
        SavingsGoal goal = savedGoal("Consola");

        SavingsGoal loaded = repository.findByIdAndOwner(goal.id(), owner).orElseThrow();

        assertThat(loaded.id()).isEqualTo(goal.id());
        assertThat(loaded.owner()).isEqualTo(owner);
        assertThat(loaded.name()).isEqualTo("Consola");
        assertThat(loaded.target()).isEqualTo(cop("1200000.50"));
        assertThat(loaded.plan()).isEqualTo(new SavingsPlan.ByTargetDate(LocalDate.of(2026, 12, 1)));
        assertThat(loaded.createdAt()).isEqualTo(NOW);
        assertThat(loaded.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void aGoalByMonthlyAmountSurvivesTheRoundTrip() {
        SavingsPlan plan = new SavingsPlan.ByMonthlyAmount(cop("150000.25"));
        SavingsGoal goal = savedGoal(owner, "Viaje", plan, NOW);

        assertThat(repository.findByIdAndOwner(goal.id(), owner).orElseThrow().plan()).isEqualTo(plan);
    }

    @Test
    void savingAgainUpdatesTheSameRowAndSwitchesThePlanColumns() {
        SavingsGoal goal = savedGoal("Consola");
        SavingsPlan monthly = new SavingsPlan.ByMonthlyAmount(cop("90000"));

        goal.update("Consola nueva", cop("2000000"), monthly, NOW.plusSeconds(60));
        repository.save(goal);

        SavingsGoal loaded = repository.findByIdAndOwner(goal.id(), owner).orElseThrow();
        assertThat(loaded.name()).isEqualTo("Consola nueva");
        assertThat(loaded.plan()).isEqualTo(monthly);
        assertThat(loaded.createdAt()).isEqualTo(NOW);
        assertThat(loaded.updatedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(jdbc.queryForObject("select target_date from goals.savings_goals where id = ?", LocalDate.class,
                goal.id().value())).isNull();
        assertThat(repository.findAllByOwner(owner)).hasSize(1);
    }

    @Test
    void everyQueryFiltersByOwner() {
        SavingsGoal goal = savedGoal("Consola");
        SavingsContribution contribution = contribution(goal, JAN_15, "1000", NOW);
        rateChange(goal, JAN_15, "10");

        assertThat(repository.findByIdAndOwner(goal.id(), stranger)).isEmpty();
        assertThat(repository.findAllByOwner(stranger)).isEmpty();
        assertThat(repository.contributions(goal.id(), stranger)).isEmpty();
        assertThat(repository.findContribution(goal.id(), contribution.id(), stranger)).isEmpty();
        assertThat(repository.rateChanges(goal.id(), stranger)).isEmpty();
    }

    @Test
    void listsTheGoalsNewestFirst() {
        savedGoal(owner, "Primera", new SavingsPlan.ByTargetDate(JAN_15), NOW);
        savedGoal(owner, "Tercera", new SavingsPlan.ByTargetDate(JAN_15), NOW.plusSeconds(120));
        savedGoal(owner, "Segunda", new SavingsPlan.ByTargetDate(JAN_15), NOW.plusSeconds(60));
        savedGoal(stranger, "Ajena", new SavingsPlan.ByTargetDate(JAN_15), NOW.plusSeconds(600));

        assertThat(repository.findAllByOwner(owner)).extracting(SavingsGoal::name)
                .containsExactly("Tercera", "Segunda", "Primera");
    }

    @Test
    void listsTheContributionsByDateAndThenByCreationNewestFirst() {
        SavingsGoal goal = savedGoal("Consola");
        SavingsGoal other = savedGoal("Viaje");
        SavingsContribution old = contribution(goal, JAN_15.minusDays(5), "1", NOW.plusSeconds(300));
        SavingsContribution firstOfDay = contribution(goal, JAN_15, "2", NOW);
        SavingsContribution secondOfDay = contribution(goal, JAN_15, "3.75", NOW.plusSeconds(60));
        contribution(other, JAN_15, "999", NOW);

        assertThat(repository.contributions(goal.id(), owner)).containsExactly(secondOfDay, firstOfDay, old);
    }

    @Test
    void aContributionIsOnlyFoundThroughItsOwnGoal() {
        SavingsGoal goal = savedGoal("Consola");
        SavingsGoal other = savedGoal("Viaje");
        SavingsContribution contribution = contribution(goal, JAN_15, "1000", NOW);

        assertThat(repository.findContribution(goal.id(), contribution.id(), owner)).contains(contribution);
        assertThat(repository.findContribution(other.id(), contribution.id(), owner)).isEmpty();

        repository.deleteContribution(contribution);
        assertThat(repository.findContribution(goal.id(), contribution.id(), owner)).isEmpty();
    }

    @Test
    void listsTheRateChangesByStartDate() {
        SavingsGoal goal = savedGoal("Consola");
        SavingsRateChange later = rateChange(goal, JAN_15.plusDays(10), "12.3456");
        SavingsRateChange earlier = rateChange(goal, JAN_15, "0");

        assertThat(repository.rateChanges(goal.id(), owner)).containsExactly(earlier, later);
    }

    @Test
    void deletesOnlyTheRateChangesThatStartAfterTheDate() {
        SavingsGoal goal = savedGoal("Consola");
        SavingsGoal other = savedGoal("Viaje");
        SavingsRateChange inEffect = rateChange(goal, JAN_15, "8");
        rateChange(goal, JAN_15.plusDays(1), "9");
        SavingsRateChange ofOtherGoal = rateChange(other, JAN_15.plusDays(1), "9");

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> repository.deleteRateChangesAfter(goal.id(), owner, JAN_15));

        assertThat(repository.rateChanges(goal.id(), owner)).containsExactly(inEffect);
        assertThat(repository.rateChanges(other.id(), owner)).containsExactly(ofOtherGoal);
    }

    /** H-08: el borrado abre su propia transacción si quien llama no tiene una. */
    @Test
    void deletingRateChangesWorksWithoutACallersTransaction() {
        SavingsGoal goal = savedGoal("Consola");
        SavingsRateChange inEffect = rateChange(goal, JAN_15, "8");
        rateChange(goal, JAN_15.plusDays(1), "9");

        repository.deleteRateChangesAfter(goal.id(), owner, JAN_15);

        assertThat(repository.rateChanges(goal.id(), owner)).containsExactly(inEffect);
    }

    @Test
    void theDatabaseAllowsOnlyOneRateChangePerGoalAndDay() {
        SavingsGoal goal = savedGoal("Consola");
        rateChange(goal, JAN_15, "8");

        assertThatThrownBy(() -> rateChange(goal, JAN_15, "9")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingAGoalCascadesToItsContributionsAndRateChanges() {
        SavingsGoal goal = savedGoal("Consola");
        SavingsGoal other = savedGoal("Viaje");
        contribution(goal, JAN_15, "1000", NOW);
        rateChange(goal, JAN_15, "8");
        contribution(other, JAN_15, "2000", NOW);

        repository.delete(goal);

        assertThat(repository.findByIdAndOwner(goal.id(), owner)).isEmpty();
        assertThat(count("savings_contributions", goal.id())).isZero();
        assertThat(count("savings_rate_changes", goal.id())).isZero();
        assertThat(count("savings_contributions", other.id())).isEqualTo(1);
    }

    private static final String INSERT_GOAL = "insert into goals.savings_goals (id, user_id, name, target_amount,"
            + " currency, plan_kind, target_date, monthly_amount, created_at, updated_at) values (?, ?, 'x', ?, 'COP',"
            + " ?, ?, ?, now(), now())";

    /**
     * H-05: en SQL, {@code NULL > 0} no es falso sino desconocido, y un CHECK desconocido se da por
     * cumplido. V12 exige de forma explícita que el aporte de un plan mensual no sea nulo.
     */
    @Test
    void theSchemaRejectsAMonthlyPlanWithoutItsAmount() {
        assertThatThrownBy(() -> jdbc.update(INSERT_GOAL, UUID.randomUUID(), owner.value(), new BigDecimal("1"),
                "MONTHLY_AMOUNT", null, null)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theSchemaRejectsRowsThatBreakTheRules() {
        SavingsGoal goal = savedGoal("Consola");
        String insertGoal = INSERT_GOAL;

        // Plan por fecha con aporte mensual, plan por fecha sin fecha, aporte en cero, objetivo en cero y plan
        // desconocido.
        assertThatThrownBy(() -> jdbc.update(insertGoal, UUID.randomUUID(), owner.value(), new BigDecimal("1"),
                "TARGET_DATE", JAN_15, new BigDecimal("5"))).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insertGoal, UUID.randomUUID(), owner.value(), new BigDecimal("1"),
                "TARGET_DATE", null, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insertGoal, UUID.randomUUID(), owner.value(), new BigDecimal("1"),
                "MONTHLY_AMOUNT", null, BigDecimal.ZERO)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insertGoal, UUID.randomUUID(), owner.value(), BigDecimal.ZERO,
                "TARGET_DATE", JAN_15, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insertGoal, UUID.randomUUID(), owner.value(), new BigDecimal("1"),
                "OTHER", JAN_15, null)).isInstanceOf(DataIntegrityViolationException.class);
        // Meta de un usuario que no existe.
        assertThatThrownBy(() -> jdbc.update(insertGoal, UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1"),
                "TARGET_DATE", JAN_15, null)).isInstanceOf(DataIntegrityViolationException.class);
        // Abono en cero y tasa fuera de rango.
        assertThatThrownBy(() -> jdbc.update("insert into goals.savings_contributions (id, goal_id, user_id,"
                + " contribution_date, amount, currency, created_at) values (?, ?, ?, ?, 0, 'COP', now())",
                UUID.randomUUID(), goal.id().value(), owner.value(), JAN_15))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into goals.savings_rate_changes (id, goal_id, user_id,"
                + " effective_from, annual_rate, created_at) values (?, ?, ?, ?, 10.000001, now())",
                UUID.randomUUID(), goal.id().value(), owner.value(), JAN_15))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Dos ediciones simultáneas de la misma meta: la segunda en confirmar falla en lugar de pisar a la
     * primera. H-09: la API traduce esa excepción a 409 ({@code GlobalExceptionHandlerTests}).
     */
    @Test
    void aConcurrentEditIsDetectedInsteadOfOverwritingTheFirstOne() {
        SavingsGoal goal = savedGoal("Consola");
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        TransactionTemplate inner = new TransactionTemplate(transactionManager);
        inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> outer.executeWithoutResult(status -> {
            SavingsGoal mine = repository.findByIdAndOwner(goal.id(), owner).orElseThrow();
            inner.executeWithoutResult(other -> {
                SavingsGoal theirs = repository.findByIdAndOwner(goal.id(), owner).orElseThrow();
                theirs.update("Editada por otra pestaña", cop("5"), theirs.plan(), NOW.plusSeconds(10));
                repository.save(theirs);
            });
            mine.update("Mi edición", cop("7"), mine.plan(), NOW.plusSeconds(20));
            repository.save(mine);
        })).isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(repository.findByIdAndOwner(goal.id(), owner).orElseThrow().name())
                .isEqualTo("Editada por otra pestaña");
    }
}
