package dev.snbv2.cloudcart.support.config;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Checks for required environment variables at startup and fails fast with
 * a clear, actionable error message if any are missing.
 *
 * <p>Each entry maps an environment variable to the Spring property it populates.
 * The check passes if either the env var or the Spring property is set (so tests
 * that set properties directly via {@code @TestPropertySource} are not affected).</p>
 *
 * <p>Registered via {@code META-INF/spring.factories} so it runs before the
 * application context is created.</p>
 */
public class RequiredEnvChecker implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    /** Maps environment variable name to the Spring property it feeds. */
    private static final Map<String, String> REQUIRED_VARS = Map.of(
            "ANTHROPIC_API_KEY", "spring.ai.anthropic.api-key"
    );

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment env = event.getEnvironment();
        List<String> missing = new ArrayList<>();

        for (Map.Entry<String, String> entry : REQUIRED_VARS.entrySet()) {
            String envVar = entry.getKey();
            String property = entry.getValue();

            String envValue = env.getProperty(envVar);
            String propValue = env.getProperty(property);

            boolean envSet = envValue != null && !envValue.isBlank();
            boolean propSet = propValue != null && !propValue.isBlank();

            if (!envSet && !propSet) {
                missing.add(envVar);
            }
        }

        if (!missing.isEmpty()) {
            System.err.println();
            System.err.println("=".repeat(70));
            System.err.println(" MISSING REQUIRED ENVIRONMENT VARIABLES");
            System.err.println("=".repeat(70));
            for (String var : missing) {
                System.err.println("  - " + var);
            }
            System.err.println();
            System.err.println(" To fix this:");
            System.err.println("  1. cp .env.example .env");
            System.err.println("  2. Fill in your API key(s) in .env");
            System.err.println("  3. Run: direnv allow   (or: source .env)");
            System.err.println("=".repeat(70));
            System.err.println();
            System.exit(1);
        }
    }
}
