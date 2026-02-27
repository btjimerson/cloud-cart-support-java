package dev.snbv2.cloudcart.support.service.tools;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the {@link NotificationToolsService}.
 * Verifies notification operations including sending emails, sending SMS messages,
 * creating support tickets with explicit and default priorities, and escalating
 * issues to a supervisor. Each test validates that the returned response map
 * contains the expected fields and values.
 */
@SpringBootTest
@TestPropertySource(properties = {"spring.ai.anthropic.api-key=test-key"})
class NotificationToolsServiceTest {

    @Autowired
    private NotificationToolsService notificationToolsService;

    /**
     * Tests that sending an email returns a success response with the recipient
     * address, subject line, and a message ID prefixed with "EMAIL-".
     */
    @Test
    void sendEmail_returnsSuccess() {
        Map<String, Object> result = notificationToolsService.sendEmail(
                "user@example.com", "Test Subject", "Test body");

        assertEquals(true, result.get("success"));
        assertEquals("user@example.com", result.get("to"));
        assertEquals("Test Subject", result.get("subject"));
        assertTrue(((String) result.get("message_id")).startsWith("EMAIL-"));
    }

    /**
     * Tests that sending an SMS returns a success response with the recipient
     * phone number and a message ID prefixed with "SMS-".
     */
    @Test
    void sendSms_returnsSuccess() {
        Map<String, Object> result = notificationToolsService.sendSms("555-123-4567", "Test SMS");

        assertEquals(true, result.get("success"));
        assertEquals("555-123-4567", result.get("to"));
        assertTrue(((String) result.get("message_id")).startsWith("SMS-"));
    }

    /**
     * Tests that creating a support ticket returns a success response with the
     * customer ID, subject, specified priority, "open" status, and a generated ticket ID.
     */
    @Test
    void createSupportTicket_createsAndReturnsTicket() {
        Map<String, Object> result = notificationToolsService.createSupportTicket(
                "CUST-001", "Test Ticket", "Detailed description", "high");

        assertEquals(true, result.get("success"));
        assertEquals("CUST-001", result.get("customer_id"));
        assertEquals("Test Ticket", result.get("subject"));
        assertEquals("high", result.get("priority"));
        assertEquals("open", result.get("status"));
        assertNotNull(result.get("ticket_id"));
    }

    /**
     * Tests that creating a support ticket with a null priority defaults
     * to "medium" priority.
     */
    @Test
    void createSupportTicket_defaultPriority_isMedium() {
        Map<String, Object> result = notificationToolsService.createSupportTicket(
                "CUST-001", "Subject", "Description", null);

        assertEquals("medium", result.get("priority"));
    }

    /**
     * Tests that escalating to a supervisor returns a success response with the
     * customer ID, reason, "pending" status, a generated escalation ID, and a
     * message indicating a 24-hour response time.
     */
    @Test
    void escalateToSupervisor_createsEscalation() {
        Map<String, Object> result = notificationToolsService.escalateToSupervisor(
                "CUST-001", "Customer demands manager");

        assertEquals(true, result.get("success"));
        assertEquals("CUST-001", result.get("customer_id"));
        assertEquals("Customer demands manager", result.get("reason"));
        assertEquals("pending", result.get("status"));
        assertNotNull(result.get("escalation_id"));
        assertTrue(((String) result.get("message")).contains("24 hours"));
    }
}
