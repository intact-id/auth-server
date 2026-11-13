package com.intact_id.orchestration.verification;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.accounts.CompanyCredentials;
import com.intact_id.orchestration.VerificationRequest;
import com.intact_id.accounts.CompanyCredentialsRepository;
import com.intact_id.orchestration.model.VerificationRequestRepository;
import com.intact_id.orchestration.events.RateLimitConsumedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final VerificationRequestRepository verificationRequestRepository;
    private final CompanyCredentialsRepository companyCredentialsRepository;
    private final ApplicationEventPublisher eventPublisher;

    public record LimitExceeded(String window, long used, int limit, LocalDateTime resetAt) {}

    /**
     * Checks per-company rate limits sourced from CompanyCredentials.
     * Returns LimitExceeded if over quota, null if within limits.
     * On success publishes RateLimitConsumedEvent and increments the usage counters.
     */
    public LimitExceeded checkAndConsume(VerificationRequest request) {
        String companyId = request.getCompany().getId();

        CompanyCredentials creds = companyCredentialsRepository.findByCompanyId(companyId)
                .orElse(null);

        if (creds == null) {
            log.warn("No credentials found for company {}, skipping rate limit check", companyId);
            return null;
        }

        // Hourly
        Integer hourlyLimit = creds.getRateLimitPerHour();
        if (hourlyLimit != null && hourlyLimit > 0) {
            LocalDateTime hourStart = LocalDateTime.now(ZoneOffset.UTC).withMinute(0).withSecond(0).withNano(0);
            long used = countProcessed(companyId, hourStart);
            if (used >= hourlyLimit) {
                LocalDateTime resetAt = hourStart.plusHours(1);
                log.warn("Hourly rate limit exceeded company={} used={} limit={}", companyId, used, hourlyLimit);
                return new LimitExceeded("hourly", used, hourlyLimit, resetAt);
            }
        }

        // Daily
        Integer dailyLimit = creds.getRateLimitPerDay();
        if (dailyLimit != null && dailyLimit > 0) {
            LocalDateTime dayStart = LocalDateTime.now(ZoneOffset.UTC).toLocalDate().atStartOfDay();
            long used = countProcessed(companyId, dayStart);
            if (used >= dailyLimit) {
                LocalDateTime resetAt = dayStart.plusDays(1);
                log.warn("Daily rate limit exceeded company={} used={} limit={}", companyId, used, dailyLimit);
                return new LimitExceeded("daily", used, dailyLimit, resetAt);
            }
        }

        // Monthly
        Integer monthlyLimit = creds.getRateLimitPerMonth();
        if (monthlyLimit != null && monthlyLimit > 0) {
            LocalDateTime monthStart = LocalDateTime.now(ZoneOffset.UTC).withDayOfMonth(1).toLocalDate().atStartOfDay();
            long used = countProcessed(companyId, monthStart);
            if (used >= monthlyLimit) {
                LocalDateTime resetAt = monthStart.plusMonths(1);
                log.warn("Monthly rate limit exceeded company={} used={} limit={}", companyId, used, monthlyLimit);
                return new LimitExceeded("monthly", used, monthlyLimit, resetAt);
            }
        }

        // Within limits — record usage and publish event
        creds.recordUsage(true);
        companyCredentialsRepository.save(creds);
        eventPublisher.publishEvent(new RateLimitConsumedEvent(this, companyId));
        return null;
    }

    private long countProcessed(String companyId, LocalDateTime since) {
        return verificationRequestRepository
                .countByCompanyIdAndStatusNotAndCreatedAtAfter(companyId, GlobalStatuses.SUBMITTED, since);
    }
}
