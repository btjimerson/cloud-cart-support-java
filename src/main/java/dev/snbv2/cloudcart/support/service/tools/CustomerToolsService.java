package dev.snbv2.cloudcart.support.service.tools;

import dev.snbv2.cloudcart.support.model.*;
import dev.snbv2.cloudcart.support.repository.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Service providing customer account tool implementations for agent interactions.
 * Supports retrieving customer profiles with notes, querying loyalty points and tier
 * progression, adding interaction notes, and issuing store credits. All methods return
 * results as {@link Map} structures suitable for serialization in agent tool call responses.
 */
@Service
public class CustomerToolsService {

    private final CustomerRepository customerRepository;
    private final CustomerNoteRepository customerNoteRepository;
    private final StoreCreditRepository storeCreditRepository;

    /**
     * Constructs a new {@code CustomerToolsService} with the required repositories.
     *
     * @param customerRepository     the repository for accessing customer data
     * @param customerNoteRepository the repository for persisting customer interaction notes
     * @param storeCreditRepository  the repository for persisting store credits
     */
    public CustomerToolsService(CustomerRepository customerRepository,
                                CustomerNoteRepository customerNoteRepository,
                                StoreCreditRepository storeCreditRepository) {
        this.customerRepository = customerRepository;
        this.customerNoteRepository = customerNoteRepository;
        this.storeCreditRepository = storeCreditRepository;
    }

    /**
     * Retrieves the full profile for a customer, including their personal details,
     * loyalty information, preferences, and interaction notes.
     *
     * @param customerId the unique identifier of the customer
     * @return a map containing the customer profile and notes, or an error entry if the
     *         customer is not found
     */
    public Map<String, Object> getCustomer(String customerId) {
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

    /**
     * Retrieves the loyalty points balance and tier information for a customer,
     * including the next tier name and points required to reach it. Tier thresholds
     * are: Silver at 1000, Gold at 3000, and Platinum at 5000 points.
     *
     * @param customerId the unique identifier of the customer
     * @return a map containing loyalty points, current tier, next tier, and points
     *         to next tier, or an error entry if the customer is not found
     */
    public Map<String, Object> getLoyaltyPoints(String customerId) {
        return customerRepository.findById(customerId)
                .map(customer -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("customer_id", customer.getId());
                    result.put("name", customer.getName());
                    result.put("loyalty_points", customer.getLoyaltyPoints());
                    result.put("tier", customer.getTier());

                    // Calculate points to next tier
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

    /**
     * Adds an interaction note to the specified customer's record. The note is
     * timestamped with the current time.
     *
     * @param customerId the unique identifier of the customer
     * @param note       the interaction note text to add
     * @return a map indicating success with the note ID, or an error entry if the
     *         customer is not found
     */
    public Map<String, Object> addInteractionNote(String customerId, String note) {
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

    /**
     * Issues a store credit to the specified customer for the given amount and reason.
     * The credit is timestamped with the current time.
     *
     * @param customerId the unique identifier of the customer
     * @param amount     the dollar amount of store credit to issue
     * @param reason     the reason for issuing the store credit
     * @return a map indicating success with the credit ID and amount, or an error entry
     *         if the customer is not found
     */
    public Map<String, Object> issueCredit(String customerId, double amount, String reason) {
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
