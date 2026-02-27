package dev.snbv2.cloudcart.support.repository;

import dev.snbv2.cloudcart.support.model.Escalation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Spring Data JPA repository for {@link Escalation} entities. Provides standard CRUD
 * operations with {@link Long} as the primary key type, plus a custom query method for
 * finding escalations by customer ID.
 */
public interface EscalationRepository extends JpaRepository<Escalation, Long> {

    /**
     * Finds all escalation records for the specified customer.
     *
     * @param customerId the unique identifier of the customer
     * @return a list of escalations associated with the given customer ID
     */
    List<Escalation> findByCustomerId(String customerId);
}
