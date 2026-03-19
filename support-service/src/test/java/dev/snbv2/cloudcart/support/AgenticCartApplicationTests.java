package dev.snbv2.cloudcart.support;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "kagent.a2a.base-url=http://localhost:8083",
    "kagent.a2a.namespace=kagent"
})
class AgenticCartApplicationTests {

    @Test
    void contextLoads() {
    }
}
