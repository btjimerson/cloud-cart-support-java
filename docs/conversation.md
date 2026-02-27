This is a strong pedagogical concept. The structural parallel between cloud-cart and agentic-cart is surprisingly clean, and leaning into it would make the comparison obvious to someone already familiar with microservices.

Here's how I see the mapping:

### The natural service-to-service parallel

| Cloud-Cart (Microservices) | Agentic-Cart (Agentic) |
|---|---|
| `catalog/` — owns product data, REST API | `product-tools/` — owns product data, exposes tools |
| `orders/` — owns order data, REST API | `order-tools/` — owns order data, exposes tools |
| `payments/` — processes payments via Stripe | `notification-tools/` — sends emails/SMS, creates tickets |
| `payment-history/` — consumes RabbitMQ events | `customer-tools/` — owns customer data, loyalty, credits |
| `frontend/` — BFF gateway, Thymeleaf UI, `RestTemplate` to backends | `orchestrator/` — AI agents, guardrails, handoff, WebSocket, Thymeleaf UI, `RestClient` to tool servers |

The data ownership pattern is identical: each service owns its tables, has its own H2 instance, seeds its own data, and exposes operations over HTTP. The difference is *who decides what to call and when*.

### What makes the comparison land

In cloud-cart's frontend, the orchestration is **imperative** — the `CheckoutController` calls catalog, then orders, then payments in a hardcoded sequence. The developer wrote that workflow.

In agentic-cart's orchestrator, the orchestration is **declarative** — the agent gets a system prompt and a set of available tools, and Claude decides which tools to call, in what order, based on the user's message. The developer defined the *capabilities*, not the *workflow*.

Same backend services. Same data. Same HTTP calls. Fundamentally different control flow.

### Structural decisions to mirror cloud-cart

To make the parallel visually obvious:

- **No parent POM** — each service gets its own `pom.xml` with `spring-boot-starter-parent`, just like cloud-cart
- **Own Maven wrapper** per service — independent builds
- **Own Dockerfile** per service — same multi-stage build pattern
- **Own `application.properties`** with default (K8s DNS) and `local` profile (localhost + unique port)
- **Same port convention** — all services run on 8080 in containers, different ports locally
- **`manifests/`** directory with a single K8s YAML, same structure as `cloud-cart.yaml`
- **`loadgenerator/`** with Locust scenarios that send chat messages instead of browsing a catalog
- **GitHub Actions** CI/CD with the same build-publish pattern

### Where the architecture intentionally diverges

These are the differences you'd *want* to highlight:

1. **The orchestrator has no hardcoded routes to backends.** Cloud-cart's frontend has `@Value("${endpoint.catalog}")` and calls specific endpoints. The orchestrator instead registers tools with schemas, and the LLM decides what to invoke. The tool URLs are configuration, but the *decision* to call them is AI-driven.

2. **Agents replace controllers as the decision layer.** Cloud-cart has `CatalogController`, `OrderController`. Agentic-cart has `OrderAgent`, `ProductAgent`. Controllers encode business logic; agents encode *intent and capability* and delegate reasoning to the model.

3. **Guardrails and handoffs have no microservices equivalent.** PII detection, off-topic blocking, agent-to-agent transfer with context summarization — these are new concepts that don't map to anything in cloud-cart. They're the "middleware" of agentic systems.

4. **The conversation context replaces session state.** Cloud-cart uses HTTP sessions for the shopping cart. Agentic-cart uses `ConversationContext` with turn history, handoff records, and agent-scoped views. Same purpose (stateful user interaction), different model.

5. **MCP vs REST.** Cloud-cart tool servers could expose plain REST endpoints (matching cloud-cart's pattern), or they could use Spring AI MCP over HTTP/SSE. Using MCP would highlight that agentic systems have a *protocol for tool discovery* that doesn't exist in traditional microservices — the orchestrator can query a tool server for its capabilities at startup rather than having them hardcoded.

### My recommendation

Use **plain REST endpoints** on the tool servers, not MCP. This keeps the tool servers structurally identical to cloud-cart's services — same `@RestController`, same `@GetMapping`/`@PostMapping`, same `RestClient` calls from the orchestrator. The only difference the viewer sees is that in the orchestrator, those REST calls are wrapped in `FunctionCallback` lambdas and given to an LLM instead of being called from a controller. That makes the "same infrastructure, different control plane" message much cleaner than introducing a new protocol.

MCP could be a follow-up ("here's how you'd evolve this with a tool discovery protocol"), but the initial comparison benefits from minimizing surface-area differences.

Want me to restructure the project along these lines?