package dev.snbv2.cloudcart.support.repository;

import dev.snbv2.cloudcart.support.model.CustomerNote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Spring Data JPA repository for {@link CustomerNote} entities. Provides standard CRUD
 * operations with {@link Long} as the primary key type, plus a custom query method for
 * finding notes by customer ID.
 */
public interface CustomerNoteRepository extends JpaRepository<CustomerNote, Long> {

    /**
     * Finds all interaction notes for the specified customer.
     *
     * @param customerId the unique identifier of the customer
     * @return a list of notes associated with the given customer ID
     */
    List<CustomerNote> findByCustomerId(String customerId);
}
