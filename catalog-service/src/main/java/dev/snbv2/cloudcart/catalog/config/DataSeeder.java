package dev.snbv2.cloudcart.catalog.config;

import dev.snbv2.cloudcart.catalog.model.Product;
import dev.snbv2.cloudcart.catalog.repository.ProductRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/**
 * Seeds the product catalog database with initial data on application startup.
 *
 * <p>Reads product definitions from {@code seed-data/products.json} on the classpath
 * and persists them via {@link ProductRepository}.</p>
 */
@Component
@CommonsLog
public class DataSeeder implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper;

    public DataSeeder(ProductRepository productRepository, ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        try (InputStream is = new ClassPathResource("seed-data/products.json").getInputStream()) {
            List<JsonNode> products = objectMapper.readValue(is, new TypeReference<>() {});
            for (JsonNode node : products) {
                Product p = new Product();
                p.setId(node.get("id").asInt());
                p.setName(node.get("name").asText());
                p.setDescription(node.get("description").asText());
                p.setPrice(node.get("price").asDouble());
                p.setCategory(node.get("category").asText());
                p.setImageUrl(node.has("image_url") ? node.get("image_url").asText() : "");
                p.setInStock(node.get("in_stock").asBoolean());
                p.setStockQuantity(node.get("stock_quantity").asInt());
                productRepository.save(p);
            }
            log.info(String.format("Seeded %d products", products.size()));
        }
    }
}
