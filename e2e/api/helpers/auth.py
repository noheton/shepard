"""Auth helpers for the shepard API integration test suite.

Provides a Keycloak ROPC flow that mirrors what entrypoint.sh does so no
new authentication path is introduced. The preferred CI path is to supply
SHEPARD_API_KEY directly and skip this entirely.
"""

import base64
import json
import httpx


def fetch_api_key_via_keycloak(
    backend_url: str,
    kc_url: str,
    realm: str,
    client_id: str,
    username: str,
    password: str,
) -> str:
    """Mint a fresh API key for the given Keycloak user.

    Steps:
    1. ROPC token grant from Keycloak.
    2. Decode the JWT sub from the access token payload.
    3. Touch GET /users/{sub} to trigger first-time :User node creation.
    4. POST /users/{sub}/apikeys with Bearer auth.
    5. Return the 'jwt' field (the API key string).
    """
    # Step 1: ROPC grant
    resp = httpx.post(
        f"{kc_url}/realms/{realm}/protocol/openid-connect/token",
        data={
            "grant_type": "password",
            "client_id": client_id,
            "username": username,
            "password": password,
            "scope": "openid",
        },
        verify=False,
        timeout=15,
    )
    resp.raise_for_status()
    access_token = resp.json()["access_token"]

    # Step 2: decode sub from JWT payload (no signature verification needed here)
    payload_b64 = access_token.split(".")[1]
    pad = payload_b64 + "=" * (-len(payload_b64) % 4)
    sub = json.loads(base64.urlsafe_b64decode(pad))["sub"]

    headers = {"Authorization": f"Bearer {access_token}"}

    # Step 3: first-touch to create the :User node (UserFilter on first request)
    httpx.get(
        f"{backend_url}/users/{sub}",
        headers=headers,
        verify=False,
        timeout=10,
    )

    # Step 4: mint API key
    key_resp = httpx.post(
        f"{backend_url}/users/{sub}/apikeys",
        headers={**headers, "Content-Type": "application/json"},
        json={"name": "integration-test-key"},
        verify=False,
        timeout=10,
    )
    key_resp.raise_for_status()
    return key_resp.json()["jwt"]
