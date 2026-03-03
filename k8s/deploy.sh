#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# --- 1. Create namespace and secret ---
echo "==> Creating namespace and secret..."
kubectl apply -f "${SCRIPT_DIR}/namespace.yaml"
kubectl apply -f "${SCRIPT_DIR}/secret.yaml"

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

# --- 4. Wait for rollouts ---
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
