export async function getLogoutUrl(wellKnown: URL): Promise<URL> {
  const oidcConfig = await getOidcConfiguration(wellKnown);
  const logoutUrl = oidcConfig.end_session_endpoint;
  return new URL(logoutUrl);
}

export async function getTokenUrl(wellKnown: URL): Promise<URL> {
  const oidcConfig = await getOidcConfiguration(wellKnown);
  const tokenUrl = oidcConfig.token_endpoint;
  return new URL(tokenUrl);
}

async function getOidcConfiguration(
  wellKnown: URL,
): Promise<OidcConfiguration> {
  const response = await fetch(wellKnown);

  if (!response.ok) {
    throw new Error(
      `Failed to fetch OIDC configuration: ${response.statusText}`,
    );
  }
  return response.json();
}

type OidcConfiguration = {
  token_endpoint: string;
  end_session_endpoint: string;
};
