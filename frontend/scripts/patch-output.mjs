/**
 * Post-build patch: Nitro's file trace incompletely copies the vue package when
 * vite.ssr.external includes packages that share vue as a dependency.
 * Specifically, index.mjs and index.js are not traced but are required by Node.js
 * ESM resolution (the exports map's "node" condition points to index.mjs).
 * Copy them from the installed source package.
 */
import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const outputVueDir = path.resolve(__dirname, "../.output/server/node_modules/vue");

if (!fs.existsSync(outputVueDir)) {
  console.info("[patch-output] .output/server/node_modules/vue not found — skipping");
  process.exit(0);
}

const require = createRequire(import.meta.url);
const vuePkgPath = require.resolve("vue/package.json");
const vueSourceDir = path.dirname(vuePkgPath);

for (const file of ["index.js", "index.mjs"]) {
  const src = path.join(vueSourceDir, file);
  const dest = path.join(outputVueDir, file);
  if (!fs.existsSync(dest) && fs.existsSync(src)) {
    fs.copyFileSync(src, dest);
    console.info(`[patch-output] copied vue/${file}`);
  }
}

// Also ensure dist/vue.cjs.js (dev) exists — index.js requires it in non-prod mode
const distSrc = path.join(vueSourceDir, "dist", "vue.cjs.js");
const distDest = path.join(outputVueDir, "dist", "vue.cjs.js");
if (!fs.existsSync(distDest) && fs.existsSync(distSrc)) {
  fs.copyFileSync(distSrc, distDest);
  console.info("[patch-output] copied vue/dist/vue.cjs.js");
}

// ── SECURITY: pin Nitro-traced runtime deps to CVE-fixed versions ────────────
//
// Trivy flags HIGH/CRITICAL `node-pkg` CVEs in production deps Nitro traces
// into `.output/server/node_modules`:
//   - uuid     8.3.2  (pulled by next-auth)   → CVE-2026-41907            fixed in 11.1.1
//   - devalue  5.6.3  (pulled by Nuxt/Nitro)  → CVE-2026-42570            fixed in 5.8.1
//   - fast-uri 3.0.6  (pulled by Nitro/ofetch)→ CVE-2026-6321/-6322 (HIGH) fixed in 3.1.2
//   - lodash-es 4.17.21 (transitive)          → CVE-2026-4800 (HIGH)       fixed in 4.18.0
//
// We patch these in the built output rather than via a workspace `overrides`
// block because npm 9 cannot apply a new override to the existing committed
// lockfile without a full re-resolve, and that re-resolve drifts the dependency
// hoisting enough to break Nuxt's auto-import type generation (vue-tsc then
// reports ~95 spurious auto-import errors). Patching the output keeps the
// committed lockfile byte-identical (zero typecheck drift) while shipping the
// fixed bytes. All four packages keep a backward-compatible public API for the
// call sites in use (next-auth's `uuid.v4()`, Nitro's devalue
// `stringify`/`parse`, fast-uri's `parse`/`serialize`, lodash-es named imports),
// so the drop-in swap is safe. Revisit and migrate to `overrides` on the next
// npm / Nuxt major that re-resolves the lockfile anyway (FRONTEND-IMAGE-CVE).
const { execFileSync } = await import("node:child_process");
const os = await import("node:os");

const SECURITY_PINS = [
  { name: "uuid", version: "11.1.1" },
  { name: "devalue", version: "5.8.1" },
  { name: "fast-uri", version: "3.1.2" },
  { name: "lodash-es", version: "4.18.0" },
];

const outputServer = path.resolve(__dirname, "../.output/server");

// A pinned dep may be hoisted to the top-level `node_modules` or nested under
// another package (npm hoisting is non-deterministic across lockfile shapes).
// Walk every `node_modules` tree under the server bundle and collect every
// copy of each pinned package so we replace them all — Trivy reads each jar/
// package.json independently, so a single missed nested copy keeps the CVE.
function findPackageDirs(root, pkgName) {
  const matches = [];
  const stack = [root];
  while (stack.length > 0) {
    const dir = stack.pop();
    let entries;
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true });
    } catch {
      continue;
    }
    for (const entry of entries) {
      if (!entry.isDirectory() && !entry.isSymbolicLink()) continue;
      const full = path.join(dir, entry.name);
      if (entry.name === "node_modules") {
        // candidate: <node_modules>/<pkgName>[/...]
        const candidate = path.join(full, pkgName);
        if (fs.existsSync(path.join(candidate, "package.json"))) matches.push(candidate);
        // keep descending: nested node_modules can live deeper
        stack.push(full);
      } else if (entry.name !== ".bin") {
        stack.push(full);
      }
    }
  }
  return matches;
}

// Extract a fixed npm tarball once, then reuse it for every copy found.
function fetchPackage(name, version) {
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), `pin-${name.replace("/", "-")}-`));
  const packed = execFileSync("npm", ["pack", `${name}@${version}`, "--silent", "--pack-destination", tmp], {
    encoding: "utf8",
  })
    .trim()
    .split("\n")
    .filter(Boolean)
    .pop();
  execFileSync("tar", ["-xzf", path.join(tmp, packed), "-C", tmp]);
  return { tmp, extracted: path.join(tmp, "package") };
}

for (const { name, version } of SECURITY_PINS) {
  const dests = fs.existsSync(outputServer) ? findPackageDirs(outputServer, name) : [];
  if (dests.length === 0) {
    // Nitro did not trace this package into the bundle — nothing to patch.
    console.info(`[patch-output] ${name} not in output bundle — skipping security pin`);
    continue;
  }

  const stale = dests.filter((dest) => {
    const current = JSON.parse(fs.readFileSync(path.join(dest, "package.json"), "utf8")).version;
    return current !== version;
  });
  if (stale.length === 0) {
    console.info(`[patch-output] ${name}@${version} already pinned (${dests.length} copy/copies)`);
    continue;
  }

  // Fetch the fixed tarball once, then replace every stale traced copy in-place.
  const { tmp, extracted } = fetchPackage(name, version);
  try {
    for (const dest of stale) {
      const current = JSON.parse(fs.readFileSync(path.join(dest, "package.json"), "utf8")).version;
      fs.rmSync(dest, { recursive: true, force: true });
      fs.cpSync(extracted, dest, { recursive: true });
      console.info(`[patch-output] pinned ${name} ${current} → ${version} at ${path.relative(outputServer, dest)} (CVE fix)`);
    }
  } finally {
    fs.rmSync(tmp, { recursive: true, force: true });
  }
}

console.info("[patch-output] done");
