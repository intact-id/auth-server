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

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationProcessingScheduler {

    private final VerificationRequestRepository verificationRequestRepository;
    private final VerificationProcessingConfig config;
    private final VerificationProcessor verificationProcessor;

    private ExecutorService processingExecutor;

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

    @Scheduled(fixedDelayString = "${verification.processing.poll-interval-ms:30000}")
    public void processSubmittedVerifications() {
        if (!config.isEnabled()) return;

        try {
            List<VerificationRequest> pending = verificationRequestRepository
                    .findByStatusOrderByCreatedAtAsc(
                            GlobalStatuses.SUBMITTED,
                            PageRequest.of(0, config.getBatchSize()));

            if (pending.isEmpty()) return;

            log.info("Picked up {} submitted verification(s) — processing in parallel (max {})",
                    pending.size(), config.getMaxConcurrent());

            // Mark all as PROCESSING before spawning threads so the next scheduler tick
            // doesn't re-pick the same records while this batch is still running.
            for (VerificationRequest r : pending) {
                r.markAsProcessing();
                verificationRequestRepository.save(r);
            }

            CompletableFuture<?>[] futures = pending.stream()
                    .map(request -> CompletableFuture.runAsync(() -> {
                        try {
                            verificationProcessor.process(request);
                        } catch (Exception e) {
                            log.error("Unhandled error processing verification {}", request.getId(), e);
                        }
                    }, processingExecutor))
                    .toArray(CompletableFuture[]::new);

            CompletableFuture.allOf(futures).join();

        } catch (Exception e) {
            log.error("Error in verification processing scheduler", e);
        }
    }
}
