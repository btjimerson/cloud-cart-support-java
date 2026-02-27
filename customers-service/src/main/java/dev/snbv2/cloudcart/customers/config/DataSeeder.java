package dev.snbv2.cloudcart.customers.config;

import dev.snbv2.cloudcart.customers.model.*;
import dev.snbv2.cloudcart.customers.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;

@Component
@CommonsLog
public class DataSeeder implements CommandLineRunner {

    private final CustomerRepository customerRepository;
    private final CustomerNoteRepository customerNoteRepository;
    private final ObjectMapper objectMapper;

    public DataSeeder(CustomerRepository customerRepository,
                      CustomerNoteRepository customerNoteRepository,
                      ObjectMapper objectMapper) {
        this.customerRepository = customerRepository;
        this.customerNoteRepository = customerNoteRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        try (InputStream is = new ClassPathResource("seed-data/customers.json").getInputStream()) {
            List<JsonNode> customers = objectMapper.readValue(is, new TypeReference<>() {});
            for (JsonNode node : customers) {
                Customer c = new Customer();
                c.setId(node.get("id").asText());
                c.setName(node.get("name").asText());
                c.setEmail(node.get("email").asText());
                c.setLoyaltyPoints(node.get("loyalty_points").asInt());

                List<String> prefs = objectMapper.convertValue(node.get("preferences"), new TypeReference<>() {});
                c.setPreferences(String.join(",", prefs));

                int points = c.getLoyaltyPoints();
                if (points >= 5000) c.setTier("Platinum");
                else if (points >= 3000) c.setTier("Gold");
                else if (points >= 1000) c.setTier("Silver");
                else c.setTier("Bronze");

                customerRepository.save(c);

                List<String> notes = objectMapper.convertValue(node.get("notes"), new TypeReference<>() {});
                for (String noteText : notes) {
                    CustomerNote note = new CustomerNote();
                    note.setCustomerId(c.getId());
                    note.setNote(noteText);
                    note.setTimestamp(Instant.now());
                    customerNoteRepository.save(note);
                }
            }
            log.info(String.format("Seeded %d customers", customers.size()));
        }
    }
}
