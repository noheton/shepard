/**
 * UX Scrutinizer 2026-05-24 — auth setup: log in once per role, cache state.
 *
 * Subsequent ux-workflow-*.spec.ts files load alice-state.json or admin-state.json
 * via test.use({ storageState: ... }) and skip the OIDC roundtrip entirely.
 */
import { test } from "@playwright/test";
import { loginAs } from "./helpers/auth";
import * as fs from "fs";
import * as path from "path";

// JWT-bearing state stays OUT of the public repo evidence dir.
// (Per CLAUDE.md feedback_uploads_never_in_repo + general credential hygiene.)
const STATE_DIR = "/tmp/shepard-ux-auth-state";
fs.mkdirSync(STATE_DIR, { recursive: true });

test("UX-WF-00 — cache alice storage state", async ({ page, context }) => {
  await loginAs(page, "alice", "alice-demo");
  await context.storageState({ path: path.join(STATE_DIR, "alice.json") });
});

test("UX-WF-00 — cache admin storage state", async ({ page, context }) => {
  await loginAs(page, "admin", "admin-demo");
  await context.storageState({ path: path.join(STATE_DIR, "admin.json") });
});
