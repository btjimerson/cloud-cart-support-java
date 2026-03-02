package dev.snbv2.cloudcart.orders.repository;

import dev.snbv2.cloudcart.orders.model.ReturnRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Spring Data JPA repository for {@link ReturnRequest} entities.
 *
 * <p>Supports lookup of return requests by order ID.</p>
 */
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    List<ReturnRequest> findByOrderId(String orderId);
}
