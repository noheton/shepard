/**
 * PROJ-PANEL-1 — composable wrapping GET /v2/projects/{appId} and
 * GET /v2/projects/{appId}/sub-collections.
 *
 * Cross-reference: aidocs/integrations/121-project-and-subcollections.md §3.1 + §3.2.
 *
 * Raw fetch rather than a generated client because there is no ProjectApi in
 * @dlr-shepard/backend-client yet. When the regenerated client ships one,
 * swap this composable to use it.
 */

export interface ProjectIO {
  appId: string;
  name: string;
  description?: string | null;
  ownerGroup?: string | null;
  programmes: string[];
  subCollectionCount: number;
  aggregateDoCount: number;
  lastActivityMillis?: number | null;
  isProject: boolean;
}

export interface SubCollectionItemIO {
  appId: string;
  name: string;
  ownerGroup?: string | null;
  doCount: number;
  lastActivityMillis?: number | null;
  alsoMemberOf: string[];
}

export interface SubCollectionsIO {
  projectAppId: string;
  programmes: string[];
  subCollections: SubCollectionItemIO[];
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

/**
 * Load the Project envelope at `appId`. The returned reactive bag carries:
 *   project (ProjectIO | null) — null both before-load and on 404 / not-a-Project.
 *   isLoading
 *   isMissing — true when the load returned a 404 (i.e. the appId is not a
 *               Project). The UI uses this to hide Project-only affordances
 *               on Collection-detail pages without showing an error.
 *   error     — non-null on real fetch errors (not 404s).
 *   refresh() — re-runs the load.
 */
export function useProject(appId: string) {
  const project = ref<ProjectIO | null>(null);
  const isLoading = ref(false);
  const isMissing = ref(false);
  const error = ref<string | null>(null);

  async function refresh() {
    if (!appId) {
      project.value = null;
      isMissing.value = true;
      return;
    }
    isLoading.value = true;
    error.value = null;
    isMissing.value = false;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const url = `${v2BaseUrl()}/v2/projects/${appId}`;
      const response = await fetch(url, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (response.status === 404) {
        project.value = null;
        isMissing.value = true;
        return;
      }
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      project.value = await response.json();
    } catch (e) {
      error.value = "Failed to load Project";
      handleError(e, "fetching Project envelope");
    } finally {
      isLoading.value = false;
    }
  }

  refresh();

  return { project, isLoading, isMissing, error, refresh };
}

/**
 * Load the sub-Collections of the Project at `appId`. Same isMissing / 404
 * semantics as {@link useProject}.
 */
export function useProjectSubCollections(appId: string) {
  const subCollections = ref<SubCollectionsIO | null>(null);
  const isLoading = ref(false);
  const isMissing = ref(false);
  const error = ref<string | null>(null);

  async function refresh() {
    if (!appId) {
      subCollections.value = null;
      isMissing.value = true;
      return;
    }
    isLoading.value = true;
    error.value = null;
    isMissing.value = false;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const url = `${v2BaseUrl()}/v2/projects/${appId}/sub-collections`;
      const response = await fetch(url, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (response.status === 404) {
        subCollections.value = null;
        isMissing.value = true;
        return;
      }
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      subCollections.value = await response.json();
    } catch (e) {
      error.value = "Failed to load sub-Collections";
      handleError(e, "fetching project sub-collections");
    } finally {
      isLoading.value = false;
    }
  }

  refresh();

  return { subCollections, isLoading, isMissing, error, refresh };
}

/**
 * Load the appIds of every Project on the instance, ordered by Collection name.
 * Powers the top-nav /projects page.
 */
export function useProjectList() {
  const projectAppIds = ref<string[]>([]);
  const isLoading = ref(false);
  const error = ref<string | null>(null);

  async function refresh() {
    isLoading.value = true;
    error.value = null;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const url = `${v2BaseUrl()}/v2/projects`;
      const response = await fetch(url, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      // GET /v2/projects returns the PagedResponseIO {items,...} envelope
      // (APISIMP-PAGINATION-ENVELOPE). Tolerate both the envelope and a legacy
      // bare array.
      const body: unknown = await response.json();
      projectAppIds.value = Array.isArray(body)
        ? body
        : ((body as { items?: string[] })?.items ?? []);
    } catch (e) {
      error.value = "Failed to load Projects";
      handleError(e, "fetching Project list");
    } finally {
      isLoading.value = false;
    }
  }

  refresh();

  return { projectAppIds, isLoading, error, refresh };
}
