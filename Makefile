# shepard local build / deploy Makefile
#
# Background: the Dockerfiles copy pre-built artifacts, so you MUST run
# the language-specific build before docker build:
#   backend  → mvn package → docker build  (image: shepard-backend-patched:local)
#   frontend → npm run build → docker build (image: shepard-frontend:local)
#
# Usage:
#   make build-backend        compile + package backend JAR via Maven
#   make image-backend        docker build the backend image (requires build-backend first)
#   make build-frontend       Nuxt build (generates frontend/.output)
#   make image-frontend       docker build the frontend image (requires build-frontend first)
#   make deploy               docker compose up -d for backend + frontend
#   make redeploy-backend     build-backend + image-backend + deploy backend only
#   make redeploy-frontend    build-frontend + image-frontend + deploy frontend only
#   make redeploy             full rebuild and deploy of both services
#   make check-images         print image timestamps to verify freshness
#   make integration-test     run API integration tests against localhost:8080

BACKEND_IMAGE  := shepard-backend-patched:local
FRONTEND_IMAGE := shepard-frontend:local
COMPOSE_DIR    := ./infrastructure
MVN            := $(CURDIR)/backend/mvnw

.PHONY: build-backend build-plugins image-backend build-frontend image-frontend \
        deploy redeploy-backend redeploy-frontend redeploy check-images \
        integration-test

# ADR-0023 two-pass dance: backend install (no plugins) → plugins install → backend package.
# Required whenever a plugin module changes; safe to run even when only backend changed.
build-plugins:
	cd backend && $(MVN) -DnoPlugins -Dmaven.test.skip=true -Dquarkus.build.skip=true install -q
	# AI and wiki-writer must install before other plugins because backend now depends on them.
	# Other plugin poms exclude them, but Maven still resolves transitive deps from local repo.
	cd plugins/ai && $(MVN) -Dmaven.test.skip=true install -q
	cd plugins/wiki-writer && $(MVN) -Dmaven.test.skip=true install -q
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

redeploy-backend: image-backend
	cd $(COMPOSE_DIR) && docker compose up -d --no-build backend

redeploy-frontend: image-frontend
	cd $(COMPOSE_DIR) && docker compose up -d --no-build frontend

redeploy: image-backend image-frontend
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
