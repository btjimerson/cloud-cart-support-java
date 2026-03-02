#!/usr/bin/env bash
set -euo pipefail

echo "==> Installing Gateway API CRDs..."
kubectl apply -f https://github.com/kubernetes-sigs/gateway-api/releases/download/v1.4.0/standard-install.yaml

echo "==> Installing Enterprise Agent Gateway CRDs..."
helm upgrade -i enterprise-agentgateway-crds \
  oci://us-docker.pkg.dev/solo-public/enterprise-agentgateway/charts/enterprise-agentgateway-crds \
  --namespace agentgateway-system --create-namespace --version 2.1.1

echo "==> Installing Enterprise Agent Gateway control plane..."
helm upgrade -i enterprise-agentgateway \
  oci://us-docker.pkg.dev/solo-public/enterprise-agentgateway/charts/enterprise-agentgateway \
  -n agentgateway-system --version 2.1.1

echo "==> Agent Gateway installation complete."
