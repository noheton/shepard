import type { Page, BrowserContext } from "@playwright/test";

const KC = process.env.KEYCLOAK_HOST || "http://192.168.1.49:8082";
const REALM = "shepard-demo";

/** Full OIDC browser login. Returns when the app's home page is loaded. */
export async function loginAs(page: Page, username: string, password: string) {
  await page.goto("/auth/signIn");
  await page.getByRole("button", { name: /sign in|login/i }).first().click();
  await page.waitForURL(`${KC}/realms/${REALM}/**`, { timeout: 10_000 });
  await page.fill("#username", username);
  await page.fill("#password", password);
  await page.click('[type="submit"]');
  // Wait until we're back on the app and NOT on an error page.
  await page.waitForURL(/shepard\.nuclide\.systems(?!.*error)/, {
    timeout: 15_000,
  });
  // Confirm login succeeded: the nav should show "SIGN OUT".
  await page.waitForSelector("text=SIGN OUT", { timeout: 10_000 });
}

/** Save auth state to a file so other tests can skip the login UI. */
export async function saveAuthState(
  context: BrowserContext,
  file: string,
): Promise<void> {
  await context.storageState({ path: file });
}
