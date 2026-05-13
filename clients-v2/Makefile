# clients-v2/Makefile — Kiota-driven /v2/ client generation
#
# Generates Python, TypeScript and Java clients for shepard's /v2/ API surface
# from the per-shelf OpenAPI document at /shepard/doc/openapi/v2.json
# (shipped by P4c, see aidocs/16).
#
# This is the CG1a pipeline — per ADR-0022 (aidocs/63), Kiota is the
# new baseline for /v2/ client generation. The legacy /shepard/api/...
# clients live under sibling `clients/` and are driven by OpenAPI
# Generator (CG1b).
#
# --- Versions (pinned; bump deliberately) ---------------------------
# Kiota CLI:  v1.31.1  (April 2026; per aidocs/57 §4.1)
# Installed via:  dotnet tool install --global Microsoft.OpenApi.Kiota --version 1.31.1
# --------------------------------------------------------------------
#
# --- Inputs ---------------------------------------------------------
# KIOTA_INPUT_URL  — OpenAPI v2 source. Defaults to localhost for the
#                    inner dev loop. CI overrides to a tagged-release
#                    URL so the published artefact tree is reproducible.
# KIOTA_BIN        — `kiota` binary path. Defaults to `kiota` on PATH;
#                    CI sets it to the dotnet tool-install location.
# CLASS_NAME       — Top-level client class. Stable across languages so
#                    docs and the convenience-wrapper (aidocs/27) can
#                    refer to one name.
# --------------------------------------------------------------------

KIOTA_VERSION ?= 1.31.1
KIOTA_BIN ?= kiota
KIOTA_INPUT_URL ?= http://localhost:8080/shepard/doc/openapi/v2.json
CLASS_NAME ?= ShepardV2Client

PYTHON_OUT := python-kiota/shepard_v2
TS_OUT := typescript-kiota/src
JAVA_OUT := java-kiota/src/main/java/de/dlr/shepard/v2/clients

PYTHON_NAMESPACE := shepard_v2
TS_NAMESPACE := ShepardV2
JAVA_NAMESPACE := de.dlr.shepard.v2.clients

.PHONY: help check generate-all generate-python generate-typescript generate-java clean

help:
	@echo "Targets:"
	@echo "  generate-all        Generate Python + TypeScript + Java clients"
	@echo "  generate-python     Python only          (-> $(PYTHON_OUT))"
	@echo "  generate-typescript TypeScript only      (-> $(TS_OUT))"
	@echo "  generate-java       Java only            (-> $(JAVA_OUT))"
	@echo "  check               Verify kiota CLI is on PATH at the pinned version"
	@echo "  clean               Remove every generated tree (keeps pyproject/package/pom)"
	@echo ""
	@echo "Inputs:"
	@echo "  KIOTA_INPUT_URL=$(KIOTA_INPUT_URL)"
	@echo "  KIOTA_BIN=$(KIOTA_BIN)"
	@echo "  KIOTA_VERSION=$(KIOTA_VERSION)"
	@echo ""
	@echo "Install kiota:"
	@echo "  dotnet tool install --global Microsoft.OpenApi.Kiota --version $(KIOTA_VERSION)"

check:
	@command -v $(KIOTA_BIN) >/dev/null 2>&1 || { \
		echo "ERROR: kiota CLI not found on PATH (looked for: $(KIOTA_BIN))."; \
		echo "Install via:  dotnet tool install --global Microsoft.OpenApi.Kiota --version $(KIOTA_VERSION)"; \
		exit 1; \
	}
	@$(KIOTA_BIN) --version

generate-all: generate-python generate-typescript generate-java

generate-python:
	@echo "==> Generating Python client via Kiota $(KIOTA_VERSION)"
	@mkdir -p $(PYTHON_OUT)
	$(KIOTA_BIN) generate \
		--openapi $(KIOTA_INPUT_URL) \
		--language python \
		--output $(PYTHON_OUT) \
		--class-name $(CLASS_NAME) \
		--namespace-name $(PYTHON_NAMESPACE) \
		--clean-output \
		--clear-cache \
		--log-level Information

generate-typescript:
	@echo "==> Generating TypeScript client via Kiota $(KIOTA_VERSION)"
	@mkdir -p $(TS_OUT)
	$(KIOTA_BIN) generate \
		--openapi $(KIOTA_INPUT_URL) \
		--language typescript \
		--output $(TS_OUT) \
		--class-name $(CLASS_NAME) \
		--namespace-name $(TS_NAMESPACE) \
		--clean-output \
		--clear-cache \
		--log-level Information

generate-java:
	@echo "==> Generating Java client via Kiota $(KIOTA_VERSION)"
	@mkdir -p $(JAVA_OUT)
	$(KIOTA_BIN) generate \
		--openapi $(KIOTA_INPUT_URL) \
		--language java \
		--output $(JAVA_OUT) \
		--class-name $(CLASS_NAME) \
		--namespace-name $(JAVA_NAMESPACE) \
		--clean-output \
		--clear-cache \
		--log-level Information

clean:
	@echo "==> Removing generated sources (keeping pyproject.toml / package.json / pom.xml)"
	rm -rf $(PYTHON_OUT)
	rm -rf $(TS_OUT)
	rm -rf $(JAVA_OUT)
	@mkdir -p $(PYTHON_OUT) $(TS_OUT) $(JAVA_OUT)
	@touch $(PYTHON_OUT)/.gitkeep $(TS_OUT)/.gitkeep $(JAVA_OUT)/.gitkeep
