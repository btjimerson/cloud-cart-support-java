package dev.snbv2.cloudcart.support.service;

import dev.snbv2.cloudcart.support.model.GuardrailResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Input safety service that screens user messages for personally identifiable information (PII)
 * and off-topic content. Uses compiled regex patterns to detect SSNs, credit card numbers,
 * email addresses, and phone numbers. Maintains a static list of off-topic keywords to
 * block inappropriate or dangerous content. PII is redacted from allowed messages while
 * off-topic messages are rejected outright.
 */
@Service
public class GuardrailService {

    private static final Pattern SSN_PATTERN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern CC_PATTERN = Pattern.compile("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\b(\\(\\d{3}\\)\\s*|\\d{3}[-.]?)\\d{3}[-.]?\\d{4}\\b");

    private static final List<String> OFF_TOPIC_KEYWORDS = List.of(
            "violence", "weapon", "drug", "illegal", "hack", "exploit",
            "kill", "harm", "steal", "fraud", "scam", "phishing",
            "porn", "abuse", "threat"
    );

    /**
     * Applies guardrail checks to the given user message. Detects PII patterns (SSN,
     * credit card, email, phone) and flags them. Checks for off-topic keywords and
     * immediately rejects the message if any are found. For allowed messages, PII is
     * sanitized (redacted) from the message content.
     *
     * @param message the raw user input message to evaluate
     * @return a {@link GuardrailResult} indicating whether the message is allowed,
     *         any flags raised, and the sanitized message content
     */
    public GuardrailResult apply(String message) {
        List<String> flags = new ArrayList<>();

        // Check for PII
        if (SSN_PATTERN.matcher(message).find()) flags.add("PII_SSN");
        if (CC_PATTERN.matcher(message).find()) flags.add("PII_CREDIT_CARD");
        if (EMAIL_PATTERN.matcher(message).find()) flags.add("PII_EMAIL");
        if (PHONE_PATTERN.matcher(message).find()) flags.add("PII_PHONE");

        // Check for off-topic content
        String lower = message.toLowerCase();
        for (String keyword : OFF_TOPIC_KEYWORDS) {
            if (lower.contains(keyword)) {
                flags.add("OFF_TOPIC");
                return new GuardrailResult(false, flags, message);
            }
        }

        // Sanitize PII from message
        String sanitized = sanitize(message);

        return new GuardrailResult(true, flags, sanitized);
    }

    /**
     * Sanitizes the given text by replacing all detected PII patterns with redaction
     * placeholders. SSNs are replaced with {@code [REDACTED-SSN]}, credit card numbers
     * with {@code [REDACTED-CC]}, email addresses with {@code [REDACTED-EMAIL]}, and
     * phone numbers with {@code [REDACTED-PHONE]}.
     *
     * @param text the text to sanitize
     * @return the sanitized text with all PII patterns replaced by redaction placeholders
     */
    public String sanitize(String text) {
        String result = text;
        result = SSN_PATTERN.matcher(result).replaceAll("[REDACTED-SSN]");
        result = CC_PATTERN.matcher(result).replaceAll("[REDACTED-CC]");
        result = EMAIL_PATTERN.matcher(result).replaceAll("[REDACTED-EMAIL]");
        result = PHONE_PATTERN.matcher(result).replaceAll("[REDACTED-PHONE]");
        return result;
    }
}
