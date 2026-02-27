# Cloud Cart Support -- Demo Script

This script walks through the key features of Cloud Cart Support using concrete examples against the seeded data. Each section demonstrates a distinct capability of the multi-agent system.

## Prerequisites

Start the application before running the demo:

```sh
export ANTHROPIC_API_KEY=your-api-key
mvn spring-boot:run
```

The app starts on `http://localhost:8080`. You can use the web UI in a browser or `curl` against the REST API. Both are shown below.

---

## 1. Intent-Based Routing

The router agent classifies every incoming message and hands off to the right specialist. Start with a simple greeting to see it respond directly, then ask something domain-specific to trigger a handoff.

**Greeting (handled by router directly):**

```sh
curl -s -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Hi there!", "customer_id": "CUST-001"}' | jq .
```

The response comes from the `router` agent with no handoff.

**Order question (routed to order agent):**

```sh
curl -s -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Where is my order ORD-2024-0003?", "customer_id": "CUST-003"}' | jq .
```

Note the `handoff` field in the response showing the transfer from `router` to `order` with the confidence score and reasoning.

---

## 2. Order Tracking and Management

The order agent has tools to look up orders, track shipments, cancel orders, and send notifications.

### Track a shipped order

Order `ORD-2024-0003` belongs to Emily Rodriguez and is currently shipped with tracking number `1Z999AA10123456786`.

```sh
curl -s -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Can you track order ORD-2024-0003?", "customer_id": "CUST-003"}' | jq .
```

The agent calls `get_order_status` and `track_shipment` tools, returning carrier info, estimated delivery, and last known location.

### Cancel a pending order

Order `ORD-2024-0005` belongs to Amanda Taylor and is in `pending` status -- eligible for cancellation.

```sh
curl -s -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "I need to cancel order ORD-2024-0005", "customer_id": "CUST-005"}' | jq .
```

The agent calls `cancel_order`, which updates the status to `cancelled` and returns the refund amount ($129.44).

### Attempt to cancel a shipped order

Order `ORD-2024-0006` is already shipped. Cancellation should be denied.

```sh
curl -s -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Cancel order ORD-2024-0006 please", "customer_id": "CUST-006"}' | jq .
```

The agent explains that shipped orders cannot be cancelled and suggests alternatives.

---

## 3. Product Search and Recommendations

The product agent searches the catalog of 50 products across 10 categories: electronics, kitchen, sports, beauty, toys, home, pets, office, automotive, and tools.

### Simple search

```sh
curl -s -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Do you have any wireless earbuds?", "customer_id": "CUST-001"}' | jq .
```

Returns matching products with names, prices, descriptions, and availability. The product agent is instructed to display product images using markdown when available.

### Multi-term search

The product search supports compound queries split on "and", commas, and "&".

```sh
curl -s -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "I am looking for a blender and a charger", "customer_id": "CUST-002"}' | jq .
```

The search splits "blender and charger" into separate terms and returns results matching both.

### Out-of-stock handling

Product ID 500 (Logitech C920x HD Pro Webcam) is out of stock with 0 quantity. Ask about webcams to see how the agent handles unavailable items.

```sh
curl -s -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "I need a webcam for video calls", "customer_id": "CUST-009"}' | jq .
```

The agent should note the stock status and may suggest alternatives.

---

## 4. Returns and Refunds

The returns agent checks eligibility, initiates returns, and generates shipping labels. The return policy is embedded in the agent's system prompt: 30-day window, items in original condition, $5.99 return fee for non-defective items.

### Initiate a return on a delivered order

Order `ORD-2024-0001` is delivered to Sarah Johnson (Turtle Beach headset + UGREEN charger).

```sh
curl -s -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "I want to return my order ORD-2024-0001, the headset does not fit well", "customer_id": "CUST-001"}' | jq .
```

The agent calls `check_return_eligibility` and `initiate_return`, then offers to generate a return label.

### Return a non-delivered order (should be denied)

Order `ORD-2024-0003` is still in `shipped` status.

```sh
curl -s -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "I want to return order ORD-2024-0003", "customer_id": "CUST-003"}' | jq .
```

The agent explains the order must be delivered before a return can be initiated.

---

## 5. Complaint Handling and Escalation

The complaint agent uses empathetic language, creates support tickets, issues store credits, and escalates to supervisors. It has automatic escalation keyword detection.

### Standard complaint

```sh
curl -s -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "I received a damaged product in my order and I am very disappointed", "customer_id": "CUST-004"}' | jq .
```

The agent acknowledges frustration, creates a support ticket, and may offer compensation via store credit.

### Escalation trigger

Keywords like "lawyer", "lawsuit", "manager", "supervisor", "unacceptable", and "outrageous" trigger automatic escalation. The agent creates a HIGH priority ticket and escalates to a supervisor.

```sh
curl -s -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "This is unacceptable! I want to speak to a manager immediately. I have been waiting weeks for a resolution.", "customer_id": "CUST-004"}' | jq .
```

Watch for `create_support_ticket` (with high priority) and `escalate_to_supervisor` in the tool calls. The escalation response includes a 24-hour response commitment.

---

## 6. Guardrails -- PII Detection and Content Filtering

The guardrail layer runs before any agent sees the message. It detects and redacts PII, and blocks off-topic content entirely.

### PII redaction

PII is redacted but the message is still processed. The agent never sees the raw sensitive data.

```sh
curl -s -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "My SSN is 123-45-6789 and my card is 4111 1111 1111 1111, can you check my order?", "customer_id": "CUST-001"}' | jq .
```

The SSN becomes `[REDACTED-SSN]` and the credit card becomes `[REDACTED-CC]` before reaching the agent. Email and phone patterns are also redacted.

### Off-topic blocking

Messages containing flagged keywords are rejected outright and never reach an agent.

```sh
curl -s -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "How do I hack into someone account?", "customer_id": "CUST-001"}' | jq .
```

The response comes from the `guardrails` agent with a rejection message. No routing or tool calls occur.

---

## 7. Conversation Context and Multi-Turn Interactions

Conversations maintain full history across turns and agent handoffs. Use the `conversation_id` from a previous response to continue the conversation.

### Multi-turn flow

```sh
# Turn 1: Ask about an order
RESPONSE=$(curl -s -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What is the status of order ORD-2024-0008?", "customer_id": "CUST-008"}')
echo "$RESPONSE" | jq .

# Extract conversation ID
CONV_ID=$(echo "$RESPONSE" | jq -r '.conversation_id')

# Turn 2: Follow up in the same conversation
curl -s -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d "{\"message\": \"Can you also tell me what products were in that order?\", \"conversation_id\": \"$CONV_ID\"}" | jq .
```

The agent remembers the order context from the first turn and can answer follow-up questions without the customer repeating the order ID.

### Inspect conversation history

```sh
curl -s http://localhost:8080/conversations/$CONV_ID | jq .
```

Returns the full conversation including all turns, agent names, handoff records, and metadata.

---

## 8. Customer Loyalty and Account Tools

The complaint and returns agents have access to customer account tools that expose loyalty tiers and store credits.

**Loyalty tiers** are calculated from points: Platinum (5000+), Gold (3000+), Silver (1000+), Bronze (below 1000).

Notable customers for demo:

| Customer | Points | Tier | Notes |
|---|---|---|---|
| CUST-010 Thomas Brown | 5100 | Platinum | Business owner, bulk orders |
| CUST-004 James Wilson | 3200 | Gold | Works from home, electronics |
| CUST-001 Sarah Johnson | 2450 | Silver | VIP since 2023, prefers expedited |
| CUST-008 Robert Anderson | 450 | Bronze | Weekend camper |

```sh
curl -s -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "I have been a loyal customer and I feel like I deserve some compensation for this terrible experience", "customer_id": "CUST-010"}' | jq .
```

The complaint agent retrieves customer info via `get_customer_info`, sees the Platinum tier and 5100 loyalty points, and can issue store credit via `issue_credit` as compensation.

---

## 9. WebSocket Chat UI

Open `http://localhost:8080` in a browser to use the Thymeleaf-based chat interface. Messages are sent over WebSocket for real-time interaction. The same routing, guardrails, and agent handoff logic applies.

This is useful for live demos where you want to show the conversational flow visually rather than through `curl` output.

---

## 10. Actuator Health Check

The application exposes Spring Boot Actuator endpoints for operational monitoring.

```sh
curl -s http://localhost:8080/actuator/health | jq .
```

Available endpoints: `/actuator/health`, `/actuator/info`, `/actuator/metrics`.

---

## Quick Reference: Seed Data

**Order statuses in seed data:**

| Status | Example Orders |
|---|---|
| delivered | ORD-2024-0001, ORD-2024-0002, ORD-2024-0004, ORD-2024-0010 |
| shipped | ORD-2024-0003, ORD-2024-0006, ORD-2024-0008, ORD-2024-0013, ORD-2024-0017 |
| pending | ORD-2024-0005, ORD-2024-0012, ORD-2024-0016, ORD-2024-0020 |
| confirmed | ORD-2024-0007, ORD-2024-0015, ORD-2024-0019 |
| cancelled | ORD-2024-0009, ORD-2024-0018 |
| return_requested | ORD-2024-0011 |
| returned | ORD-2024-0014 |

**Product categories:** electronics, kitchen, sports, beauty, toys, home, pets, office, automotive, tools (50 products total)
