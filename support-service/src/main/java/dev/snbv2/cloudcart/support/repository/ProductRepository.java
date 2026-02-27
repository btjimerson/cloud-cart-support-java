package dev.snbv2.cloudcart.support.repository;

import dev.snbv2.cloudcart.support.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

/**
 * Spring Data JPA repository for {@link Product} entities. Provides standard CRUD
 * operations with {@link Integer} as the primary key type, along with custom query
 * methods for searching products by name/description, filtering by category, and
 * finding in-stock products.
 */
public interface ProductRepository extends JpaRepository<Product, Integer> {

    /**
     * Finds all products belonging to the specified category.
     *
     * @param category the category to filter by
     * @return a list of products in the given category
     */
    List<Product> findByCategory(String category);

    /**
     * Searches for products whose name or description contains the given query string
     * (case-insensitive).
     *
     * @param query the search term to match against product names and descriptions
     * @return a list of products matching the query
     */
    @Query("SELECT p FROM Product p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Product> searchByQuery(@Param("query") String query);

    /**
     * Searches for products whose name or description contains the given query string
     * (case-insensitive), filtered to the specified category (case-insensitive).
     *
     * @param query    the search term to match against product names and descriptions
     * @param category the category to filter by
     * @return a list of products matching the query within the specified category
     */
    @Query("SELECT p FROM Product p WHERE (LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%'))) AND LOWER(p.category) = LOWER(:category)")
    List<Product> searchByQueryAndCategory(@Param("query") String query, @Param("category") String category);

    /**
     * Finds all products in the specified category (case-insensitive).
     *
     * @param category the category to filter by
     * @return a list of products in the given category
     */
    @Query("SELECT p FROM Product p WHERE LOWER(p.category) = LOWER(:category)")
    List<Product> findAllByCategory(@Param("category") String category);

    /**
     * Finds all products that are currently in stock.
     *
     * @return a list of products where {@code inStock} is {@code true}
     */
    List<Product> findByInStockTrue();

    /**
     * Finds all products in the specified category that are currently in stock.
     *
     * @param category the category to filter by
     * @return a list of in-stock products in the given category
     */
    List<Product> findByCategoryAndInStockTrue(String category);
}
