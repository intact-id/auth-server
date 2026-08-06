package com.intact_id.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.intact_id.db.model.billing.CompanyBillingProfile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Caffeine cache for CompanyBillingProfile lookups. CompanyBillingProfile carries a live
 * wallet balance (currentBalance) that's mutated on every PREPAID debit/top-up, so this is
 * NOT a blanket cache-everything setup — see BillingService for which call sites are allowed
 * to read from it (advisory/read-only paths only) versus which must always read the DB fresh
 * (anything that feeds a read-modify-write of currentBalance, e.g. debitWallet/topUpWallet).
 * Short TTL plus explicit invalidation on every write keeps the staleness window small even
 * for the paths that do use it.
 */
@Configuration
public class BillingCacheConfig {

    @Value("${billing.profile-cache-ttl-seconds:30}")
    private int profileCacheTtlSeconds;

    @Value("${billing.profile-cache-max-size:5000}")
    private int profileCacheMaxSize;

    @Bean
    public Cache<String, CompanyBillingProfile> companyBillingProfileCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(profileCacheTtlSeconds))
                .maximumSize(profileCacheMaxSize)
                .build();
    }
}
