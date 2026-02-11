package com.intact_id.config;

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
