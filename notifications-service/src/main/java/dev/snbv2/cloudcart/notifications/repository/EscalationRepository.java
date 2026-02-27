package dev.snbv2.cloudcart.notifications.repository;

import dev.snbv2.cloudcart.notifications.model.Escalation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EscalationRepository extends JpaRepository<Escalation, Long> {

    List<Escalation> findByCustomerId(String customerId);
}
