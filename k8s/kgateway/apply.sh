#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "==> Applying kgateway Gateway..."
kubectl apply -f "${SCRIPT_DIR}/gateway.yaml"

echo "==> Applying HTTPRoute..."
kubectl apply -f "${SCRIPT_DIR}/httproute.yaml"

echo "==> Applying HTTPListenerPolicy (WebSocket upgrade)..."
kubectl apply -f "${SCRIPT_DIR}/httplistenerpolicy.yaml"

echo ""
echo "==> kgateway resources applied."
echo "    Get the external IP:"
echo "    kubectl get svc cloud-cart-gateway -n kgateway-system -o jsonpath='{.status.loadBalancer.ingress[0].ip}'"
