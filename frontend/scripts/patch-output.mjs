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
// Trivy flags HIGH `node-pkg` CVEs in two production deps Nitro traces into
// `.output/server/node_modules`:
//   - uuid 8.3.2   (pulled by next-auth)    → CVE-2026-41907  fixed in 11.1.1
//   - devalue 5.6.3 (pulled by Nuxt/Nitro)  → CVE-2026-42570  fixed in 5.8.1
//
// We patch these in the built output rather than via a workspace `overrides`
// block because npm 9 cannot apply a new override to the existing committed
// lockfile without a full re-resolve, and that re-resolve drifts the dependency
// hoisting enough to break Nuxt's auto-import type generation (vue-tsc then
// reports ~95 spurious auto-import errors). Patching the output keeps the
// committed lockfile byte-identical (zero typecheck drift) while shipping the
// fixed bytes. Both packages keep a backward-compatible public API for the call
// sites in use (next-auth's `uuid.v4()`, Nitro's devalue `stringify`/`parse`),
// so the drop-in swap is safe. Revisit and migrate to `overrides` on the next
// npm / Nuxt major that re-resolves the lockfile anyway (FRONTEND-IMAGE-CVE).
const { execFileSync } = await import("node:child_process");
const os = await import("node:os");

const SECURITY_PINS = [
  { name: "uuid", version: "11.1.1" },
  { name: "devalue", version: "5.8.1" },
];

const outputNodeModules = path.resolve(__dirname, "../.output/server/node_modules");

for (const { name, version } of SECURITY_PINS) {
  const dest = path.join(outputNodeModules, name);
  if (!fs.existsSync(dest)) {
    // Nitro did not trace this package into the bundle — nothing to patch.
    console.info(`[patch-output] ${name} not in output bundle — skipping security pin`);
    continue;
  }

  const current = JSON.parse(fs.readFileSync(path.join(dest, "package.json"), "utf8")).version;
  if (current === version) {
    console.info(`[patch-output] ${name}@${version} already pinned`);
    continue;
  }

  // Fetch the fixed tarball from the same registry npm install uses, extract it,
  // and replace the traced copy in-place.
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), `pin-${name}-`));
  try {
    const packed = execFileSync("npm", ["pack", `${name}@${version}`, "--silent", "--pack-destination", tmp], {
      encoding: "utf8",
    })
      .trim()
      .split("\n")
      .filter(Boolean)
      .pop();
    execFileSync("tar", ["-xzf", path.join(tmp, packed), "-C", tmp]);
    const extracted = path.join(tmp, "package");
    fs.rmSync(dest, { recursive: true, force: true });
    fs.cpSync(extracted, dest, { recursive: true });
    console.info(`[patch-output] pinned ${name} ${current} → ${version} (CVE fix)`);
  } finally {
    fs.rmSync(tmp, { recursive: true, force: true });
  }
}

console.info("[patch-output] done");
