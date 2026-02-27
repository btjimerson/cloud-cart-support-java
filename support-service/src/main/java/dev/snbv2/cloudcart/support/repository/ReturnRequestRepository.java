package dev.snbv2.cloudcart.support.repository;

import dev.snbv2.cloudcart.support.model.ReturnRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Spring Data JPA repository for {@link ReturnRequest} entities. Provides standard CRUD
 * operations with {@link Long} as the primary key type, plus a custom query method for
 * finding return requests by order ID.
 */
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    /**
     * Finds all return requests associated with the specified order.
     *
     * @param orderId the unique identifier of the order
     * @return a list of return requests for the given order ID
     */
    List<ReturnRequest> findByOrderId(String orderId);
}
