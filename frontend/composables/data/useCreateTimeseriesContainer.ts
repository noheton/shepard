import type { PermissionType } from "@dlr-shepard/backend-client";
import {
  createV2Container,
  v2BaseUrl,
} from "../container/createV2Container";

/**
 * CONTAINER-PERMS-V2: container creation and permission setup now both go
 * through the `/v2/` surface exclusively:
 *
 *  - Container creation: `POST /v2/containers?kind=timeseries` via {@link createV2Container}.
 *  - Permission setup: `PATCH /v2/containers/{appId}/permissions` via a
 *    hand-written fetch, keyed by the `appId` of the created v2 container.
 *
 * The v1 `TimeseriesContainerApi` is no longer called from this composable.
 */
export async function useCreateTimeseriesContainer(
  timeseriesContainerName: string,
  permissionType: PermissionType,
) {
  const newTimeseriesContainer = await createV2Container(
    "timeseries",
    timeseriesContainerName,
  );
  if (!newTimeseriesContainer) return;

  const appId = newTimeseriesContainer.appId;
  if (!appId) return;

  const { data: session } = useAuth();
  const accessToken = session.value?.accessToken;
  const headers: Record<string, string> = {
    "Content-Type": "application/merge-patch+json",
    Accept: "application/json",
  };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;

  const permissionsUpdateSuccess = await fetch(
    `${v2BaseUrl()}/v2/containers/${encodeURIComponent(appId)}/permissions`,
    {
      method: "PATCH",
      headers,
      body: JSON.stringify({ permissionType }),
    },
  )
    .then(resp => {
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      return true;
    })
    .catch(error => {
      handleError(error, "updatePermissions");
      return false;
    });
  if (!permissionsUpdateSuccess) return;

  emitSuccess(
    `Successfully created container "${newTimeseriesContainer.name}"`,
  );

  return newTimeseriesContainer;
}
