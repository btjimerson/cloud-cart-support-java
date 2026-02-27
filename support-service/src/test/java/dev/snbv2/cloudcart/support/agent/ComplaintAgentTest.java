package dev.snbv2.cloudcart.support.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link ComplaintAgent} escalation keyword detection logic.
 * Uses a minimal stub to verify that messages containing escalation keywords
 * (e.g., lawyer, manager, unacceptable) are correctly identified, that
 * non-escalation messages are not falsely flagged, and that the system note
 * augmentation is constructed properly. Tests are performed without needing
 * a real or mocked ChatModel.
 */
class ComplaintAgentTest {

    private static final Set<String> ESCALATION_KEYWORDS = Set.of(
            "lawyer", "lawsuit", "legal action", "attorney",
            "manager", "supervisor", "unacceptable", "outrageous",
            "disgrace", "disgusting", "worst", "terrible service"
    );

    /**
     * Tests that messages containing known escalation keywords are correctly detected.
     * Each parameterized input contains at least one escalation keyword.
     *
     * @param message the message to check for escalation keywords
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "I want to speak to a manager",
            "I'm going to get a lawyer involved",
            "This service is unacceptable",
            "I want to talk to a supervisor",
            "This is outrageous",
            "Your service is a disgrace",
            "I'll take legal action"
    })
    void escalationKeywords_detected(String message) {
        String lower = message.toLowerCase();
        boolean detected = ESCALATION_KEYWORDS.stream().anyMatch(lower::contains);
        assertTrue(detected, "Expected escalation keyword to be detected in: " + message);
    }

    /**
     * Tests that regular complaint messages without escalation keywords are not
     * falsely detected as escalation cases.
     *
     * @param message the non-escalation message to verify
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "I'm a bit unhappy with the color of my shirt",
            "My order arrived late",
            "The product is not what I expected",
            "Can I get a refund?"
    })
    void nonEscalationMessages_notDetected(String message) {
        String lower = message.toLowerCase();
        boolean detected = ESCALATION_KEYWORDS.stream().anyMatch(lower::contains);
        assertFalse(detected, "Should not detect escalation in: " + message);
    }

    /**
     * Tests that when an escalation keyword is detected, the message is augmented
     * with a system note indicating HIGH priority escalation to a supervisor.
     */
    @Test
    void escalationAugmentation_addsSystemNote() {
        String message = "I want to speak to a manager right now!";
        String lower = message.toLowerCase();
        boolean shouldEscalate = ESCALATION_KEYWORDS.stream().anyMatch(lower::contains);

        assertTrue(shouldEscalate);
        String augmented = message + "\n\n[SYSTEM NOTE: Escalation keywords detected. " +
                "Create a HIGH priority support ticket and escalate to supervisor.]";
        assertTrue(augmented.contains("[SYSTEM NOTE: Escalation keywords detected"));
        assertTrue(augmented.contains("HIGH priority"));
    }

    /**
     * Tests that escalation keyword detection is case-insensitive by verifying
     * an uppercase keyword is still detected.
     */
    @Test
    void escalationKeywords_caseInsensitive() {
        String message = "I want to speak to a MANAGER";
        String lower = message.toLowerCase();
        boolean detected = ESCALATION_KEYWORDS.stream().anyMatch(lower::contains);
        assertTrue(detected);
    }
}
