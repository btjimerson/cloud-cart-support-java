# Cloud Cart Support: Enterprise Agent Gateway Demo Recipe

A step-by-step walkthrough for demonstrating progressive migration from in-app AI plumbing to Solo.io's Enterprise Agent Gateway on Kubernetes. Each step is a separate Git branch that replaces application code with declarative gateway-managed CRDs.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Environment Setup](#environment-setup)
- [Step 0: Baseline](#step-0-baseline)
- [Step 1: API Key Management](#step-1-api-key-management)
- [Step 2: Prompt Guards](#step-2-prompt-guards)
- [Step 3: Model Configuration](#step-3-model-configuration)
- [Step 4: Rate Limiting](#step-4-rate-limiting)
- [Step 5: Observability](#step-5-observability)
- [Step 6: MCP Federation](#step-6-mcp-federation)
- [Step 7: Declarative Agents](#step-7-declarative-agents)
- [Step 8: Agent Tracing](#step-8-agent-tracing)
- [Cleanup](#cleanup)
- [Troubleshooting](#troubleshooting)

---

## Prerequisites

- A clean Kubernetes cluster with `kubectl` configured
- Helm 3.x
- `jq` (for formatting JSON responses)
- `curl`
- Git (to switch between demo branches)
- Clone this repository and `cd` into it

---

## Environment Setup

This one-time setup installs the platform infrastructure needed for all demo steps.

### 1. Load environment variables

```bash
# Copy the example env file and fill in your values
cp .env.example .env
```

Edit `.env` and set the required values:

- `ANTHROPIC_API_KEY` — your Anthropic API key
- `OPENAI_API_KEY` — required by kagent's default model config
- `ENTERPRISE_AGENTGATEWAY_LICENSE_KEY` — Solo Enterprise Agent Gateway license
- `ENTERPRISE_KGATEWAY_LICENSE_KEY` — Solo Enterprise kgateway license
- `ENTERPRISE_KAGENT_LICENSE_KEY` — Solo Enterprise kagent license

Version variables (`ENTERPRISE_AGENTGATEWAY_VERSION`, `ENTERPRISE_KGATEWAY_VERSION`, `ENTERPRISE_KAGENT_VERSION`) have defaults you can override.

Then load them:

```bash
source .env
```

### 2. Install Gateway API CRDs

```bash
kubectl apply -f https://github.com/kubernetes-sigs/gateway-api/releases/download/v1.4.0/standard-install.yaml
```

### 3. Install Solo Enterprise for kgateway (ingress)

```bash
helm upgrade -i enterprise-kgateway-crds \
  oci://us-docker.pkg.dev/solo-public/enterprise-kgateway/charts/enterprise-kgateway-crds \
  --namespace kgateway-system --create-namespace \
  --version "${ENTERPRISE_KGATEWAY_VERSION}"

helm upgrade -i enterprise-kgateway \
  oci://us-docker.pkg.dev/solo-public/enterprise-kgateway/charts/enterprise-kgateway \
  --namespace kgateway-system \
  --version "${ENTERPRISE_KGATEWAY_VERSION}" \
  --set licensing.licenseKey="${ENTERPRISE_KGATEWAY_LICENSE_KEY}"
```

### 4. Install Solo Enterprise for Agent Gateway

> **Note:** Enterprise kgateway and Enterprise Agent Gateway share some CRDs (extauth, ratelimit). Use `helm template` + server-side apply for the CRDs to avoid ownership conflicts, then install the control plane normally.

```bash
# CRDs (server-side apply to avoid ownership conflict with shared kgateway CRDs)
helm template enterprise-agentgateway-crds \
  oci://us-docker.pkg.dev/solo-public/enterprise-agentgateway/charts/enterprise-agentgateway-crds \
  --version "${ENTERPRISE_AGENTGATEWAY_VERSION}" \
  | kubectl apply --server-side --force-conflicts --validate=false -f -

# Control plane (use a values file because --set/--set-string can mangle the JWT license key)
kubectl create namespace agentgateway-system --dry-run=client -o yaml | kubectl apply -f -
cat <<VALS > /tmp/agw-values.yaml
licensing:
  licenseKey: "${ENTERPRISE_AGENTGATEWAY_LICENSE_KEY}"
VALS
helm upgrade -i enterprise-agentgateway \
  oci://us-docker.pkg.dev/solo-public/enterprise-agentgateway/charts/enterprise-agentgateway \
  --namespace agentgateway-system --create-namespace \
  --version "${ENTERPRISE_AGENTGATEWAY_VERSION}" \
  -f /tmp/agw-values.yaml
rm /tmp/agw-values.yaml
```

### 5. Install Solo Enterprise for Kagent

```bash
# Install the management plane (UI, OIDC provider, telemetry, ClickHouse)
cat <<VALS > /tmp/kagent-mgmt-values.yaml
cluster: $(kubectl config current-context)
products:
  kagent:
    enabled: true
oidc:
  issuer: ""
VALS
helm upgrade -i kagent-mgmt \
  oci://us-docker.pkg.dev/solo-public/solo-enterprise-helm/charts/management \
  -n kagent --create-namespace \
  --version "${ENTERPRISE_KAGENT_VERSION}" \
  --values /tmp/kagent-mgmt-values.yaml
rm /tmp/kagent-mgmt-values.yaml

# Wait for the management plane (includes the OIDC provider the controller needs)
kubectl rollout status deploy/solo-enterprise-ui -n kagent --timeout=120s

# Wait for the OIDC provider (Dex) inside solo-enterprise-ui to be fully serving
# The pod may show Ready before the OIDC endpoint is available on port 5556
echo "Waiting for OIDC provider to be ready..."
until kubectl run oidc-check --rm -i --restart=Never --image=curlimages/curl -n kagent -- \
  curl -sf http://solo-enterprise-ui.kagent.svc.cluster.local:5556/.well-known/openid-configuration >/dev/null 2>&1; do
  sleep 5; echo "  ...waiting"
done
echo "OIDC provider ready"

# Create JWT secret for kagent auth
openssl genrsa -out /tmp/key.pem 2048
kubectl create secret generic jwt -n kagent \
  --from-file=jwt=/tmp/key.pem --dry-run=client -o yaml | kubectl apply -f -
rm /tmp/key.pem

# Install kagent CRDs
helm upgrade -i kagent-crds \
  oci://us-docker.pkg.dev/solo-public/kagent-enterprise-helm/charts/kagent-enterprise-crds \
  -n kagent \
  --version "${ENTERPRISE_KAGENT_VERSION}"

# Install kagent control plane (with UI and agentgateway proxy)
# Note: kagent 0.3.10+ requires a license key via licensing.licenseKey
cat <<VALS > /tmp/kagent-values.yaml
ui:
  enabled: true
providers:
  default: openAI
  openAI:
    apiKey: ${OPENAI_API_KEY}
otel:
  tracing:
    enabled: true
    exporter:
      otlp:
        endpoint: solo-enterprise-telemetry-collector.kagent.svc.cluster.local:4317
        insecure: true
VALS
helm upgrade -i kagent \
  oci://us-docker.pkg.dev/solo-public/kagent-enterprise-helm/charts/kagent-enterprise \
  -n kagent \
  --version "${ENTERPRISE_KAGENT_VERSION}" \
  --values /tmp/kagent-values.yaml \
  --set licensing.licenseKey="${ENTERPRISE_KAGENT_LICENSE_KEY}"
rm /tmp/kagent-values.yaml

# Verify all kagent pods are running
kubectl get pods -n kagent
```

### 6. Verify infrastructure

```bash
# kgateway pods
kubectl get pods -n kgateway-system

# Agent Gateway pods
kubectl get pods -n agentgateway-system

# kagent pods
kubectl get pods -n kagent

# GatewayClasses registered
kubectl get gatewayclasses

# kagent controller running
kubectl rollout status deploy/kagent-controller -n kagent --timeout=120s
```

All pods should be `Running`, both `enterprise-kgateway` and `enterprise-agentgateway` GatewayClasses should show `Accepted`, and the kagent controller should be ready.

### 7. Apply kgateway ingress resources

> **Note:** The HTTPRoute is in the `cloud-cart-support` namespace. Apply the Gateway and policy first, then apply the HTTPRoute after deploying the application (Step 0) which creates the namespace.

```bash
kubectl apply -f k8s/kgateway/gateway.yaml
kubectl apply -f k8s/kgateway/httplistenerpolicy.yaml
```

Get the external IP (may take a minute for the load balancer):

```bash
kubectl get gateway cloud-cart-gateway -n kgateway-system
```

Save the external IP for later:

```bash
export GATEWAY_IP=$(kubectl get svc cloud-cart-gateway -n kgateway-system \
  -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
echo "Gateway IP: $GATEWAY_IP"
```

---

## Step 0: Baseline

**Branch:** `main`

### Current State

The application runs with all AI plumbing implemented in application code:

- `ANTHROPIC_API_KEY` injected as a pod environment variable from a Kubernetes Secret
- `GuardrailService.java` performs regex-based PII detection (SSN, credit card, email, phone) and off-topic content filtering
- `RateLimitService.java` implements in-memory per-client rate limiting (20 requests/60 seconds)
- Model name (`claude-sonnet-4-5-20250929`) and `maxTokens` (4096) hardcoded in `application.yml`
- Each agent connects directly to 4 MCP server microservices via hardcoded URLs
- No LLM-specific observability

### Challenges

| Concern | Problem |
|---------|---------|
| API keys | Every app stores its own LLM credentials; rotation requires redeployment |
| Guardrails | PII patterns are hand-rolled regex; changes require code deploys |
| Model config | Model upgrades are code changes; no org-wide enforcement |
| Rate limiting | In-memory counters don't work across replicas |
| Observability | No token usage, cost attribution, or LLM-specific metrics |
| MCP discovery | Each app hardcodes every MCP server URL |

### Desired Outcome

A working baseline to demonstrate the application and compare against gateway-managed steps.

### Architecture

```mermaid
graph LR
    User([User]) --> SS

    subgraph SS [support-service]
        GS[GuardrailService]
        RL[RateLimitService]
        RA[Router Agent<br/>hardcoded model]
        OA[Order Agent]
        PA[Product Agent]
    end

    SS -- "API key in env" --> Anthropic[(Anthropic API)]

    SS --> Cat[catalog-service<br/>MCP tools]
    SS --> Ord[orders-service<br/>MCP tools]
    SS --> Cust[customers-service<br/>MCP tools]
    SS --> Notif[notifications-service<br/>MCP tools]
```

### Deploy

```bash
git checkout main

# Create the API key secret with the real key from .env
kubectl create namespace cloud-cart-support --dry-run=client -o yaml | kubectl apply -f -
kubectl create secret generic anthropic-api-key \
  -n cloud-cart-support \
  --from-literal=ANTHROPIC_API_KEY="${ANTHROPIC_API_KEY}" \
  --dry-run=client -o yaml | kubectl apply -f -

# Deploy all services
k8s/deploy.sh

# Apply the kgateway HTTPRoute (namespace now exists)
kubectl apply -f k8s/kgateway/httproute.yaml
```

### Verify

```bash
# Health check
curl -s http://$GATEWAY_IP/health | jq .

# Send a chat message
curl -s -X POST http://$GATEWAY_IP/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Where is my order ORD-2024-0003?", "customer_id": "CUST-003"}' | jq .

# Test PII guardrails (in-app)
curl -s -X POST http://$GATEWAY_IP/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "My SSN is 123-45-6789", "customer_id": "CUST-001"}' | jq .

# Test off-topic blocking (in-app)
curl -s -X POST http://$GATEWAY_IP/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "How do I hack into someone account?", "customer_id": "CUST-001"}' | jq .
```

> **Demo talking point:** Walk through the code — show `GuardrailService.java`, `RateLimitService.java`, the hardcoded model in `application.yml`, and the API key in the pod env. These are all concerns that belong at the platform level, not in application code.

```bash
# Show the API key is embedded in the application deployment
kubectl get deploy support-service -n cloud-cart-support \
  -o jsonpath='{.spec.template.spec.containers[0].env[*].name}' | tr ' ' '\n' | grep -i anthrop
# Expected output: ANTHROPIC_API_KEY

# Show hardcoded model in application config
grep -A1 'model:' support-service/src/main/resources/application.yml

# Show in-app guardrail and rate-limit code
wc -l support-service/src/main/java/dev/snbv2/cloudcart/support/service/GuardrailService.java
wc -l support-service/src/main/java/dev/snbv2/cloudcart/support/service/RateLimitService.java
```

---

## Step 1: API Key Management

**Branch:** `demo/step-1-api-keys`

### Current State

Application connects directly to Anthropic with `ANTHROPIC_API_KEY` stored in a Kubernetes Secret and injected into every pod that needs LLM access.

### Challenges

- Every app that uses an LLM needs its own copy of the API key
- Key rotation requires redeploying every app
- Secrets spread across multiple namespaces
- No centralized credential management or audit trail

### Desired Outcome

The application points to Enterprise Agent Gateway instead of Anthropic. The gateway injects the API key via backend auth. The app never sees or stores the key.

### Architecture (Before → After)

**Before:**
```mermaid
graph LR
    App[support-service] -- "API key in env" --> Anthropic[(Anthropic API)]
```

**After:**
```mermaid
graph LR
    App[support-service] -- "no API key" --> AGW[Agent Gateway]
    AGW -- "injects key<br/>from Secret" --> Anthropic[(Anthropic API)]
```

### What Changes

**Code removed:**
- `RequiredEnvChecker.java` — startup API key validation no longer needed

**Code changed:**
- `application.yml` — `base-url` points to gateway; `api-key: not-used`
- `support-service.yaml` — env var `SPRING_AI_ANTHROPIC_BASE_URL` points to `agentgateway.agentgateway-system.svc:8080`

**CRDs added:**
- `k8s/agentgateway/backend-anthropic.yaml` — AgentgatewayBackend with `backendAuth` referencing the API key Secret
- `k8s/agentgateway/gateway.yaml` — Gateway (enterprise-agentgateway class, port 8080)
- `k8s/agentgateway/route-ai.yaml` — HTTPRoute routing LLM traffic to the Anthropic backend
- `k8s/agentgateway/policy-ai-routes.yaml` — EnterpriseAgentgatewayPolicy enabling bidirectional protocol translation between Anthropic `/v1/messages` format and OpenAI `/v1/chat/completions` format

**Secret moved:**
- `k8s/secret.yaml` — now in `agentgateway-system` namespace with `Authorization: <key>` format (the gateway translates this to `x-api-key` for Anthropic)

### Deploy

```bash
git checkout demo/step-1-api-keys

# Ensure agentgateway-system namespace exists and create the API key secret
kubectl create namespace agentgateway-system --dry-run=client -o yaml | kubectl apply -f -
kubectl create secret generic anthropic-api-key \
  -n agentgateway-system \
  --from-literal=Authorization="${ANTHROPIC_API_KEY}" \
  --dry-run=client -o yaml | kubectl apply -f -

# Deploy application + Agent Gateway CRDs
k8s/deploy.sh
```

> **Protocol Translation:** The Agent Gateway acts as a universal LLM proxy. The `ai-routes` policy enables the gateway to accept requests in either OpenAI format (`/v1/chat/completions`) or Anthropic native format (`/v1/messages` with content blocks), and automatically translate to the backend provider's native API. This means your application code doesn't need to change if you switch LLM providers — only the `AgentgatewayBackend` configuration changes.

### Verify

```bash
# Gateway accepted the backend
kubectl get agentgatewaybackend -n agentgateway-system

# HTTPRoute bound
kubectl get httproute -n agentgateway-system

# Agent Gateway has an address
kubectl get gateway agentgateway -n agentgateway-system

# Chat still works (traffic now flows through gateway)
curl -s -X POST http://$GATEWAY_IP/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Where is my order ORD-2024-0003?", "customer_id": "CUST-003"}' | jq .

# Confirm: app no longer has the API key
kubectl get deploy support-service -n cloud-cart-support \
  -o jsonpath='{.spec.template.spec.containers[0].env[*].name}' | tr ' ' '\n' | grep -i anthrop
# Should NOT show ANTHROPIC_API_KEY
```

> **Demo talking point:** The application code is simpler — no API key validation. Key rotation is now a Secret update, not an app redeploy. Every app behind the gateway shares the same credential management.

```bash
# Confirm: app no longer has the API key in its env
kubectl get deploy support-service -n cloud-cart-support \
  -o jsonpath='{.spec.template.spec.containers[0].env[*].name}' | tr ' ' '\n' | grep -i anthrop
# Expected: empty (no ANTHROPIC_API_KEY)

# Show the key moved to the gateway namespace
kubectl get secret anthropic-api-key -n agentgateway-system
```

---

## Step 2: Prompt Guards

**Branch:** `demo/step-2-prompt-guards`

### Current State

`GuardrailService.java` (82 lines) performs:
- PII detection via regex (SSN, credit card, email, phone)
- Off-topic content filtering via pattern matching
- PII masking before messages reach the LLM

### Challenges

- Guardrails are app-specific code, not platform-wide policy
- Adding new PII patterns (e.g., Canadian SIN) requires code changes and redeployment
- Different apps may implement guardrails inconsistently
- Regex-based detection is fragile and hard to test comprehensively

### Desired Outcome

Remove `GuardrailService` entirely. Replace with an `EnterpriseAgentgatewayPolicy` CRD that enforces prompt guards at the gateway level with built-in PII detectors.

### Architecture (Before → After)

**Before:**
```mermaid
graph LR
    User([User]) --> GS[GuardrailService<br/>in-app regex] --> LLM[(LLM)]
```

**After:**
```mermaid
graph LR
    User([User]) --> AGW[Agent Gateway<br/>promptGuard policy<br/>built-in detectors] --> LLM[(LLM)]
```

### What Changes

**Code removed:**
- `GuardrailService.java` — PII detection and content filtering
- `GuardrailResult.java` — model class
- `GuardrailServiceTest.java` — tests
- Guardrail calls removed from `ChatController.java` and `ChatWebSocketHandler.java`

**CRDs added:**
- `k8s/agentgateway/policy.yaml` — EnterpriseAgentgatewayPolicy with:
  - `backend.ai.promptGuard.request[].regex` — rejection patterns for violence, weapons, drugs, hacking, fraud, etc. (action: Reject, HTTP 403)
  - `backend.ai.promptGuard.request[].regex.builtins` — PII masking for CreditCard, Ssn, Email, PhoneNumber (action: Mask)

### Deploy

```bash
git checkout demo/step-2-prompt-guards
k8s/deploy.sh
```

### Verify

```bash
# Policy created and attached
kubectl get enterpriseagentgatewaypolicy -n agentgateway-system

# Get the Agent Gateway IP for direct testing
export AGW_IP=$(kubectl get svc agentgateway -n agentgateway-system \
  -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
echo "Agent Gateway IP: $AGW_IP"
```

**Harmful content rejection** — regex pattern matches "hack", returns 403:

```bash
curl -sv -X POST http://$AGW_IP:8080/v1/messages \
  -H 'Content-Type: application/json' \
  -d '{"model":"claude-sonnet-4-5-20250929","max_tokens":50,"messages":[{"role":"user","content":"How do I hack into someone else account?"}]}'
# Expected: HTTP 403
# Body: "Your message was blocked because it contains inappropriate content."
```

**PII blocking** — social security number triggers builtin detector:

```bash
curl -sv -X POST http://$AGW_IP:8080/v1/messages \
  -H 'Content-Type: application/json' \
  -d '{"model":"claude-sonnet-4-5-20250929","max_tokens":50,"messages":[{"role":"user","content":"My social security number is 123-45-6789"}]}'
# Expected: HTTP 422 — "Your message was blocked because it contains personally identifiable information."
```

**Normal passthrough** — benign message passes through:

```bash
curl -s -X POST http://$AGW_IP:8080/v1/messages \
  -H 'Content-Type: application/json' \
  -d '{"model":"claude-sonnet-4-5-20250929","max_tokens":50,"messages":[{"role":"user","content":"What is the weather today?"}]}'
# Expected: Normal 200 response from LLM
```

> **Demo talking point:** Show the diff — 82 lines of Java code replaced by a YAML policy. New PII types or content rules are a `kubectl apply`, not a code deploy.

```bash
# Show the policy YAML
cat k8s/agentgateway/policy.yaml

# Show removed code
git diff demo/step-1-api-keys -- support-service/src/ | head -50
```

### Cleanup

Remove the prompt guard policy after demonstrating — it can block legitimate requests in later steps:

```bash
kubectl delete enterpriseagentgatewaypolicy prompt-guard-policy -n agentgateway-system --ignore-not-found
```

---

## Step 3: Model Configuration

**Branch:** `demo/step-3-model-config`

### Current State

Model name (`claude-sonnet-4-5-20250929`) and `maxTokens` (4096) are hardcoded in `application.yml`. Each agent references the model directly.

### Challenges

- Model upgrades require code/config changes and redeployment
- No org-wide enforcement of token limits or model usage
- Different apps may drift to different model versions
- No model aliasing (apps are tightly coupled to specific model names)

### Desired Outcome

Model configuration managed via `AgentgatewayPolicy`. The app uses a placeholder model name; the gateway enforces the real model, temperature, and token limits.

### What Changes

**Code changed:**
- `application.yml` — model changed to `placeholder`, max-tokens removed
- `BaseToolAgent.java` and `RouterAgent.java` — hardcoded model references removed

**CRDs changed:**
- `k8s/agentgateway/backend-anthropic.yaml` — `anthropic.model` set to `claude-sonnet-4-5-20250929` (the backend-level model overrides whatever the client sends, so the app's `placeholder` value is replaced)

**CRDs added:**
- `k8s/agentgateway/model-policy.yaml` — AgentgatewayPolicy with:
  - `defaults.temperature: 0.7`
  - `overrides.max_tokens: 4096`

### Deploy

```bash
git checkout demo/step-3-model-config
k8s/deploy.sh
```

### Verify

```bash
# Policy created
kubectl get agentgatewaypolicy -n agentgateway-system

# Chat still works (gateway enforces model)
curl -s -X POST http://$GATEWAY_IP/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Can you track order ORD-2024-0003?", "customer_id": "CUST-003"}' | jq .
```

> **Demo talking point:** Model upgrades are now a `kubectl apply` — change one YAML field and every app behind the gateway uses the new model. No code changes, no redeployments.

```bash
# Show the model is set on the backend, not in app code
kubectl get agentgatewaybackend anthropic -n agentgateway-system \
  -o jsonpath='{.spec.ai.provider.anthropic.model}'
# Expected: claude-sonnet-4-5-20250929

# Show the policy enforcing temperature and token limits
kubectl get agentgatewaypolicy anthropic-model-policy -n agentgateway-system -o yaml
```

---

## Step 4: Rate Limiting

**Branch:** `demo/step-4-rate-limiting`

### Current State

`RateLimitService.java` (53 lines) implements an in-memory sliding window rate limiter: 20 requests per 60 seconds per client.

### Challenges

- In-memory counters reset on pod restart and don't work across replicas
- No token-based cost control (only request count)
- Rate limit changes require code changes
- No per-user tiering (free vs paid)

### Desired Outcome

Remove `RateLimitService` entirely. Add `EnterpriseAgentgatewayPolicy` with request-based and token-based rate limiting enforced at the gateway.

### Architecture (Before → After)

**Before:**
```mermaid
graph LR
    User([User]) --> RL[RateLimitService<br/>in-memory counters<br/>single-replica only] --> LLM[(LLM)]
```

**After:**
```mermaid
graph LR
    User([User]) --> AGW[Agent Gateway<br/>rateLimit policy<br/>works across replicas] --> LLM[(LLM)]
```

### What Changes

**Code removed:**
- `RateLimitService.java`
- `RateLimitServiceTest.java`
- Rate limit calls removed from `ChatController.java` and `ChatWebSocketHandler.java`

**CRDs added:**
- `k8s/agentgateway/rate-limit-policy.yaml` — EnterpriseAgentgatewayPolicy with:
  - `traffic.rateLimit.local[].requests: 3` per minute with burst of 2 (5 total before throttling)

### Deploy

```bash
git checkout demo/step-4-rate-limiting
k8s/deploy.sh
```

### Verify

```bash
# Policy created
kubectl get enterpriseagentgatewaypolicy -n agentgateway-system

# Normal request through the app still works
curl -s -X POST http://$GATEWAY_IP/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Hello!", "customer_id": "CUST-001"}' | jq .

# Test rate limiting directly against the Agent Gateway
# (testing through the app hides 429s because the app catches gateway errors)
export AGW_IP=$(kubectl get svc agentgateway -n agentgateway-system \
  -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
echo "Agent Gateway IP: $AGW_IP"

for i in $(seq 1 10); do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 \
    -X POST http://$AGW_IP:8080/v1/messages \
    -H "Content-Type: application/json" \
    -d '{"model":"claude-sonnet-4-5-20250929","max_tokens":1,"messages":[{"role":"user","content":"hi"}]}')
  echo "Request $i: HTTP $STATUS"
done
# First 3-5 requests succeed, then 429 (Too Many Requests)
```

> **Demo talking point:** Show the diff — 53 lines of Java code plus tests replaced by a YAML policy. Rate limits now work across replicas and can be changed without code deploys. The gateway also supports global distributed rate limiting via `RateLimitConfig` CRDs for production use.

```bash
# Show the rate limit policy
cat k8s/agentgateway/rate-limit-policy.yaml

# Show removed code
git diff demo/step-3-model-config -- support-service/src/ | head -50
```

### Cleanup

Remove the rate limit policy after demonstrating — it causes 429 errors in later steps:

```bash
kubectl delete enterpriseagentgatewaypolicy rate-limit-policy -n agentgateway-system --ignore-not-found
```

---

## Step 5: Observability

**Branch:** `demo/step-5-observability`

### Current State

The application has standard Spring Boot Actuator metrics only. No LLM-specific observability — no token usage, model latency, or cost attribution.

### Challenges

- No visibility into token consumption or LLM costs
- No distributed tracing across agent calls
- Cannot do chargeback or capacity planning
- Adding instrumentation requires code changes in every app

### Desired Outcome

Add gateway-level observability with Prometheus metrics and OpenTelemetry tracing. No application code changes required.

### What Changes

**Code changes:** None. Purely additive gateway configuration.

**CRDs added:**
- `k8s/agentgateway/observability-policy.yaml` — EnterpriseAgentgatewayPolicy with:
  - `frontend.accessLog.attributes` — custom access log attributes for AI request metadata (model, client ID)

### Deploy

```bash
git checkout demo/step-5-observability

# Only apply the observability policy — no app redeploy needed
kubectl apply -f k8s/agentgateway/ --recursive
```

### Verify

```bash
# Policy created
kubectl get enterpriseagentgatewaypolicy -n agentgateway-system

# Generate some traffic
curl -s -X POST http://$GATEWAY_IP/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What products do you have?", "customer_id": "CUST-001"}' | jq .

# Check Agent Gateway proxy metrics (port 15020 on the proxy pod, not the control plane)
kubectl port-forward -n agentgateway-system deploy/agentgateway 15020:15020 &
sleep 2
curl -s localhost:15020/stats/prometheus | grep "gen_ai" | head -20
kill %1 2>/dev/null
```

> **Demo talking point:** Zero code changes. The gateway emits LLM-aware metrics (tokens, latency, model, cost) for every request flowing through it. Plug into Prometheus/Grafana for dashboards and alerting.

```bash
# Prove no app code changed
git diff demo/step-4-rate-limiting -- support-service/src/
# Expected: empty (no changes)

# Show the observability policy
cat k8s/agentgateway/observability-policy.yaml
```

---

## Step 6: MCP Federation

**Branch:** `demo/step-6-mcp-federation`

### Current State

The application connects to 4 MCP server microservices individually via hardcoded URLs in `application.yml`:
- `catalog-service:8081`
- `orders-service:8082`
- `customers-service:8083`
- `notifications-service:8084`

### Challenges

- Every app must know every MCP server URL
- Adding or removing MCP servers requires application config changes
- No centralized MCP tool discovery or governance
- No authentication/authorization on MCP connections

### Desired Outcome

The gateway federates all MCP backends. The application connects to a single gateway MCP endpoint. The gateway handles routing to the correct MCP backend.

### Architecture (Before → After)

**Before:**
```mermaid
graph LR
    App[support-service] --> Cat[catalog-service]
    App --> Ord[orders-service]
    App --> Cust[customers-service]
    App --> Notif[notifications-service]
    style App fill:#f9f,stroke:#333
```
_4 hardcoded MCP connections_

**After:**
```mermaid
graph LR
    App[support-service] -- "single MCP<br/>connection" --> AGW[Agent Gateway]
    AGW --> Cat[catalog-service]
    AGW --> Ord[orders-service]
    AGW --> Cust[customers-service]
    AGW --> Notif[notifications-service]
    style AGW fill:#4af,stroke:#333,color:#fff
```
_1 connection — gateway federates all MCP backends_

### What Changes

**Code changed:**
- `application.yml` — 4 individual MCP connections replaced with single `agentgateway` connection to `MCP_GATEWAY_URL`

**CRDs added:**
- `k8s/agentgateway/backends-mcp.yaml` — 4 AgentgatewayBackend resources (catalog-mcp, orders-mcp, customers-mcp, notifications-mcp) pointing to in-cluster MCP services
- `k8s/agentgateway/route-mcp.yaml` — HTTPRoute aggregating all MCP backends

### Deploy

```bash
git checkout demo/step-6-mcp-federation
k8s/deploy.sh
```

### Verify

```bash
# MCP backends registered
kubectl get agentgatewaybackend -n agentgateway-system

# MCP route bound
kubectl get httproute -n agentgateway-system

# Chat that requires MCP tool calls still works
curl -s -X POST http://$GATEWAY_IP/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What products do you have in electronics?", "customer_id": "CUST-001"}' | jq .

# Order lookup (requires orders-service MCP tools)
curl -s -X POST http://$GATEWAY_IP/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Can you track order ORD-2024-0003?", "customer_id": "CUST-003"}' | jq .
```

> **Demo talking point:** The application now has a single MCP connection. Adding a new MCP server is `kubectl apply` of a new AgentgatewayBackend — no application changes needed. The gateway handles discovery, routing, and governance of all MCP tools.

```bash
# Show all federated MCP backends
kubectl get agentgatewaybackend -n agentgateway-system

# Show the single gateway connection in app config
grep -A10 mcp support-service/src/main/resources/application.yml
```

---

## Step 7: Declarative Agents

**Branch:** `demo/step-7-declarative-agents`

### Current State

The application runs a custom, in-process multi-agent system in Java/Spring Boot:
- 5 agent classes (RouterAgent, OrderAgent, ProductAgent, ReturnsAgent, ComplaintAgent)
- HandoffManager for inter-agent routing
- BaseToolAgent wiring Spring AI's tool-calling loop
- AgentRegistry (ConcurrentHashMap) for agent lookup
- AiConfig wiring all agents, tools, and prompts together
- ~80% of the support-service code is agent orchestration

### Challenges

- Agent definitions, system prompts, and tool bindings are all in Java code
- Adding or modifying an agent requires code changes and redeployment
- No standardized inter-agent protocol (custom handoff mechanism)
- Agent scaling is tied to the monolith — can't scale agents independently
- No external visibility into agent definitions (`kubectl get agents` doesn't work)

### Desired Outcome

Replace all Java agent orchestration with kagent Kubernetes CRDs. Agent definitions, system prompts, model config, and tool bindings become declarative YAML. The support-service shrinks to just the UI + A2A client.

### Architecture (Before → After)

**Before:**
```mermaid
graph LR
    User([User]) --> SS[support-service<br/>RouterAgent + HandoffManager<br/>+ 4 specialist agents<br/>+ AgentRegistry + AiConfig]
    SS -- "Spring AI" --> AGW[Agent Gateway]
    AGW --> Anthropic[(Anthropic)]
    SS -- "Spring AI MCP" --> AGW2[Agent Gateway MCP]
```
_Agent orchestration is Java code inside the monolith_

**After:**
```mermaid
graph LR
    User([User]) --> SS[support-service<br/>UI + A2A client only]
    SS -- "A2A protocol" --> KA[kagent controller]
    KA --> RA[router-agent CR]
    RA --> OA[order-agent CR]
    RA --> PA[product-agent CR]
    RA --> RetA[returns-agent CR]
    RA --> CA[complaint-agent CR]
    KA -- "via ModelConfig" --> AGW[Agent Gateway]
    AGW --> Anthropic[(Anthropic)]
    OA & PA & RetA & CA -. "RemoteMCPServer" .-> MCP[MCP services]
    style KA fill:#4af,stroke:#333,color:#fff
```
_Agent orchestration is Kubernetes CRDs managed by kagent_

### What Changes

**Code removed (~3,400 lines):**
- All agent classes: `Agent.java`, `BaseToolAgent.java`, `RouterAgent.java`, `OrderAgent.java`, `ProductAgent.java`, `ReturnsAgent.java`, `ComplaintAgent.java`
- `AgentRegistry.java`, `HandoffManager.java`, `AiConfig.java`
- All `*ToolsService.java` classes
- Spring AI Anthropic and MCP client dependencies from `pom.xml`
- All corresponding tests

**Code added (~150 lines):**
- `A2AClient.java` — HTTP client that invokes kagent agents via the A2A protocol

**Code changed:**
- `ChatController.java` — calls `A2AClient.invoke("router-agent", ...)` instead of `routerAgent.handle(...)`
- `ChatWebSocketHandler.java` — same A2A client change
- `HealthController.java` — reports `agent_runtime: kagent` with static agent list
- `application.yml` — Spring AI config removed, replaced with `kagent.a2a.base-url` and `kagent.a2a.namespace`
- `support-service.yaml` — new image tag, env vars `KAGENT_A2A_URL` and `KAGENT_NAMESPACE`

**CRDs added (in `k8s/kagent/`):**
- `model-config.yaml` — ModelConfig pointing to agentgateway (`provider: OpenAI`, `baseUrl` = gateway service URL with `/v1` suffix, e.g. `http://agentgateway.agentgateway-system.svc:8080/v1`)
- `agents.yaml` — 5 Agent CRs: router-agent (with agent-as-tool references) + 4 specialist agents (with MCP tool references and system prompts)
- `remote-mcp-servers.yaml` — 4 RemoteMCPServer CRs pointing to the application MCP services in `cloud-cart-support` namespace (orders, catalog, customers, notifications)

### Deploy

```bash
git checkout demo/step-7-declarative-agents

# kagent must be installed first (see Environment Setup)

# Deploy application + kagent CRDs
k8s/deploy.sh
```

### Wait for agents to be ready

```bash
# Wait for all agents to report Accepted and Ready
kubectl get agents -n kagent -o wide
# All agents should show ACCEPTED=True and READY=True
```

### Verify

```bash
# Agents are now Kubernetes resources
kubectl get agents -n kagent

# ModelConfig points to agentgateway
kubectl get modelconfig -n kagent -o yaml

# RemoteMCPServers discovered tools
kubectl get remotemcpserver -n kagent

# Wait for kagent controller to be ready before testing chat
kubectl exec -n cloud-cart-support deploy/support-service -- \
  curl -sf -o /dev/null "http://kagent-controller.kagent.svc:8083/health"

# Router agent card via A2A
kubectl port-forward svc/kagent-controller -n kagent 8083:8083 &
sleep 2
curl -s localhost:8083/api/a2a/kagent/router-agent/.well-known/agent.json | jq .
kill %1 2>/dev/null

# Chat still works (now via kagent A2A)
# Note: avoid PII in the message — the prompt guard from Step 2 will block it
curl -s -X POST http://$GATEWAY_IP/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Hello, what can you help me with?"}' | jq .
```

> **Demo talking point:** Show the massive code reduction — 3,400 lines of Java replaced by ~150 lines of code + YAML CRDs. Agent definitions are now visible with `kubectl get agents`. System prompt changes are `kubectl edit agent order-agent` — no rebuild needed. All gateway policies (prompt guards, rate limits, observability) still apply because kagent routes through agentgateway.

```bash
# Show agents as Kubernetes resources
kubectl get agents -n kagent

# Show the router agent definition
kubectl get agent router-agent -n kagent -o yaml

# Show the massive code reduction
git diff --stat demo/step-6-mcp-federation
```

---

## Step 8: Agent Tracing

**Branch:** `demo/step-8-agent-tracing`

### Current State

All agents are deployed as kagent CRDs and running successfully. The kagent Enterprise UI includes built-in tracing that captures every agent interaction automatically — no code instrumentation needed.

### What to Show

Review execution traces in the kagent Enterprise UI to demonstrate end-to-end observability of the agent system.

### Commands

```bash
git checkout demo/step-8-agent-tracing

# Generate a request that exercises agent delegation and MCP tools
GATEWAY_IP=$(kubectl get svc cloud-cart-gateway -n kgateway-system -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
curl -s -X POST "http://$GATEWAY_IP/chat" \
  -H "Content-Type: application/json" \
  -d '{"message": "What products do you have in electronics?"}' | jq .

# Port-forward the kagent Enterprise UI
kubectl port-forward svc/solo-enterprise-ui -n kagent 4000:80 &
open http://localhost:4000
```

### Walkthrough

1. Click **Tracing** in the left navigation menu
2. Find the trace for the request just sent
3. Click the trace to expand the full execution flow:
   - Router agent receives the user message
   - Router delegates to product-agent via A2A
   - Product-agent calls catalog-service MCP tools
   - Response flows back through the agent chain
4. Each span shows: LLM model, token usage, tool calls, and latency

> **Demo talking point:** Every agent interaction is automatically traced — no code instrumentation needed. kagent captures the full delegation chain, tool invocations, and LLM calls. Combined with the gateway's observability policy from Step 5, you get end-to-end visibility from the user request through every agent and tool call.

---

## Cleanup

Uninstall everything and restore the cluster to its original state.

```bash
# Delete application namespace
kubectl delete namespace cloud-cart-support --ignore-not-found

# Delete kagent resources
kubectl delete agent --all -n kagent --ignore-not-found
kubectl delete modelconfig --all -n kagent --ignore-not-found
kubectl delete remotemcpserver --all -n kagent --ignore-not-found
helm uninstall kagent -n kagent || true
helm uninstall kagent-crds -n kagent || true
helm uninstall kagent-mgmt -n kagent || true
kubectl get crds -o name | grep kagent | xargs kubectl delete --ignore-not-found
kubectl delete namespace kagent --ignore-not-found

# Delete Agent Gateway CRDs (backends, routes)
kubectl delete agentgatewaybackend --all -n agentgateway-system --ignore-not-found
kubectl delete httproute --all -n agentgateway-system --ignore-not-found
kubectl delete gateway agentgateway -n agentgateway-system --ignore-not-found
kubectl delete secret anthropic-api-key -n agentgateway-system --ignore-not-found

# Uninstall Enterprise Agent Gateway
helm uninstall enterprise-agentgateway -n agentgateway-system || true
helm uninstall enterprise-agentgateway-crds -n agentgateway-system || true
kubectl delete namespace agentgateway-system --ignore-not-found

# Delete kgateway resources
kubectl delete gateway cloud-cart-gateway -n kgateway-system --ignore-not-found
kubectl delete httproute --all -n cloud-cart-support --ignore-not-found
kubectl delete httplistenerpolicy --all -n kgateway-system --ignore-not-found

# Uninstall Enterprise kgateway
helm uninstall enterprise-kgateway -n kgateway-system || true
helm uninstall enterprise-kgateway-crds -n kgateway-system || true
kubectl delete namespace kgateway-system --ignore-not-found

# Remove leftover Solo/agentgateway CRDs
kubectl get crds -o name | grep 'solo\|agentgateway' | xargs kubectl delete --ignore-not-found

# Remove Gateway API CRDs
kubectl delete -f https://github.com/kubernetes-sigs/gateway-api/releases/download/v1.4.0/standard-install.yaml

# Return to main branch
git checkout main
```

---

## Troubleshooting

### Pods in CrashLoopBackOff

```bash
kubectl logs -n <namespace> <pod-name> --previous
```

Common causes:
- Missing secrets (API key not created in the right namespace)
- Wrong service account permissions
- Image pull errors (check pod events with `kubectl describe pod`)

### Agent Gateway not accepting backends

```bash
kubectl describe agentgatewaybackend -n agentgateway-system
```

Check that the CRD version matches the installed Helm chart version.

### 404 from gateway

```bash
kubectl describe httproute -n agentgateway-system
```

Check that the HTTPRoute's `parentRef` matches an existing Gateway and that the route is `Accepted`.

### MCP tools not discovered (Step 6)

```bash
# Verify DNS resolution from the gateway pod
kubectl exec -n agentgateway-system deploy/enterprise-agentgateway -- \
  nslookup catalog-service.cloud-cart-support.svc

# Check MCP service ports
kubectl get svc -n cloud-cart-support
```

Ensure the backend hostnames in `backends-mcp.yaml` match the actual service namespace (`cloud-cart-support`, not `cloud-cart`).

### Rate limit triggering unexpectedly (Step 4)

Check the policy unit — `requests: 20` with `unit: Minutes` means 20 per minute, not per second.

```bash
kubectl get enterpriseagentgatewaypolicy -n agentgateway-system -o yaml
```

### Image pull errors

Container images are public on GHCR (`ghcr.io/btjimerson/cloud-cart-support-java/*`). If you see `ErrImagePull`, verify the image tag matches the branch name:

```bash
kubectl describe pod -n cloud-cart-support <pod-name> | grep -A2 "Image:"
```

### Enterprise Agent Gateway controller Unauthorized / no proxy pod

If the controller logs show `watch error: Unauthorized` or the Gateway proxy pod never appears, the ServiceAccount may be missing. Recreate it:

```bash
kubectl apply -f - <<EOF
apiVersion: v1
kind: ServiceAccount
metadata:
  name: enterprise-agentgateway
  namespace: agentgateway-system
automountServiceAccountToken: true
EOF

kubectl rollout restart deploy/enterprise-agentgateway -n agentgateway-system
```

### Gateway name conflict with controller Deployment

The Gateway resource **must not** be named `enterprise-agentgateway` — that name is used by the Helm-installed controller Deployment. The controller tries to create a proxy Deployment with the same name as the Gateway, and Kubernetes rejects it because label selectors are immutable. Use `agentgateway` as the Gateway name instead.

### Content blocks format (Spring AI)

Agent Gateway 2.2.x supports Anthropic's content blocks format (`"content": [{"type":"text","text":"..."}]`) via the `ai-routes` policy. If you see `"messages: at least one message is required"` errors, ensure:
1. You are running Agent Gateway **2.2.0-beta.4+** (not 2.1.x)
2. The `policy-ai-routes.yaml` is applied, which enables bidirectional protocol translation between `/v1/messages` and `/v1/chat/completions`

### Model "placeholder" 404

If you see `"model: placeholder"` errors from Anthropic (HTTP 404), the model is not being overridden. Set the model on the `AgentgatewayBackend` resource (`spec.ai.provider.anthropic.model`) rather than using `AgentgatewayPolicy` overrides — backend-level model configuration takes precedence over client-sent values.
