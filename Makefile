# DX5a — one-command demo / dev convenience targets.
#
# The `demo-*` targets wrap the layered docker-compose invocation
# documented in `infrastructure-local/README-demo.md`. The intent
# is that someone with `git clone` + `docker` installed can run
# `make demo-up` and have a complete, seeded shepard at
# http://localhost:3000 (frontend) / :8080 (backend) in well under
# 90 seconds (assuming images are cached).
#
# Targets:
#   demo-up        bring up the seeded demo
#   demo-down      stop the demo (keep data volumes)
#   demo-reset     stop + nuke data volumes for a clean re-seed
#   demo-seed      re-run the seeder (idempotent)
#   demo-status    print plugins + seeded Collections
#   demo-logs      tail backend logs
#   demo-smoke     run the post-up smoke test
#   help           show this list

COMPOSE := docker compose \
	--env-file infrastructure-local/.env.demo \
	-f infrastructure-local/docker-compose.yml \
	-f infrastructure-local/docker-compose.demo.yml

ADMIN_API_KEY ?= demo-admin-api-key-value-not-for-prod

.PHONY: demo-up demo-down demo-reset demo-seed demo-status demo-logs demo-smoke help

demo-up:  ## Bring up the seeded demo (frontend, backend, dbs, OIDC, plugins, seed data).
	@echo "==> Building images (~2-3 min on first run, cached after)..."
	$(COMPOSE) build
	@echo "==> Starting services..."
	$(COMPOSE) up -d
	@echo "==> Waiting for backend health (up to 90s)..."
	@./infrastructure-local/wait-for-backend.sh
	@echo "==> Running the demo-seeder (idempotent)..."
	$(COMPOSE) run --rm demo-seeder
	@echo ""
	@echo "    +-----------------------------------------------------+"
	@echo "    | Demo shepard is up.                                 |"
	@echo "    |                                                     |"
	@echo "    | Frontend:   http://localhost:3000                   |"
	@echo "    | Backend:    http://localhost:8080/q/health/ready    |"
	@echo "    | Plugins:    http://localhost:8080/v2/admin/plugins  |"
	@echo "    | Keycloak:   http://localhost:8082/realms/shepard-demo |"
	@echo "    |                                                     |"
	@echo "    | Test users  (password = <user>-demo):               |"
	@echo "    |   alice / alice-demo         (Manager)              |"
	@echo "    |   bob   / bob-demo           (Reader)               |"
	@echo "    |   admin / admin-demo         (instance-admin)       |"
	@echo "    |   harvester / harvester-demo (service)              |"
	@echo "    |                                                     |"
	@echo "    | Admin API key (for shepard-admin):                  |"
	@echo "    |   $(ADMIN_API_KEY)         |"
	@echo "    +-----------------------------------------------------+"
	@echo ""
	@echo "    Run 'make demo-status' to inspect what's loaded."
	@echo "    Run 'make demo-smoke'  to verify the deploy."
	@echo "    Run 'make demo-down'   to stop."

demo-down:  ## Stop the demo (keeps data volumes for fast re-up).
	$(COMPOSE) down

demo-reset:  ## Stop + nuke data volumes (next demo-up re-seeds from scratch).
	$(COMPOSE) down -v
	@echo "==> Data volumes nuked. Next 'make demo-up' will re-seed."

demo-seed:  ## Re-run the seeder (idempotent; safe on a running stack).
	$(COMPOSE) run --rm demo-seeder

demo-status:  ## Show plugins installed + Collections seeded.
	@echo "==> Plugins installed:"
	@curl -sS -H "X-API-KEY: $(ADMIN_API_KEY)" \
		http://localhost:8080/v2/admin/plugins \
		| (command -v jq >/dev/null && jq -r '.plugins[] | "  " + (.id // "?") + " v" + (.version // "?") + " " + (.state // "?")' \
		  || cat)
	@echo ""
	@echo "==> Seeded Collections:"
	@$(COMPOSE) exec -T neo4j cypher-shell \
		-u neo4j -p "$${NEO4J_PW:-demo-neo4j-password}" \
		--format plain \
		"MATCH (c:Collection) WHERE c.appId STARTS WITH 'demo-collection-' RETURN c.appId AS appId, c.name AS name ORDER BY c.shepardId LIMIT 20;" \
		2>/dev/null || echo "  (neo4j container not running — start with 'make demo-up')"

demo-logs:  ## Tail backend logs (Ctrl-C to detach).
	$(COMPOSE) logs -f backend

demo-smoke:  ## Run the post-up smoke test against the running demo.
	@./infrastructure-local/demo-smoke-test.sh

help:  ## Show available make targets.
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'
