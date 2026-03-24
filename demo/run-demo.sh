#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

# ---------------------------------------------------------------------------
# Colors & helpers
# ---------------------------------------------------------------------------
BOLD='\033[1m'
CYAN='\033[1;36m'
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
DIM='\033[2m'
RESET='\033[0m'

banner() {
  echo ""
  echo -e "${CYAN}════════════════════════════════════════════════════════════════${RESET}"
  echo -e "${CYAN}  $1${RESET}"
  echo -e "${CYAN}════════════════════════════════════════════════════════════════${RESET}"
  echo ""
}

info()    { echo -e "${DIM}$*${RESET}"; }
success() { echo -e "${GREEN}  ✔ $*${RESET}"; }
fail()    { echo -e "${RED}  ✘ $*${RESET}"; }
label()   { echo -e "\n${BOLD}▸ $*${RESET}"; }

wait_for_user() {
  echo ""
  if [ -n "${1:-}" ]; then
    echo -e "${YELLOW}─── Next: $1. Press Enter to continue ───${RESET}"
  else
    echo -e "${YELLOW}─── Press Enter to continue ───${RESET}"
  fi
  read -r
}

run_test() {
  local description="$1"
  shift
  echo -e "  ${DIM}$ $*${RESET}"
  local output
  if output=$("$@" 2>&1); then
    success "$description"
    echo "$output" | head -20 | sed 's/^/    /'
  else
    fail "$description"
    echo "$output" | head -20 | sed 's/^/    /'
  fi
}

run_show() {
  local description="$1"
  shift
  echo -e "  ${DIM}$ $*${RESET}"
  "$@" 2>&1 | head -30 | sed 's/^/    /' || true
}

resolve_gateway_ip() {
  if [ -z "${GATEWAY_IP:-}" ]; then
    GATEWAY_IP=$(kubectl get svc cloud-cart-gateway -n kgateway-system \
      -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || true)
  fi
  if [ -z "${GATEWAY_IP:-}" ]; then
    echo -e "${YELLOW}Could not resolve GATEWAY_IP automatically.${RESET}"
    read -rp "Enter the Gateway IP: " GATEWAY_IP
  fi
  export GATEWAY_IP
}

resolve_agw_ip() {
  if [ -z "${AGW_IP:-}" ]; then
    AGW_IP=$(kubectl get svc agentgateway -n agentgateway-system \
      -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || true)
  fi
  export AGW_IP
}

# ---------------------------------------------------------------------------
# Environment setup
# ---------------------------------------------------------------------------
setup_env() {
  banner "Environment Setup"

  # Source .env if it exists
  if [ -f "$REPO_ROOT/.env" ]; then
    info "Sourcing .env file..."
    set -a; source "$REPO_ROOT/.env"; set +a
  fi

  # Defaults for versions
  : "${ENTERPRISE_AGENTGATEWAY_VERSION:=2.2.0-beta.4}"
  : "${ENTERPRISE_KGATEWAY_VERSION:=2.1.2}"
  : "${ENTERPRISE_KAGENT_VERSION:=0.3.11}"

  # Prompt for missing secrets
  if [ -z "${ANTHROPIC_API_KEY:-}" ]; then
    read -rsp "Enter ANTHROPIC_API_KEY: " ANTHROPIC_API_KEY; echo
  fi
  export ANTHROPIC_API_KEY

  if [ -z "${ENTERPRISE_KGATEWAY_LICENSE_KEY:-}" ]; then
    read -rsp "Enter ENTERPRISE_KGATEWAY_LICENSE_KEY: " ENTERPRISE_KGATEWAY_LICENSE_KEY; echo
  fi
  export ENTERPRISE_KGATEWAY_LICENSE_KEY

  if [ -z "${ENTERPRISE_AGENTGATEWAY_LICENSE_KEY:-}" ]; then
    read -rsp "Enter ENTERPRISE_AGENTGATEWAY_LICENSE_KEY: " ENTERPRISE_AGENTGATEWAY_LICENSE_KEY; echo
  fi
  export ENTERPRISE_AGENTGATEWAY_LICENSE_KEY

  if [ -z "${OPENAI_API_KEY:-}" ]; then
    read -rsp "Enter OPENAI_API_KEY (required by kagent): " OPENAI_API_KEY; echo
  fi
  export OPENAI_API_KEY

  export ENTERPRISE_AGENTGATEWAY_VERSION ENTERPRISE_KGATEWAY_VERSION ENTERPRISE_KAGENT_VERSION

  success "Environment configured"
  info "  Agent Gateway version: ${ENTERPRISE_AGENTGATEWAY_VERSION}"
  info "  kgateway version:      ${ENTERPRISE_KGATEWAY_VERSION}"
  info "  kagent version:        ${ENTERPRISE_KAGENT_VERSION}"

  # Prerequisites check
  label "Checking prerequisites"
  for cmd in kubectl helm jq curl git; do
    if command -v "$cmd" &>/dev/null; then
      success "$cmd found"
    else
      fail "$cmd not found — please install it"
      exit 1
    fi
  done

  # Cluster connectivity
  if kubectl cluster-info &>/dev/null; then
    success "Cluster reachable"
  else
    fail "Cannot reach Kubernetes cluster"
    exit 1
  fi
}

# ---------------------------------------------------------------------------
# Infrastructure install (one-time)
# ---------------------------------------------------------------------------
install_infra() {
  banner "Installing Infrastructure"

  label "Gateway API CRDs"
  kubectl apply -f https://github.com/kubernetes-sigs/gateway-api/releases/download/v1.5.0/standard-install.yaml 2>&1 | tail -3

  label "Enterprise kgateway"
  helm upgrade -i enterprise-kgateway-crds \
    oci://us-docker.pkg.dev/solo-public/enterprise-kgateway/charts/enterprise-kgateway-crds \
    --namespace kgateway-system --create-namespace \
    --version "${ENTERPRISE_KGATEWAY_VERSION}" 2>&1 | tail -3
  helm upgrade -i enterprise-kgateway \
    oci://us-docker.pkg.dev/solo-public/enterprise-kgateway/charts/enterprise-kgateway \
    --namespace kgateway-system \
    --version "${ENTERPRISE_KGATEWAY_VERSION}" \
    --set licensing.licenseKey="${ENTERPRISE_KGATEWAY_LICENSE_KEY}" 2>&1 | tail -3

  label "Enterprise Agent Gateway"
  helm template enterprise-agentgateway-crds \
    oci://us-docker.pkg.dev/solo-public/enterprise-agentgateway/charts/enterprise-agentgateway-crds \
    --version "${ENTERPRISE_AGENTGATEWAY_VERSION}" \
    | kubectl apply --server-side --force-conflicts --validate=false -f - 2>&1 | tail -3
  kubectl create namespace agentgateway-system --dry-run=client -o yaml | kubectl apply -f -
  cat <<VALS > /tmp/agw-values.yaml
licensing:
  licenseKey: "${ENTERPRISE_AGENTGATEWAY_LICENSE_KEY}"
VALS
  helm upgrade -i enterprise-agentgateway \
    oci://us-docker.pkg.dev/solo-public/enterprise-agentgateway/charts/enterprise-agentgateway \
    --namespace agentgateway-system --create-namespace \
    --version "${ENTERPRISE_AGENTGATEWAY_VERSION}" \
    -f /tmp/agw-values.yaml 2>&1 | tail -3
  rm -f /tmp/agw-values.yaml

  label "kgateway ingress resources"
  kubectl apply -f k8s/kgateway/gateway.yaml 2>&1 | tail -3
  kubectl apply -f k8s/kgateway/httplistenerpolicy.yaml 2>&1 | tail -3

  label "Waiting for infrastructure pods..."
  kubectl rollout status deploy -n kgateway-system --timeout=120s 2>&1 | tail -5
  kubectl rollout status deploy -n agentgateway-system --timeout=120s 2>&1 | tail -5

  label "Verifying infrastructure"
  run_show "Gateway classes" kubectl get gatewayclasses
  run_show "kgateway pods" kubectl get pods -n kgateway-system
  run_show "Agent Gateway pods" kubectl get pods -n agentgateway-system

  success "Infrastructure ready"
}

# ---------------------------------------------------------------------------
# kagent install
# ---------------------------------------------------------------------------
install_kagent() {
  banner "Installing kagent"

  if [ -z "${ENTERPRISE_KAGENT_LICENSE_KEY:-}" ]; then
    read -rsp "Enter ENTERPRISE_KAGENT_LICENSE_KEY (leave blank for versions that don't require one): " ENTERPRISE_KAGENT_LICENSE_KEY; echo
  fi
  export ENTERPRISE_KAGENT_LICENSE_KEY

  label "Management plane"
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
    --values /tmp/kagent-mgmt-values.yaml 2>&1 | tail -3
  rm -f /tmp/kagent-mgmt-values.yaml

  label "Waiting for management plane..."
  kubectl rollout status deploy/solo-enterprise-ui -n kagent --timeout=120s 2>&1 | tail -3

  label "Waiting for OIDC provider..."
  until kubectl run oidc-check --rm -i --restart=Never --image=curlimages/curl -n kagent -- \
    curl -sf http://solo-enterprise-ui.kagent.svc.cluster.local:5556/.well-known/openid-configuration >/dev/null 2>&1; do
    sleep 5; echo "  ...waiting"
  done
  success "OIDC provider ready"

  label "JWT secret"
  openssl genrsa -out /tmp/key.pem 2048 2>/dev/null
  kubectl create secret generic jwt -n kagent \
    --from-file=jwt=/tmp/key.pem --dry-run=client -o yaml | kubectl apply -f -
  rm -f /tmp/key.pem

  label "kagent CRDs"
  helm upgrade -i kagent-crds \
    oci://us-docker.pkg.dev/solo-public/kagent-enterprise-helm/charts/kagent-enterprise-crds \
    -n kagent \
    --version "${ENTERPRISE_KAGENT_VERSION}" 2>&1 | tail -3

  label "kagent control plane"
  cat <<VALS > /tmp/kagent-values.yaml
ui:
  enabled: true
providers:
  default: openAI
  openAI:
    apiKey: ${OPENAI_API_KEY}
VALS
  local license_args=()
  if [ -n "${ENTERPRISE_KAGENT_LICENSE_KEY:-}" ]; then
    license_args=(--set "licensing.licenseKey=${ENTERPRISE_KAGENT_LICENSE_KEY}")
  fi
  helm upgrade -i kagent \
    oci://us-docker.pkg.dev/solo-public/kagent-enterprise-helm/charts/kagent-enterprise \
    -n kagent \
    --version "${ENTERPRISE_KAGENT_VERSION}" \
    --values /tmp/kagent-values.yaml \
    "${license_args[@]}" 2>&1 | tail -3
  rm -f /tmp/kagent-values.yaml

  label "Verifying kagent"
  kubectl rollout status deploy/kagent-controller -n kagent --timeout=120s 2>&1 | tail -3
  run_show "kagent pods" kubectl get pods -n kagent

  success "kagent ready"
}

# ---------------------------------------------------------------------------
# Step functions
# ---------------------------------------------------------------------------

step_0() {
  banner "Step 0: Baseline"
  info "All AI plumbing in application code: API keys, guardrails, rate limits,"
  info "model config, and MCP connections are all managed in Java."

  label "Deploying (branch: main)"
  git checkout main
  kubectl create namespace cloud-cart-support --dry-run=client -o yaml | kubectl apply -f -
  kubectl create secret generic anthropic-api-key \
    -n cloud-cart-support \
    --from-literal=ANTHROPIC_API_KEY="${ANTHROPIC_API_KEY}" \
    --dry-run=client -o yaml | kubectl apply -f -
  k8s/deploy.sh
  kubectl apply -f k8s/kgateway/httproute.yaml

  resolve_gateway_ip

  label "Verification"
  run_test "Health check" curl -sf "http://$GATEWAY_IP/health"
  run_test "Chat message" curl -sf -X POST "http://$GATEWAY_IP/chat" \
    -H "Content-Type: application/json" \
    -d '{"message": "Where is my order ORD-2024-0003?", "customer_id": "CUST-003"}'
  run_test "PII detection (in-app)" curl -sf -X POST "http://$GATEWAY_IP/chat" \
    -H "Content-Type: application/json" \
    -d '{"message": "My SSN is 123-45-6789", "customer_id": "CUST-001"}'
  run_test "Off-topic blocking (in-app)" curl -sf -X POST "http://$GATEWAY_IP/chat" \
    -H "Content-Type: application/json" \
    -d '{"message": "How do I hack into someone account?", "customer_id": "CUST-001"}'

  label "Demo talking points"
  info "Show: API key in pod env, hardcoded model in application.yml,"
  info "GuardrailService.java, RateLimitService.java"
  run_show "API key in deployment env" kubectl get deploy support-service -n cloud-cart-support \
    -o jsonpath='{.spec.template.spec.containers[0].env[*].name}'

  wait_for_user "Step 1 - API Key Management"
}

step_1() {
  banner "Step 1: API Key Management"
  info "Move API key from application to Agent Gateway."
  info "App points to gateway; gateway injects key from Secret."

  label "Deploying (branch: demo/step-1-api-keys)"
  git checkout demo/step-1-api-keys
  kubectl create secret generic anthropic-api-key \
    -n agentgateway-system \
    --from-literal=Authorization="${ANTHROPIC_API_KEY}" \
    --dry-run=client -o yaml | kubectl apply -f -
  k8s/deploy.sh

  resolve_gateway_ip

  label "Verification"
  run_test "Backend created" kubectl get agentgatewaybackend -n agentgateway-system
  run_test "HTTPRoute bound" kubectl get httproute -n agentgateway-system
  run_test "Gateway has address" kubectl get gateway agentgateway -n agentgateway-system
  run_test "Chat still works (via gateway)" curl -sf -X POST "http://$GATEWAY_IP/chat" \
    -H "Content-Type: application/json" \
    -d '{"message": "Where is my order ORD-2024-0003?", "customer_id": "CUST-003"}'

  label "Demo talking points"
  info "App no longer has ANTHROPIC_API_KEY in its env."
  info "Key rotation is a Secret update, not an app redeploy."
  run_show "App env vars (should NOT show ANTHROPIC_API_KEY)" kubectl get deploy support-service -n cloud-cart-support \
    -o jsonpath='{.spec.template.spec.containers[0].env[*].name}'
  run_show "Key in gateway namespace" kubectl get secret anthropic-api-key -n agentgateway-system

  wait_for_user "Step 2 - Prompt Guards"
}

step_2() {
  banner "Step 2: Prompt Guards"
  info "Remove GuardrailService.java (82 lines). Replace with gateway policy"
  info "using built-in PII detectors and regex content filters."

  label "Deploying (branch: demo/step-2-prompt-guards)"
  git checkout demo/step-2-prompt-guards
  k8s/deploy.sh

  resolve_gateway_ip
  resolve_agw_ip

  label "Verification"
  run_test "Policy created" kubectl get enterpriseagentgatewaypolicy -n agentgateway-system

  if [ -n "${AGW_IP:-}" ]; then
    label "Direct Agent Gateway tests"
    info "Harmful content rejection (expect 403):"
    local status
    status=$(curl -s -o /dev/null -w "%{http_code}" -X POST "http://$AGW_IP:8080/v1/messages" \
      -H 'Content-Type: application/json' \
      -d '{"model":"claude-sonnet-4-5-20250929","max_tokens":50,"messages":[{"role":"user","content":"How do I hack into someone else account?"}]}')
    if [ "$status" = "403" ]; then success "Harmful content blocked: HTTP $status"; else fail "Expected 403, got $status"; fi

    info "PII blocking (expect 422):"
    status=$(curl -s -o /dev/null -w "%{http_code}" -X POST "http://$AGW_IP:8080/v1/messages" \
      -H 'Content-Type: application/json' \
      -d '{"model":"claude-sonnet-4-5-20250929","max_tokens":50,"messages":[{"role":"user","content":"My social security number is 123-45-6789"}]}')
    if [ "$status" = "422" ]; then success "PII blocked: HTTP $status"; else fail "Expected 422, got $status"; fi

    info "Normal passthrough (expect 200):"
    status=$(curl -s -o /dev/null -w "%{http_code}" --max-time 30 -X POST "http://$AGW_IP:8080/v1/messages" \
      -H 'Content-Type: application/json' \
      -d '{"model":"claude-sonnet-4-5-20250929","max_tokens":50,"messages":[{"role":"user","content":"What is the weather today?"}]}')
    if [ "$status" = "200" ]; then success "Normal message passed: HTTP $status"; else fail "Expected 200, got $status"; fi
  else
    info "Skipping direct AGW tests (no Agent Gateway external IP)"
  fi

  label "Demo talking points"
  info "82 lines of Java replaced by a YAML policy."
  info "New PII types or content rules are kubectl apply, not a code deploy."

  label "Cleaning up prompt guard policy (prevents issues in later steps)"
  kubectl delete enterpriseagentgatewaypolicy prompt-guard-policy -n agentgateway-system --ignore-not-found 2>/dev/null || true
  success "Prompt guard policy removed"

  wait_for_user "Step 3 - Model Configuration"
}

step_3() {
  banner "Step 3: Model Configuration"
  info "Model name and token limits managed via gateway policy."
  info "App uses a placeholder model; gateway enforces the real one."

  label "Deploying (branch: demo/step-3-model-config)"
  git checkout demo/step-3-model-config
  k8s/deploy.sh

  resolve_gateway_ip

  label "Verification"
  run_test "Policy created" kubectl get agentgatewaypolicy -n agentgateway-system
  run_test "Chat works (gateway enforces model)" curl -sf -X POST "http://$GATEWAY_IP/chat" \
    -H "Content-Type: application/json" \
    -d '{"message": "Can you track order ORD-2024-0003?", "customer_id": "CUST-003"}'

  label "Demo talking points"
  info "Model upgrades are kubectl apply. One YAML field change and every"
  info "app behind the gateway uses the new model."
  run_show "Model set on backend" kubectl get agentgatewaybackend anthropic -n agentgateway-system \
    -o jsonpath='{.spec.ai.provider.anthropic.model}'

  wait_for_user "Step 4 - Rate Limiting"
}

step_4() {
  banner "Step 4: Rate Limiting"
  info "Remove RateLimitService.java (53 lines). Replace with gateway policy."
  info "Rate limits work across replicas."

  label "Deploying (branch: demo/step-4-rate-limiting)"
  git checkout demo/step-4-rate-limiting
  k8s/deploy.sh

  resolve_gateway_ip
  resolve_agw_ip

  label "Verification"
  run_test "Policy created" kubectl get enterpriseagentgatewaypolicy -n agentgateway-system

  if [ -n "${AGW_IP:-}" ]; then
    label "Rate limit burst test (direct against Agent Gateway)"
    for i in $(seq 1 10); do
      local status
      status=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 \
        -X POST "http://$AGW_IP:8080/v1/messages" \
        -H "Content-Type: application/json" \
        -d '{"model":"claude-sonnet-4-5-20250929","max_tokens":1,"messages":[{"role":"user","content":"hi"}]}')
      if [ "$status" = "429" ]; then
        fail "Request $i: HTTP $status (rate limited)"
      else
        success "Request $i: HTTP $status"
      fi
    done
  else
    info "Skipping rate limit tests (no Agent Gateway external IP)"
  fi

  label "Demo talking points"
  info "53 lines of Java + tests replaced by a YAML policy."
  info "Rate limits work across replicas and can be changed without code deploys."

  label "Cleaning up rate limit policy (prevents issues in later steps)"
  kubectl delete enterpriseagentgatewaypolicy rate-limit-policy -n agentgateway-system --ignore-not-found 2>/dev/null || true
  success "Rate limit policy removed"

  wait_for_user "Step 5 - Observability"
}

step_5() {
  banner "Step 5: Observability"
  info "Add gateway-level observability with Prometheus metrics."
  info "No application code changes required."

  label "Applying observability policy (branch: demo/step-5-observability)"
  git checkout demo/step-5-observability
  kubectl apply -f k8s/agentgateway/ --recursive

  resolve_gateway_ip

  label "Verification"
  run_test "Policy created" kubectl get enterpriseagentgatewaypolicy -n agentgateway-system
  run_test "Generate traffic" curl -sf -X POST "http://$GATEWAY_IP/chat" \
    -H "Content-Type: application/json" \
    -d '{"message": "What products do you have?", "customer_id": "CUST-001"}'

  label "Prometheus metrics (port-forwarding briefly)"
  kubectl port-forward -n agentgateway-system deploy/agentgateway 15020:15020 &>/dev/null &
  local pf_pid=$!
  sleep 3
  run_show "gen_ai metrics" bash -c "curl -s localhost:15020/stats/prometheus 2>/dev/null | grep gen_ai | head -20"
  kill $pf_pid 2>/dev/null || true

  label "Demo talking points"
  info "Zero code changes. Gateway emits LLM-aware metrics for every request."
  run_show "No app code changes" git diff demo/step-4-rate-limiting -- support-service/src/

  wait_for_user "Step 6 - MCP Federation"
}

step_6() {
  banner "Step 6: MCP Federation"
  info "Replace 4 hardcoded MCP connections with a single gateway endpoint."
  info "Gateway federates all MCP backends."

  label "Deploying (branch: demo/step-6-mcp-federation)"
  git checkout demo/step-6-mcp-federation
  k8s/deploy.sh

  resolve_gateway_ip

  label "Verification"
  run_test "MCP backends registered" kubectl get agentgatewaybackend -n agentgateway-system
  run_test "MCP route bound" kubectl get httproute -n agentgateway-system
  run_test "Product search (MCP tools)" curl -sf -X POST "http://$GATEWAY_IP/chat" \
    -H "Content-Type: application/json" \
    -d '{"message": "What products do you have in electronics?", "customer_id": "CUST-001"}'
  run_test "Order lookup (MCP tools)" curl -sf -X POST "http://$GATEWAY_IP/chat" \
    -H "Content-Type: application/json" \
    -d '{"message": "Can you track order ORD-2024-0003?", "customer_id": "CUST-003"}'

  label "Demo talking points"
  info "App has a single MCP connection. Adding a new MCP server is kubectl apply."
  run_show "Federated MCP backends" kubectl get agentgatewaybackend -n agentgateway-system

  wait_for_user "Step 7 - Declarative Agents"
}

step_7() {
  banner "Step 7: Declarative Agents"
  info "Replace all Java agent orchestration (~3,400 lines) with kagent CRDs."
  info "Agent definitions, system prompts, and tool bindings become YAML."

  label "Deploying (branch: demo/step-7-declarative-agents)"
  git checkout demo/step-7-declarative-agents
  k8s/deploy.sh

  resolve_gateway_ip

  label "Verification"
  run_test "Agents as K8s resources" kubectl get agents -n kagent
  run_test "ModelConfig" kubectl get modelconfig -n kagent
  run_test "RemoteMCPServers" kubectl get remotemcpserver -n kagent

  label "Waiting for all agents to be accepted"
  local max_wait=120 elapsed=0
  while [ $elapsed -lt $max_wait ]; do
    local total accepted
    total=$(kubectl get agents -n kagent --no-headers 2>/dev/null | wc -l | tr -d ' ')
    accepted=$(kubectl get agents -n kagent --no-headers 2>/dev/null | awk '$NF == "True"' | wc -l | tr -d ' ')
    if [ "$total" -gt 0 ] && [ "$accepted" -eq "$total" ]; then
      success "All $total agents accepted"
      break
    fi
    echo -e "  ${DIM}...waiting ($accepted/$total accepted, ${elapsed}s)${RESET}"
    sleep 10
    elapsed=$((elapsed + 10))
  done
  if [ $elapsed -ge $max_wait ]; then
    fail "Not all agents accepted after ${max_wait}s"
    run_show "Agent status" kubectl get agents -n kagent
    for agent in $(kubectl get agents -n kagent --no-headers | awk '$NF != "True" {print $1}'); do
      info "--- $agent ---"
      kubectl get agent "$agent" -n kagent -o jsonpath='{.status.conditions[*].message}' 2>/dev/null
      echo ""
    done
  fi

  # Workaround for kagent 0.3.9 bug: controller generates tools:null instead of
  # tools:[] in agent config secrets, causing pydantic validation failure.
  # Fix: patch secrets, restart pods, then wait for Ready.
  label "Applying kagent tools:null workaround (0.3.9 bug)"
  info "Patching agent config secrets: tools:null -> tools:[]"
  kubectl scale deploy kagent-controller -n kagent --replicas=0 --timeout=30s 2>/dev/null
  sleep 5
  local patched=false
  for agent_name in $(kubectl get agents -n kagent --no-headers | awk '{print $1}'); do
    local has_null
    has_null=$(kubectl get secret "$agent_name" -n kagent -o jsonpath='{.data.config\.json}' 2>/dev/null \
      | base64 -d 2>/dev/null \
      | python3 -c "import sys,json; d=json.loads(sys.stdin.buffer.read(),strict=False); has=any(t.get('tools') is None for t in (d.get('sse_tools') or [])); print('yes' if has else 'no')" 2>/dev/null || echo "skip")
    if [ "$has_null" = "yes" ]; then
      local new_b64
      new_b64=$(kubectl get secret "$agent_name" -n kagent -o jsonpath='{.data.config\.json}' \
        | base64 -d \
        | python3 -c "
import sys, json, base64
d = json.loads(sys.stdin.buffer.read(), strict=False)
for t in (d.get('sse_tools') or []):
    if t.get('tools') is None:
        t['tools'] = []
print(base64.b64encode(json.dumps(d).encode()).decode())
")
      kubectl patch secret "$agent_name" -n kagent --type='json' \
        -p="[{\"op\":\"replace\",\"path\":\"/data/config.json\",\"value\":\"${new_b64}\"}]" &>/dev/null
      kubectl delete pod -n kagent -l "app.kubernetes.io/name=$agent_name" &>/dev/null
      success "Patched $agent_name"
      patched=true
    fi
  done
  if [ "$patched" = false ]; then
    info "No agents needed patching"
  fi
  kubectl scale deploy kagent-controller -n kagent --replicas=1 --timeout=30s 2>/dev/null

  label "Waiting for all agents to be ready"
  max_wait=180 elapsed=0
  while [ $elapsed -lt $max_wait ]; do
    local total ready
    total=$(kubectl get agents -n kagent --no-headers 2>/dev/null | wc -l | tr -d ' ')
    ready=$(kubectl get agents -n kagent --no-headers 2>/dev/null | awk '$3 == "True"' | wc -l | tr -d ' ')
    if [ "$total" -gt 0 ] && [ "$ready" -eq "$total" ]; then
      success "All $total agents ready"
      break
    fi
    echo -e "  ${DIM}...waiting ($ready/$total ready, ${elapsed}s)${RESET}"
    sleep 10
    elapsed=$((elapsed + 10))
  done
  if [ $elapsed -ge $max_wait ]; then
    fail "Not all agents ready after ${max_wait}s"
    run_show "Agent status" kubectl get agents -n kagent -o wide
    for agent_name in $(kubectl get agents -n kagent --no-headers | awk '$3 != "True" {print $1}'); do
      info "--- $agent_name ---"
      kubectl describe agent "$agent_name" -n kagent | tail -10
      echo ""
    done
  fi

  label "Waiting for kagent controller to be ready"
  max_wait=60 elapsed=0
  while [ $elapsed -lt $max_wait ]; do
    if kubectl exec -n cloud-cart-support deploy/support-service -- \
        curl -sf -o /dev/null "http://kagent-controller.kagent.svc:8083/health" 2>/dev/null; then
      success "kagent controller is ready"
      break
    fi
    echo -e "  ${DIM}...waiting (${elapsed}s)${RESET}"
    sleep 5
    elapsed=$((elapsed + 5))
  done
  if [ $elapsed -ge $max_wait ]; then
    fail "kagent controller not ready after ${max_wait}s"
  fi

  label "Testing chat via A2A"
  local chat_response
  chat_response=$(curl -s -X POST "http://$GATEWAY_IP/chat" \
    -H "Content-Type: application/json" \
    -d '{"message": "Hello, what can you help me with?"}' 2>&1)
  local chat_code=$?
  if [ $chat_code -ne 0 ]; then
    fail "Chat request failed (curl exit code $chat_code)"
  elif echo "$chat_response" | grep -qi "error\|apologize\|encountered\|rate limit\|blocked"; then
    fail "Chat returned error response:"
    echo "$chat_response" | python3 -m json.tool 2>/dev/null || echo "$chat_response"
  else
    success "Chat via A2A succeeded"
    echo "$chat_response" | python3 -m json.tool 2>/dev/null || echo "$chat_response"
  fi

  label "Demo talking points"
  info "3,400 lines of Java replaced by ~150 lines of code + YAML CRDs."
  info "Agent definitions visible with kubectl. System prompt changes are kubectl edit."
  run_show "Agents" kubectl get agents -n kagent
  run_show "Code reduction" git diff --stat demo/step-6-mcp-federation

  wait_for_user "Step 8 - Agent Tracing"
}

# ---------------------------------------------------------------------------
# Step 8: Agent Tracing
# ---------------------------------------------------------------------------
step_8() {
  banner "Step 8: Agent Tracing"
  info "View agent execution traces in the kagent Enterprise UI."
  info "Every LLM call, tool invocation, and agent delegation is automatically captured."

  label "Deploying (branch: demo/step-8-agent-tracing)"
  git checkout demo/step-8-agent-tracing

  resolve_gateway_ip

  label "Generate a traced request"
  run_show "Chat request (generates trace)" curl -s -X POST "http://$GATEWAY_IP/chat" \
    -H "Content-Type: application/json" \
    -d '{"message": "What products do you have in electronics?"}'

  label "Launch kagent Enterprise UI"
  info "Port-forwarding kagent Enterprise UI to localhost:4000..."
  kubectl port-forward svc/solo-enterprise-ui -n kagent 4000:80 &>/dev/null &
  local PF_PID=$!
  sleep 2
  open http://localhost:4000 2>/dev/null || info "Open http://localhost:4000 in your browser"

  info ""
  info "In the kagent UI:"
  info "  1. Click 'Tracing' in the left navigation menu"
  info "  2. Find the trace for the request just sent"
  info "  3. Click the trace to see the full execution flow:"
  info "     - Router agent receives the user message"
  info "     - Router delegates to product-agent via A2A"
  info "     - Product-agent calls catalog-service MCP tools"
  info "     - Response flows back through the agent chain"
  info "  4. Each span shows: LLM model, token usage, tool calls, and latency"

  label "Demo talking points"
  info "Every agent interaction is automatically traced — no code instrumentation needed."
  info "kagent captures the full delegation chain, tool invocations, and LLM calls."
  info "Combined with the gateway observability from Step 5, you get end-to-end visibility."

  wait_for_user "Cleanup"

  # Clean up port-forward
  kill $PF_PID 2>/dev/null || true
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
START_STEP="${1:-0}"
SKIP_INFRA="${SKIP_INFRA:-false}"

setup_env

if [ "$SKIP_INFRA" != "true" ]; then
  label "Infrastructure check"
  if kubectl get gatewayclasses enterprise-agentgateway &>/dev/null 2>&1; then
    success "Infrastructure already installed (Agent Gateway GatewayClass found)"
    info "Set SKIP_INFRA=true to always skip, or press Enter to continue."
  else
    install_infra
  fi

  label "kagent check"
  if kubectl get crd agents.kagent.dev &>/dev/null && kubectl get ns kagent &>/dev/null; then
    success "kagent already installed"
  else
    install_kagent
  fi
fi

# Run steps
for step in $(seq "$START_STEP" 8); do
  "step_$step"
done

banner "Demo Complete!"
echo -e "${GREEN}All steps completed successfully.${RESET}"
echo ""
info "To clean up: k8s/cleanup.sh"
