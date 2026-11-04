package com.intact_id.service.kyc;

import com.intact_id.config.VerificationProcessingConfig;
import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.db.model.kyc.VerificationRequest;
import com.intact_id.db.repository.kyc.VerificationRequestRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationProcessingScheduler {

    private final VerificationRequestRepository verificationRequestRepository;
    private final VerificationProcessingConfig config;
    private final VerificationProcessor verificationProcessor;

    private ExecutorService processingExecutor;
    /** Verifications handed to the pool and not finished yet; caps pickup at max-concurrent. */
    private final AtomicInteger inFlight = new AtomicInteger();

    @PostConstruct
    void init() {
        int threads = Math.max(1, config.getMaxConcurrent());
        processingExecutor = Executors.newFixedThreadPool(threads,
                r -> new Thread(r, "kyc-processor-" + System.nanoTime()));
        log.info("KYC processing pool initialised with {} threads", threads);
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        processingExecutor.shutdown();
        if (!processingExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
            processingExecutor.shutdownNow();
        }
    }

    /**
     * Polls often and hands each verification to the pool as soon as a slot is free. It used to
     * join() the whole batch before polling again, so a submission arriving while one
     * verification was running (30–50 s) waited for it to finish plus the poll delay.
     */
    @Scheduled(fixedDelayString = "${verification.processing.poll-interval-ms:2000}")
    public void processSubmittedVerifications() {
        if (!config.isEnabled()) return;

        int freeSlots = Math.max(1, config.getMaxConcurrent()) - inFlight.get();
        if (freeSlots <= 0) return;

        try {
            List<VerificationRequest> pending = verificationRequestRepository
                    .findByStatusOrderByCreatedAtAsc(
                            GlobalStatuses.SUBMITTED,
                            PageRequest.of(0, Math.min(config.getBatchSize(), freeSlots)));

            if (pending.isEmpty()) return;

            log.info("Picked up {} submitted verification(s) — {} already in progress (max {})",
                    pending.size(), inFlight.get(), config.getMaxConcurrent());

            // Mark as PROCESSING before handing off so the next tick doesn't re-pick them.
            for (VerificationRequest r : pending) {
                r.markAsProcessing();
                verificationRequestRepository.save(r);
            }

            for (VerificationRequest request : pending) {
                inFlight.incrementAndGet();
                CompletableFuture.runAsync(() -> {
                    try {
                        verificationProcessor.process(request);
                    } catch (Exception e) {
                        log.error("Unhandled error processing verification {}", request.getId(), e);
                    } finally {
                        inFlight.decrementAndGet();
                    }
                }, processingExecutor);
            }

        } catch (Exception e) {
            log.error("Error in verification processing scheduler", e);
        }
    }
}
