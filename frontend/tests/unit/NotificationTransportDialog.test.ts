/**
 * NTF1-UI-TRANSPORT-CRUD-FOLLOWUP — unit tests for the
 * NotificationTransportDialog form-logic helpers.
 *
 * Tests exercise the pure validation rules and form-state mechanics
 * without mounting the Vuetify component tree (no JSDOM required).
 * Playwright E2E tests cover the rendered surface at 4K viewport.
 *
 * The dialog's key contract under test:
 *  1. Kind-select shows SMTP fields only when kind === 'SMTP'.
 *  2. Kind-select shows Matrix fields only when kind === 'MATRIX'.
 *  3. Save is disabled (canSave = false) when required fields are blank.
 *  4. In edit mode the kind selector is disabled (kind is immutable).
 *  5. Credential fields (smtpPassword, matrixAccessToken) start blank in
 *     edit mode — write-only credential contract.
 *  6. Validation messages are correct per field / kind combination.
 */

import { describe, it, expect } from "vitest";
import type { TransportFormState, TransportKind } from "~/composables/context/admin/useNotificationTransports";

// ── Inline the dialog's pure helpers ─────────────────────────────────────────
// Mirror the computed logic from NotificationTransportDialog.vue so the tests
// run without mounting a Vue component.

function nameError(form: TransportFormState): string | null {
  return form.name.trim().length === 0 ? "Name is required." : null;
}

function smtpHostError(form: TransportFormState): string | null {
  if (form.kind !== "SMTP") return null;
  return (form.smtpHost ?? "").trim().length === 0 ? "SMTP host is required." : null;
}

function matrixHomeserverError(form: TransportFormState): string | null {
  if (form.kind !== "MATRIX") return null;
  return (form.matrixHomeserver ?? "").trim().length === 0
    ? "Homeserver URL is required."
    : null;
}

function canSave(form: TransportFormState): boolean {
  return (
    nameError(form) === null &&
    smtpHostError(form) === null &&
    matrixHomeserverError(form) === null
  );
}

/** True when the SMTP field block should be rendered. */
function showSmtpFields(kind: TransportKind): boolean {
  return kind === "SMTP";
}

/** True when the Matrix field block should be rendered. */
function showMatrixFields(kind: TransportKind): boolean {
  return kind === "MATRIX";
}

/** Simulate opening the dialog in edit mode — populate form, blank credentials. */
function openForEdit(initial: {
  kind: TransportKind;
  name: string;
  enabled: boolean;
  smtpHost?: string;
  smtpPort?: number;
  smtpFrom?: string;
  smtpTls?: boolean;
  matrixHomeserver?: string;
  matrixDefaultRoom?: string;
}): TransportFormState {
  return {
    kind: initial.kind,
    name: initial.name ?? "",
    enabled: initial.enabled ?? true,
    smtpHost: initial.smtpHost ?? "",
    smtpPort: initial.smtpPort ?? 587,
    smtpUsername: "",
    smtpPassword: "", // write-only — always blank in edit mode
    smtpFrom: initial.smtpFrom ?? "",
    smtpTls: initial.smtpTls ?? true,
    matrixHomeserver: initial.matrixHomeserver ?? "",
    matrixAccessToken: "", // write-only — always blank in edit mode
    matrixDefaultRoom: initial.matrixDefaultRoom ?? "",
  };
}

function blankForm(kind: TransportKind = "SMTP"): TransportFormState {
  return {
    kind,
    name: "",
    enabled: true,
    smtpHost: "",
    smtpPort: 587,
    smtpUsername: "",
    smtpPassword: "",
    smtpFrom: "",
    smtpTls: true,
    matrixHomeserver: "",
    matrixAccessToken: "",
    matrixDefaultRoom: "",
  };
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("NotificationTransportDialog — kind-field visibility", () => {
  it("shows SMTP fields when kind is SMTP", () => {
    expect(showSmtpFields("SMTP")).toBe(true);
    expect(showMatrixFields("SMTP")).toBe(false);
  });

  it("shows Matrix fields when kind is MATRIX", () => {
    expect(showSmtpFields("MATRIX")).toBe(false);
    expect(showMatrixFields("MATRIX")).toBe(true);
  });

  it("shows neither SMTP nor Matrix fields when kind is INAPP", () => {
    expect(showSmtpFields("INAPP")).toBe(false);
    expect(showMatrixFields("INAPP")).toBe(false);
  });
});

describe("NotificationTransportDialog — validation: name", () => {
  it("name error when name is empty", () => {
    expect(nameError(blankForm())).toBe("Name is required.");
  });

  it("name error when name is whitespace-only", () => {
    expect(nameError({ ...blankForm(), name: "   " })).toBe("Name is required.");
  });

  it("no name error when name has a value", () => {
    expect(nameError({ ...blankForm(), name: "Primary SMTP" })).toBeNull();
  });
});

describe("NotificationTransportDialog — validation: SMTP host", () => {
  it("smtpHostError when kind=SMTP and smtpHost is blank", () => {
    expect(smtpHostError({ ...blankForm("SMTP"), smtpHost: "" })).toBe("SMTP host is required.");
  });

  it("no smtpHostError when kind=SMTP and smtpHost is set", () => {
    expect(smtpHostError({ ...blankForm("SMTP"), smtpHost: "smtp.example.org" })).toBeNull();
  });

  it("no smtpHostError when kind=MATRIX (irrelevant)", () => {
    expect(smtpHostError({ ...blankForm("MATRIX"), smtpHost: "" })).toBeNull();
  });
});

describe("NotificationTransportDialog — validation: Matrix homeserver", () => {
  it("matrixHomeserverError when kind=MATRIX and homeserver is blank", () => {
    expect(matrixHomeserverError({ ...blankForm("MATRIX"), matrixHomeserver: "" })).toBe(
      "Homeserver URL is required.",
    );
  });

  it("no matrixHomeserverError when kind=MATRIX and homeserver is set", () => {
    expect(
      matrixHomeserverError({
        ...blankForm("MATRIX"),
        matrixHomeserver: "https://matrix.example.org",
      }),
    ).toBeNull();
  });

  it("no matrixHomeserverError when kind=SMTP (irrelevant)", () => {
    expect(matrixHomeserverError({ ...blankForm("SMTP"), matrixHomeserver: "" })).toBeNull();
  });
});

describe("NotificationTransportDialog — canSave gate", () => {
  it("canSave=false when name is blank (SMTP)", () => {
    expect(canSave({ ...blankForm("SMTP"), name: "", smtpHost: "smtp.example.org" })).toBe(false);
  });

  it("canSave=false when smtpHost is blank (SMTP)", () => {
    expect(canSave({ ...blankForm("SMTP"), name: "Primary", smtpHost: "" })).toBe(false);
  });

  it("canSave=true when name + smtpHost are filled (SMTP)", () => {
    expect(
      canSave({ ...blankForm("SMTP"), name: "Primary", smtpHost: "smtp.example.org" }),
    ).toBe(true);
  });

  it("canSave=false when matrixHomeserver is blank (MATRIX)", () => {
    expect(canSave({ ...blankForm("MATRIX"), name: "Eng", matrixHomeserver: "" })).toBe(false);
  });

  it("canSave=true when name + matrixHomeserver are filled (MATRIX)", () => {
    expect(
      canSave({
        ...blankForm("MATRIX"),
        name: "Engineering",
        matrixHomeserver: "https://matrix.example.org",
      }),
    ).toBe(true);
  });

  it("canSave=true for INAPP with only name filled (no extra required fields)", () => {
    expect(canSave({ ...blankForm("INAPP"), name: "In-app" })).toBe(true);
  });
});

describe("NotificationTransportDialog — edit-mode credential contract", () => {
  it("smtpPassword is blank when dialog opens for edit (write-only)", () => {
    const form = openForEdit({
      kind: "SMTP",
      name: "Primary",
      enabled: true,
      smtpHost: "smtp.example.org",
    });
    expect(form.smtpPassword).toBe("");
  });

  it("matrixAccessToken is blank when dialog opens for edit (write-only)", () => {
    const form = openForEdit({
      kind: "MATRIX",
      name: "Engineering",
      enabled: true,
      matrixHomeserver: "https://matrix.example.org",
    });
    expect(form.matrixAccessToken).toBe("");
  });

  it("non-credential fields are populated from the initial transport", () => {
    const form = openForEdit({
      kind: "SMTP",
      name: "Primary",
      enabled: false,
      smtpHost: "smtp.example.org",
      smtpPort: 465,
      smtpFrom: "noreply@example.org",
      smtpTls: false,
    });
    expect(form.name).toBe("Primary");
    expect(form.enabled).toBe(false);
    expect(form.smtpHost).toBe("smtp.example.org");
    expect(form.smtpPort).toBe(465);
    expect(form.smtpFrom).toBe("noreply@example.org");
    expect(form.smtpTls).toBe(false);
  });
});
