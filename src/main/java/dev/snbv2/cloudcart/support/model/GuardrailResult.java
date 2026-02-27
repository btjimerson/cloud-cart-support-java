package dev.snbv2.cloudcart.support.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

/**
 * Immutable result of a guardrail check performed on a message.
 *
 * <p>A guardrail result indicates whether the message is allowed to proceed,
 * a list of flags describing any issues detected, and a sanitized version
 * of the original message with any problematic content removed or modified.</p>
 */
@Getter
@AllArgsConstructor
@ToString
public class GuardrailResult {

    private boolean allowed;
    private List<String> flags;
    private String sanitizedMessage;
}
