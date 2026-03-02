package dev.snbv2.cloudcart.customers.repository;

import dev.snbv2.cloudcart.customers.model.CustomerNote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Spring Data JPA repository for {@link CustomerNote} entities.
 *
 * <p>Supports lookup of notes by customer ID.</p>
 */
public interface CustomerNoteRepository extends JpaRepository<CustomerNote, Long> {

    List<CustomerNote> findByCustomerId(String customerId);
}
