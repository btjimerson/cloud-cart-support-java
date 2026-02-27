package dev.snbv2.cloudcart.support.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link OrderStatus} enum.
 * Verifies that {@code fromValue()} correctly maps string values to enum constants,
 * handles case-insensitive lookups, and throws an exception for invalid values.
 */
class OrderStatusTest {

    /**
     * Tests that fromValue() correctly maps each valid lowercase string value
     * to the corresponding OrderStatus enum constant.
     *
     * @param value        the string value to convert
     * @param expectedName the expected enum constant name
     */
    @ParameterizedTest
    @CsvSource({
            "pending, PENDING",
            "confirmed, CONFIRMED",
            "shipped, SHIPPED",
            "delivered, DELIVERED",
            "cancelled, CANCELLED",
            "returned, RETURNED",
            "return_requested, RETURN_REQUESTED"
    })
    void fromValue_validValues(String value, String expectedName) {
        OrderStatus status = OrderStatus.fromValue(value);
        assertEquals(expectedName, status.name());
        assertEquals(value, status.getValue());
    }

    /**
     * Tests that fromValue() performs case-insensitive matching, so values like
     * "Pending" and "DELIVERED" resolve to the correct enum constants.
     */
    @Test
    void fromValue_caseInsensitive() {
        assertEquals(OrderStatus.PENDING, OrderStatus.fromValue("Pending"));
        assertEquals(OrderStatus.DELIVERED, OrderStatus.fromValue("DELIVERED"));
    }

    /**
     * Tests that fromValue() throws an IllegalArgumentException when given
     * a string that does not match any known order status value.
     */
    @Test
    void fromValue_invalidValue_throws() {
        assertThrows(IllegalArgumentException.class, () -> OrderStatus.fromValue("invalid"));
    }
}
