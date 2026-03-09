#!/usr/bin/env bash
set -euo pipefail

REGISTRY="ghcr.io/btjimerson/cloud-cart-support-java"
SERVICES=(catalog-service orders-service customers-service notifications-service support-service)

cd "$(dirname "$0")/.."

for svc in "${SERVICES[@]}"; do
  echo "==> Building ${svc}..."
  docker build -t "${REGISTRY}/${svc}:latest" -f "${svc}/Dockerfile" .
  echo "==> Pushing ${svc}..."
  docker push "${REGISTRY}/${svc}:latest"
done

echo "==> All images built and pushed."
