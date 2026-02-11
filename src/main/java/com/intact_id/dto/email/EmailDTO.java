package com.intact_id.dto.email;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO for email data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailDTO {

    private String to;
    private String subject;
    private String templateName;
    private Map<String, Object> variables;

    /**
     * Create a simple text email (no template)
     */
    private String textContent;

    /**
     * CC recipients
     */
    private String[] cc;

    /**
     * BCC recipients
     */
    private String[] bcc;
}
