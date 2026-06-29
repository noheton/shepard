import {
  AdminApi,
  type PermissionAuditLogEntry,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";

const PAGE_SIZE = 50;

export function useFetchPermissionAuditLog() {
  const entries = ref<PermissionAuditLogEntry[]>([]);
  const isLoading = ref(false);
  const hasMore = ref(false);

  const filterEntityAppId = ref("");
  const filterActor = ref("");
  const filterFrom = ref("");
  const filterTo = ref("");
  const page = ref(0);

  async function refresh() {
    isLoading.value = true;
    try {
      const response = await useV2ShepardApi(AdminApi).value.permissionAuditLog({
        entityAppId: filterEntityAppId.value || undefined,
        actor: filterActor.value || undefined,
        from: filterFrom.value || undefined,
        to: filterTo.value || undefined,
        page: page.value,
        pageSize: PAGE_SIZE,
      });
      const rows = (response.items ?? []) as PermissionAuditLogEntry[];
      entries.value = rows;
      hasMore.value = rows.length === PAGE_SIZE;
    } catch (error) {
      entries.value = [];
      handleError(error, "fetching permission audit log");
    } finally {
      isLoading.value = false;
    }
  }

  function applyFilters() {
    page.value = 0;
    refresh();
  }

  function nextPage() {
    page.value += 1;
    refresh();
  }

  function prevPage() {
    if (page.value > 0) {
      page.value -= 1;
      refresh();
    }
  }

  refresh();

  return {
    entries,
    isLoading,
    hasMore,
    page,
    filterEntityAppId,
    filterActor,
    filterFrom,
    filterTo,
    applyFilters,
    nextPage,
    prevPage,
    refresh,
  };
}
