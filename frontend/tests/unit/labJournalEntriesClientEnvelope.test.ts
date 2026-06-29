/**
 * BUG-LABJOURNAL-MAP-ENVELOPE — regression for "s.map is not a function" on
 * listCollectionLabJournalEntries.
 *
 * The backend migrated GET /v2/collections/{appId}/lab-journal-entries from a
 * bare JSON array to the PagedResponseIO {items,total,page,pageSize} envelope
 * (APISIMP-PAGINATION-ENVELOPE). The generated client's deserializer used to do
 * `jsonValue.map(...)` directly on the response body — once the body became an
 * envelope object, `.map` was undefined and the lab-journal panel crashed.
 *
 * Unlike useFetchCollectionLabJournalEntries.test.ts (which MOCKS the client and
 * therefore never exercised the deserializer), this drives the REAL generated
 * CollectionLabJournalEntriesApi through a stub fetch returning each wire shape.
 */
import { describe, it, expect } from "vitest";
import {
  CollectionLabJournalEntriesApi,
  Configuration,
} from "@dlr-shepard/backend-client";

const ENTRY = {
  dataObjectId: 42,
  dataObjectAppId: "0192cccc-0000-7000-8000-0000000000aa",
  journalContent: "consolidation force nominal",
  id: 7,
  createdAt: "2026-05-24T10:00:00Z",
  createdBy: "alice",
  updatedAt: null,
  updatedBy: null,
  appId: "0192dddd-0000-7000-8000-0000000000bb",
  contentFormat: "text/markdown",
};

function apiReturning(body: unknown): CollectionLabJournalEntriesApi {
  const fetchApi = async () =>
    new Response(JSON.stringify(body), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  return new CollectionLabJournalEntriesApi(new Configuration({ fetchApi }));
}

describe("CollectionLabJournalEntriesApi envelope deserialization", () => {
  it("returns the PagedResponse envelope with items[]", async () => {
    const api = apiReturning({ items: [ENTRY], total: 1, page: 0, pageSize: 50 });
    const out = await api.listLabJournalEntries({ collectionAppId: "c1" });
    expect(Array.isArray(out.items)).toBe(true);
    expect(out.items).toHaveLength(1);
    expect((out.items ?? [])[0]).toMatchObject({ id: 7, journalContent: "consolidation force nominal" });
  });

  it("returns an envelope with empty/absent items as undefined-safe", async () => {
    const api = apiReturning({ total: 0, page: 0, pageSize: 50 });
    const out = await api.listLabJournalEntries({ collectionAppId: "c1" });
    expect(out.items ?? []).toEqual([]);
  });
});
