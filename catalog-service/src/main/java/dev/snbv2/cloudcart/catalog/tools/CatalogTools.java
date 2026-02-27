package dev.snbv2.cloudcart.catalog.tools;

import dev.snbv2.cloudcart.catalog.model.Product;
import dev.snbv2.cloudcart.catalog.repository.ProductRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CatalogTools {

    private final ProductRepository productRepository;

    public CatalogTools(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Tool(description = "Search the product catalog by keywords and optional category")
    public Map<String, Object> searchProducts(
            @ToolParam(description = "Search query keywords") String query,
            @ToolParam(description = "Optional category to filter by", required = false) @Nullable String category) {
        List<String> terms = splitSearchTerms(query);
        List<Product> results = new ArrayList<>();

        for (String term : terms) {
            List<Product> matches = searchByTerm(term, category);
            for (Product p : matches) {
                if (results.stream().noneMatch(r -> r.getId().equals(p.getId()))) {
                    results.add(p);
                }
            }
        }

        List<Map<String, Object>> products = results.stream().map(this::productToMap).toList();
        return Map.of("products", products, "count", products.size());
    }

    @Tool(description = "Get detailed information about a specific product by ID")
    public Map<String, Object> getProductDetails(
            @ToolParam(description = "The product ID") int productId) {
        return productRepository.findById(productId)
                .map(this::productToMap)
                .orElse(Map.of("error", "Product not found: " + productId));
    }

    @Tool(description = "Check if a product is available and its stock level")
    public Map<String, Object> checkAvailability(
            @ToolParam(description = "The product ID") int productId) {
        return productRepository.findById(productId)
                .map(product -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("product_id", product.getId());
                    result.put("name", product.getName());
                    result.put("in_stock", product.getInStock());
                    result.put("stock_quantity", product.getStockQuantity());

                    String stockStatus;
                    if (!product.getInStock() || product.getStockQuantity() == 0) {
                        stockStatus = "out_of_stock";
                    } else if (product.getStockQuantity() < 10) {
                        stockStatus = "low_stock";
                    } else {
                        stockStatus = "in_stock";
                    }
                    result.put("status", stockStatus);
                    return result;
                })
                .orElse(Map.of("error", "Product not found: " + productId));
    }

    @Tool(description = "Get product recommendations, optionally filtered by category")
    public Map<String, Object> getRecommendations(
            @ToolParam(description = "Optional category to filter recommendations", required = false) @Nullable String category,
            @ToolParam(description = "Maximum number of recommendations (default 3)", required = false) @Nullable Integer limit) {
        int effectiveLimit = (limit != null) ? limit : 3;
        List<Product> products;
        if (category != null && !category.isBlank()) {
            products = productRepository.findByCategoryAndInStockTrue(category);
        } else {
            products = productRepository.findByInStockTrue();
        }

        List<Map<String, Object>> recommendations = products.stream()
                .sorted(Comparator.comparingDouble(Product::getPrice).reversed())
                .limit(effectiveLimit)
                .map(this::productToMap)
                .toList();

        return Map.of("recommendations", recommendations, "count", recommendations.size());
    }

    private List<Product> searchByTerm(String term, String category) {
        String[] words = term.split("\\s+");

        Set<Integer> seenIds = new HashSet<>();
        List<Product> allCandidates = new ArrayList<>();
        for (String word : words) {
            for (String variant : stemVariants(word)) {
                List<Product> matches;
                if (category != null && !category.isBlank()) {
                    matches = productRepository.searchByQueryAndCategory(variant, category);
                } else {
                    matches = productRepository.searchByQuery(variant);
                }
                for (Product p : matches) {
                    if (seenIds.add(p.getId())) {
                        allCandidates.add(p);
                    }
                }
            }
        }

        if (words.length > 1) {
            allCandidates = allCandidates.stream()
                    .filter(p -> {
                        String searchable = (p.getName() + " " + p.getDescription()).toLowerCase();
                        for (String word : words) {
                            boolean anyMatch = stemVariants(word).stream()
                                    .anyMatch(v -> searchable.contains(v.toLowerCase()));
                            if (!anyMatch) return false;
                        }
                        return true;
                    })
                    .collect(Collectors.toList());
        }

        return allCandidates;
    }

    private List<String> stemVariants(String word) {
        List<String> variants = new ArrayList<>();
        variants.add(word);
        if (word.endsWith("s") && word.length() > 2) {
            variants.add(word.substring(0, word.length() - 1));
        } else {
            variants.add(word + "s");
        }
        return variants;
    }

    List<String> splitSearchTerms(String query) {
        String[] parts = query.split("\\s+and\\s+|\\s*,\\s*|\\s*;\\s*|\\s*&\\s*");
        List<String> terms = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                terms.add(trimmed);
            }
        }
        return terms.isEmpty() ? List.of(query) : terms;
    }

    private Map<String, Object> productToMap(Product product) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("product_id", product.getId());
        map.put("name", product.getName());
        map.put("description", product.getDescription());
        map.put("price", product.getPrice());
        map.put("category", product.getCategory());
        map.put("in_stock", product.getInStock());
        map.put("stock_quantity", product.getStockQuantity());
        if (product.getImageUrl() != null && !product.getImageUrl().isBlank()) {
            map.put("image_url", product.getImageUrl());
        }
        return map;
    }
}
