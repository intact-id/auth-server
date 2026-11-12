package com.intact_id.checks.screening;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Risk model configuration and assessments, as sactions-service defines them. */
public final class RiskApi {

    private RiskApi() {
    }

    public enum CustomerType { INDIVIDUAL, BUSINESS, BOTH }

    public enum ModelStatus { DRAFT, ACTIVE, RETIRED }

    public enum CombineMethod { WEIGHTED_SUM }

    public enum RollUpMethod { WEIGHTED_AVG, WEIGHTED_SUM, AVERAGE, MAX }

    /** ONBOARDING categories score at onboarding; POST_ONBOARDING ones (e.g. screening) only afterwards. */
    public enum Timing { ONBOARDING, POST_ONBOARDING }

    /** What a missing attribute does: score it defaultScore, score it as the highest risk, or mark the assessment incomplete. */
    public enum MissingBehavior { DEFAULT_SCORE, MAX_RISK, INCOMPLETE }

    public enum MatchType { EXACT, RANGE, SET, BOOLEAN, DEFAULT }

    public enum ForceLevel { MEDIUM, HIGH }

    public enum RiskLevel { LOW, MEDIUM, HIGH }

    public enum DataType { ENUM, NUMBER, BOOLEAN, CURRENCY_AMOUNT, DATE, COUNTRY, STRING }

    public enum SourceSystem { MANUAL, ID_VERIFICATION, BIOMETRIC, REGISTRY, SCREENING }

    public enum Stage { ONBOARDING, POST_ONBOARDING }

    public enum AssessmentStatus { COMPLETE, INCOMPLETE }

    public enum TriggeredBy { ONBOARDING, SCREENING_EVENT, PERIODIC, MANUAL }

    public enum Verdict { NO_HIT, POTENTIAL_MATCH, CONFIRMED_MATCH }

    public enum ConditionOp {
        @JsonProperty("eq") EQ, @JsonProperty("ne") NE, @JsonProperty("in") IN, @JsonProperty("not_in") NOT_IN,
        @JsonProperty("gt") GT, @JsonProperty("gte") GTE, @JsonProperty("lt") LT, @JsonProperty("lte") LTE,
        @JsonProperty("exists") EXISTS
    }

    // --- Risk model config (request and response) ---

    public record RiskModelConfig(CustomerType customerType, CombineMethod combineMethod, String description,
                                  List<Threshold> overallThresholds, List<OverrideRule> overrideRules,
                                  List<Category> categories) {
    }

    public record Threshold(RiskLevel riskLevel, BigDecimal minScore, BigDecimal maxScore) {
    }

    /** Forces the overall level up to forceLevel when the condition holds. */
    public record OverrideRule(Condition condition, ForceLevel forceLevel, String reason) {
    }

    /**
     * Either a rule on one attribute ({"attribute": "sanctions", "op": "eq", "value": "CONFIRMED_MATCH"}) or a group
     * ({"all": [...]} / {"any": [...]}).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Condition(String attribute, ConditionOp op, Object value, List<Condition> all, List<Condition> any) {
    }

    public record Category(String key, String displayName, BigDecimal weight, RollUpMethod rollupMethod, Timing timing,
                           Integer ordering, List<Threshold> thresholds, List<Attribute> attributes) {
    }

    public record Attribute(String attributeDefinitionKey, BigDecimal weight, Boolean required,
                            MissingBehavior missingBehavior, Integer defaultScore, List<ValueScoreRule> rules) {
    }

    public record ValueScoreRule(MatchType matchType, MatchValue matchValue, Integer score, Integer priority) {
    }

    /** EXACT and BOOLEAN use eq, SET uses in, RANGE uses min and/or max, DEFAULT none. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MatchValue(Object eq, List<Object> in, BigDecimal min, BigDecimal max) {
    }

    // --- Responses ---

    /** tenantId is read from sactions-service for ownership checks and never sent to callers. */
    public record RiskModel(String id, Integer version, String description, CustomerType customerType,
                            ModelStatus lifeCycleStatus, CombineMethod combineMethod,
                            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String tenantId,
                            LocalDateTime effectiveFrom) {
    }

    public record RiskModelDetail(RiskModel model, RiskModelConfig config) {
    }

    public record FieldError(String field, String message) {
    }

    public record Validation(boolean valid, List<FieldError> errors, List<FieldError> warnings) {
    }

    public record AttributeDefinition(String key, String displayName, DataType dataType, SourceSystem sourceSystem,
                                      String description, String categoryKey, CustomerType customerType,
                                      boolean active) {
    }

    public record ScreeningMatch(String entityId, String caption, double score, List<String> datasets,
                                 List<String> topics, List<String> sourceUrls) {
    }

    public record ScreeningResult(Verdict verdict, List<ScreeningMatch> matches) {
    }

    public record CategoryResult(String categoryKey, BigDecimal categoryScore, RiskLevel categoryLevel) {
    }

    public record ContributionLine(String categoryKey, String attributeKey, Object rawValue, Object normalizedValue,
                                   String matchedRuleId, Integer points, BigDecimal weightApplied,
                                   BigDecimal weightedPoints, String note) {
    }

    /** categoryResults and contributionLines are filled only when the breakdown is asked for. */
    public record Assessment(String id, @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String tenantId,
                             String customerId, CustomerType customerType, String riskModelId, Integer modelVersion,
                             Stage stage, AssessmentStatus status, BigDecimal overallScore, RiskLevel overallLevel,
                             Boolean overrideApplied, TriggeredBy triggeredBy, ScreeningResult screening,
                             List<CategoryResult> categoryResults, List<ContributionLine> contributionLines) {
    }
}
