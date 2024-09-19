import { NuxtAuthHandler } from "#auth";
export default NuxtAuthHandler({
  secret: process.env.AUTH_SECRET,
  providers: [
    {
      id: "oidc",
      name: "OIDC",
      type: "oauth",
      wellKnown: `${process.env.OIDC_ISSUER}.well-known/openid-configuration`,
      clientId: process.env.OIDC_CLIENT_ID,
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
      if (account?.id_token) {
        token.idToken = account.id_token;
      }
      return token;
    },
    async session({ session, token }) {
      session.idToken = token.idToken;
      return session;
    },
  },
  events: {
    async signOut({ token }: { token: { idToken: string } }) {
      const logOutUrl = new URL(
        `${process.env.OIDC_ISSUER}protocol/openid-connect/logout`,
      );
      logOutUrl.searchParams.set("id_token_hint", token.idToken);
      fetch(logOutUrl);
    },
  },
});

declare module "next-auth/jwt" {
  interface JWT {
    idToken: string;
  }
}

declare module "next-auth" {
  interface Session {
    idToken: string;
  }
}
