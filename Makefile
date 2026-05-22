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
        wait-for-health smoke check-images integration-test

# ADR-0023 two-pass dance: backend install (no plugins) → plugins install → backend package.
# Required whenever a plugin module changes; safe to run even when only backend changed.
build-plugins:
	cd backend && $(MVN) -DnoPlugins -Dmaven.test.skip=true -Dquarkus.build.skip=true install -q
	# wiki-writer must install before ai (ai has a provided dep on wiki-writer).
	# Both must install before video (backend now depends on all three transitively).
	cd plugins/wiki-writer && $(MVN) -Dmaven.test.skip=true install -q
	cd plugins/ai && $(MVN) -Dmaven.test.skip=true install -q
	cd plugins/video && $(MVN) -Dmaven.test.skip=true install -q

build-backend: build-plugins
	cd backend && $(MVN) package -Dmaven.test.skip=true -q

image-backend: build-backend
	docker build -t $(BACKEND_IMAGE) ./backend

build-frontend:
	cd frontend && npm run build

image-frontend: build-frontend
	docker build -t $(FRONTEND_IMAGE) ./frontend

deploy:
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

# Post-deploy smoke test. Defaults to localhost; for prod:
#   FRONTEND_URL=https://shepard.nuclide.systems BACKEND_URL=https://shepard-api.nuclide.systems make smoke
smoke:
	@$(COMPOSE_DIR)/smoke-test.sh

# Per `feedback_deploy_after_visible_features.md`: build → image → redeploy → smoke-test.
# All three redeploy targets chain through wait-for-health + smoke so a green
# `make redeploy` is genuine confidence the deploy worked, not just an image swap.

redeploy-backend: image-backend
	cd $(COMPOSE_DIR) && docker compose up -d --no-build backend
	@$(MAKE) wait-for-health
	@$(MAKE) smoke

redeploy-frontend: image-frontend
	cd $(COMPOSE_DIR) && docker compose up -d --no-build frontend
	@$(MAKE) wait-for-health
	@$(MAKE) smoke

redeploy: image-backend image-frontend
	cd $(COMPOSE_DIR) && docker compose up -d --no-build backend frontend
	@$(MAKE) wait-for-health
	@$(MAKE) smoke

# Escape hatch: full rebuild + deploy WITHOUT the post-deploy verification. Use
# when the smoke test is the thing you're iterating on, or when you knowingly
# expect a partial state. Anything you'd call `redeploy` for in normal work
# should stay on `redeploy` — the verify is the safety net.
redeploy-fast: image-backend image-frontend
	cd $(COMPOSE_DIR) && docker compose up -d --no-build backend frontend

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
