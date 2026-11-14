package com.intact_id.orchestration.privacy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface RetentionCountryRuleRepository extends JpaRepository<RetentionCountryRule, String> {

    List<RetentionCountryRule> findAllByOrderByCountryAscCategoryAsc();

    List<RetentionCountryRule> findByCountryIn(List<String> countries);

    void deleteByCountry(String country);
}
