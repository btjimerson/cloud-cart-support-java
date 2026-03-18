#!/usr/bin/env bash
set -euo pipefail

# Requires ENTERPRISE_AGENTGATEWAY_LICENSE_KEY and ENTERPRISE_AGENTGATEWAY_VERSION
# env vars (e.g. source .env)
if [ -z "${ENTERPRISE_AGENTGATEWAY_LICENSE_KEY:-}" ]; then
  echo "ERROR: ENTERPRISE_AGENTGATEWAY_LICENSE_KEY is not set. Source your .env file first."
  exit 1
fi

VERSION="${ENTERPRISE_AGENTGATEWAY_VERSION:-2.1.2}"

echo "==> Installing Gateway API CRDs..."
kubectl apply -f https://github.com/kubernetes-sigs/gateway-api/releases/download/v1.4.0/standard-install.yaml

echo "==> Installing Enterprise Agent Gateway CRDs (v${VERSION})..."
helm upgrade -i enterprise-agentgateway-crds \
  oci://us-docker.pkg.dev/solo-public/enterprise-agentgateway/charts/enterprise-agentgateway-crds \
  --namespace agentgateway-system --create-namespace --version "${VERSION}"

echo "==> Installing Enterprise Agent Gateway control plane (v${VERSION})..."
helm upgrade -i enterprise-agentgateway \
  oci://us-docker.pkg.dev/solo-public/enterprise-agentgateway/charts/enterprise-agentgateway \
  -n agentgateway-system --version "${VERSION}" \
  --set licensing.licenseKey="${ENTERPRISE_AGENTGATEWAY_LICENSE_KEY}"

echo "==> Agent Gateway installation complete."
