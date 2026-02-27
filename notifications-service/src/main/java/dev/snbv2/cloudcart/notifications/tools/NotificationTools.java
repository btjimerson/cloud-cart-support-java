package dev.snbv2.cloudcart.notifications.tools;

import dev.snbv2.cloudcart.notifications.model.Escalation;
import dev.snbv2.cloudcart.notifications.model.SupportTicket;
import dev.snbv2.cloudcart.notifications.repository.EscalationRepository;
import dev.snbv2.cloudcart.notifications.repository.SupportTicketRepository;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@CommonsLog
public class NotificationTools {

    private final SupportTicketRepository supportTicketRepository;
    private final EscalationRepository escalationRepository;

    public NotificationTools(SupportTicketRepository supportTicketRepository,
                             EscalationRepository escalationRepository) {
        this.supportTicketRepository = supportTicketRepository;
        this.escalationRepository = escalationRepository;
    }

    @Tool(description = "Send an email notification to the customer")
    public Map<String, Object> sendEmail(
            @ToolParam(description = "Recipient email address") String to,
            @ToolParam(description = "Email subject line") String subject,
            @ToolParam(description = "Email body content") String body) {
        log.info(String.format("Sending email to %s with subject: %s", to, subject));
        return Map.of(
                "success", true,
                "message_id", "EMAIL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                "to", to,
                "subject", subject,
                "message", "Email sent successfully"
        );
    }

    @Tool(description = "Send an SMS notification to the customer")
    public Map<String, Object> sendSms(
            @ToolParam(description = "Recipient phone number") String to,
            @ToolParam(description = "SMS message content") String message) {
        log.info(String.format("Sending SMS to %s: %s", to, message));
        return Map.of(
                "success", true,
                "message_id", "SMS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                "to", to,
                "message", "SMS sent successfully"
        );
    }

    @Tool(description = "Create a support ticket for customer issues")
    public Map<String, Object> createSupportTicket(
            @ToolParam(description = "The customer ID") String customerId,
            @ToolParam(description = "Ticket subject") String subject,
            @ToolParam(description = "Detailed description of the issue") String description,
            @ToolParam(description = "Priority level: low, medium, high", required = false) @Nullable String priority) {
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

    @Tool(description = "Escalate the issue to a supervisor for review")
    public Map<String, Object> escalateToSupervisor(
            @ToolParam(description = "The customer ID") String customerId,
            @ToolParam(description = "Reason for escalation") String reason) {
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
