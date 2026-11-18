package com.company.communication.service;

import com.company.communication.domain.enums.CommunicationChannel;
import com.company.communication.domain.enums.PreferredPeriod;
import com.company.communication.domain.model.BoletoGerado;
import com.company.communication.domain.model.CustomerBehaviorProfile;
import com.company.communication.domain.model.CustomerContext;
import com.company.communication.domain.model.EligibilityResult;
import com.company.communication.domain.model.PaymentHistorySummary;
import com.company.communication.domain.model.ProductInfo;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Constroi o {@link CustomerContext} tipado e calcula as regras deterministicas de elegibilidade.
 */
@Service
public class CustomerContextBuilder {

    private static final LocalTime POLICY_WINDOW_START = LocalTime.of(8, 0);
    private static final LocalTime POLICY_WINDOW_END = LocalTime.of(20, 0);

    public CustomerContext build(
            BoletoGerado event,
            CustomerBehaviorProfile profile,
            PaymentHistorySummary paymentHistory,
            ProductInfo productInfo
    ) {
        long daysUntilDue = ChronoUnit.DAYS.between(event.occurredAt().toLocalDate(), event.dueDate());
        EligibilityResult eligibility = evaluateEligibility(event, profile, productInfo);

        return new CustomerContext(
                new CustomerContext.BoletoSummary(
                        event.eventId(),
                        event.boletoId(),
                        event.amountInCents(),
                        event.dueDate(),
                        daysUntilDue,
                        event.productType(),
                        event.occurredAt()
                ),
                new CustomerContext.CustomerReference(
                        profile.customerId(),
                        profile.firstName()
                ),
                new CustomerContext.BehaviorSummary(
                        profile.onTimePayments(),
                        profile.latePayments(),
                        profile.averageDelayDays(),
                        profile.lastPaymentAt(),
                        profile.preferredTone()
                ),
                new CustomerContext.PaymentMetrics(
                        paymentHistory.recentPaymentsCount(),
                        paymentHistory.onTimeCount(),
                        paymentHistory.lateCount()
                ),
                new CustomerContext.CommunicationHistorySummary(
                        profile.recentCommunicationsCount(),
                        profile.lastCommunicationAt(),
                        profile.lastCommunicationChannel()
                ),
                new CustomerContext.ChannelPreferences(
                        profile.preferredChannel(),
                        profile.preferredPeriod(),
                        profile.eligibleChannels()
                ),
                new CustomerContext.ProductSummary(
                        productInfo.productType(),
                        productInfo.displayName(),
                        productInfo.allowedChannels()
                ),
                eligibility
        );
    }

    private EligibilityResult evaluateEligibility(
            BoletoGerado event,
            CustomerBehaviorProfile profile,
            ProductInfo productInfo
    ) {
        Map<CommunicationChannel, String> blocked = new LinkedHashMap<>();
        if (profile.blockedChannels() != null) {
            blocked.putAll(profile.blockedChannels());
        }

        List<CommunicationChannel> productAllowed = productInfo.allowedChannels() == null
                ? List.of()
                : productInfo.allowedChannels();

        List<CommunicationChannel> customerEligible = profile.eligibleChannels() == null
                ? List.of()
                : profile.eligibleChannels();

        List<CommunicationChannel> allowedChannels = profile.optedOut()
                ? List.of()
                : customerEligible.stream()
                        .filter(productAllowed::contains)
                        .distinct()
                        .toList();

        for (CommunicationChannel channel : CommunicationChannel.values()) {
            if (!allowedChannels.contains(channel) && !blocked.containsKey(channel)) {
                blocked.put(channel, profile.optedOut() ? "CUSTOMER_OPT_OUT" : "NOT_ELIGIBLE_FOR_CUSTOMER_OR_PRODUCT");
            }
        }

        boolean suppressionRequired = profile.optedOut() || allowedChannels.isEmpty();
        String suppressionReason = null;
        if (profile.optedOut()) {
            suppressionReason = "CUSTOMER_OPT_OUT";
        } else if (allowedChannels.isEmpty()) {
            suppressionReason = "NO_ELIGIBLE_CHANNEL";
        }

        OffsetDateTime[] window = computeRecommendedWindow(event.occurredAt(), profile.preferredPeriod());

        return new EligibilityResult(
                !suppressionRequired,
                allowedChannels,
                Map.copyOf(blocked),
                suppressionRequired,
                suppressionReason,
                window[0],
                window[1]
        );
    }

    private OffsetDateTime[] computeRecommendedWindow(OffsetDateTime occurredAt, PreferredPeriod preferredPeriod) {
        ZoneOffset offset = occurredAt.getOffset();
        LocalDate date = occurredAt.toLocalDate();
        LocalTime periodStart;
        LocalTime periodEnd;

        PreferredPeriod effectivePeriod = preferredPeriod != null ? preferredPeriod : PreferredPeriod.MORNING;
        switch (effectivePeriod) {
            case MORNING -> {
                periodStart = LocalTime.of(8, 30);
                periodEnd = LocalTime.of(11, 30);
            }
            case AFTERNOON -> {
                periodStart = LocalTime.of(13, 30);
                periodEnd = LocalTime.of(17, 30);
            }
            case EVENING -> {
                periodStart = LocalTime.of(18, 0);
                periodEnd = LocalTime.of(19, 45);
            }
            default -> {
                periodStart = POLICY_WINDOW_START;
                periodEnd = POLICY_WINDOW_END;
            }
        }

        LocalTime eventTime = occurredAt.toLocalTime();
        if (eventTime.isAfter(POLICY_WINDOW_END)) {
            LocalDate nextDay = date.plusDays(1);
            return new OffsetDateTime[]{
                    OffsetDateTime.of(nextDay, periodStart, offset),
                    OffsetDateTime.of(nextDay, periodEnd, offset)
            };
        }

        if (eventTime.isAfter(periodEnd)) {
            LocalTime fallbackStart = eventTime.isBefore(POLICY_WINDOW_START) ? POLICY_WINDOW_START : eventTime;
            return new OffsetDateTime[]{
                    OffsetDateTime.of(date, fallbackStart, offset),
                    OffsetDateTime.of(date, POLICY_WINDOW_END, offset)
            };
        }

        LocalTime start = eventTime.isAfter(periodStart) ? eventTime : periodStart;
        return new OffsetDateTime[]{
                OffsetDateTime.of(date, start, offset),
                OffsetDateTime.of(date, periodEnd, offset)
        };
    }
}
