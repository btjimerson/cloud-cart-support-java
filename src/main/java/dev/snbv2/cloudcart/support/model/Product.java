package dev.snbv2.cloudcart.support.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * JPA entity representing a product in the catalog.
 *
 * <p>Each product has a name, description, price, category, and image URL,
 * along with stock availability information including whether the product
 * is currently in stock and the available stock quantity.</p>
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@ToString
@NoArgsConstructor
public class Product {

    @Id
    private Integer id;
    private String name;
    private String description;
    private Double price;
    private String category;
    private String imageUrl;
    private Boolean inStock;
    private Integer stockQuantity;
}
