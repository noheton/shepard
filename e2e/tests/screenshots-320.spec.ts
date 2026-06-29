/**
 * Task #320 — MFFD presentation screenshots at 1920 against live shepard.nuclide.systems.
 * Captures console + page errors per surface so we can fix any error toasts before
 * finalizing (active driver directive). Deleted after the PNGs are produced.
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "./helpers/auth";

test.use({ viewport: { width: 1920, height: 1080 } });

const SURFACES: { id: string; url: string; settle: number }[] = [
  {
    id: "A-dataobject-provo",
    url: "/collections/019ed455-66f4-7aea-8cb3-5c0b34a737df/dataobjects/019ed617-238e-7f97-a1c6-edb93be6e9bc",
    settle: 7000,
  },
  { id: "B1-vocabularies", url: "/semantic/vocabularies", settle: 6000 },
  { id: "B2-collection-fair", url: "/collections/019ed455-66f4-7aea-8cb3-5c0b34a737df", settle: 6000 },
  {
    // C — real Trace3D AFP TCP thermal trail: X/Y/Z (mm, R20/MFZ) + TemperaturTape
    // (°C, MTLH/MFZ) on container 019ede2a-...   (mffd-afp-tapelaying-timeseries).
    // 30-second window early in the imported range; inferno colormap.
    id: "C-trace3d",
    url: "/shapes/render?roles=eyJ4Ijp7Im1lYXN1cmVtZW50IjoibW0iLCJkZXZpY2UiOiJSMjAiLCJsb2NhdGlvbiI6Ik1GWiIsInN5bWJvbGljTmFtZSI6IlgiLCJmaWVsZCI6InZhbHVlIn0sInkiOnsibWVhc3VyZW1lbnQiOiJtbSIsImRldmljZSI6IlIyMCIsImxvY2F0aW9uIjoiTUZaIiwic3ltYm9saWNOYW1lIjoiWSIsImZpZWxkIjoidmFsdWUifSwieiI6eyJtZWFzdXJlbWVudCI6Im1tIiwiZGV2aWNlIjoiUjIwIiwibG9jYXRpb24iOiJNRloiLCJzeW1ib2xpY05hbWUiOiJaIiwiZmllbGQiOiJ2YWx1ZSJ9LCJ2YWx1ZSI6eyJtZWFzdXJlbWVudCI6ImNlbHNpdXMiLCJkZXZpY2UiOiJNVExIIiwibG9jYXRpb24iOiJNRloiLCJzeW1ib2xpY05hbWUiOiJUZW1wZXJhdHVyVGFwZSIsImZpZWxkIjoidmFsdWUifX0=&containerAppId=019ede2a-60ec-7ac1-899d-3fe4c6263cbb&renderer=trace-3d&startNs=1670425854562000000&endNs=1670425884562000000&colormap=inferno",
    settle: 12000,
  },
  {
    id: "D-timeseries",
    url: "/collections/019ed455-66f4-7aea-8cb3-5c0b34a737df/dataobjects/019ed617-238e-7f97-a1c6-edb93be6e9bc/timeseriesereferences/019efae9-6084-71f9-8fe1-d1f820b936d6",
    settle: 8000,
  },
  {
    id: "E-svdx-welding",
    url: "/collections/019ed455-67f7-7725-bf2d-7cd1b67aca9f/dataobjects/019ed586-87df-7710-a94c-c5fd60108d20",
    settle: 7000,
  },
];

test("capture MFFD presentation surfaces", async ({ page }) => {
  test.setTimeout(240_000);
  await loginAs(page, "bob", "bob-demo");

  for (const s of SURFACES) {
    const consoleErrors: string[] = [];
    const pageErrors: string[] = [];
    const onConsole = (msg: import("@playwright/test").ConsoleMessage) => {
      if (msg.type() === "error") consoleErrors.push(msg.text());
    };
    const onPageError = (err: Error) => pageErrors.push(err.message);
    page.on("console", onConsole);
    page.on("pageerror", onPageError);

    await page.goto(s.url, { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(s.settle);
    await page.screenshot({ path: `screenshots-320/${s.id}.png`, fullPage: true });

    const bodyText = ((await page.locator("body").innerText().catch(() => "")) || "")
      .replace(/\s+/g, " ")
      .slice(0, 600);
    console.log(`\n=== ${s.id} (${s.url}) ===`);
    console.log(`URL_AFTER=${page.url()}`);
    console.log(`CONSOLE_ERRORS=${JSON.stringify(consoleErrors.slice(0, 8))}`);
    console.log(`PAGE_ERRORS=${JSON.stringify(pageErrors.slice(0, 8))}`);
    console.log(`BODY=${bodyText}`);

    page.off("console", onConsole);
    page.off("pageerror", onPageError);
  }

  expect(true).toBe(true);
});
