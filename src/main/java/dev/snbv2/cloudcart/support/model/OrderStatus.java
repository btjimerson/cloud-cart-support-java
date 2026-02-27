package dev.snbv2.cloudcart.support.model;

/**
 * Enumeration representing the possible statuses of an {@link Order} throughout its lifecycle.
 *
 * <p>Each constant is associated with a lowercase string value suitable for serialization
 * and external representation. The {@link #fromValue(String)} factory method supports
 * case-insensitive lookup from a string value.</p>
 */
public enum OrderStatus {
    /** The order has been placed but not yet confirmed. */
    PENDING("pending"),
    /** The order has been confirmed and is being prepared. */
    CONFIRMED("confirmed"),
    /** The order has been shipped and is in transit. */
    SHIPPED("shipped"),
    /** The order has been delivered to the customer. */
    DELIVERED("delivered"),
    /** The order has been cancelled. */
    CANCELLED("cancelled"),
    /** The order has been returned by the customer. */
    RETURNED("returned"),
    /** A return has been requested for this order but not yet processed. */
    RETURN_REQUESTED("return_requested");

    private final String value;

    /**
     * Constructs an {@code OrderStatus} with the given string value.
     *
     * @param value the lowercase string representation of this status
     */
    OrderStatus(String value) {
        this.value = value;
    }

    /**
     * Returns the lowercase string value associated with this status.
     *
     * @return the string value of this status
     */
    public String getValue() {
        return value;
    }

    /**
     * Returns the {@code OrderStatus} constant matching the given string value,
     * using case-insensitive comparison.
     *
     * @param value the string value to look up
     * @return the matching {@code OrderStatus}
     * @throws IllegalArgumentException if no matching status is found
     */
    public static OrderStatus fromValue(String value) {
        for (OrderStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown order status: " + value);
    }
}
