package dev.snbv2.cloudcart.support.service.tools;

import dev.snbv2.cloudcart.support.model.Product;
import dev.snbv2.cloudcart.support.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service providing product catalog tool implementations for agent interactions.
 * Supports searching products by query and category with basic stemming, retrieving
 * individual product details, checking product availability and stock status, and
 * fetching product recommendations. All methods return results as {@link Map} structures
 * suitable for serialization in agent tool call responses.
 */
@Service
public class ProductToolsService {

    private final ProductRepository productRepository;

    /**
     * Constructs a new {@code ProductToolsService} with the required product repository.
     *
     * @param productRepository the repository for accessing product data
     */
    public ProductToolsService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Searches for products matching the given query, optionally filtered by category.
     * The query is split into individual search terms (handling conjunctions, commas,
     * semicolons, and ampersands), and results are deduplicated across all terms.
     *
     * @param query    the search query string, which may contain multiple terms separated
     *                 by conjunctions or punctuation
     * @param category the product category to filter by, or {@code null}/{@code blank} for all categories
     * @return a map containing a list of matching product maps under the "products" key and the total count
     */
    public Map<String, Object> searchProducts(String query, String category) {
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

    /**
     * Searches for products matching a single search term, optionally filtered by category.
     * Each word in the term is searched independently using stem variants, and for multi-word
     * terms the results are filtered to only include products matching at least one variant
     * of every word.
     *
     * @param term     the single search term to match against product names and descriptions
     * @param category the product category to filter by, or {@code null}/{@code blank} for all categories
     * @return a list of matching {@link Product} entities, deduplicated by product ID
     */
    private List<Product> searchByTerm(String term, String category) {
        String[] words = term.split("\\s+");

        // Search for each word independently and collect all candidate products
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

        // If multi-word term, filter to products matching at least one variant of every word
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

    /**
     * Returns the word plus basic stem variants to handle plurals.
     * For example, "headsets" produces ["headsets", "headset"] and
     * "cask" produces ["cask", "casks"].
     *
     * @param word the word to generate stem variants for
     * @return a list containing the original word and its stem variant
     */
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

    /**
     * Splits a compound search query into individual search terms.
     * Handles conjunctions ("and", "&amp;"), commas, and semicolons as delimiters.
     * Returns the original query as a single-element list if no delimiters are found.
     *
     * @param query the compound search query to split
     * @return a list of individual search terms, trimmed and non-empty
     */
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

    /**
     * Retrieves the details of a single product by its ID.
     *
     * @param productId the unique identifier of the product
     * @return a map containing the product details, or an error entry if the product is not found
     */
    public Map<String, Object> getProduct(int productId) {
        return productRepository.findById(productId)
                .map(this::productToMap)
                .orElse(Map.of("error", "Product not found: " + productId));
    }

    /**
     * Checks the availability and stock status of the specified product. Returns
     * stock quantity and a status string of "in_stock", "low_stock" (fewer than 10 units),
     * or "out_of_stock".
     *
     * @param productId the unique identifier of the product to check
     * @return a map containing availability details, or an error entry if the product is not found
     */
    public Map<String, Object> checkAvailability(int productId) {
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

    /**
     * Retrieves product recommendations, optionally filtered by category. Returns
     * in-stock products sorted by price in descending order (simulating "top rated"),
     * limited to the specified count.
     *
     * @param category the product category to filter by, or {@code null}/{@code blank} for all categories
     * @param limit    the maximum number of recommendations to return
     * @return a map containing a list of recommended product maps under the "recommendations" key
     *         and the total count
     */
    public Map<String, Object> getRecommendations(String category, int limit) {
        List<Product> products;
        if (category != null && !category.isBlank()) {
            products = productRepository.findByCategoryAndInStockTrue(category);
        } else {
            products = productRepository.findByInStockTrue();
        }

        // Return up to limit products, sorted by price (simulate "top rated")
        List<Map<String, Object>> recommendations = products.stream()
                .sorted(Comparator.comparingDouble(Product::getPrice).reversed())
                .limit(limit)
                .map(this::productToMap)
                .toList();

        return Map.of("recommendations", recommendations, "count", recommendations.size());
    }

    /**
     * Converts a {@link Product} entity into a map representation suitable for
     * serialization in agent tool call responses.
     *
     * @param product the product entity to convert
     * @return a map containing the product's fields
     */
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
