package dev.snbv2.cloudcart.support.service;

import dev.snbv2.cloudcart.support.model.GuardrailResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link GuardrailService}.
 * Covers PII detection and redaction (SSN, credit card, email, phone number),
 * off-topic message blocking, sanitization of messages containing multiple
 * PII types, and the interaction between off-topic blocking and PII sanitization.
 */
class GuardrailServiceTest {

    private GuardrailService guardrailService;

    /**
     * Initializes a fresh GuardrailService instance before each test.
     */
    @BeforeEach
    void setUp() {
        guardrailService = new GuardrailService();
    }

    /**
     * Tests that a clean message with no PII or off-topic content is allowed
     * through with no flags and the original message is preserved.
     */
    @Test
    void apply_cleanMessage_allowed() {
        GuardrailResult result = guardrailService.apply("Where is my order?");
        assertTrue(result.isAllowed());
        assertTrue(result.getFlags().isEmpty());
        assertEquals("Where is my order?", result.getSanitizedMessage());
    }

    /**
     * Tests that an SSN pattern (XXX-XX-XXXX) is detected, flagged as PII_SSN,
     * and redacted in the sanitized message while still allowing the message through.
     */
    @Test
    void apply_ssnDetected_flaggedAndRedacted() {
        GuardrailResult result = guardrailService.apply("My SSN is 123-45-6789");
        assertTrue(result.isAllowed());
        assertTrue(result.getFlags().contains("PII_SSN"));
        assertEquals("My SSN is [REDACTED-SSN]", result.getSanitizedMessage());
    }

    /**
     * Tests that a credit card number with dashes is detected, flagged as
     * PII_CREDIT_CARD, and the card digits are removed from the sanitized message.
     */
    @Test
    void apply_creditCardDetected_flaggedAndRedacted() {
        GuardrailResult result = guardrailService.apply("Card number 4111-1111-1111-1111 please");
        assertTrue(result.isAllowed());
        assertTrue(result.getFlags().contains("PII_CREDIT_CARD"));
        assertFalse(result.getSanitizedMessage().contains("4111"));
    }

    /**
     * Tests that a credit card number without separators (16 consecutive digits)
     * is detected and flagged as PII_CREDIT_CARD.
     */
    @Test
    void apply_creditCardNoSeparators_flaggedAndRedacted() {
        GuardrailResult result = guardrailService.apply("Card 4111111111111111");
        assertTrue(result.isAllowed());
        assertTrue(result.getFlags().contains("PII_CREDIT_CARD"));
    }

    /**
     * Tests that an email address is detected, flagged as PII_EMAIL, and
     * redacted in the sanitized message.
     */
    @Test
    void apply_emailDetected_flaggedAndRedacted() {
        GuardrailResult result = guardrailService.apply("Email me at user@example.com");
        assertTrue(result.isAllowed());
        assertTrue(result.getFlags().contains("PII_EMAIL"));
        assertEquals("Email me at [REDACTED-EMAIL]", result.getSanitizedMessage());
    }

    /**
     * Tests that a phone number with dashes (XXX-XXX-XXXX) is detected,
     * flagged as PII_PHONE, and the digits are removed from the sanitized message.
     */
    @Test
    void apply_phoneWithDashes_flaggedAndRedacted() {
        GuardrailResult result = guardrailService.apply("Call me at 555-123-4567");
        assertTrue(result.isAllowed());
        assertTrue(result.getFlags().contains("PII_PHONE"));
        assertFalse(result.getSanitizedMessage().contains("555"));
    }

    /**
     * Tests that a phone number with dots (XXX.XXX.XXXX) is detected
     * and flagged as PII_PHONE.
     */
    @Test
    void apply_phoneWithDots_flaggedAndRedacted() {
        GuardrailResult result = guardrailService.apply("Number: 555.123.4567");
        assertTrue(result.isAllowed());
        assertTrue(result.getFlags().contains("PII_PHONE"));
    }

    /**
     * Tests that a message containing multiple PII types (SSN, email, phone)
     * has all types detected and flagged, and all sensitive data is redacted
     * in the sanitized message.
     */
    @Test
    void apply_multiplePiiTypes_allFlagged() {
        GuardrailResult result = guardrailService.apply(
                "SSN 123-45-6789, email test@test.com, phone 555-123-4567");
        assertTrue(result.isAllowed());
        assertTrue(result.getFlags().contains("PII_SSN"));
        assertTrue(result.getFlags().contains("PII_EMAIL"));
        assertTrue(result.getFlags().contains("PII_PHONE"));
        assertFalse(result.getSanitizedMessage().contains("123-45-6789"));
        assertFalse(result.getSanitizedMessage().contains("test@test.com"));
    }

    /**
     * Tests that messages containing off-topic keywords (violence, hack, scam,
     * drugs, steal, phishing) are blocked and flagged as OFF_TOPIC.
     *
     * @param message the off-topic message to test
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "I want to commit violence",
            "How to hack the system",
            "This is a scam",
            "Tell me about drugs",
            "I want to steal something",
            "This is phishing"
    })
    void apply_offTopicKeyword_blocked(String message) {
        GuardrailResult result = guardrailService.apply(message);
        assertFalse(result.isAllowed());
        assertTrue(result.getFlags().contains("OFF_TOPIC"));
    }

    /**
     * Tests that off-topic detection takes priority over PII sanitization.
     * When a message is both off-topic and contains PII, it is blocked and
     * the original unsanitized message is returned.
     */
    @Test
    void apply_offTopicBlocksBeforePiiSanitization() {
        // Off-topic should short-circuit — returns original message, not sanitized
        GuardrailResult result = guardrailService.apply("I want to hack SSN 123-45-6789");
        assertFalse(result.isAllowed());
        assertTrue(result.getFlags().contains("OFF_TOPIC"));
        // The original unsanitized message is returned when blocked
        assertEquals("I want to hack SSN 123-45-6789", result.getSanitizedMessage());
    }

    /**
     * Tests that the sanitize method returns the original message unchanged
     * when no PII patterns are found.
     */
    @Test
    void sanitize_noMatchesReturnsOriginal() {
        assertEquals("Hello world", guardrailService.sanitize("Hello world"));
    }

    /**
     * Tests that the sanitize method correctly redacts all PII patterns (SSN,
     * credit card, email, phone) in a single message containing all types.
     */
    @Test
    void sanitize_allPatterns() {
        String input = "SSN: 111-22-3333, CC: 4444 5555 6666 7777, email: a@b.com, phone: 555-123-4567";
        String result = guardrailService.sanitize(input);
        assertTrue(result.contains("[REDACTED-SSN]"));
        assertTrue(result.contains("[REDACTED-CC]"));
        assertTrue(result.contains("[REDACTED-EMAIL]"));
        assertTrue(result.contains("[REDACTED-PHONE]"));
    }
}
