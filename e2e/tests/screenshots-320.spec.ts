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
  {
    // F — TimeseriesContainer detail for the MFFD AFP tapelaying container
    // (019ede2a-60ec-7ac1-899d-3fe4c6263cbb). Same container as C (Trace3D)
    // and G (force trace); the container holds X/Y/Z + temperature +
    // pressure + joint channels at MFFD scale (192 channels total). The
    // frame shows the container header, the channel-overview chart pane,
    // and the channel-inventory table (paginated; first page lists area_mm2,
    // TOWBORDERDETECTRANGE_MM[1], Brake_Act_Percent_INT[3], TemperaturPly,
    // TapeForce_TapeActForce[1], fitError2, CutCounter, E1_MotorTemperature,
    // VC_FeedLength3, fitCorrelation0). Replaces the earlier SVDX landing
    // (uninspiring; SVDX-CSV-SIBLINGS-MISSING blocks timeseries materialisation
    // for that file). The chart pane reads empty by default ("No data points
    // yet — enable live mode to stream"); the channel-inventory table below
    // is the "all the rich signals at MFFD scale" narrative.
    id: "F-ts-container-chart",
    url: "/containers/timeseries/019ede2a-60ec-7ac1-899d-3fe4c6263cbb",
    settle: 10000,
  },
  {
    // G — robot cell: Trace3D of the AFP TCP path colored by tape consolidation
    // force (TapeForce_TapeActForce[1], newton, MTLH/MFZ) over a 60-second
    // tapelaying window starting at Track 9 / Run 29995 (2023-01-19 08:30:39
    // UTC). Same container as Shot C. The TapeForce signal is the dynamic
    // pressure applied to each ply; varies -1.3 to 11.9 N over the window.
    id: "G-robot-cell-pressure",
    url: "/shapes/render?roles=eyJ4Ijp7Im1lYXN1cmVtZW50IjoibW0iLCJkZXZpY2UiOiJSMjAiLCJsb2NhdGlvbiI6Ik1GWiIsInN5bWJvbGljTmFtZSI6IlgiLCJmaWVsZCI6InZhbHVlIn0sInkiOnsibWVhc3VyZW1lbnQiOiJtbSIsImRldmljZSI6IlIyMCIsImxvY2F0aW9uIjoiTUZaIiwic3ltYm9saWNOYW1lIjoiWSIsImZpZWxkIjoidmFsdWUifSwieiI6eyJtZWFzdXJlbWVudCI6Im1tIiwiZGV2aWNlIjoiUjIwIiwibG9jYXRpb24iOiJNRloiLCJzeW1ib2xpY05hbWUiOiJaIiwiZmllbGQiOiJ2YWx1ZSJ9LCJ2YWx1ZSI6eyJtZWFzdXJlbWVudCI6Im5ld3RvbiIsImRldmljZSI6Ik1UTEgiLCJsb2NhdGlvbiI6Ik1GWiIsInN5bWJvbGljTmFtZSI6IlRhcGVGb3JjZV9UYXBlQWN0Rm9yY2VbMV0iLCJmaWVsZCI6InZhbHVlIn19&containerAppId=019ede2a-60ec-7ac1-899d-3fe4c6263cbb&renderer=trace-3d&startNs=1674117039068000000&endNs=1674117099068000000&colormap=inferno",
    settle: 12000,
  },
  {
    // H — OTVIS thermography viewer: /shapes/render renderer=thermography
    // streams a heatmap PNG for the bound OTvis FileReference (S10_M11_L11_F1.OTvis).
    id: "H-otvis-viewer",
    url: "/shapes/render?renderer=thermography&fileReferenceAppId=019ed593-0db3-7185-b863-fcc75379d412",
    settle: 12000,
  },
  {
    // I — video reference detail page: the new dedicated route that mirrors
    // the filereferences/timeseriesereferences shape. Inline VideoPlayer +
    // metadata + annotation list. Same DataObject as before (P11_S_2teBahn,
    // VideoStreamReference for P11_S_2.Bahn.MP4).
    id: "I-video-player",
    url: "/collections/019edb10-c107-7473-ae28-ffc592aba860/dataobjects/019f129e-c45e-703a-97de-079cb19d1052/videostreamreferences/019f129f-aa8b-70a6-b908-45e9918d7531",
    settle: 12000,
  },
  {
    // J — URDF viewer for the real KR210 R2700/2 kinematic model (KUKA
    // KR210 R2700/2 — the actual robot in the MFFD AFP cell). The URDF +
    // meshes are served from the frontend public dir; Three.js urdf-loader
    // renders the kinematic tree inline.
    //
    // KNOWN GAP — see aidocs/16:FILEREF-CONTENT-DOWNLOAD-MISSING-2026-06-30.
    // The proper SceneGraphPlay path is
    // `/scene-graphs/play/019f16d8-2fc7-76e8-b066-f713e2fb713d` (the
    // MAPPING_RECIPE template seeded 2026-06-30 against URDF FileReference
    // 019f1479-1142-... + joint TimeseriesReference 019f147a-8b40-... with
    // joint_1..joint_6 bindings on device KR210-R2700-2). Materialization
    // works end-to-end, but the play page's URDF-blob loader hits
    // `GET /v2/references/{appId}/content`, which 400s for file-kind refs
    // because `FileReferenceKindHandler.downloadContent(...)` is not
    // overridden — that's a separate backend slice (~10 lines + tests in
    // the kind handler reusing `SingletonFileReferenceService.getPayload`)
    // queued as `FILEREF-CONTENT-DOWNLOAD-MISSING-2026-06-30`. Until that
    // ships, J keeps the legacy /shapes/render path (untextured robot, no
    // trace overlay) rather than regressing to an error banner.
    id: "J-urdf-with-robot",
    url: "/shapes/render?renderer=urdf&urdfUrl=%2Furdf-samples%2Fkr210_r2700_2%2Fkuka_quantec_support%2Furdf%2Fkr210_r2700_2.urdf&packagePath=%2Furdf-samples%2Fkr210_r2700_2",
    settle: 18000,
  },
  {
    // K — Home page: hero + WAAPI animate-on-mount entrance (#162) + recent
    // collections digest (#116). The "this is what a researcher sees on
    // arrival" frame.
    id: "K-home",
    url: "/",
    settle: 6000,
  },
  {
    // L — Personal landing /me (#43): collections digest, sparklines per
    // collection, last-change timestamps, contributor badges, ORCID badge
    // (#195 + RDM-002). The "your data follows you" frame.
    id: "L-me-personal",
    url: "/me",
    settle: 7000,
  },
  {
    // M — Collections index (the unified gallery view): hero images per
    // collection, contributor badges, last-update timestamps, per-row
    // sparklines (#112), per-kind reference counts. The FAIR/HMC card
    // shape for every dataset at a glance. Replaces the prior
    // /search?q=tapelaying frame (thin 1-row result, no sparklines) so
    // the presentation deck shows the actual gallery surface.
    id: "M-collections-gallery",
    url: "/collections",
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

    // I-video-player: seek to 2/3 of duration so the first painted frame is
    // a real image rather than the black pre-decode initial frame (hevc takes
    // a moment to render its first keyframe in Chromium).
    if (s.id === "I-video-player") {
      await page.evaluate(async () => {
        const v = document.querySelector("video") as HTMLVideoElement | null;
        if (!v) return;
        await new Promise<void>((r) => {
          if (v.readyState >= 1 && Number.isFinite(v.duration)) r();
          else v.addEventListener("loadedmetadata", () => r(), { once: true });
        });
        v.currentTime = (v.duration || 0) * (2 / 3);
        await new Promise<void>((r) => {
          if (v.readyState >= 2) r();
          else v.addEventListener("seeked", () => r(), { once: true });
        });
      });
      await page.waitForTimeout(4000);
    }

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
