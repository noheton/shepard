# shepard local build / deploy Makefile
#
# Background: the Dockerfiles copy pre-built artifacts, so you MUST run
# the language-specific build before docker build:
#   backend  → mvn package → docker build  (image: shepard-backend-patched:local)
#   frontend → npm run build → docker build (image: shepard-frontend:local)
#
# Usage:
#   make build-backend         compile + package backend JAR via Maven
#   make image-backend         docker build the backend image (requires build-backend first)
#   make build-frontend        Nuxt build (generates frontend/.output)
#   make image-frontend        docker build the frontend image (requires build-frontend first)
#   make deploy                docker compose up -d for backend + frontend (no verify)
#   make redeploy-backend      build-backend + image-backend + deploy + wait + smoke
#   make redeploy-frontend     build-frontend + image-frontend + deploy + wait + smoke
#   make redeploy              full rebuild and deploy of both services + wait + smoke
#   make redeploy-fast         like `redeploy` but without the post-deploy smoke test
#   make wait-for-health       poll backend /healthz/ready until UP or 120s timeout
#   make smoke                 run infrastructure/smoke-test.sh against the live deploy
#   make check-images          print image timestamps to verify freshness
#   make integration-test      run API integration tests against localhost:8080

BACKEND_IMAGE  := shepard-backend-patched:local
FRONTEND_IMAGE := shepard-frontend:local
COMPOSE_DIR    := ./infrastructure
MVN            := $(CURDIR)/backend/mvnw
# Health endpoint used by `wait-for-health`. Override for non-local deploys
# (e.g. HEALTH_URL=https://shepard-api.nuclide.systems/shepard/api/healthz/ready).
HEALTH_URL     ?= http://localhost:8080/shepard/api/healthz/ready
HEALTH_TIMEOUT ?= 120

.PHONY: build-backend build-plugins image-backend build-frontend image-frontend \
        deploy redeploy-backend redeploy-frontend redeploy redeploy-fast \
        wait-for-health smoke check-images integration-test preflight-env

# ADR-0023 two-pass dance: backend install (no plugins) → plugins install → backend package.
# Required whenever a plugin module changes; safe to run even when only backend changed.
build-plugins:
	# Single source of truth for the full ordered plugin install — also used by
	# .github/workflows/ci.yml + codeql.yml. Installs the backend's always-on
	# fileformat-svdx/-thermography deps, the backend+cli stubs, then every
	# with-plugins plugin. Previously this list was duplicated here and in each
	# CI workflow and drifted (svdx/thermography missing from CI → red main).
	MVN="$(MVN)" bash scripts/install-plugins.sh

build-backend: build-plugins
	# `clean` removes stale class files from previously killed builds.
	# Mixed old+new .class files cause Quarkus CDI augmentation to spin
	# indefinitely on ConcurrentHashMap.computeIfAbsent (CRIT-STALE-CLASSFILES,
	# 2026-05-27). Cost: full recompile (~10 min extra); benefit: no 5h hangs.
	cd backend && $(MVN) clean package -Dmaven.test.skip=true -q

image-backend: build-backend
	docker build -t $(BACKEND_IMAGE) ./backend

build-frontend:
	# Refresh the file:../backend-client dep first — npm version-caches file: deps
	# (the package stays 1.0.0 across regens), so a stale generated client silently
	# survives `npm install`. Force a clean re-copy so the build always uses the
	# current client (the V2-SWEEP-001-CLIENT-REGEN stale-client incident, 2026-06-12).
	rm -rf frontend/node_modules/@dlr-shepard && cd frontend && npm install
	cd frontend && npm run build

image-frontend: build-frontend
	docker build -t $(FRONTEND_IMAGE) ./frontend

# Preflight check: docker compose reads $(COMPOSE_DIR)/.env. Agent worktrees
# don't inherit it from the canonical checkout, so all deploy targets gate on
# this check. If you see the actionable message below, run
# `scripts/setup-worktree.sh` from the worktree root to bootstrap the symlink.
# Surfaced by OPS-WORKTREE-ENV (2026-05-24).
preflight-env:
	@if [ ! -e "$(COMPOSE_DIR)/.env" ]; then \
	  echo "ERROR: $(COMPOSE_DIR)/.env not found." >&2; \
	  echo "       Run: scripts/setup-worktree.sh" >&2; \
	  echo "       (symlinks the canonical /opt/shepard/infrastructure/.env into this worktree.)" >&2; \
	  exit 1; \
	fi

deploy: preflight-env
	cd $(COMPOSE_DIR) && docker compose up -d --no-build backend frontend

# Poll the backend health endpoint until it reports UP, or fail after HEALTH_TIMEOUT seconds.
# Use this between `docker compose up` and `smoke` so the smoke test doesn't
# false-fail on a backend that's still mid-boot (Quarkus + Flyway migrate can
# take 30-60s cold).
wait-for-health:
	@echo "Waiting for backend health at $(HEALTH_URL) (timeout=$(HEALTH_TIMEOUT)s)..."
	@t=$(HEALTH_TIMEOUT); \
	while [ $$t -gt 0 ]; do \
	  if curl -sf --max-time 3 "$(HEALTH_URL)" 2>/dev/null | grep -q '"status": "UP"'; then \
	    echo "  ✓ backend healthy ($(HEALTH_URL))"; \
	    exit 0; \
	  fi; \
	  sleep 2; t=$$((t - 2)); \
	done; \
	echo "  ✗ backend did not become healthy within $(HEALTH_TIMEOUT)s"; \
	echo "    Check: docker compose logs backend --tail 100"; \
	exit 1

# Frontend has no Docker healthcheck, and Nuxt SSR takes ~20-40s to accept
# connections after recreate — smoke-fast racing that warmup produced two
# false-FAIL redeploys on 2026-06-12 (HTTP 000 on GET / and /login).
wait-for-frontend:
	@ip=$$(docker inspect infrastructure-frontend-1 --format '{{range $$k, $$v := .NetworkSettings.Networks}}{{$$v.IPAddress}}{{end}}' 2>/dev/null); \
	url="http://$${ip:-localhost}:3000/"; \
	echo "Waiting for frontend at $$url (timeout=90s)..."; \
	t=90; \
	while [ $$t -gt 0 ]; do \
	  code=$$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$$url" 2>/dev/null); \
	  if [ "$$code" = "200" ] || [ "$$code" = "302" ]; then \
	    echo "  ✓ frontend responding ($$code)"; \
	    exit 0; \
	  fi; \
	  sleep 3; t=$$((t - 3)); \
	done; \
	echo "  ✗ frontend did not respond within 90s"; \
	echo "    Check: docker compose logs frontend --tail 100"; \
	exit 1

# Post-deploy smoke test. Defaults to localhost; for prod:
#   FRONTEND_URL=https://shepard.nuclide.systems BACKEND_URL=https://shepard-api.nuclide.systems make smoke
#
# **Hard prerequisite**: `build-backend` runs first. Surfaced by
# CRIT-SMOKE-NOT-CATCHING-BUILD-BREAK (2026-05-24): smoke against a stale
# running image was reporting 25/25 PASS even while `mvn clean compile` was
# broken on main, hiding the build break for an entire session. Coupling the
# compile to smoke guarantees a green smoke means the source actually builds.
#
# Trade-off: this also triggers a local Maven build when smoking PROD via
# `FRONTEND_URL=https://... BACKEND_URL=https://... make smoke`. If you want
# pure prod-probing without rebuilding, invoke the script directly:
#   FRONTEND_URL=... BACKEND_URL=... ./infrastructure/smoke-test.sh
smoke: build-backend
	@$(COMPOSE_DIR)/smoke-test.sh

# Fast variant: run the smoke script without a backend rebuild. Use after
# frontend-only or backend-only deploys where the build already happened
# (image-backend / image-frontend both call their respective build targets).
# This avoids the 60+ min Quarkus augmentation when only the frontend changed.
smoke-fast:
	@$(COMPOSE_DIR)/smoke-test.sh

# Per `feedback_deploy_after_visible_features.md`: build → image → redeploy → smoke-test.
# All three redeploy targets chain through wait-for-health + smoke so a green
# `make redeploy` is genuine confidence the deploy worked, not just an image swap.

# `--force-recreate` per service: surfaced independently by Pattern-D agent
# (CRIT-WORKTREE-DOCKER-CACHE) and UX-bundle agent (MK-REDEPLOY-FORCE-RECREATE)
# on 2026-05-24. Without it, `docker compose up -d --no-build` keeps the
# existing container alive even when its image tag was just rebuilt, so the
# frontend (or backend) continues to serve the OLD code while smoke +
# `curl /` both report 200. Always recreate the targeted service.
redeploy-backend: preflight-env image-backend
	cd $(COMPOSE_DIR) && docker compose up -d --no-build --force-recreate backend
	@$(MAKE) wait-for-health
	@$(MAKE) smoke-fast

redeploy-frontend: preflight-env image-frontend
	cd $(COMPOSE_DIR) && docker compose up -d --no-build --force-recreate frontend
	@$(MAKE) wait-for-health
	@$(MAKE) wait-for-frontend
	@$(MAKE) smoke-fast

redeploy: preflight-env image-backend image-frontend
	cd $(COMPOSE_DIR) && docker compose up -d --no-build --force-recreate backend frontend
	@$(MAKE) wait-for-health
	@$(MAKE) wait-for-frontend
	@$(MAKE) smoke-fast

# Escape hatch: full rebuild + deploy WITHOUT the post-deploy verification. Use
# when the smoke test is the thing you're iterating on, or when you knowingly
# expect a partial state. Anything you'd call `redeploy` for in normal work
# should stay on `redeploy` — the verify is the safety net.
redeploy-fast: preflight-env image-backend image-frontend
	cd $(COMPOSE_DIR) && docker compose up -d --no-build --force-recreate backend frontend

check-images:
	@echo "Backend  image: $$(docker images $(BACKEND_IMAGE)  --format '{{.CreatedAt}}')"
	@echo "Frontend image: $$(docker images $(FRONTEND_IMAGE) --format '{{.CreatedAt}}')"
	@echo "Backend  container: $$(docker inspect infrastructure-backend-1  --format '{{.Config.Image}} (started {{.State.StartedAt}})' 2>/dev/null || echo 'not running')"
	@echo "Frontend container: $$(docker inspect infrastructure-frontend-1 --format '{{.Config.Image}} (started {{.State.StartedAt}})' 2>/dev/null || echo 'not running')"

integration-test:
	@echo "Running API integration tests against localhost:8080..."
	cd e2e/api && \
	  BACKEND_URL=http://localhost:8080/shepard/api \
	  KC_URL=http://localhost:8082 \
	  pytest tests/ -v --tb=short
