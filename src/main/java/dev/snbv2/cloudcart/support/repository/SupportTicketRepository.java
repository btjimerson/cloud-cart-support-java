package dev.snbv2.cloudcart.support.repository;

import dev.snbv2.cloudcart.support.model.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Spring Data JPA repository for {@link SupportTicket} entities. Provides standard CRUD
 * operations with {@link Long} as the primary key type, plus a custom query method for
 * finding support tickets by customer ID.
 */
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    /**
     * Finds all support tickets filed by the specified customer.
     *
     * @param customerId the unique identifier of the customer
     * @return a list of support tickets associated with the given customer ID
     */
    List<SupportTicket> findByCustomerId(String customerId);
}
