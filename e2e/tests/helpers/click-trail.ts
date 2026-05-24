/**
 * Click-trail instrumentation helper for UX Scrutinizer workflow specs.
 *
 * Wraps user interactions in `step(label, action)` calls that:
 *   - Increment a click counter
 *   - Record the action label + before/after URL + timestamp
 *   - Optionally screenshot at each step
 *
 * At the end of a workflow, dump the trail JSON to disk so a finding doc
 * can quote it ("N clicks, M page transitions, T seconds").
 *
 * Usage:
 *   const trail = new ClickTrail(page, "wf-03-tr004-timeseries-chart", OUT);
 *   await trail.goto("/collections/42");
 *   await trail.step("Click TR-004 row", async () => {
 *     await page.getByText("TR-004").click();
 *   });
 *   await trail.save();
 *   // trail.clicks, trail.transitions, trail.elapsedMs available
 */
import type { Page } from "@playwright/test";
import * as fs from "fs";
import * as path from "path";

export type TrailStep = {
  n: number;
  label: string;
  kind: "goto" | "click" | "type" | "wait" | "note";
  urlBefore: string;
  urlAfter: string;
  pageTransition: boolean;
  durationMs: number;
  screenshot?: string;
  notes?: string;
};

export class ClickTrail {
  page: Page;
  label: string;
  outDir: string;
  steps: TrailStep[] = [];
  t0: number;
  screenshotEvery: boolean;

  constructor(page: Page, label: string, outDir: string, opts: { screenshotEvery?: boolean } = {}) {
    this.page = page;
    this.label = label;
    this.outDir = outDir;
    this.t0 = Date.now();
    this.screenshotEvery = opts.screenshotEvery ?? true;
    fs.mkdirSync(outDir, { recursive: true });
  }

  private async record(kind: TrailStep["kind"], label: string, action: () => Promise<void>): Promise<void> {
    const urlBefore = this.page.url();
    const tStart = Date.now();
    let err: Error | undefined;
    try {
      await action();
    } catch (e) {
      err = e as Error;
    }
    // Let any navigation settle. Use 'load' (cheaper than networkidle, which can
    // hang on pages with long-poll subscriptions like notifications).
    try {
      await this.page.waitForLoadState("load", { timeout: 4000 });
    } catch {
      // ignore — measurement should be resilient to slow pages
    }
    // Fixed extra delay so framework hydration completes before screenshot
    await this.page.waitForTimeout(500);
    const urlAfter = this.page.url();
    const n = this.steps.length + 1;
    const step: TrailStep = {
      n,
      label,
      kind,
      urlBefore,
      urlAfter,
      pageTransition: stripQuery(urlBefore) !== stripQuery(urlAfter),
      durationMs: Date.now() - tStart,
      notes: err ? `ERROR: ${err.message}` : undefined,
    };
    if (this.screenshotEvery) {
      const fname = `${this.label}-step-${String(n).padStart(2, "0")}.png`;
      const ss = path.join(this.outDir, fname);
      try {
        await this.page.screenshot({ path: ss, fullPage: false });
        step.screenshot = fname;
      } catch {
        // ignore screenshot failures
      }
    }
    this.steps.push(step);
  }

  async goto(url: string, label?: string): Promise<void> {
    await this.record("goto", label ?? `Navigate to ${url}`, async () => {
      await this.page.goto(url, { waitUntil: "domcontentloaded", timeout: 20_000 });
    });
  }

  /** Counts as 1 click. The label should describe the affordance the user perceives. */
  async step(label: string, action: () => Promise<void>): Promise<void> {
    await this.record("click", label, action);
  }

  /** Typing into a field counts as user effort but not as a "click". */
  async type(label: string, action: () => Promise<void>): Promise<void> {
    await this.record("type", label, action);
  }

  /** Free-text annotation, e.g., "page took 3.5s to render data". */
  async note(label: string): Promise<void> {
    const urlNow = this.page.url();
    this.steps.push({
      n: this.steps.length + 1,
      label,
      kind: "note",
      urlBefore: urlNow,
      urlAfter: urlNow,
      pageTransition: false,
      durationMs: 0,
    });
  }

  get clicks(): number {
    return this.steps.filter(s => s.kind === "click").length;
  }
  get transitions(): number {
    return this.steps.filter(s => s.pageTransition).length;
  }
  get elapsedMs(): number {
    return Date.now() - this.t0;
  }

  async save(extra: Record<string, unknown> = {}): Promise<void> {
    const summary = {
      workflow: this.label,
      runAt: new Date().toISOString(),
      totalSteps: this.steps.length,
      clicks: this.clicks,
      typingSteps: this.steps.filter(s => s.kind === "type").length,
      pageTransitions: this.transitions,
      elapsedMs: this.elapsedMs,
      elapsedSeconds: Math.round(this.elapsedMs / 100) / 10,
      ...extra,
      steps: this.steps,
    };
    const f = path.join(this.outDir, `${this.label}-trail.json`);
    fs.writeFileSync(f, JSON.stringify(summary, null, 2));
    // Compact console line so the test log shows the headline number
    console.log(`[trail] ${this.label}: ${this.clicks} clicks, ${this.transitions} transitions, ${summary.elapsedSeconds}s — ${this.steps.length} total steps`);
  }
}

function stripQuery(u: string): string {
  return u.split("?")[0].split("#")[0];
}
