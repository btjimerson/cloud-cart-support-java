package dev.snbv2.cloudcart.customers.tools;

import dev.snbv2.cloudcart.customers.model.*;
import dev.snbv2.cloudcart.customers.repository.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * MCP tool methods for customer management operations.
 *
 * <p>Provides AI-accessible tools for retrieving customer information,
 * checking loyalty points and tiers, adding interaction notes, and
 * issuing store credits as compensation.</p>
 */
@Service
public class CustomerTools {

    private final CustomerRepository customerRepository;
    private final CustomerNoteRepository customerNoteRepository;
    private final StoreCreditRepository storeCreditRepository;

    public CustomerTools(CustomerRepository customerRepository,
                         CustomerNoteRepository customerNoteRepository,
                         StoreCreditRepository storeCreditRepository) {
        this.customerRepository = customerRepository;
        this.customerNoteRepository = customerNoteRepository;
        this.storeCreditRepository = storeCreditRepository;
    }

    @Tool(description = "Retrieve customer account information")
    public Map<String, Object> getCustomerInfo(
            @ToolParam(description = "The customer ID") String customerId) {
        return customerRepository.findById(customerId)
                .map(customer -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("customer_id", customer.getId());
                    result.put("name", customer.getName());
                    result.put("email", customer.getEmail());
                    result.put("loyalty_points", customer.getLoyaltyPoints());
                    result.put("tier", customer.getTier());
                    result.put("preferences", customer.getPreferences() != null
                            ? Arrays.asList(customer.getPreferences().split(",")) : List.of());

                    List<CustomerNote> notes = customerNoteRepository.findByCustomerId(customerId);
                    result.put("notes", notes.stream().map(n -> Map.of(
                            "note", n.getNote(),
                            "timestamp", n.getTimestamp().toString()
                    )).toList());

                    return result;
                })
                .orElse(Map.of("error", "Customer not found: " + customerId));
    }

    @Tool(description = "Get loyalty points balance and tier information for a customer")
    public Map<String, Object> getLoyaltyPoints(
            @ToolParam(description = "The customer ID") String customerId) {
        return customerRepository.findById(customerId)
                .map(customer -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("customer_id", customer.getId());
                    result.put("name", customer.getName());
                    result.put("loyalty_points", customer.getLoyaltyPoints());
                    result.put("tier", customer.getTier());

                    int points = customer.getLoyaltyPoints();
                    if (points < 1000) {
                        result.put("next_tier", "Silver");
                        result.put("points_to_next_tier", 1000 - points);
                    } else if (points < 3000) {
                        result.put("next_tier", "Gold");
                        result.put("points_to_next_tier", 3000 - points);
                    } else if (points < 5000) {
                        result.put("next_tier", "Platinum");
                        result.put("points_to_next_tier", 5000 - points);
                    } else {
                        result.put("next_tier", "Maximum tier reached");
                        result.put("points_to_next_tier", 0);
                    }
                    return result;
                })
                .orElse(Map.of("error", "Customer not found: " + customerId));
    }

    @Tool(description = "Add an interaction note to a customer's record")
    public Map<String, Object> addInteractionNote(
            @ToolParam(description = "The customer ID") String customerId,
            @ToolParam(description = "The interaction note text") String note) {
        if (!customerRepository.existsById(customerId)) {
            return Map.of("error", "Customer not found: " + customerId);
        }

        CustomerNote cn = new CustomerNote();
        cn.setCustomerId(customerId);
        cn.setNote(note);
        cn.setTimestamp(Instant.now());
        customerNoteRepository.save(cn);

        return Map.of(
                "success", true,
                "customer_id", customerId,
                "note_id", cn.getId(),
                "message", "Note added successfully"
        );
    }

    @Tool(description = "Issue store credit to a customer as compensation")
    public Map<String, Object> issueCredit(
            @ToolParam(description = "The customer ID") String customerId,
            @ToolParam(description = "Credit amount in dollars") double amount,
            @ToolParam(description = "Reason for issuing the credit") String reason) {
        if (!customerRepository.existsById(customerId)) {
            return Map.of("error", "Customer not found: " + customerId);
        }

        StoreCredit credit = new StoreCredit();
        credit.setCustomerId(customerId);
        credit.setAmount(amount);
        credit.setReason(reason);
        credit.setCreatedAt(Instant.now());
        storeCreditRepository.save(credit);

        return Map.of(
                "success", true,
                "customer_id", customerId,
                "credit_id", credit.getId(),
                "amount", amount,
                "reason", reason,
                "message", String.format("$%.2f store credit issued successfully", amount)
        );
    }
}
