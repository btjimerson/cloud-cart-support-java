# Agent Gateway Demo Plan

## Purpose

This repo serves two goals:

1. **Agentic vs microservices comparison** -- show how agentic app patterns (agents, orchestrator, guardrails) map to traditional microservice patterns (controllers, service mesh, middleware). See `../cloud-cart` for the microservices equivalent.
2. **Platform replacement walkthrough** -- demonstrate how in-app "plumbing" code can be replaced by Solo.io's Agent Gateway as a platform component in higher environments (staging, production, Kubernetes).

## Demo Structure

The demo steps are organized as a stacked branch chain. Each branch builds on the previous one, and each represents a self-contained, runnable state of the application.

### Branch Strategy

**While the baseline is still evolving**, use stacked branches:

```
main (step 0 -- baseline)
 └── demo/step-1-prompt-guards
      └── demo/step-2-api-keys
           └── demo/step-3-model-config
                └── demo/step-4-rate-limiting
                     └── demo/step-5-observability
```

When `main` changes, rebase the chain forward:

```bash
git rebase main demo/step-1-prompt-guards
git rebase demo/step-1-prompt-guards demo/step-2-api-keys
git rebase demo/step-2-api-keys demo/step-3-model-config
git rebase demo/step-3-model-config demo/step-4-rate-limiting
git rebase demo/step-4-rate-limiting demo/step-5-observability
```

**Once the baseline stabilizes**, freeze the demo by tagging each branch tip and deleting the branches:

```bash
git tag demo/step-0-baseline main
git tag demo/step-1-prompt-guards demo/step-1-prompt-guards
# ... etc
git branch -d demo/step-1-prompt-guards demo/step-2-api-keys ...
```

Tags are immutable snapshots -- the right format for a finished demo, but the wrong format for a codebase that's still changing.

### Navigating Steps

```bash
# Jump to any step (works with both branches and tags)
git checkout demo/step-2-api-keys

# See what a step changed relative to the previous step
git diff demo/step-1-prompt-guards..demo/step-2-api-keys

# Reset to the baseline
git checkout main

# List all demo steps
git branch -l 'demo/*'    # branches
git tag -l 'demo/*'        # tags (after freeze)
```

## Demo Steps

### Step 0: Baseline

**Branch:** `main`

The application as-is with all plumbing implemented in code. Launch the app, walk through the architecture, show comparisons between this agentic app and the microservices version (`cloud-cart`).

Key talking points:
- Agents = controllers (domain logic handlers)
- RouterAgent = API gateway / service mesh routing
- GuardrailService = middleware / input validation
- ConversationContext = session management
- HandoffManager = service-to-service orchestration

### Step 1: Prompt Guards

**Branch:** `demo/step-1-prompt-guards` (from `main`)

Replace `GuardrailService` (in-app regex-based PII detection and content filtering) with Agent Gateway's prompt guards.

**What changes:**
- Remove or bypass `GuardrailService`
- Configure Agent Gateway with `promptGuard` policy (reject + mask rules)
- Built-in PII detectors (credit card, SSN, email, phone) replace hand-rolled regex
- Off-topic blocking moves to gateway-level reject rules

**Why this matters:**
- Guardrails become a platform concern, not application code
- Consistent enforcement across all apps behind the gateway
- New patterns (e.g., Canadian SIN) added via config, not code deploys

### Step 2: API Key Management

**Branch:** `demo/step-2-api-keys` (from step 1)

Move LLM API key management from the application to Agent Gateway's backend auth.

**What changes:**
- Application no longer needs `ANTHROPIC_API_KEY` at all
- App points to Agent Gateway at `localhost:3000` instead of directly to Anthropic
- Gateway injects the API key via `backendAuth` policy

**Why this matters:**
- Applications never see or store LLM credentials
- Key rotation is a gateway config change, not an app redeploy
- Centralized credential management across all apps

### Step 3: Model Configuration

**Branch:** `demo/step-3-model-config` (from step 2)

Move model selection, token limits, and temperature settings from application code/config to Agent Gateway.

**What changes:**
- Remove model name and `maxTokens` from `AnthropicChatOptions` in code
- Configure `defaults` and `overrides` in Agent Gateway (model, max_tokens, temperature)
- Optionally set up model aliases (`fast` -> `claude-haiku`, `smart` -> `claude-sonnet`)

**Why this matters:**
- Model upgrades are a gateway config change, not a code change
- Org-wide token limits enforced at the platform level
- Model aliases decouple app code from specific model versions

### Step 4: Rate Limiting

**Branch:** `demo/step-4-rate-limiting` (from step 3)

Add token-based and request-based rate limiting via Agent Gateway.

**What changes:**
- No code in the app -- purely additive gateway configuration
- Per-route or per-user rate limits on LLM requests
- Token-based limits to control cost

**Why this matters:**
- Cost control without application changes
- Per-user tiering (free vs paid) at the gateway level
- Protection against runaway agent loops

### Step 5: Observability

**Branch:** `demo/step-5-observability` (from step 4)

Add LLM-aware observability via Agent Gateway's built-in OpenTelemetry support.

**What changes:**
- No code in the app -- purely additive gateway configuration
- Token usage metrics, latency tracking, cost attribution
- Distributed traces across agent calls

**Why this matters:**
- LLM-specific metrics (tokens, cost, model) without instrumentation code
- Centralized observability across all apps behind the gateway
- Enables chargeback and capacity planning

## Demo Script Helper

```bash
#!/bin/bash
# demo.sh -- jump to a demo step
STEPS=(
  "main"
  "demo/step-1-prompt-guards"
  "demo/step-2-api-keys"
  "demo/step-3-model-config"
  "demo/step-4-rate-limiting"
  "demo/step-5-observability"
)

step=${1:-0}
if [ "$step" -ge "${#STEPS[@]}" ]; then
  echo "Invalid step. Available: 0-$((${#STEPS[@]}-1))"
  exit 1
fi

echo "Switching to: ${STEPS[$step]}"
git checkout "${STEPS[$step]}"
```

## Agent Gateway Configuration (OSS Standalone)

The demo uses the OSS standalone binary for local runs. Example base config:

```yaml
# agentgateway.yaml
# yaml-language-server: $schema=https://agentgateway.dev/schema/config
binds:
- port: 3000
  listeners:
  - routes:
    - backends:
      - ai:
          name: anthropic
          provider:
            anthropic:
              model: claude-sonnet-4-5-20250929
      policies:
        backendAuth:
          key: "$ANTHROPIC_API_KEY"
```

This config evolves with each step as policies are added.
