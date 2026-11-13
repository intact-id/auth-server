package com.intact_id.workflow.web;

import com.intact_id.workflow.ServiceRequirements;
import com.intact_id.workflow.SubjectType;
import com.intact_id.workflow.WorkflowGraph;
import com.intact_id.workflow.WorkflowPricing.PriceQuote;
import com.intact_id.workflow.WorkflowProblem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Request and response bodies of the workflows API. */
public final class WorkflowDtos {

    private WorkflowDtos() {
    }

    /** Start from an empty canvas, a graph, or a copy of a template (fromTemplateId). */
    public record CreateWorkflowRequest(
            @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,99}$", message = "key: lowercase letters, digits and dashes, e.g. full-onboarding")
            String key,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 2000) String description,
            SubjectType subjectType,
            WorkflowGraph graph,
            String fromTemplateId,
            // SUPER_ADMIN only: an Intact template instead of a company workflow
            Boolean template) {
    }

    public record UpdateDraftRequest(@Size(max = 255) String name, @Size(max = 2000) String description,
                                     @NotNull WorkflowGraph graph) {
    }

    public record PublishRequest(@Size(max = 2000) String notes) {
    }

    public record PromoteRequest(@NotNull @Positive Integer version) {
    }

    public record QuoteRequest(@NotEmpty List<@NotBlank String> services) {
    }

    public record WorkflowSummary(String id, String key, String name, String description, SubjectType subjectType,
                                  boolean template, int latestVersion, Integer liveVersion,
                                  LocalDateTime draftUpdatedAt, BigDecimal livePrice, String currency,
                                  boolean defaultWorkflow) {
    }

    /** created is false when the draft had not changed since the latest version, which is returned instead. */
    public record PublishResult(VersionSummary version, boolean created) {
    }

    public record WorkflowDetail(WorkflowSummary workflow, WorkflowGraph draft, List<VersionSummary> versions) {
    }

    public record VersionSummary(int number, List<String> services, BigDecimal price, String currency, String notes,
                                 String publishedBy, LocalDateTime publishedAt, LocalDateTime promotedAt, boolean live) {
    }

    /** What the builder shows next to the canvas: problems to fix, what the widget will ask for, and the price. */
    public record ValidationResponse(boolean valid, List<WorkflowProblem> problems, List<String> services,
                                     ServiceRequirements requirements, PriceQuote price) {
    }
}
