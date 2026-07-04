/**
 * PLACEHOLDER-FS1e1-TESTS
 * Vitest unit tests for useFileMigration composable
 * (frontend/composables/context/admin/useFileMigration.ts)
 *
 * Covers:
 *   - refreshStorage(): GET /v2/admin/storage → populates storageStatus
 *   - refreshMigrationState(): GET /v2/admin/files/migrate/status → populates migrationState
 *   - triggerMigration(): POST /v2/admin/files/migrate → returns true/false + error extraction
 *   - rollbackFile(): POST /v2/admin/files/migrate/rollback/{appId}
 *   - Polling: startPolling/stopPolling, auto-stop on DONE/FAILED/IDLE
 *   - Auth header forwarding
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import {
  useFileMigration,
  type StorageStatusIO,
  type FileMigrationStateIO,
} from "~/composables/context/admin/useFileMigration";

const ACCESS_TOKEN = "test-admin-token";
const BASE = "http://localhost:8080";

// ─── Fixtures ────────────────────────────────────────────────────────────────

const storageOk: StorageStatusIO = {
  activeProviderId: "garage",
  adapters: [
    { id: "garage", enabled: true, active: true },
    { id: "local", enabled: false, active: false },
  ],
};

const stateIdle: FileMigrationStateIO = {
  status: "IDLE",
  sourceProviderId: null,
  targetProviderId: null,
  filesTotal: 0,
  filesMigrated: 0,
  filesFailed: 0,
  startedAt: null,
  updatedAt: null,
  errorMessage: null,
};

const stateRunning: FileMigrationStateIO = {
  ...stateIdle,
  status: "RUNNING",
  sourceProviderId: "local",
  targetProviderId: "garage",
  filesTotal: 100,
  filesMigrated: 42,
  filesFailed: 0,
  startedAt: "2026-06-16T08:00:00Z",
  updatedAt: "2026-06-16T08:01:00Z",
};

const stateDone: FileMigrationStateIO = {
  ...stateRunning,
  status: "DONE",
  filesMigrated: 100,
  updatedAt: "2026-06-16T08:05:00Z",
};

const stateFailed: FileMigrationStateIO = {
  ...stateIdle,
  status: "FAILED",
  errorMessage: "Storage backend refused connection.",
};

// ─── Helpers ─────────────────────────────────────────────────────────────────

/**
 * Flush pending microtasks so that the auto-init fetch calls inside
 * useFileMigration() complete before the test inspects state.
 * Uses 10 Promise.resolve() hops (each async step in the composable
 * init chain — fetch → json → state set — is a separate microtask hop).
 * Does NOT use setTimeout so it works whether or not fake timers are active.
 */
async function flush() {
  for (let i = 0; i < 10; i++) await Promise.resolve();
}

/**
 * Set up fetch to handle the two auto-init calls (refreshStorage + refreshMigrationState)
 * then provide additional responses for the operation under test.
 */
function setupFetch(
  ...extraResponses: Array<{ ok: boolean; json?: () => Promise<unknown>; text?: () => Promise<string>; status?: number }>
): ReturnType<typeof vi.fn> {
  const spy = vi.fn();
  // Auto-init: refreshStorage() + refreshMigrationState()
  spy
    .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(storageOk), text: () => Promise.resolve("") })
    .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(stateIdle), text: () => Promise.resolve("") });
  for (const r of extraResponses) {
    spy.mockResolvedValueOnce(r);
  }
  vi.stubGlobal("fetch", spy);
  return spy;
}

// ─── Global setup ─────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.clearAllMocks();
  (globalThis as unknown as { useAuth: () => unknown }).useAuth = () => ({
    data: ref<{ accessToken: string }>({ accessToken: ACCESS_TOKEN }),
  });
  // onUnmounted is a Vue lifecycle hook — outside a component context Vue warns
  // but does not throw. We stub it here so tests run silently.
  (globalThis as unknown as { onUnmounted: (fn: () => void) => void }).onUnmounted = vi.fn();
  // Default fetch stub to prevent unhandled network errors on auto-init
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
    ok: true,
    json: () => Promise.resolve(stateIdle),
    text: () => Promise.resolve(""),
  }));
});

afterEach(() => {
  vi.restoreAllMocks();
});

// ─── refreshStorage ──────────────────────────────────────────────────────────

describe("refreshStorage()", () => {
  it("GETs /v2/admin/storage and populates storageStatus", async () => {
    const spy = setupFetch();
    const { refreshStorage, storageStatus, isLoadingStorage } = useFileMigration();
    await flush();

    // explicit call after init
    spy.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(storageOk), text: () => Promise.resolve("") });
    await refreshStorage();

    expect(storageStatus.value).toEqual(storageOk);
    expect(isLoadingStorage.value).toBe(false);
    const [url] = spy.mock.calls.at(-1) as [string, RequestInit];
    expect(url).toBe(`${BASE}/v2/admin/storage`);
  });

  it("sends the Bearer token in the Authorization header", async () => {
    const spy = setupFetch();
    const { refreshStorage } = useFileMigration();
    await flush();

    spy.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(storageOk), text: () => Promise.resolve("") });
    await refreshStorage();

    const [, init] = spy.mock.calls.at(-1) as [string, RequestInit];
    const headers = init.headers as Record<string, string>;
    expect(headers["Authorization"]).toBe(`Bearer ${ACCESS_TOKEN}`);
  });

  it("sets storageError on HTTP failure", async () => {
    setupFetch();
    const { refreshStorage, storageError } = useFileMigration();
    await flush();

    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false, status: 500 }));
    await refreshStorage();

    expect(storageError.value).toMatch(/Failed to load storage adapters/);
  });

  it("sets storageError on network failure", async () => {
    setupFetch();
    const { refreshStorage, storageError } = useFileMigration();
    await flush();

    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("net::ERR_CONNECTION_REFUSED")));
    await refreshStorage();

    expect(storageError.value).toMatch(/Failed to load storage adapters/);
  });
});

// ─── refreshMigrationState ───────────────────────────────────────────────────

describe("refreshMigrationState()", () => {
  it("GETs /v2/admin/files/migrate/status and populates migrationState", async () => {
    const spy = setupFetch();
    const { refreshMigrationState, migrationState } = useFileMigration();
    await flush();

    spy.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(stateRunning), text: () => Promise.resolve("") });
    await refreshMigrationState();

    expect(migrationState.value).toEqual(stateRunning);
    const [url] = spy.mock.calls.at(-1) as [string, RequestInit];
    expect(url).toBe(`${BASE}/v2/admin/files/migrate/status`);
  });

  it("sets migrationError on HTTP failure", async () => {
    setupFetch();
    const { refreshMigrationState, migrationError } = useFileMigration();
    await flush();

    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false, status: 403 }));
    await refreshMigrationState();

    expect(migrationError.value).toMatch(/Failed to load migration status/);
  });
});

// ─── triggerMigration ────────────────────────────────────────────────────────

describe("triggerMigration()", () => {
  it("POSTs to /v2/admin/files/migrate with correct body and returns true on success", async () => {
    const spy = setupFetch(
      { ok: true, json: () => Promise.resolve(stateRunning), text: () => Promise.resolve("") },
    );
    const { triggerMigration, triggerSuccess } = useFileMigration();
    await flush();

    const ok = await triggerMigration("local", "garage");

    expect(ok).toBe(true);
    expect(triggerSuccess.value).toBe(true);

    const [url, init] = spy.mock.calls.at(-1) as [string, RequestInit];
    expect(url).toBe(`${BASE}/v2/admin/files/migrate`);
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body as string)).toEqual({
      sourceProviderId: "local",
      targetProviderId: "garage",
    });
    const headers = init.headers as Record<string, string>;
    expect(headers["Authorization"]).toBe(`Bearer ${ACCESS_TOKEN}`);
    expect(headers["Content-Type"]).toBe("application/json");
  });

  it("returns false and surfaces {detail} from error body", async () => {
    setupFetch();
    const { triggerMigration, triggerError } = useFileMigration();
    await flush();

    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      ok: false,
      status: 409,
      text: () => Promise.resolve(JSON.stringify({ detail: "Migration already running." })),
    }));
    const ok = await triggerMigration("local", "garage");

    expect(ok).toBe(false);
    expect(triggerError.value).toBe("Migration already running.");
  });

  it("surfaces {title} when {detail} is absent", async () => {
    setupFetch();
    const { triggerMigration, triggerError } = useFileMigration();
    await flush();

    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      ok: false,
      status: 400,
      text: () => Promise.resolve(JSON.stringify({ title: "Bad request." })),
    }));
    await triggerMigration("x", "y");

    expect(triggerError.value).toBe("Bad request.");
  });

  it("surfaces {message} when neither {detail} nor {title} is present", async () => {
    setupFetch();
    const { triggerMigration, triggerError } = useFileMigration();
    await flush();

    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
      text: () => Promise.resolve(JSON.stringify({ message: "Internal error." })),
    }));
    await triggerMigration("x", "y");

    expect(triggerError.value).toBe("Internal error.");
  });

  it("surfaces a generic HTTP message when body is not valid JSON", async () => {
    setupFetch();
    const { triggerMigration, triggerError } = useFileMigration();
    await flush();

    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      ok: false,
      status: 503,
      text: () => Promise.resolve("Service Unavailable"),
    }));
    await triggerMigration("x", "y");

    expect(triggerError.value).toMatch(/HTTP 503/);
  });

  it("flips isTriggering to true while in flight, then false after", async () => {
    setupFetch();
    const { triggerMigration, isTriggering } = useFileMigration();
    await flush();

    let settle!: (r: unknown) => void;
    vi.stubGlobal("fetch", vi.fn().mockReturnValue(
      new Promise((r) => { settle = r; }),
    ));

    const p = triggerMigration("local", "garage");
    expect(isTriggering.value).toBe(true);

    settle({ ok: true, json: () => Promise.resolve(stateRunning), text: () => Promise.resolve("") });
    await p;
    expect(isTriggering.value).toBe(false);
  });
});

// ─── rollbackFile ─────────────────────────────────────────────────────────────

describe("rollbackFile()", () => {
  const FILE_APPID = "0197b6a2-aaaa-7000-8000-000000000001";

  it("POSTs to /v2/admin/files/migrate/rollback/{appId} and returns true on success", async () => {
    const spy = setupFetch(
      // rollback POST
      { ok: true, json: () => Promise.resolve({}), text: () => Promise.resolve("") },
      // refreshMigrationState after rollback
      { ok: true, json: () => Promise.resolve(stateDone), text: () => Promise.resolve("") },
    );
    const { rollbackFile, rollbackSuccess } = useFileMigration();
    await flush();

    const ok = await rollbackFile(FILE_APPID);

    expect(ok).toBe(true);
    expect(rollbackSuccess.value).toBe(FILE_APPID);

    const rollbackCall = spy.mock.calls.find(([url]) =>
      (url as string).includes("/rollback/"),
    )!;
    expect(rollbackCall[0]).toBe(
      `${BASE}/v2/admin/files/migrate/rollback/${FILE_APPID}`,
    );
    expect((rollbackCall[1] as RequestInit).method).toBe("POST");
    const headers = (rollbackCall[1] as RequestInit).headers as Record<string, string>;
    expect(headers["Authorization"]).toBe(`Bearer ${ACCESS_TOKEN}`);
  });

  it("URL-encodes the fileAppId in the rollback path", async () => {
    const spy = setupFetch(
      { ok: true, json: () => Promise.resolve({}), text: () => Promise.resolve("") },
      { ok: true, json: () => Promise.resolve(stateDone), text: () => Promise.resolve("") },
    );
    const { rollbackFile } = useFileMigration();
    await flush();

    await rollbackFile("id/with/slashes");

    const rollbackCall = spy.mock.calls.find(([url]) =>
      (url as string).includes("/rollback/"),
    )!;
    expect(rollbackCall[0]).toContain("id%2Fwith%2Fslashes");
  });

  it("returns false and surfaces {detail} from error body", async () => {
    setupFetch(
      {
        ok: false,
        status: 404,
        text: () => Promise.resolve(JSON.stringify({ detail: "File not found in migration set." })),
      },
    );
    const { rollbackFile, rollbackError } = useFileMigration();
    await flush();

    const ok = await rollbackFile("nonexistent");

    expect(ok).toBe(false);
    expect(rollbackError.value).toBe("File not found in migration set.");
  });

  it("refreshes migration state after a successful rollback", async () => {
    setupFetch(
      { ok: true, json: () => Promise.resolve({}), text: () => Promise.resolve("") },
      { ok: true, json: () => Promise.resolve(stateDone), text: () => Promise.resolve("") },
    );
    const { rollbackFile, migrationState } = useFileMigration();
    await flush();

    await rollbackFile(FILE_APPID);

    expect(migrationState.value?.status).toBe("DONE");
  });
});

// ─── Polling behavior (fake timers) ─────────────────────────────────────────

describe("startPolling() / stopPolling()", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it("stopPolling() is a no-op when polling is not active", async () => {
    setupFetch();
    const { stopPolling } = useFileMigration();
    expect(() => stopPolling()).not.toThrow();
  });

  it("startPolling() then stopPolling() leaves no active timers", async () => {
    const spy = setupFetch();
    spy.mockResolvedValue({ ok: true, json: () => Promise.resolve(stateRunning), text: () => Promise.resolve("") });
    const { startPolling, stopPolling } = useFileMigration();

    startPolling();
    stopPolling();
    expect(vi.getTimerCount()).toBe(0);
  });

  it("polling stops automatically when state becomes DONE", async () => {
    const spy = setupFetch();
    spy.mockResolvedValue({ ok: true, json: () => Promise.resolve(stateDone), text: () => Promise.resolve("") });
    const { startPolling } = useFileMigration();

    startPolling();
    // Advance past the 2000ms polling interval
    await vi.advanceTimersByTimeAsync(2500);
    // The interval tick returned DONE — polling self-cancels
    expect(vi.getTimerCount()).toBe(0);
  });

  it("polling stops automatically when state becomes FAILED", async () => {
    const spy = setupFetch();
    spy.mockResolvedValue({ ok: true, json: () => Promise.resolve(stateFailed), text: () => Promise.resolve("") });
    const { startPolling } = useFileMigration();

    startPolling();
    await vi.advanceTimersByTimeAsync(2500);
    expect(vi.getTimerCount()).toBe(0);
  });

  it("polling continues while state is RUNNING", async () => {
    const spy = setupFetch();
    spy.mockResolvedValue({ ok: true, json: () => Promise.resolve(stateRunning), text: () => Promise.resolve("") });
    const { startPolling, stopPolling } = useFileMigration();

    startPolling();
    await vi.advanceTimersByTimeAsync(2500);
    // Still running — timer should still be active
    expect(vi.getTimerCount()).toBeGreaterThanOrEqual(1);
    stopPolling();
  });

  it("duplicate startPolling() calls do not register a second interval", async () => {
    const spy = setupFetch();
    spy.mockResolvedValue({ ok: true, json: () => Promise.resolve(stateRunning), text: () => Promise.resolve("") });
    const { startPolling, stopPolling } = useFileMigration();

    startPolling();
    const countAfterFirst = vi.getTimerCount();
    startPolling(); // second call must be idempotent
    expect(vi.getTimerCount()).toBe(countAfterFirst);
    stopPolling();
  });
});
