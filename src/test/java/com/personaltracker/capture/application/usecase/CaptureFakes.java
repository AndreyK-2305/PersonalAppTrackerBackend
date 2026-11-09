package com.personaltracker.capture.application.usecase;

import com.personaltracker.capture.application.port.out.CaptureOwners;
import com.personaltracker.capture.application.port.out.CapturedNotificationRepository;
import com.personaltracker.capture.application.port.out.ExchangeRates;
import com.personaltracker.capture.application.port.out.Ledger;
import com.personaltracker.capture.application.port.out.OwnKeys;
import com.personaltracker.capture.application.port.out.UserProducts;
import com.personaltracker.capture.domain.model.CaptureEntry;
import com.personaltracker.capture.domain.model.CaptureStatus;
import com.personaltracker.capture.domain.model.CapturedNotification;
import com.personaltracker.capture.domain.model.NotificationId;
import com.personaltracker.capture.domain.model.OwnKey;
import com.personaltracker.capture.domain.model.OwnedProduct;
import com.personaltracker.capture.domain.model.OwnerName;
import com.personaltracker.shared.domain.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Dobles en memoria de los puertos de {@code capture}. */
final class CaptureFakes {

    private CaptureFakes() {}

    static final class Notifications implements CapturedNotificationRepository {

        final Map<NotificationId, CapturedNotification> stored = new LinkedHashMap<>();

        @Override
        public Optional<CapturedNotification> findById(NotificationId id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public void save(CapturedNotification notification) {
            stored.put(notification.id(), notification);
        }

        @Override
        public Optional<CapturedNotification> findByIdAndOwner(NotificationId id, UserId owner) {
            return findById(id).filter(notification -> notification.owner().equals(owner));
        }

        @Override
        public Optional<CapturedNotification> findByTransaction(UserId owner, UUID transactionId) {
            return stored.values().stream()
                    .filter(notification -> notification.owner().equals(owner))
                    .filter(notification -> notification.transactionId().filter(transactionId::equals).isPresent())
                    .findFirst();
        }

        @Override
        public List<CapturedNotification> findInReview(UserId owner) {
            return stored.values().stream()
                    .filter(notification -> notification.owner().equals(owner))
                    .filter(notification -> notification.status() == CaptureStatus.REVIEW)
                    .toList();
        }

        @Override
        public Optional<CapturedNotification> lockPending(NotificationId id) {
            return findById(id).filter(CapturedNotification::isPending);
        }

        @Override
        public boolean existsByContent(UserId owner, String contentHash) {
            return stored.values().stream().anyMatch(notification -> notification.owner().equals(owner)
                    && notification.raw().contentHash().equals(contentHash));
        }

        @Override
        public List<NotificationId> findReadyToProcess(Instant receivedBefore, int limit) {
            return stored.values().stream()
                    .filter(CapturedNotification::isPending)
                    .filter(notification -> !notification.createdAt().isAfter(receivedBefore))
                    .sorted(java.util.Comparator
                            .comparing((CapturedNotification notification) ->
                                    notification.raw().app().equalsIgnoreCase("Wallet") ? 1 : 0)
                            .thenComparing(CapturedNotification::createdAt))
                    .limit(limit)
                    .map(CapturedNotification::id)
                    .toList();
        }

        @Override
        public int purgeRawText(Instant receivedBefore, Instant now) {
            throw new UnsupportedOperationException("Se prueba contra PostgreSQL");
        }

        @Override
        public List<CapturedNotification> findUnpairedWithMovement(UserId owner, Instant from, Instant to) {
            Set<NotificationId> paired = stored.values().stream()
                    .filter(other -> other.status() == CaptureStatus.PAIRED)
                    .flatMap(other -> other.pairedWith().stream())
                    .collect(Collectors.toSet());
            return stored.values().stream()
                    .filter(other -> other.owner().equals(owner))
                    .filter(CapturedNotification::hasMovement)
                    .filter(other -> !paired.contains(other.id()))
                    .filter(other -> other.footprint()
                            .filter(footprint -> !footprint.occurredAt().isBefore(from)
                                    && !footprint.occurredAt().isAfter(to))
                            .isPresent())
                    .toList();
        }
    }

    static final class Products implements UserProducts {

        final Map<UserId, List<OwnedProduct>> active = new HashMap<>();
        final Map<UserId, UUID> other = new HashMap<>();

        @Override
        public List<OwnedProduct> activeProducts(UserId owner) {
            return active.getOrDefault(owner, List.of());
        }

        @Override
        public UUID otherProduct(UserId owner) {
            return other.computeIfAbsent(owner, ignored -> UUID.randomUUID());
        }
    }

    static final class Owners implements CaptureOwners {

        final Map<UserId, OwnerName> names = new HashMap<>();

        @Override
        public ZoneId timeZone(UserId owner) {
            return ZoneId.of("America/Bogota");
        }

        @Override
        public OwnerName name(UserId owner) {
            return names.getOrDefault(owner, OwnerName.of("", ""));
        }
    }

    static final class Keys implements OwnKeys {

        final Map<String, UUID> byKey = new HashMap<>();
        UserId owner;

        @Override
        public Optional<UUID> productFor(UserId user, String key) {
            if (!user.equals(owner)) {
                return Optional.empty();
            }
            return Optional.ofNullable(byKey.get(key.trim().toLowerCase(Locale.ROOT)));
        }

        @Override
        public List<OwnKey> list(UserId user) {
            throw new UnsupportedOperationException("Se prueba contra PostgreSQL");
        }

        @Override
        public Optional<OwnKey> findByIdAndOwner(UUID id, UserId user) {
            throw new UnsupportedOperationException("Se prueba contra PostgreSQL");
        }

        @Override
        public boolean exists(UserId user, String normalizedKey) {
            throw new UnsupportedOperationException("Se prueba contra PostgreSQL");
        }

        @Override
        public void save(OwnKey key) {
            throw new UnsupportedOperationException("Se prueba contra PostgreSQL");
        }

        @Override
        public void delete(OwnKey key) {
            throw new UnsupportedOperationException("Se prueba contra PostgreSQL");
        }
    }

    static final class Rates implements ExchangeRates {

        final Map<LocalDate, BigDecimal> byDate = new HashMap<>();

        @Override
        public Optional<BigDecimal> copPerUsd(LocalDate date) {
            return Optional.ofNullable(byDate.get(date));
        }
    }

    static final class RecordingLedger implements Ledger {

        record Recorded(UserId owner, CaptureEntry entry, UUID id) {}

        final List<Recorded> recorded = new ArrayList<>();

        @Override
        public UUID record(UserId owner, CaptureEntry entry) {
            UUID id = UUID.randomUUID();
            recorded.add(new Recorded(owner, entry, id));
            return id;
        }

        @Override
        public void delete(UserId owner, UUID transactionId) {
            throw new UnsupportedOperationException("Se prueba contra PostgreSQL");
        }

        @Override
        public UUID convertExpenseToTransfer(UserId owner, UUID expenseId, UUID destination) {
            throw new UnsupportedOperationException("Se prueba contra PostgreSQL");
        }

        @Override
        public UUID convertTransferToExpense(UserId owner, UUID transferId) {
            throw new UnsupportedOperationException("Se prueba contra PostgreSQL");
        }

        CaptureEntry last() {
            return recorded.getLast().entry();
        }
    }
}
