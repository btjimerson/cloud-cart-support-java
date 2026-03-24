# Cloud Cart Support - Claude Code Context

## Project Overview

Multi-agent AI customer service demo using Spring Boot, Spring AI, and MCP (SSE transport). Progressive demo across 9 Git branches (main + demo/step-1 through demo/step-8) showing migration from in-app AI plumbing to Solo.io Enterprise Agent Gateway and kagent.

## Branch Strategy

All shared files (demo script, recipe, cleanup, README) must be identical across all 9 branches. When making changes, always propagate to: `main`, `demo/step-1-api-keys`, `demo/step-2-prompt-guards`, `demo/step-3-model-config`, `demo/step-4-rate-limiting`, `demo/step-5-observability`, `demo/step-6-mcp-federation`, `demo/step-7-declarative-agents`, `demo/step-8-agent-tracing`.

## File Change Checklist

When modifying files in this repo, check these dependencies:

- **Version bump** → update in: `.env.example`, `demo/run-demo.sh`, `docs/recipe.md`, `README.md`, `k8s/cleanup.sh`
- **Demo script change** → mirror in `docs/recipe.md` (and vice versa)
- **K8s resource change** → check `k8s/deploy.sh`, `k8s/cleanup.sh`, `demo/run-demo.sh`, `docs/recipe.md`
- **Helm values change** → update in both `demo/run-demo.sh` and `docs/recipe.md`
- **Service name/port change** → update `k8s/kagent/remote-mcp-servers.yaml`, `k8s/support-service.yaml` env vars
- **Tool added/removed in Java** → update `k8s/kagent/agents.yaml` toolNames
- **All changes** → propagate to all 9 branches (main + step-1 through step-8)

## File Dependency Groups

### Versions (must stay in sync)
| Value | `.env.example` | `demo/run-demo.sh` | `docs/recipe.md` | `README.md` | `k8s/cleanup.sh` |
|-------|:-:|:-:|:-:|:-:|:-:|
| ENTERPRISE_KAGENT_VERSION | ✓ | ✓ | ✓ | ✓ | |
| ENTERPRISE_KGATEWAY_VERSION | ✓ | ✓ | ✓ | ✓ | |
| ENTERPRISE_AGENTGATEWAY_VERSION | ✓ | ✓ | ✓ | ✓ | |
| Gateway API CRD version | | ✓ | ✓ (x2) | ✓ | ✓ |

### Demo flow (always update together)
- `demo/run-demo.sh` ↔ `docs/recipe.md` — mirror each other step-by-step
- `k8s/cleanup.sh` — must clean up everything the demo creates
- `README.md` — high-level description of steps, versions, prerequisites

### Kubernetes resources
- `k8s/*.yaml` (services) → referenced by `k8s/deploy.sh`, `k8s/kagent/remote-mcp-servers.yaml` (URLs)
- `k8s/kagent/agents.yaml` → toolNames must match Java `@Tool` annotations
- `k8s/kagent/remote-mcp-servers.yaml` → URLs must match service names/ports

### Helm values
- `demo/run-demo.sh` kagent values heredoc ↔ `docs/recipe.md` kagent values heredoc — must match

### Environment
- `.env.example` versions must match defaults in `demo/run-demo.sh`
