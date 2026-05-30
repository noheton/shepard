/**
 * KRL-INTERPRETER-06 — unit tests for the useKrlInterpret composable's
 * pure status-to-message helper.
 *
 * The wrapper's I/O behaviour (fetch dispatch, auth header, response
 * parsing) is integration-tested via the Playwright 4K spec — the bits
 * we test here are the user-friendly status code → message mapping.
 */
import { describe, it, expect } from "vitest";
import { krlErrorMessageForStatus } from "../../composables/useKrlInterpret";

describe("useKrlInterpret — krlErrorMessageForStatus", () => {
  it("returns the auth-expired message for 401", () => {
    expect(krlErrorMessageForStatus(401)).toMatch(/sign in expired/i);
  });

  it("returns the forbidden message for 403", () => {
    expect(krlErrorMessageForStatus(403)).toMatch(/write access/i);
  });

  it("returns the unsupported-constructs message for 422", () => {
    expect(krlErrorMessageForStatus(422)).toMatch(/unsupported constructs/i);
  });

  it("returns the HARD-STOP message for 501", () => {
    expect(krlErrorMessageForStatus(501)).toMatch(/hard-?stop/i);
  });

  it("returns the operator-hint sidecar-down message for 502", () => {
    const msg = krlErrorMessageForStatus(502);
    expect(msg).toMatch(/sidecar/i);
    expect(msg).toMatch(/COMPOSE_PROFILES=krl-interpreter/);
    expect(msg).toMatch(/operator action/i);
  });

  it("returns the timeout message for 504", () => {
    expect(krlErrorMessageForStatus(504)).toMatch(/timed out/i);
  });

  it("falls back to the server-supplied detail string when given on 400", () => {
    expect(krlErrorMessageForStatus(400, "srcFileAppId is required")).toMatch(
      /srcFileAppId/,
    );
  });

  it("uses a generic message when status is unknown and no fallback is given", () => {
    expect(krlErrorMessageForStatus(418)).toMatch(/HTTP 418/);
  });
});
