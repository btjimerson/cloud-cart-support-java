package dev.snbv2.cloudcart.support.service.tools;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the {@link CustomerToolsService}.
 * Verifies customer operations against seeded data including retrieving customer
 * details, checking loyalty points and tier information, adding interaction notes,
 * and issuing credits. Tests cover both success paths and error handling for
 * non-existent customers.
 */
@SpringBootTest
@TestPropertySource(properties = {"spring.ai.anthropic.api-key=test-key"})
class CustomerToolsServiceTest {

    @Autowired
    private CustomerToolsService customerToolsService;

    /**
     * Tests that retrieving an existing customer returns complete details including
     * customer ID, name, email, loyalty points, tier, preferences, and notes.
     */
    @Test
    @SuppressWarnings("unchecked")
    void getCustomer_existingCustomer_returnsDetails() {
        Map<String, Object> result = customerToolsService.getCustomer("CUST-001");
        assertEquals("CUST-001", result.get("customer_id"));
        assertNotNull(result.get("name"));
        assertNotNull(result.get("email"));
        assertNotNull(result.get("loyalty_points"));
        assertNotNull(result.get("tier"));
        assertInstanceOf(List.class, result.get("preferences"));
        assertInstanceOf(List.class, result.get("notes"));
    }

    /**
     * Tests that retrieving a non-existent customer returns an error map.
     */
    @Test
    void getCustomer_nonExistent_returnsError() {
        Map<String, Object> result = customerToolsService.getCustomer("CUST-FAKE");
        assertTrue(result.containsKey("error"));
    }

    /**
     * Tests that retrieving loyalty points for an existing customer returns
     * the customer ID, current points, current tier, next tier, and points
     * needed to reach the next tier.
     */
    @Test
    void getLoyaltyPoints_existingCustomer_returnsPointsAndTier() {
        Map<String, Object> result = customerToolsService.getLoyaltyPoints("CUST-001");
        assertEquals("CUST-001", result.get("customer_id"));
        assertNotNull(result.get("loyalty_points"));
        assertNotNull(result.get("tier"));
        assertNotNull(result.get("next_tier"));
        assertNotNull(result.get("points_to_next_tier"));
    }

    /**
     * Tests that retrieving loyalty points for a non-existent customer returns an error.
     */
    @Test
    void getLoyaltyPoints_nonExistent_returnsError() {
        Map<String, Object> result = customerToolsService.getLoyaltyPoints("CUST-FAKE");
        assertTrue(result.containsKey("error"));
    }

    /**
     * Tests that adding an interaction note to an existing customer succeeds,
     * returns a note ID, and the note subsequently appears in the customer's
     * notes list.
     */
    @Test
    void addInteractionNote_existingCustomer_addsNote() {
        Map<String, Object> result = customerToolsService.addInteractionNote("CUST-001", "Test interaction note");
        assertEquals(true, result.get("success"));
        assertNotNull(result.get("note_id"));

        // Verify note appears in customer info
        Map<String, Object> customer = customerToolsService.getCustomer("CUST-001");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> notes = (List<Map<String, Object>>) customer.get("notes");
        assertTrue(notes.stream().anyMatch(n -> "Test interaction note".equals(n.get("note"))));
    }

    /**
     * Tests that adding an interaction note for a non-existent customer returns an error.
     */
    @Test
    void addInteractionNote_nonExistentCustomer_returnsError() {
        Map<String, Object> result = customerToolsService.addInteractionNote("CUST-FAKE", "note");
        assertTrue(result.containsKey("error"));
    }

    /**
     * Tests that issuing a credit to an existing customer succeeds, returns the
     * correct amount and reason, generates a credit ID, and includes a formatted
     * message with the dollar amount.
     */
    @Test
    void issueCredit_existingCustomer_createsCredit() {
        Map<String, Object> result = customerToolsService.issueCredit("CUST-001", 25.00, "Service recovery");
        assertEquals(true, result.get("success"));
        assertEquals(25.00, result.get("amount"));
        assertEquals("Service recovery", result.get("reason"));
        assertNotNull(result.get("credit_id"));
        assertTrue(((String) result.get("message")).contains("$25.00"));
    }

    /**
     * Tests that issuing a credit to a non-existent customer returns an error.
     */
    @Test
    void issueCredit_nonExistentCustomer_returnsError() {
        Map<String, Object> result = customerToolsService.issueCredit("CUST-FAKE", 10.0, "reason");
        assertTrue(result.containsKey("error"));
    }
}
