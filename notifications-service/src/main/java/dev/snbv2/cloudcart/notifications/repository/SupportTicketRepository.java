package dev.snbv2.cloudcart.notifications.repository;

import dev.snbv2.cloudcart.notifications.model.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    List<SupportTicket> findByCustomerId(String customerId);
}
