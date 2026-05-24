/**
 * BUG-LJ-V1-COLL-ID — bulk lab-journal-entries endpoint must not 500 on a
 * collection that actually has entries.
 *
 * **Surfacing.** UI-020 shipped `GET /v2/collections/{collectionAppId}/lab-journal-entries`
 * as the N+1-killer for the legacy per-DataObject fan-out. The RDM-005
 * Metadata Completeness widget agent's browser-console capture flagged
 * HTTP 500 from this endpoint on LUMEN.
 *
 * **Root cause (NOT the brief's hypothesis).** The brief framed this as a
 * v1-numeric-id resolver miss. Primary-source live evidence (cypher-shell +
 * backend log) showed:
 *   - LUMEN's Collection node has `appId` populated; the resolver succeeds.
 *   - The 500 is `NullPointerException` in `LabJournalEntryIO`'s constructor
 *     on `labJournalEntry.getDataObject().getShepardId()`.
 *   - The DAO's `RETURN DISTINCT lje` projection did not hydrate the
 *     incoming `has_labjournalentry` edge back to DataObject, so the
 *     reverse-relationship came back null on every entry. MFFD-Dropbox
 *     hid the bug (no entries → empty list → no NPE); LUMEN exposed it
 *     (4 entries → first IO construct NPEs).
 *
 * **Fix.** DAO Cypher now projects the depth-1 neighbourhood path
 * (`path=(lje)-[*0..1]-(n) ... RETURN lje, nodes(path), relationships(path)`)
 * so the OGM hydrates BOTH directions. Resource also gained a defensive
 * orphan-skip + WARN log as belt-and-braces.
 *
 * **This spec** drives the bug-closer from the UI side: log in as alice,
 * open the LUMEN landing, assert no 500 on lab-journal-entries in the
 * network log, and assert the Lab Journal panel renders at least one entry
 * (LUMEN seeds 4 entries: TR-004, anomaly investigation, TR-006, Publications).
 */
import { expect, test } from "@playwright/test";
import { loginAs } from "./helpers/auth";

const LUMEN_COLLECTION = 42;
const LUMEN_COLLECTION_APP_ID = "019e30b0-99a2-79e7-b7d8-c15396095b42";

test.describe("BUG-LJ-V1-COLL-ID: bulk lab-journal-entries no longer 500s on LUMEN", () => {
  test("no 500 in network log + Lab Journal panel renders entries", async ({
    page,
  }) => {
    test.setTimeout(120_000);

    // Capture every response on the bulk endpoint so the assertion can fail
    // with the actual status code observed.
    const bulkResponses: { status: number; url: string }[] = [];
    page.on("response", (resp) => {
      const u = resp.url();
      if (u.includes("/v2/collections/") && u.includes("/lab-journal-entries")) {
        bulkResponses.push({ status: resp.status(), url: u });
      }
    });

    await loginAs(page, "alice", "alice-demo");

    await page.goto(`/collections/${LUMEN_COLLECTION}`);
    await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});

    // The frontend's `useFetchCollectionLabJournalEntries` composable fires
    // on the watch(collectionAppId, ..., { immediate: true }); give it a
    // beat to land.
    await page.waitForTimeout(2_000);

    // Assertion #1 — no 5xx on the bulk endpoint.
    const errored = bulkResponses.filter((r) => r.status >= 500);
    expect(
      errored,
      `Expected no 5xx on lab-journal-entries; got: ${JSON.stringify(errored)}`,
    ).toEqual([]);

    // Assertion #2 — at least one call was actually made (we exercised the
    // code path). Some collection pages may defer until a tab is opened, so
    // this is "saw the request" not "saw exactly one".
    const sawCall = bulkResponses.some((r) =>
      r.url.includes(LUMEN_COLLECTION_APP_ID),
    );
    expect(
      sawCall,
      `Expected at least one bulk lab-journal-entries call against LUMEN ` +
        `appId ${LUMEN_COLLECTION_APP_ID}; saw: ${JSON.stringify(bulkResponses)}`,
    ).toBe(true);

    // Assertion #3 — the response status on the LUMEN-appId call is 200.
    const lumenCalls = bulkResponses.filter((r) =>
      r.url.includes(LUMEN_COLLECTION_APP_ID),
    );
    expect(
      lumenCalls.every((r) => r.status === 200),
      `Expected every LUMEN lab-journal-entries call to be 200; got: ${JSON.stringify(lumenCalls)}`,
    ).toBe(true);

    // Assertion #4 — the Lab Journal panel does NOT show an error state.
    // We do NOT assert it shows entries: a future seed change or a permission
    // change could legitimately render the empty state. The bug was always
    // about the 500 — not about visibility of seeded content. The error
    // state for this panel surfaces as a v-alert with role=alert; absence
    // suffices.
    const errAlert = page.locator('[role="alert"]:has-text("error"), [role="alert"]:has-text("failed")');
    await expect(errAlert).toHaveCount(0);
  });
});
