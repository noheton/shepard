import { NuxtAuthHandler } from "#auth";
import type { Account } from "next-auth";

const runtimeConfig = useRuntimeConfig();
const OIDC_CONFIGURATION_URL = new URL(
  `${runtimeConfig.oidcIssuer}.well-known/openid-configuration`,
);
export default NuxtAuthHandler({
  secret: runtimeConfig.authSecret,
  providers: [
    {
      id: "oidc",
      name: "OIDC",
      type: "oauth",
      wellKnown: `${runtimeConfig.oidcIssuer}.well-known/openid-configuration`,
      clientId: runtimeConfig.oidcClientId,
      authorization: { params: { scope: "openid email profile" } },
      client: {
        token_endpoint_auth_method: "none",
      },

      idToken: true,
      checks: ["pkce", "state"],
      profile: profile => ({ id: profile.sub, ...profile }),
    },
  ],
  callbacks: {
    async jwt({ token, account }) {
      // Case1: intial login
      if (account) {
        const { refresh_expires_in } = account as Account & {
          refresh_expires_in?: number;
        };
        return {
          accessToken: account.access_token,
          idToken: account.id_token,
          expiresAt: account.expires_at,
          refreshToken: account.refresh_token,
          refreshTokenExpiresAt: refresh_expires_in
            ? Math.floor(Date.now() / 1000 + refresh_expires_in)
            : undefined, // Store refresh token expiry if provided
          userId: account.providerAccountId,
        } as typeof token;
      }
      // Case2: Access token still valid
      if (
        Date.now() <
        token.expiresAt * 1000 - runtimeConfig.sessionRefreshInterval * 2
      )
        // 30000 * 2 since this is double of the session refresh interval
        return token;
      // Case3: Refresh token not valid/existing
      if (
        !token.refreshToken ||
        (token.refreshTokenExpiresAt &&
          Date.now() >= token.refreshTokenExpiresAt * 1000)
      ) {
        token.error = "RefreshTokenError";
        console.error(
          "Refresh token is not valid or does not exist",
          token.error,
        );
        return token;
      }
      // Case4: Refreshing the access token
      try {
        const tokenUrl = await getTokenUrl(OIDC_CONFIGURATION_URL);
        const response = await fetch(tokenUrl, {
          method: "POST",
          body: new URLSearchParams({
            client_id: runtimeConfig.oidcClientId,
            grant_type: "refresh_token",
            refresh_token: token.refreshToken,
          }),
        });

        const tokensOrError = await response.json();

        if (!response.ok) throw tokensOrError;

        const newTokens = tokensOrError as {
          access_token: string;
          expires_in: number;
          refresh_token?: string;
          refresh_expires_in?: number;
        };

        token.accessToken = newTokens.access_token;
        token.expiresAt = Math.floor(Date.now() / 1000 + newTokens.expires_in);

        // Some providers only issue refresh tokens once, so preserve if we did not get a new one
        if (newTokens.refresh_token) {
          token.refreshToken = newTokens.refresh_token;
        }
        // Preserve the refresh token expiry if provided
        if (newTokens.refresh_expires_in) {
          token.refreshTokenExpiresAt = Math.floor(
            Date.now() / 1000 + newTokens.refresh_expires_in,
          );
        }
        delete token.error;
        return token;
      } catch (error) {
        console.error("Error refreshing access_token", error);
        // If we fail to refresh the token, return an error so we can handle it on the page
        token.error = "RefreshTokenError";
        return token;
      }
    },
    async session({ session, token }) {
      session.accessToken = token.accessToken;
      session.idToken = token.idToken;
      session.userId = token.userId;
      session.error = token.error;
      return session;
    },
  },
  session: {
    maxAge: 1800, // 30 min - sets 'exp' field in token, but currently do not has an effect
  },
  pages: {
    signIn: "/auth/signIn",
  },
  events: {
    async signOut(message) {
      const logOutUrl = await getLogoutUrl(OIDC_CONFIGURATION_URL);
      logOutUrl.searchParams.set("id_token_hint", message.token.idToken);
      try {
        await fetch(logOutUrl);
      } catch (error) {
        console.error("Error during logout:", error);
      }
    },
  },
});

declare module "next-auth/jwt" {
  interface JWT {
    expiresAt: number;
    accessToken: string;
    idToken: string;
    refreshToken: string;
    refreshTokenExpiresAt?: number; // Added to track refresh token expiry
    error?: "RefreshTokenError";
    userId: string;
  }
}

declare module "next-auth" {
  interface Session {
    accessToken: string;
    idToken: string;
    userId: string;
    error?: string;
  }
}
