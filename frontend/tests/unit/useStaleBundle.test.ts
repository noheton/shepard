import { describe, it, expect, beforeEach, vi } from "vitest";

// Module-level refs persist across imports within a test run.
// Use vi.resetModules() + dynamic import to get a fresh instance per test.
let useStaleBundle: typeof import("~/composables/layout/useStaleBundle").useStaleBundle;

beforeEach(async () => {
  vi.resetModules();
  useStaleBundle = (await import("~/composables/layout/useStaleBundle")).useStaleBundle;
});

describe("useStaleBundle — initial state", () => {
  it("show is false on init", () => {
    const { show } = useStaleBundle();
    expect(show.value).toBe(false);
  });

  it("authExpired is false on init", () => {
    const { authExpired } = useStaleBundle();
    expect(authExpired.value).toBe(false);
  });
});

describe("useStaleBundle — triggerAuthExpired", () => {
  it("sets authExpired and show to true", () => {
    const { show, authExpired, triggerAuthExpired } = useStaleBundle();
    triggerAuthExpired();
    expect(authExpired.value).toBe(true);
    expect(show.value).toBe(true);
  });

  it("is idempotent — second call does not re-show after dismiss", () => {
    const { show, triggerAuthExpired, dismiss } = useStaleBundle();
    triggerAuthExpired();
    dismiss();
    expect(show.value).toBe(false);
    // second trigger must not override the user's dismiss
    triggerAuthExpired();
    expect(show.value).toBe(false);
  });
});

describe("useStaleBundle — dismiss", () => {
  it("hides the auth-expired banner", () => {
    const { show, triggerAuthExpired, dismiss } = useStaleBundle();
    triggerAuthExpired();
    dismiss();
    expect(show.value).toBe(false);
  });

  it("hides the version-stale banner", () => {
    const { show, initVersion, setVersions, dismiss } = useStaleBundle();
    initVersion("1.0");
    setVersions("1.0", "1.1");
    expect(show.value).toBe(true);
    dismiss();
    expect(show.value).toBe(false);
  });
});

describe("useStaleBundle — version mismatch", () => {
  it("show is true when versions differ", () => {
    const { show, initVersion, setVersions } = useStaleBundle();
    initVersion("1.0");
    setVersions("1.0", "1.1");
    expect(show.value).toBe(true);
  });

  it("show is false when versions match", () => {
    const { show, initVersion, setVersions } = useStaleBundle();
    initVersion("1.0");
    setVersions("1.0", "1.0");
    expect(show.value).toBe(false);
  });
});

describe("useStaleBundle — triggerChunkReload", () => {
  it("sets pendingReload and show to true", () => {
    const { show, pendingReload, triggerChunkReload } = useStaleBundle();
    triggerChunkReload();
    expect(pendingReload.value).toBe(true);
    expect(show.value).toBe(true);
  });

  it("is idempotent — second call while counting down is a no-op", () => {
    const { reloadCountdown, triggerChunkReload } = useStaleBundle();
    triggerChunkReload();
    const first = reloadCountdown.value;
    triggerChunkReload();
    expect(reloadCountdown.value).toBe(first);
  });

  it("dismiss cancels the countdown and hides the banner", () => {
    const { show, pendingReload, triggerChunkReload, dismiss } = useStaleBundle();
    triggerChunkReload();
    dismiss();
    expect(pendingReload.value).toBe(false);
    expect(show.value).toBe(false);
  });
});
