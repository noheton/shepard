/**
 * L4 — ontology search-as-you-type with tree/graph view, live 4K verification.
 *
 * Backlog row L4 shipped a frontend-only `/semantic/search` page over the
 * existing `GET /v2/semantic/terms/search` (N1e) endpoint: a debounced
 * combobox + a tree/graph toggle that places each matched term in its
 * vocabulary namespace hierarchy (derived client-side from the term IRI).
 *
 * The debounce / race-guard / tree-derivation logic is covered by 28 Vitest
 * unit tests (ontologyTermTree.test.ts + useOntologySearch.test.ts). This
 * spec's job is the live smoke: the page mounts at the user's 4K viewport,
 * presents its search affordance, and reaches no console crash.
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "./helpers/auth";

test("ontology search page mounts at 4K without a console crash", async ({
  page,
}) => {
  test.setTimeout(60000); // OIDC login can be slow on a cold session
  const errors: string[] = [];
  page.on("console", (m) => {
    if (m.type() === "error") errors.push(m.text());
  });
  page.on("pageerror", (e) => errors.push(String(e)));

  await page.setViewportSize({ width: 3840, height: 2160 });
  await loginAs(page, "alice", "alice-demo");

  await page.goto("/semantic/search", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(4000); // let the page + composable settle

  // Coherent surface, not an error page.
  await expect(page.locator("body")).not.toContainText("Internal Server Error");

  // The search affordance is present (combobox / text field).
  const search = page
    .locator('input[type="text"], input[role="combobox"], .v-field input')
    .first();
  await expect(search).toBeVisible({ timeout: 10000 });

  // No JS crash reached the console on mount.
  const crashes = errors.filter((e) =>
    /TypeError|undefined \(reading|is not a function|Cannot read/i.test(e),
  );
  expect(
    crashes,
    `console crash on /semantic/search:\n${crashes.join("\n")}`,
  ).toHaveLength(0);
});
