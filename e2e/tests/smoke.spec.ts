/**
 * Smoke tests: static reachability and API health.
 * These never need login and run fast.
 */
import { test, expect } from "@playwright/test";

const BACKEND = process.env.BACKEND_URL || "https://shepard-api.nuclide.systems";

test("frontend home page loads", async ({ page }) => {
  const res = await page.goto("/");
  expect(res?.status()).toBe(200);
});

test("backend healthz reports UP", async ({ request }) => {
  const res = await request.get(`${BACKEND}/shepard/api/healthz`);
  expect(res.status()).toBe(200);
  const body = await res.json();
  expect(body.status).toBe("UP");
});

test("no redirect loop on home page", async ({ page }) => {
  // Count navigations (actual page-level redirects, not asset loads).
  const navUrls: string[] = [];
  page.on("framenavigated", f => {
    if (f === page.mainFrame()) navUrls.push(f.url());
  });

  await page.goto("/", { waitUntil: "networkidle" });

  // A redirect loop would spin the main frame across many different URLs.
  // Normal load: 1-3 navigations (initial + any soft redirects).
  const unique = new Set(navUrls.map(u => u.split("?")[0]));
  expect(unique.size).toBeLessThan(5);
  expect(navUrls.length).toBeLessThan(10);
});

test("Nuxt meta tags present", async ({ page }) => {
  await page.goto("/");
  // The Nuxt app injects <title> and meta charset.
  const title = await page.title();
  expect(title.length).toBeGreaterThan(0);
  expect(title.toLowerCase()).not.toBe("error");
});
