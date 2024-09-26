import { NuxtAuthHandler } from "#auth";
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
      if (account) {
        return {
          accessToken: account.access_token,
          idToken: account.id_token,
          expiresAt: account.expires_at,
          refreshToken: account.refresh_token,
        } as typeof token;
      } else if (Date.now() < token.expiresAt * 1000) {
        return token;
      } else {
        if (!token.refreshToken) throw new TypeError("Missing refresh_token");
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
          };
          token.accessToken = newTokens.access_token;
          token.expiresAt = Math.floor(
            Date.now() / 1000 + newTokens.expires_in,
          );
          // Some providers only issue refresh tokens once, so preserve if we did not get a new one
          if (newTokens.refresh_token)
            token.refreshToken = newTokens.refresh_token;
          return token;
        } catch (error) {
          console.error("Error refreshing access_token", error);
          // If we fail to refresh the token, return an error so we can handle it on the page
          token.error = "RefreshTokenError";
          return token;
        }
      }
    },
    async session({ session, token }) {
      session.accessToken = token.accessToken;
      session.idToken = token.idToken;
      return session;
    },
  },
  session: {
    maxAge: 1800,
  },
  events: {
    async signOut({ token }: { token: { idToken: string } }) {
      const logOutUrl = await getLogoutUrl(OIDC_CONFIGURATION_URL);
      logOutUrl.searchParams.set("id_token_hint", token.idToken);
      fetch(logOutUrl);
    },
  },
});

declare module "next-auth/jwt" {
  interface JWT {
    expiresAt: number;
    accessToken: string;
    idToken: string;
    refreshToken: string;
    error?: "RefreshTokenError";
  }
}

declare module "next-auth" {
  interface Session {
    accessToken: string;
    idToken: string;
  }
}
