/**
 * P13 — Collection SSE change-feed composable.
 *
 * Opens a persistent HTTP connection to `GET /v2/collections/{appId}/events`
 * and surfaces typed change events. Uses `fetch()` + streaming body reader
 * (NOT native `EventSource`) so that the `Authorization: Bearer <token>`
 * header can be attached — native `EventSource` does not support custom
 * headers.
 *
 * Reconnects automatically after 3 s on network errors or server close.
 * Stops on unmount (or explicit `close()`).
 *
 * Usage:
 *   const { onEvent, close } = useCollectionEvents(collectionAppId);
 *   onEvent((event) => {
 *     if (event.eventType === "DATA_OBJECT_CREATED") refreshItems();
 *   });
 *
 * The composable is self-contained — it does not expose a reactive state;
 * callers register side-effect handlers via `onEvent(handler)`.
 */

export interface CollectionEventIO {
  /** Discriminator: DATA_OBJECT_CREATED | DATA_OBJECT_UPDATED | DATA_OBJECT_DELETED | COLLECTION_UPDATED | HEARTBEAT */
  eventType: string;
  /** appId of the entity that changed. Null for HEARTBEAT events. */
  entityAppId: string | null;
  /** Kind of entity: "DataObject" | "Collection". Null for HEARTBEAT. */
  entityKind: string | null;
  /** appId of the Collection this event belongs to. Null for HEARTBEAT. */
  collectionAppId: string | null;
  /** Username of the actor who triggered the change. Null for HEARTBEAT. */
  actorUsername: string | null;
  /** Epoch-millis when the event was emitted. */
  timestamp: number;
}

type EventHandler = (event: CollectionEventIO) => void;

const RECONNECT_DELAY_MS = 3_000;

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function getToken(): Promise<string | null> {
  const { data: session } = useAuth();
  return session.value?.accessToken ?? null;
}

/**
 * Parse a raw SSE buffer into zero or more {@link CollectionEventIO} records.
 * Buffer accumulates across chunks. Caller must pass back the un-consumed tail.
 *
 * SSE wire format (RFC EventStream):
 *   event: DATA_OBJECT_CREATED\n
 *   data: {"eventType":"DATA_OBJECT_CREATED",...}\n
 *   \n
 *   (blank line = message boundary)
 *
 * Returns { events, remaining } where `remaining` is the portion of the buffer
 * that has not yet been terminated by a blank line.
 */
function parseBuffer(buffer: string): { events: CollectionEventIO[]; remaining: string } {
  const events: CollectionEventIO[] = [];
  let remaining = buffer;

  while (true) {
    const boundaryIdx = remaining.indexOf("\n\n");
    if (boundaryIdx === -1) break;

    const block = remaining.slice(0, boundaryIdx);
    remaining = remaining.slice(boundaryIdx + 2);

    // Skip comment-only blocks (SSE keep-alive: ": heartbeat")
    const lines = block.split("\n");
    let dataAccumulator = "";

    for (const line of lines) {
      if (line.startsWith("data: ")) {
        dataAccumulator += line.slice(6);
      }
      // Ignore `id:`, `event:`, `:` comment lines — the eventType is already
      // embedded in the JSON payload.
    }

    if (!dataAccumulator) continue;

    try {
      const parsed = JSON.parse(dataAccumulator) as CollectionEventIO;
      events.push(parsed);
    } catch {
      // Malformed JSON — skip block
    }
  }

  return { events, remaining };
}

export function useCollectionEvents(
  collectionAppId: Ref<string | null | undefined> | string | null | undefined,
) {
  const appIdRef = isRef(collectionAppId)
    ? collectionAppId
    : ref(collectionAppId);

  const handlers: EventHandler[] = [];
  let abortController: AbortController | null = null;
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  let stopped = false;

  function dispatch(event: CollectionEventIO) {
    // Silently drop HEARTBEAT events — callers don't need them.
    if (event.eventType === "HEARTBEAT") return;
    for (const h of handlers) {
      try {
        h(event);
      } catch {
        // Handler errors must not interrupt delivery to other handlers.
      }
    }
  }

  async function connect(appId: string) {
    if (stopped) return;

    const token = await getToken();
    if (!token) {
      // Not authenticated — don't attempt connection.
      return;
    }

    abortController = new AbortController();
    const url = `${v2BaseUrl()}/v2/collections/${appId}/events`;

    try {
      const response = await fetch(url, {
        headers: { Authorization: `Bearer ${token}` },
        signal: abortController.signal,
      });

      if (!response.ok || !response.body) {
        // 401 / 403 / 404 — don't reconnect, these are auth/routing errors
        // that won't resolve on retry.
        if (response.status === 401 || response.status === 403 || response.status === 404) {
          return;
        }
        // Other errors (5xx, network) — reconnect after delay.
        scheduleReconnect(appId);
        return;
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const { events, remaining } = parseBuffer(buffer);
        buffer = remaining;

        for (const event of events) {
          dispatch(event);
        }
      }

      // Server closed the stream cleanly — reconnect after short delay.
      scheduleReconnect(appId);
    } catch (err: unknown) {
      if ((err as Error)?.name === "AbortError") {
        // Intentional close (unmount or close()) — do not reconnect.
        return;
      }
      // Network error — reconnect after delay.
      scheduleReconnect(appId);
    }
  }

  function scheduleReconnect(appId: string) {
    if (stopped) return;
    reconnectTimer = setTimeout(() => {
      if (!stopped) void connect(appId);
    }, RECONNECT_DELAY_MS);
  }

  function clearReconnect() {
    if (reconnectTimer !== null) {
      clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }
  }

  function close() {
    stopped = true;
    clearReconnect();
    abortController?.abort();
    abortController = null;
  }

  function open(appId: string | null | undefined) {
    close();
    stopped = false;
    if (appId) void connect(appId);
  }

  // React to appId changes (e.g. navigating from one collection to another).
  watch(
    appIdRef,
    (newAppId) => {
      open(newAppId);
    },
    { immediate: true },
  );

  // Stop on component unmount.
  onUnmounted(() => {
    close();
  });

  /**
   * Register a handler that will be called for every non-HEARTBEAT event.
   * Multiple handlers can be registered; they fire in registration order.
   *
   * @param handler callback receiving a {@link CollectionEventIO}
   */
  function onEvent(handler: EventHandler) {
    handlers.push(handler);
  }

  return { onEvent, close };
}
