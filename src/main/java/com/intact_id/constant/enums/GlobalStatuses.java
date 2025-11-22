package com.intact_id.constant.enums;

import lombok.Getter;

/**
 * @Author frank
 * email frank.mwangi@dadanadagroup.com
 * Created on 16/11/2025 17:39
 *
 * Neutral global statuses that can be applied across different entities or processes.
 */
@Getter
public enum GlobalStatuses {
    SUBMITTED("Action or process is submitted"),
    PENDING("Action or process is created but not yet started or completed"),
    PENDING_APPROVAL("Action or process is created but cannot be used since it is not approved"),
    APPROVED("Action or process has been approved "),
    ACTIVE("Currently active or operational"),
    INACTIVE("Currently inactive or disabled"),
    SUSPENDED("Temporarily suspended, action or entity is on hold"),
    COMPLETED("Action or process has been successfully finished"),
    FAILED("Action or process did not succeed"),
    CANCELLED("Action or process was intentionally cancelled"),
    REVOKED("Action or process or credential was intentionally revoked"),
    EXPIRED("Time-bound action or entity is no longer valid"),
    PROCESSING("Action or process is currently being executed"),
    DRAFT("Work is in progress, not finalized"),
    INFO_REQUESTED("extra info is being requested so as to continue with the action or proccess"),
    REJECTED("action or proccess has been rejected"),
    ARCHIVED("Entity or record is stored for historical purposes"),
    UNKNOWN("Status is unknown or not set"),
    CLOSED("entity or business or service has been closed"),;


    private final String description;

    GlobalStatuses(String description) {
        this.description = description;
    }

}
