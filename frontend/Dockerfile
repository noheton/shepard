# Shepard frontend runtime image.
#
# The image ships ONLY Nuxt's self-contained Nitro output (`.output/`) — the
# server bundle in `.output/server` plus the static assets in `.output/public`.
# No build-tool `node_modules`, no source tree. `.output/server/node_modules`
# already contains only the production deps Nitro actually traced.
#
# SECURITY (Trivy `node-pkg` HIGH CVEs): the `node:20-slim` base image bundles
# the npm CLI under `/usr/local/lib/node_modules/npm`, which vendors `tar`,
# `glob`, `minimatch`, and `cross-spawn` copies that Trivy flags as HIGH. The
# runtime only ever runs `node server/index.mjs` — it never invokes npm — so we
# strip the bundled npm + corepack package managers in a multi-stage build.
# That removes the entire `tar`/`glob`/`minimatch`/`cross-spawn` CVE class at
# the image layer. The residual production deps `uuid` and `devalue` are pinned
# to fixed versions via the workspace-root `overrides` block.
FROM node:20-slim AS runtime

WORKDIR /app

# Strip the package managers the runtime never uses (npm bundles the
# tar/glob/minimatch/cross-spawn CVE copies; corepack is unneeded too).
RUN rm -rf /usr/local/lib/node_modules/npm \
           /usr/local/lib/node_modules/corepack \
           /usr/local/bin/npm \
           /usr/local/bin/npx \
           /usr/local/bin/corepack

EXPOSE 3000

COPY .output .

CMD [ "node", "server/index.mjs" ]
