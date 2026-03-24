#!/usr/bin/env bash
set -euo pipefail

echo "==> Cleaning up Cloud Cart Support demo..."

# --- 1. Delete application namespace ---
echo "==> Deleting application namespace..."
kubectl delete httproute --all -n cloud-cart-support --ignore-not-found 2>/dev/null || true
kubectl delete namespace cloud-cart-support --ignore-not-found --wait=false

# --- 2. Delete kagent resources (Step 7) ---
echo "==> Cleaning up kagent resources..."
kubectl delete agents --all -n kagent --ignore-not-found 2>/dev/null || true
kubectl delete modelconfig --all -n kagent --ignore-not-found 2>/dev/null || true
kubectl delete remotemcpserver --all -n kagent --ignore-not-found 2>/dev/null || true
kubectl delete secret kagent-llm-key -n kagent --ignore-not-found 2>/dev/null || true
kubectl delete secret jwt -n kagent --ignore-not-found 2>/dev/null || true

echo "==> Uninstalling kagent helm releases..."
helm uninstall kagent -n kagent 2>/dev/null || true
helm uninstall kagent-crds -n kagent 2>/dev/null || true
helm uninstall kagent-mgmt -n kagent 2>/dev/null || true
kubectl get crds -o name 2>/dev/null | grep kagent | xargs kubectl delete --ignore-not-found 2>/dev/null || true
kubectl delete namespace kagent --ignore-not-found --wait=false

# --- 3. Delete Agent Gateway CRDs and resources ---
echo "==> Cleaning up Agent Gateway resources..."
kubectl delete agentgatewaybackend --all -n agentgateway-system --ignore-not-found 2>/dev/null || true
kubectl delete httproute --all -n agentgateway-system --ignore-not-found 2>/dev/null || true
kubectl delete gateway enterprise-agentgateway -n agentgateway-system --ignore-not-found 2>/dev/null || true
kubectl delete secret anthropic-api-key -n agentgateway-system --ignore-not-found 2>/dev/null || true

echo "==> Uninstalling Enterprise Agent Gateway..."
helm uninstall enterprise-agentgateway -n agentgateway-system 2>/dev/null || true
helm uninstall enterprise-agentgateway-crds -n agentgateway-system 2>/dev/null || true
kubectl get crds -o name 2>/dev/null | grep 'solo\|agentgateway' | xargs kubectl delete --ignore-not-found 2>/dev/null || true
kubectl delete namespace agentgateway-system --ignore-not-found --wait=false

# --- 4. Delete kgateway resources ---
echo "==> Cleaning up kgateway resources..."
kubectl delete gateway cloud-cart-gateway -n kgateway-system --ignore-not-found 2>/dev/null || true
kubectl delete httproute --all -n kgateway-system --ignore-not-found 2>/dev/null || true
kubectl delete httplistenerpolicy --all -n kgateway-system --ignore-not-found 2>/dev/null || true

echo "==> Uninstalling Enterprise kgateway..."
helm uninstall enterprise-kgateway -n kgateway-system 2>/dev/null || true
helm uninstall enterprise-kgateway-crds -n kgateway-system 2>/dev/null || true
kubectl delete namespace kgateway-system --ignore-not-found --wait=false

# --- 5. Remove Gateway API CRDs ---
echo "==> Removing Gateway API CRDs..."
kubectl delete -f https://github.com/kubernetes-sigs/gateway-api/releases/download/v1.4.0/standard-install.yaml 2>/dev/null || true

# --- 6. Return to main branch ---
echo "==> Returning to main branch..."
git checkout main 2>/dev/null || true

echo ""
echo "==> Cleanup complete!"
