package dev.snbv2.cloudcart.catalog.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

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
