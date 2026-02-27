package dev.snbv2.cloudcart.orders.repository;

import dev.snbv2.cloudcart.orders.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, String> {

    List<Order> findByCustomerId(String customerId);
}
