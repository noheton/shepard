/**
 * PLACEHOLDER-REPLACE-FS1e1 — Vitest tests for useFileMigration composable.
 *
 * Tests the six acceptance criteria:
 *   1. Initial state is null / not loading
 *   2. refresh() fetches status and populates state
 *   3. refresh() on HTTP error populates error
 *   4. triggerMigration() calls POST then refreshes
 *   5. triggerMigration() with API error surfaces error
 *   6. Polling starts when status is RUNNING and stops when DONE/FAILED
 */

import {
  describe,
  it,
  expect,
  vi,
  beforeEach,
  afterEach,
} from "vitest";
import type { FileMigrationStateIO } from "~/composables/context/admin/useFileMigration";
import { useFileMigration } from "~/composables/context/admin/useFileMigration";

const ACCESS_TOKEN = "test-token-fs1e1";

const IDLE_STATE: FileMigrationStateIO = {
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

const RUNNING_STATE: FileMigrationStateIO = {
  status: "RUNNING",
  sourceProviderId: "gridfs",
  targetProviderId: "s3",
  filesTotal: 1500,
  filesMigrated: 300,
  filesFailed: 0,
  startedAt: "2026-06-02T03:00:00Z",
  updatedAt: "2026-06-02T03:05:00Z",
  errorMessage: null,
};

const DONE_STATE: FileMigrationStateIO = {
  ...RUNNING_STATE,
  status: "DONE",
  filesMigrated: 1500,
  updatedAt: "2026-06-02T03:10:00Z",
};

const FAILED_STATE: FileMigrationStateIO = {
  ...RUNNING_STATE,
  status: "FAILED",
  filesMigrated: 500,
  filesFailed: 5,
  errorMessage: "Connection refused to gridfs adapter",
};

beforeEach(() => {
  vi.clearAllMocks();
  vi.useFakeTimers();

  // Stub Vue lifecycle hook — onUnmounted is not available in Vitest node env.
  // The composable uses it to clean up polling; stub it to be a no-op for tests.
  (globalThis as unknown as Record<string, unknown>).onUnmounted = vi.fn();
  // Stub watch — used for auto-starting polling when migrationState becomes RUNNING
  (globalThis as unknown as Record<string, unknown>).watch = vi.fn();

  (globalThis as unknown as Record<string, unknown>).useRuntimeConfig = () => ({
    public: { backendApiUrl: "http://localhost:8080/shepard/api" },
  });
  (globalThis as unknown as Record<string, unknown>).useAuth = () => ({
    data: ref<{ accessToken: string }>({ accessToken: ACCESS_TOKEN }),
  });

  vi.stubGlobal("fetch", vi.fn());
});

afterEach(() => {
  vi.useRealTimers();
});

function mockFetchOk(body: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(body),
    }),
  );
}

function mockFetchError(status: number, bodyText = "error") {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: false,
      status,
      text: () => Promise.resolve(bodyText),
    }),
  );
}

// ─── 1. Initial state ────────────────────────────────────────────────────────

describe("useFileMigration — initial state", () => {
  it("migrationState starts null before first fetch", () => {
    // Prevent auto-fetch from completing during construction
    vi.stubGlobal("fetch", vi.fn().mockReturnValue(new Promise(() => {})));

    const { migrationState, migrationError } = useFileMigration();

    // migrationState is null before any response comes back
    expect(migrationState.value).toBeNull();
    expect(migrationError.value).toBeNull();
  });

  it("isTriggering starts false", () => {
    vi.stubGlobal("fetch", vi.fn().mockReturnValue(new Promise(() => {})));
    const { isTriggering } = useFileMigration();
    expect(isTriggering.value).toBe(false);
  });
});

// ─── 2. refresh() populates state ────────────────────────────────────────────

describe("useFileMigration — refreshMigrationState()", () => {
  it("populates migrationState from successful GET", async () => {
    mockFetchOk(IDLE_STATE);
    const { migrationState, migrationError, refreshMigrationState } =
      useFileMigration();
    await refreshMigrationState();

    expect(migrationState.value).toEqual(IDLE_STATE);
    expect(migrationError.value).toBeNull();
  });

  it("sends Authorization header with Bearer token", async () => {
    mockFetchOk(IDLE_STATE);
    const { refreshMigrationState } = useFileMigration();
    await refreshMigrationState();

    const calls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls;
    const statusCall = calls.find(([url]: [string]) =>
      url.includes("/v2/admin/files/migrate/status"),
    );
    expect(statusCall).toBeDefined();
    const [, opts] = statusCall as [string, RequestInit];
    expect((opts?.headers as Record<string, string>)?.Authorization).toBe(
      `Bearer ${ACCESS_TOKEN}`,
    );
  });
});

// ─── 3. refresh() on error ────────────────────────────────────────────────────

describe("useFileMigration — refreshMigrationState() error path", () => {
  it("sets migrationError on HTTP 500", async () => {
    mockFetchError(500);
    const { migrationState, migrationError, refreshMigrationState } =
      useFileMigration();
    await refreshMigrationState();

    expect(migrationError.value).toBeTruthy();
    expect(migrationState.value).toBeNull();
  });

  it("sets migrationError on network failure", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockRejectedValue(new Error("Network down")),
    );
    const { migrationError, refreshMigrationState } = useFileMigration();
    await refreshMigrationState();

    expect(migrationError.value).toBe("Failed to load migration status.");
  });
});

// ─── 4. triggerMigration() calls POST then refreshes ─────────────────────────

describe("useFileMigration — triggerMigration()", () => {
  it("calls POST /v2/admin/files/migrate with correct body and updates state", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(RUNNING_STATE),
    });
    vi.stubGlobal("fetch", fetchMock);

    const { migrationState, triggerMigration } = useFileMigration();
    const result = await triggerMigration("gridfs", "s3");

    expect(result).toBe(true);
    expect(migrationState.value?.status).toBe("RUNNING");

    // Check that POST was called with correct body
    const postCall = fetchMock.mock.calls.find(([url, opts]: [string, RequestInit]) =>
      url.includes("/v2/admin/files/migrate") &&
      !url.includes("status") &&
      opts?.method === "POST",
    );
    expect(postCall).toBeDefined();
    const [, opts] = postCall as [string, RequestInit];
    expect(JSON.parse(opts.body as string)).toEqual({
      sourceProviderId: "gridfs",
      targetProviderId: "s3",
    });
  });

  it("sets triggerSuccess to true on successful trigger", async () => {
    mockFetchOk(RUNNING_STATE);
    const { triggerSuccess, triggerMigration } = useFileMigration();
    await triggerMigration("gridfs", "s3");

    expect(triggerSuccess.value).toBe(true);
  });
});

// ─── 5. triggerMigration() API error ─────────────────────────────────────────

describe("useFileMigration — triggerMigration() error path", () => {
  it("sets triggerError on HTTP 409 (already running)", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        status: 409,
        text: () =>
          Promise.resolve(
            JSON.stringify({ detail: "Migration already in progress" }),
          ),
      }),
    );
    const { triggerError, triggerSuccess, triggerMigration } =
      useFileMigration();
    const result = await triggerMigration("gridfs", "s3");

    expect(result).toBe(false);
    expect(triggerError.value).toBe("Migration already in progress");
    expect(triggerSuccess.value).toBe(false);
  });

  it("sets triggerError on network failure", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockRejectedValue(new Error("Connection refused")),
    );
    const { triggerError, triggerMigration } = useFileMigration();
    const result = await triggerMigration("gridfs", "s3");

    expect(result).toBe(false);
    expect(triggerError.value).toBe("Failed to trigger migration.");
  });
});

// ─── 6. Polling ──────────────────────────────────────────────────────────────

describe("useFileMigration — polling", () => {
  it("startPolling() and stopPolling() can be called without error", async () => {
    mockFetchOk(IDLE_STATE);
    const { startPolling, stopPolling } = useFileMigration();

    // Should not throw
    startPolling();
    stopPolling();
  });

  it("startPolling() calls refresh on each interval tick", async () => {
    let tickCount = 0;
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation(() => {
        tickCount++;
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve(RUNNING_STATE),
        });
      }),
    );

    const { startPolling, stopPolling } = useFileMigration();

    // Reset counter after construction auto-fetches
    tickCount = 0;

    startPolling();

    // Advance 2 ticks at 2000ms each
    await vi.advanceTimersByTimeAsync(2001);
    await Promise.resolve();
    await Promise.resolve();

    // At least one polling fetch happened
    expect(tickCount).toBeGreaterThan(0);

    stopPolling();
  });

  it("polling stops automatically after DONE state is observed", async () => {
    // First few calls return RUNNING, then DONE
    let callNum = 0;
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation(() => {
        callNum++;
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve(callNum >= 3 ? DONE_STATE : RUNNING_STATE),
        });
      }),
    );

    const { startPolling, migrationState } = useFileMigration();
    // Prime with RUNNING
    migrationState.value = RUNNING_STATE;

    startPolling();

    // Advance several ticks
    await vi.advanceTimersByTimeAsync(2001);
    await Promise.resolve();
    await Promise.resolve();
    await vi.advanceTimersByTimeAsync(2001);
    await Promise.resolve();
    await Promise.resolve();

    const countAtDone = callNum;

    // After DONE is returned, no more ticks should fire
    await vi.advanceTimersByTimeAsync(6001);
    await Promise.resolve();

    // callNum should not grow after polling stopped
    expect(callNum).toBe(countAtDone);
  });

  it("polling stops automatically after FAILED state is observed", async () => {
    let callNum = 0;
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation(() => {
        callNum++;
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve(FAILED_STATE),
        });
      }),
    );

    const { startPolling, migrationState } = useFileMigration();
    migrationState.value = RUNNING_STATE;

    startPolling();

    await vi.advanceTimersByTimeAsync(2001);
    await Promise.resolve();
    await Promise.resolve();

    const countAfterFailed = callNum;

    await vi.advanceTimersByTimeAsync(6001);
    await Promise.resolve();

    expect(callNum).toBe(countAfterFailed);
  });
});
