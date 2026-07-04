# shepard-plugin-krl-interpreter sidecar (KRL-INTERPRETER-04)
#
# Containerises the FastAPI sidecar that parses KRL `.src`/`.dat`,
# runs ikpy IK against a URDF, and emits a joint trajectory.
#
# See `plugins/krl-interpreter/docs/install.md` for the operator
# bring-up runbook + env-var reference.

FROM python:3.12-slim AS base

# Numpy / scipy / ikpy wheels are typically prebuilt for cp312-linux,
# but build-essential is here as a fallback if a transitive dep needs
# to compile (e.g. on a non-glibc arch). gcc + python3-dev costs ~80MB
# but means the image builds reliably across host arches.
RUN apt-get update \
  && apt-get install -y --no-install-recommends \
      build-essential \
      curl \
  && rm -rf /var/lib/apt/lists/*

# Non-root user — sidecar has no need for root after install.
RUN groupadd --system --gid 1000 krl \
 && useradd --system --uid 1000 --gid krl --create-home --home-dir /home/krl krl

WORKDIR /opt/krl-interpreter

# Copy the plugin source. The Dockerfile lives at the plugin root, so
# the build context is `plugins/krl-interpreter/` from the compose
# `build:` directive.
COPY pyproject.toml README.md ./
COPY krl_interpreter ./krl_interpreter

# Install the plugin (and the FastAPI runtime). --no-cache-dir keeps
# the image lean.
RUN pip install --no-cache-dir --upgrade pip \
 && pip install --no-cache-dir .

# Switch to non-root for runtime.
USER krl
WORKDIR /home/krl

# Default knobs — admin can override via compose env. See
# krl_interpreter/sidecar/config.py for the full env-var table.
ENV PORT=8000 \
    HOST=0.0.0.0 \
    KRL_IK_TOLERANCE=1e-3 \
    KRL_MAX_ITERATIONS=100 \
    KRL_TIME_STEP_DEFAULT=0.01 \
    KRL_MOTION_DURATION=1.0 \
    KRL_MAX_IR_ITERATIONS=100000

EXPOSE 8000

# Healthcheck doubles as the compose-profile healthcheck below.
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl --fail --silent http://localhost:8000/health || exit 1

CMD ["python", "-m", "krl_interpreter.sidecar"]
