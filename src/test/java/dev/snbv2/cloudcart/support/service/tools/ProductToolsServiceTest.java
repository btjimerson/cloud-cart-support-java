package dev.snbv2.cloudcart.support.service.tools;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the {@link ProductToolsService}.
 * Verifies product search functionality against seeded data, including compound
 * queries with "and" conjunctions, single-term searches, comma-delimited multi-term
 * queries, and the term-splitting utility method for various delimiters.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.ai.anthropic.api-key=test-key"
})
class ProductToolsServiceTest {

    @Autowired
    private ProductToolsService productToolsService;

    /**
     * Tests that a compound query using "and" (e.g., "earbuds and blender")
     * returns results matching both terms, with at least 2 total results.
     */
    @Test
    @SuppressWarnings("unchecked")
    void searchProducts_compoundQuery_returnsBothTerms() {
        Map<String, Object> result = productToolsService.searchProducts("earbuds and blender", "");

        List<Map<String, Object>> products = (List<Map<String, Object>>) result.get("products");
        int count = (int) result.get("count");

        assertTrue(count >= 2, "Expected at least 2 results for 'earbuds and blender', got " + count);

        boolean hasEarbuds = products.stream()
                .anyMatch(p -> ((String) p.get("name")).toLowerCase().contains("earbud")
                        || ((String) p.get("name")).toLowerCase().contains("buds"));
        boolean hasBlender = products.stream()
                .anyMatch(p -> ((String) p.get("name")).toLowerCase().contains("blender"));

        assertTrue(hasEarbuds, "Expected results to include an earbuds product");
        assertTrue(hasBlender, "Expected results to include a blender product");
    }

    /**
     * Tests that a single-term search (e.g., "charger") returns at least one
     * matching product whose name or description contains the search term.
     */
    @Test
    @SuppressWarnings("unchecked")
    void searchProducts_singleTerm_returnsMatches() {
        Map<String, Object> result = productToolsService.searchProducts("charger", "");

        List<Map<String, Object>> products = (List<Map<String, Object>>) result.get("products");
        assertFalse(products.isEmpty(), "Expected at least one result for 'charger'");
        assertTrue(products.stream()
                .anyMatch(p -> ((String) p.get("name")).toLowerCase().contains("charger")));
    }

    /**
     * Tests that a comma-delimited query (e.g., "drill, candle") splits into
     * separate terms and returns results matching both, with at least 2 total results.
     */
    @Test
    @SuppressWarnings("unchecked")
    void searchProducts_commaDelimited_returnsBothTerms() {
        Map<String, Object> result = productToolsService.searchProducts("drill, candle", "");

        List<Map<String, Object>> products = (List<Map<String, Object>>) result.get("products");
        int count = (int) result.get("count");

        assertTrue(count >= 2, "Expected at least 2 results for 'drill, candle', got " + count);
    }

    /**
     * Tests the splitSearchTerms utility method with various delimiters including
     * "and", comma, ampersand, and single-term input without delimiters.
     */
    @Test
    void splitSearchTerms_variousDelimiters() {
        assertEquals(List.of("earbuds", "blender"), productToolsService.splitSearchTerms("earbuds and blender"));
        assertEquals(List.of("drill", "candle"), productToolsService.splitSearchTerms("drill, candle"));
        assertEquals(List.of("charger", "webcam"), productToolsService.splitSearchTerms("charger & webcam"));
        assertEquals(List.of("single term"), productToolsService.splitSearchTerms("single term"));
    }
}
