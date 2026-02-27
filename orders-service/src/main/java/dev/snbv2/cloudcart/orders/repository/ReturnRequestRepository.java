package dev.snbv2.cloudcart.orders.repository;

import dev.snbv2.cloudcart.orders.model.ReturnRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    List<ReturnRequest> findByOrderId(String orderId);
}
