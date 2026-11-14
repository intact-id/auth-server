package com.intact_id.orchestration.privacy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface RetentionCompanyRuleRepository extends JpaRepository<RetentionCompanyRule, String> {

    List<RetentionCompanyRule> findByCompanyIdOrderByCountryAscCategoryAsc(String companyId);

    List<RetentionCompanyRule> findByCompanyIdAndCountryIn(String companyId, List<String> countries);

    void deleteByCompanyIdAndCountry(String companyId, String country);
}
