package dev.snbv2.cloudcart.notifications.repository;

import dev.snbv2.cloudcart.notifications.model.Escalation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Spring Data JPA repository for {@link Escalation} entities.
 *
 * <p>Supports lookup of escalations by customer ID.</p>
 */
public interface EscalationRepository extends JpaRepository<Escalation, Long> {

    List<Escalation> findByCustomerId(String customerId);
}
