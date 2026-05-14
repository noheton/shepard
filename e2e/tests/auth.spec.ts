/**
 * Auth-flow tests.
 * Run against https://shepard.nuclide.systems (requires Keycloak on :8082).
 *
 * Demo credentials (shepard-demo realm):
 *   alice / alice-demo   (regular user)
 *   admin / admin-demo   (instance admin)
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "./helpers/auth";

const KC = process.env.KEYCLOAK_HOST || "http://192.168.1.49:8082";
const REALM = "shepard-demo";

test.describe("OIDC discovery", () => {
  test("well-known endpoint responds", async ({ request }) => {
    const res = await request.get(
      `${KC}/realms/${REALM}/.well-known/openid-configuration`,
    );
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.issuer).toContain(REALM);
    expect(body.authorization_endpoint).toBeDefined();
  });

  test("auth/providers endpoint returns 200", async ({ request }) => {
    const res = await request.get("/api/auth/providers");
    expect(res.status()).toBe(200);
  });
});

test.describe("Sign-in page", () => {
  test("loads without 500", async ({ page }) => {
    await page.goto("/auth/signIn");
    await expect(page.locator("body")).not.toContainText("500");
    await expect(page.locator("body")).not.toContainText("Internal Server Error");
  });

  test("has a sign-in button", async ({ page }) => {
    await page.goto("/auth/signIn");
    await expect(
      page.getByRole("button", { name: /sign in|login/i }).first(),
    ).toBeVisible();
  });
});

test.describe("Login flow", () => {
  test("alice can log in and sees SIGN OUT", async ({ page }) => {
    await loginAs(page, "alice", "alice-demo");
    // Nav should show the logout action — proof of authenticated session.
    await expect(page.getByText("SIGN OUT")).toBeVisible();
    // Should not be on an error page.
    expect(page.url()).not.toContain("error=");
  });

  test("logged-in user is not shown the sign-in page on /", async ({ page }) => {
    await loginAs(page, "alice", "alice-demo");
    await page.goto("/");
    // After login, visiting / should NOT redirect back to signIn.
    expect(page.url()).not.toContain("/auth/signIn");
  });

  test("invalid credentials show Keycloak error", async ({ page }) => {
    await page.goto("/auth/signIn");
    await page.getByRole("button", { name: /sign in|login/i }).first().click();
    await page.waitForURL(`${KC}/realms/${REALM}/**`, { timeout: 8_000 });
    await page.fill("#username", "alice");
    await page.fill("#password", "wrong-password");
    await page.click('[type="submit"]');
    await expect(
      page.getByText(/invalid username or password/i),
    ).toBeVisible({ timeout: 6_000 });
  });
});
