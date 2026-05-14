/**
 * E2E auth flow tests.
 * These run against the local stack (http://localhost:80) and Keycloak (port 8082).
 *
 * Demo credentials (shepard-demo realm):
 *   alice / alice-demo   (regular user)
 *   admin / admin-demo   (instance admin)
 */
import { test, expect, Page } from "@playwright/test";

const KEYCLOAK_HOST = process.env.KEYCLOAK_HOST || "http://192.168.1.49:8082";
const REALM = "shepard-demo";

async function login(page: Page, username: string, password: string) {
  // Sign-in page should have a button that starts the OIDC flow.
  await page.getByRole("button", { name: /sign in|login/i }).first().click();

  // Should redirect to Keycloak.
  await page.waitForURL(`${KEYCLOAK_HOST}/realms/${REALM}/**`);
  await page.fill("#username", username);
  await page.fill("#password", password);
  await page.click('[type="submit"]');

  // Should land back on the app after login.
  await page.waitForURL(/localhost|shepard\.nuclide\.systems/);
}

test.describe("App loads", () => {
  test("home page returns 200 and renders HTML", async ({ page }) => {
    const response = await page.goto("/");
    expect(response?.status()).toBe(200);
    await expect(page.locator("html")).toBeAttached();
  });

  test("sign-in page loads without 500", async ({ page }) => {
    await page.goto("/auth/signIn");
    expect(page.url()).not.toContain("error");
    // Should not show an internal server error.
    await expect(page.locator("body")).not.toContainText("500");
    await expect(page.locator("body")).not.toContainText("Internal Server Error");
  });

  test("sign-in page has a login button or provider link", async ({ page }) => {
    await page.goto("/auth/signIn");
    // The Nuxt auth sign-in page should present a button to start OIDC flow.
    const loginEl = page.getByRole("button", { name: /sign in|login|oidc/i }).first();
    await expect(loginEl).toBeVisible();
  });
});

test.describe("OIDC discovery", () => {
  test("OIDC well-known endpoint responds", async ({ request }) => {
    const res = await request.get(
      `${KEYCLOAK_HOST}/realms/${REALM}/.well-known/openid-configuration`,
    );
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.issuer).toContain(REALM);
    expect(body.authorization_endpoint).toBeDefined();
  });

  test("auth/providers endpoint returns OIDC provider", async ({ request }) => {
    const res = await request.get("/api/auth/providers");
    expect(res.status()).toBe(200);
  });
});

test.describe("Login flow", () => {
  test("alice can log in and reach the main app", async ({ page }) => {
    await page.goto("/auth/signIn");
    await login(page, "alice", "alice-demo");

    // After login the app should load some content — not a 500 or error page.
    await expect(page.locator("body")).not.toContainText("500");
    await expect(page.locator("body")).not.toContainText("Realm does not exist");
  });

  test("invalid credentials show Keycloak error", async ({ page }) => {
    await page.goto("/auth/signIn");
    await page.getByRole("button", { name: /sign in|login/i }).first().click();
    await page.waitForURL(`${KEYCLOAK_HOST}/realms/${REALM}/**`);
    await page.fill("#username", "alice");
    await page.fill("#password", "wrong-password");
    await page.click('[type="submit"]');

    // Keycloak shows an inline error on bad credentials.
    await expect(
      page.getByText(/invalid username or password/i),
    ).toBeVisible();
  });
});
