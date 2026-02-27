package dev.snbv2.cloudcart.support.service.tools;

import dev.snbv2.cloudcart.support.model.Escalation;
import dev.snbv2.cloudcart.support.model.SupportTicket;
import dev.snbv2.cloudcart.support.repository.EscalationRepository;
import dev.snbv2.cloudcart.support.repository.SupportTicketRepository;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service providing notification tool implementations for agent interactions.
 * Supports sending mock emails and SMS messages, creating support tickets, and
 * escalating issues to supervisors. Email and SMS operations are simulated (logged
 * but not actually sent), while support tickets and escalations are persisted to
 * the database. All methods return results as {@link Map} structures suitable for
 * serialization in agent tool call responses.
 */
@Service
@CommonsLog
public class NotificationToolsService {

    private final SupportTicketRepository supportTicketRepository;
    private final EscalationRepository escalationRepository;

    /**
     * Constructs a new {@code NotificationToolsService} with the required repositories.
     *
     * @param supportTicketRepository the repository for persisting support tickets
     * @param escalationRepository    the repository for persisting escalation records
     */
    public NotificationToolsService(SupportTicketRepository supportTicketRepository,
                                    EscalationRepository escalationRepository) {
        this.supportTicketRepository = supportTicketRepository;
        this.escalationRepository = escalationRepository;
    }

    /**
     * Sends a mock email to the specified recipient. The email is logged but not
     * actually transmitted. A unique message ID is generated for tracking purposes.
     *
     * @param to      the email recipient address
     * @param subject the email subject line
     * @param body    the email body content
     * @return a map containing the success status, generated message ID, recipient, subject,
     *         and a confirmation message
     */
    public Map<String, Object> sendEmail(String to, String subject, String body) {
        // Mock email sending
        log.info(String.format("Sending email to %s with subject: %s", to, subject));
        return Map.of(
                "success", true,
                "message_id", "EMAIL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                "to", to,
                "subject", subject,
                "message", "Email sent successfully"
        );
    }

    /**
     * Sends a mock SMS to the specified phone number. The SMS is logged but not
     * actually transmitted. A unique message ID is generated for tracking purposes.
     *
     * @param to      the SMS recipient phone number
     * @param message the SMS message content
     * @return a map containing the success status, generated message ID, recipient,
     *         and a confirmation message
     */
    public Map<String, Object> sendSms(String to, String message) {
        // Mock SMS sending
        log.info(String.format("Sending SMS to %s: %s", to, message));
        return Map.of(
                "success", true,
                "message_id", "SMS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                "to", to,
                "message", "SMS sent successfully"
        );
    }

    /**
     * Creates a support ticket for the specified customer and persists it to the database.
     * The ticket is created with an "open" status and the current timestamp.
     *
     * @param customerId  the unique identifier of the customer
     * @param subject     the subject of the support ticket
     * @param description a detailed description of the issue
     * @param priority    the priority level (e.g., "low", "medium", "high"); defaults to
     *                    "medium" if {@code null}
     * @return a map containing the success status, ticket ID, customer ID, subject, priority,
     *         status, and a confirmation message
     */
    public Map<String, Object> createSupportTicket(String customerId, String subject,
                                                    String description, String priority) {
        SupportTicket ticket = new SupportTicket();
        ticket.setCustomerId(customerId);
        ticket.setSubject(subject);
        ticket.setDescription(description);
        ticket.setPriority(priority != null ? priority : "medium");
        ticket.setStatus("open");
        ticket.setCreatedAt(Instant.now());
        supportTicketRepository.save(ticket);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("ticket_id", ticket.getId());
        result.put("customer_id", customerId);
        result.put("subject", subject);
        result.put("priority", ticket.getPriority());
        result.put("status", "open");
        result.put("message", "Support ticket created successfully");
        return result;
    }

    /**
     * Escalates a customer issue to a supervisor by creating an escalation record
     * and persisting it to the database. The escalation is created with a "pending"
     * status and the current timestamp.
     *
     * @param customerId the unique identifier of the customer whose issue is being escalated
     * @param reason     the reason for escalating to a supervisor
     * @return a map containing the success status, escalation ID, customer ID, reason,
     *         status, and an informational message about the expected response time
     */
    public Map<String, Object> escalateToSupervisor(String customerId, String reason) {
        Escalation escalation = new Escalation();
        escalation.setCustomerId(customerId);
        escalation.setReason(reason);
        escalation.setStatus("pending");
        escalation.setCreatedAt(Instant.now());
        escalationRepository.save(escalation);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("escalation_id", escalation.getId());
        result.put("customer_id", customerId);
        result.put("reason", reason);
        result.put("status", "pending");
        result.put("message", "Issue has been escalated to a supervisor. They will review and respond within 24 hours.");
        return result;
    }
}
