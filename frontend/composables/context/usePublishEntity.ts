/**
 * KIP1e composable wrapping `POST /v2/{kind}/{appId}/publish`.
 *
 * Raw `fetch` rather than a generated client because the
 * `@dlr-shepard/backend-client` regeneration for the KIP1a endpoints
 * hasn't landed yet (the OpenAPI Generator pipeline runs separately
 * from this slice; this composable is the temporary bridge). When
 * the regenerated client ships a `PublishApi`, swap this composable
 * to use it — the wire shape is the same.
 *
 * Idempotency: a second POST on an already-published entity returns
 * the existing `:Publication` row (same PID); `?force=true` mints a
 * fresh row. See `aidocs/66 §4.1` and `docs/reference/publish-and-pids.md`.
 */

export type PublishableKind = "data-objects" | "collections";

export interface PublicationResponse {
  appId: string;
  pid: string;
  mintedAt: string;
  minterId: string;
  resolverUrl: string;
  publishedBy?: string | null;
  entityKind?: string | null;
  entityAppId?: string | null;
}

interface PublishOptions {
  force?: boolean;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

export function usePublishEntity() {
  const isPublishing = ref(false);
  const publishError = ref<string | null>(null);

  async function publish(
    kind: PublishableKind,
    appId: string,
    opts: PublishOptions = {},
  ): Promise<PublicationResponse | null> {
    isPublishing.value = true;
    publishError.value = null;

    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    if (!accessToken) {
      publishError.value = "Not authenticated";
      isPublishing.value = false;
      return null;
    }

    const force = opts.force ? "?force=true" : "";
    const url = `${v2BaseUrl()}/v2/${kind}/${encodeURIComponent(appId)}/publish${force}`;

    try {
      const response = await fetch(url, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (!response.ok) {
        // KIP1a returns RFC 7807 problem+json on non-200 paths.
        const bodyText = await response.text().catch(() => "");
        let detail = `Publish failed (HTTP ${response.status})`;
        try {
          const parsed = JSON.parse(bodyText);
          if (parsed && typeof parsed.detail === "string") detail = parsed.detail;
          else if (parsed && typeof parsed.title === "string") detail = parsed.title;
        } catch {
          if (bodyText) detail = bodyText.slice(0, 200);
        }
        publishError.value = detail;
        handleError(detail, "publishEntity");
        return null;
      }
      const body = (await response.json()) as PublicationResponse;
      return body;
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "Network error during publish";
      publishError.value = message;
      handleError(message, "publishEntity");
      return null;
    } finally {
      isPublishing.value = false;
    }
  }

  return { publish, isPublishing, publishError };
}
