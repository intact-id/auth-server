package com.intact_id.ops;

import com.intact_id.accounts.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pings each service auth-server depends on once a minute and records whether it answered and how fast.
 * A service is up when its health endpoint answers 2xx within the timeout. Results older than 30 days are deleted.
 */
@Slf4j
@Component
class ServiceHealthMonitor {

    static final String DATABASE = "database";
    static final Duration KEEP = Duration.ofDays(30);
    private static final String SYSTEM_PAGE = "/dashboard/analytics";

    /** Display order is the order here. */
    private final Map<String, String> healthUrls = new LinkedHashMap<>();
    private final RestClient http;
    private final JdbcTemplate jdbc;
    private final ServicePingRepository pings;
    private final NotificationService notifications;
    // Last known state per service, so staff are told when one goes down or comes back, not every minute
    private final Map<String, Boolean> lastUp = new ConcurrentHashMap<>();

    ServiceHealthMonitor(@Value("${face-service.url}") String faceUrl,
                         @Value("${ocr-service.url}") String ocrUrl,
                         @Value("${screening-service.url}") String screeningUrl,
                         @Value("${billing-service.url}") String billingUrl,
                         JdbcTemplate jdbc, ServicePingRepository pings, NotificationService notifications) {
        healthUrls.put("face-service", faceUrl + "/health/live");
        healthUrls.put("ocr-service", ocrUrl + "/health");
        healthUrls.put("screening-service", screeningUrl + "/actuator/health");
        healthUrls.put("billing-service", billingUrl + "/actuator/health");
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(3));
        timeouts.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder().requestFactory(timeouts).build();
        this.jdbc = jdbc;
        this.pings = pings;
        this.notifications = notifications;
    }

    /** Every service this monitor checks, in display order. */
    Iterable<String> services() {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(DATABASE), healthUrls.keySet().stream()).toList();
    }

    @Scheduled(fixedDelayString = "${ops.health.interval-ms:60000}", initialDelayString = "${ops.health.initial-delay-ms:30000}")
    void pingAll() {
        LocalDateTime now = LocalDateTime.now();
        record(pingDatabase(now));
        healthUrls.forEach((service, url) -> record(ping(service, url, now)));
    }

    private void record(ServicePing ping) {
        pings.save(ping);
        Boolean before = lastUp.put(ping.getService(), ping.isUp());
        // The first check after a restart only sets the baseline
        if (before == null || before == ping.isUp()) return;
        if (ping.isUp()) {
            notifications.notifyStaff(ping.getService() + " is back up",
                    ping.getService() + " is answering again.", "SUCCESS", SYSTEM_PAGE);
        } else {
            notifications.notifyStaff(ping.getService() + " is down",
                    ping.getService() + " stopped answering its health check"
                            + (ping.getError() != null ? ": " + ping.getError() : ".")
                            + " Verifications that need it may fail or go to manual review.",
                    "ERROR", SYSTEM_PAGE);
        }
    }

    @Scheduled(cron = "0 15 3 * * *")
    void deleteOld() {
        int deleted = pings.deleteOlderThan(LocalDateTime.now().minus(KEEP));
        log.info("Deleted {} service pings older than {} days", deleted, KEEP.toDays());
    }

    private ServicePing ping(String service, String url, LocalDateTime now) {
        long start = System.nanoTime();
        try {
            int status = http.get().uri(url).retrieve().toBodilessEntity().getStatusCode().value();
            return new ServicePing(service, true, elapsedMs(start), status, null, now);
        } catch (RestClientResponseException e) {
            return new ServicePing(service, false, elapsedMs(start), e.getStatusCode().value(), e.getStatusText(), now);
        } catch (Exception e) {
            log.debug("Health check of {} failed: {}", service, e.getMessage());
            return new ServicePing(service, false, elapsedMs(start), null, e.getClass().getSimpleName() + ": " + e.getMessage(), now);
        }
    }

    private ServicePing pingDatabase(LocalDateTime now) {
        long start = System.nanoTime();
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return new ServicePing(DATABASE, true, elapsedMs(start), null, null, now);
        } catch (Exception e) {
            return new ServicePing(DATABASE, false, elapsedMs(start), null, e.getMessage(), now);
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
