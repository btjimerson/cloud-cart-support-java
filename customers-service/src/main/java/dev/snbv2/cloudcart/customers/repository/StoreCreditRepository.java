package dev.snbv2.cloudcart.customers.repository;

import dev.snbv2.cloudcart.customers.model.StoreCredit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StoreCreditRepository extends JpaRepository<StoreCredit, Long> {

    List<StoreCredit> findByCustomerId(String customerId);
}
