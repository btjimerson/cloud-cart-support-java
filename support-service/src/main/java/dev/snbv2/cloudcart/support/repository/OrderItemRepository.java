package dev.snbv2.cloudcart.support.repository;

import dev.snbv2.cloudcart.support.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link OrderItem} entities. Provides standard CRUD
 * operations with {@link Long} as the primary key type. No custom query methods are defined.
 */
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
