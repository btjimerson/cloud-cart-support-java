# Cloud Cart Support

A distributed multi-agent AI customer service system built with Spring Boot, Spring AI, and the Model Context Protocol (MCP). Uses Claude to intelligently route and handle customer inquiries across specialized domain agents backed by independent microservices.

## Table of Contents

- [Background](#background)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Install](#install)
- [Usage](#usage)
- [API](#api)
- [Maintainers](#maintainers)
- [License](#license)

## Background

Cloud Cart Support demonstrates a distributed multi-agent architecture for AI-powered customer service. A router agent classifies incoming customer messages by intent and delegates them to the appropriate specialist agent. Each agent discovers and calls domain-specific tools exposed by independent MCP server microservices over Streamable HTTP transport.

Key features:

- **Intent-based routing** -- A router agent classifies messages and hands off to specialist agents with full conversation context.
- **Guardrails** -- Input screening detects and redacts PII (SSN, credit card, email, phone) and blocks off-topic content before it reaches any agent.
- **MCP tool discovery** -- Agents call tools exposed by remote MCP server services, discovered automatically via the Spring AI MCP client.
- **Microservice architecture** -- Each domain (catalog, orders, customers, notifications) runs as an independent Spring Boot service with its own database.
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
| **Catalog Service** | MCP server exposing product search, details, availability, and recommendation tools |
| **Orders Service** | MCP server exposing order status, tracking, cancellation, and return tools |
| **Customers Service** | MCP server exposing customer info, loyalty points, notes, and credit tools |
| **Notifications Service** | MCP server exposing email, SMS, ticket, and escalation tools |
| **Anthropic Claude API** | LLM backing all agent reasoning and tool use |

## Project Structure

This is a Maven multi-module monorepo:

```
cloud-cart-support/
├── pom.xml                    # Parent aggregator POM
├── docker-compose.yaml        # Run all services together
├── support-service/           # Orchestrator (agents, routing, frontend) - port 8080
├── catalog-service/           # MCP server: product tools - port 8081
├── orders-service/            # MCP server: order tools - port 8082
├── customers-service/         # MCP server: customer tools - port 8083
└── notifications-service/     # MCP server: notification tools - port 8084
```

Each MCP server service owns its data and exposes tools via Streamable HTTP transport using `spring-ai-starter-mcp-server-webflux`. The orchestrator (`support-service`) connects to all MCP servers as a client and routes discovered tools to the appropriate agents.

## Prerequisites

- Java 21
- Maven (or use the included Maven wrapper)
- Docker and Docker Compose (for containerized deployment)
- An [Anthropic API key](https://console.anthropic.com/)

## Install

Clone the repository and build all modules:

```sh
git clone <repository-url>
cd cloud-cart-support
./mvnw clean install
```

## Usage

### Running with Docker Compose

The simplest way to run all services together:

```sh
export ANTHROPIC_API_KEY=your-api-key
docker compose up --build
```

### Running locally

Start each MCP server service, then start the orchestrator:

```sh
export ANTHROPIC_API_KEY=your-api-key

# Start MCP server services (each in a separate terminal)
./mvnw -pl catalog-service spring-boot:run
./mvnw -pl orders-service spring-boot:run
./mvnw -pl customers-service spring-boot:run
./mvnw -pl notifications-service spring-boot:run

# Start the orchestrator
./mvnw -pl support-service spring-boot:run
```

The orchestrator starts on `http://localhost:8080`. Open the web UI in a browser to chat with the support agents, or use the REST API directly.

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
