package com.intact_id.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Slf4j
@Configuration

@ConfigurationProperties(prefix = "tiers")
@PropertySource(value = "classpath:conf/tiers.yaml", factory = YamlPropertySourceFactory.class)
public class TiersConfig {

    private Map<String, Tier> tiers = new HashMap<>();

    @Data
    public static class Tier {
        private String name;
        private Double cost;
        private List<Double> priceRange = new ArrayList<>();
        private List<String> features = new ArrayList<>();
        private List<String> exclusions = new ArrayList<>();
    }

    @PostConstruct
    void init() {


        if (tiers == null || tiers.isEmpty()) {
            log.error("No tiers loaded.");
            return;
        }

        log.info("Total tiers loaded: {}", tiers.size());

        tiers.forEach((key, tier) -> {
            log.info("");
            log.info("Tier Key: {}", key);
            log.info("  Name: {}", tier.getName());
            log.info("  Cost: ${}", tier.getCost());
            log.info("  Price Range: ${} - ${}",
                    tier.getPriceRange().get(0),
                    tier.getPriceRange().get(1));
            log.info("  Features ({}): {}", tier.getFeatures().size(), tier.getFeatures());

            if (tier.getExclusions() != null && !tier.getExclusions().isEmpty()) {
                log.info("  Exclusions ({}): {}", tier.getExclusions().size(), tier.getExclusions());
            }

            // Calculate profit margin
            double minProfit = tier.getPriceRange().get(0) - tier.getCost();
            double maxProfit = tier.getPriceRange().get(1) - tier.getCost();
            log.info("  Profit Margin: ${} - ${}",
                    String.format("%.3f", minProfit),
                    String.format("%.3f", maxProfit));
        });

        log.info("====================================");
    }

    // Helper methods for easier access
    public Tier getTier(String tierKey) {
        return tiers.get(tierKey);
    }

    public Tier getTierByName(String name) {
        return tiers.values().stream()
                .filter(tier -> tier.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public boolean tierExists(String tierKey) {
        return tiers.containsKey(tierKey);
    }

    public List<String> getAllTierKeys() {
        return new ArrayList<>(tiers.keySet());
    }
}
