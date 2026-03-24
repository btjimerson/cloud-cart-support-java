#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# --- 1. Create namespace and secret ---
echo "==> Creating namespace and secret..."
kubectl apply -f "${SCRIPT_DIR}/namespace.yaml"
if [ -n "${ANTHROPIC_API_KEY:-}" ]; then
  kubectl create secret generic anthropic-api-key \
    -n cloud-cart-support \
    --from-literal=ANTHROPIC_API_KEY="${ANTHROPIC_API_KEY}" \
    --dry-run=client -o yaml | kubectl apply -f -
else
  kubectl apply -f "${SCRIPT_DIR}/secret.yaml"
fi

# --- 2. Deploy application services ---
echo "==> Deploying application services..."
kubectl apply -f "${SCRIPT_DIR}/catalog-service.yaml"
kubectl apply -f "${SCRIPT_DIR}/orders-service.yaml"
kubectl apply -f "${SCRIPT_DIR}/customers-service.yaml"
kubectl apply -f "${SCRIPT_DIR}/notifications-service.yaml"
kubectl apply -f "${SCRIPT_DIR}/support-service.yaml"

# --- 3. Apply Agent Gateway CRDs (if any exist) ---
if ls "${SCRIPT_DIR}"/agentgateway/*.yaml 1>/dev/null 2>&1; then
  echo "==> Applying Agent Gateway CRDs..."
  kubectl apply -f "${SCRIPT_DIR}/agentgateway/" --recursive
fi

# --- 4. Apply kagent CRDs (if any exist) ---
if ls "${SCRIPT_DIR}"/kagent/*.yaml 1>/dev/null 2>&1; then
  echo "==> Applying kagent CRDs..."
  # Create API key secret for kagent ModelConfig
  kubectl create secret generic kagent-llm-key \
    -n kagent \
    --from-literal=API_KEY="${OPENAI_API_KEY}" \
    --dry-run=client -o yaml | kubectl apply -f -
  kubectl apply -f "${SCRIPT_DIR}/kagent/" --recursive
fi

# --- 5. Wait for rollouts ---
echo "==> Waiting for deployments to become ready..."
kubectl rollout status deployment/catalog-service -n cloud-cart-support --timeout=120s
kubectl rollout status deployment/orders-service -n cloud-cart-support --timeout=120s
kubectl rollout status deployment/customers-service -n cloud-cart-support --timeout=120s
kubectl rollout status deployment/notifications-service -n cloud-cart-support --timeout=120s
kubectl rollout status deployment/support-service -n cloud-cart-support --timeout=120s

echo ""
echo "==> Deployment complete!"
echo "    Port-forward to access the UI:"
echo "    kubectl port-forward svc/support-service -n cloud-cart-support 8080:8080"
