# Cloud Cart Support

A multi-agent AI customer service system built with Spring Boot and Spring AI. Uses Claude to intelligently route and handle customer inquiries across specialized domain agents for orders, products, returns, and complaints.

## Table of Contents

- [Background](#background)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Install](#install)
- [Usage](#usage)
- [API](#api)
- [Maintainers](#maintainers)
- [License](#license)

## Background

Cloud Cart Support demonstrates a multi-agent architecture for AI-powered customer service. A router agent classifies incoming customer messages by intent and delegates them to the appropriate specialist agent. Each agent has access to domain-specific tools and can perform actions such as looking up orders, searching products, processing returns, and handling complaints.

Key features:

- **Intent-based routing** -- A router agent classifies messages and hands off to specialist agents with full conversation context.
- **Guardrails** -- Input screening detects and redacts PII (SSN, credit card, email, phone) and blocks off-topic content before it reaches any agent.
- **Tool use** -- Agents call structured tool functions (order lookup, product search, customer management, notifications) backed by a database.
- **WebSocket support** -- Real-time chat interface via WebSocket in addition to REST endpoints.
- **Conversation context** -- Full conversation history, handoff tracking, and metadata are maintained across agent transfers.

## Architecture

![Cloud Cart Support Architecture](images/cloud-cart-support-architecture.png)

The system is composed of:

| Component | Description |
|---|---|
| **Frontend** | Thymeleaf templates with WebSocket client |
| **Guardrails** | PII detection/redaction and off-topic filtering |
| **Router Agent** | Classifies intent and delegates to specialist agents |
| **Order Agent** | Handles order status, tracking, cancellations, and shipping |
| **Product Agent** | Handles product search, recommendations, and availability |
| **Returns Agent** | Handles return requests, refunds, and exchange questions |
| **Complaint Agent** | Handles complaints, escalations, and service issues |
| **Tool Services** | Order, product, customer, and notification tool functions |
| **H2 Database** | In-memory database seeded with sample data |
| **Anthropic Claude API** | LLM backing all agent reasoning and tool use |

## Prerequisites

- Java 21
- Maven
- An [Anthropic API key](https://console.anthropic.com/)

## Install

Clone the repository and build with Maven:

```sh
git clone <repository-url>
cd cloud-cart-support
./mvnw clean install
```

## Usage

Set your Anthropic API key and start the application:

```sh
export ANTHROPIC_API_KEY=your-api-key
./mvnw spring-boot:run
```

The application starts on `http://localhost:8080`.

Open the web UI in a browser to chat with the support agents, or use the REST API directly.

### Example

```sh
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Where is my order?", "customer_id": "C001"}'
```

## API

### `POST /chat`

Send a chat message. The router agent classifies the intent and delegates to the appropriate specialist agent.

**Request body:**

| Field | Type | Required | Description |
|---|---|---|---|
| `message` | string | yes | The customer's message |
| `conversation_id` | string | no | ID of an existing conversation to continue |
| `customer_id` | string | no | Customer ID to associate with the conversation |

**Response:**

```json
{
  "response": "Agent's reply text",
  "conversation_id": "uuid",
  "agent": "order",
  "tool_calls": [],
  "handoff": {
    "from_agent": "router",
    "to_agent": "order",
    "reason": "Intent classified as 'order' (confidence: 0.98)"
  }
}
```

### `GET /conversations/{conversationId}`

Retrieve conversation history including turns, handoffs, and metadata.

### `GET /health`

Health check endpoint.

## Maintainers

[@brianjimerson](https://github.com/brianjimerson)

## License

[Apache-2.0](LICENSE)
