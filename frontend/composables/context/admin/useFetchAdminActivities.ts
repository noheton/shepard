import { ProvenanceApi, type ActivityIO } from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";

const LOAD_BATCH = 100;

export function useFetchAdminActivities() {
  const activities = ref<ActivityIO[]>([]);
  const isLoading = ref(false);
  const hasMore = ref(false);

  const filterAgent = ref("");
  const filterTargetKind = ref("");
  const filterTargetAppId = ref("");

  // Active limit — bumped on each "Load more"
  const limit = ref(LOAD_BATCH);

  async function load() {
    isLoading.value = true;
    try {
      const rows = await useV2ShepardApi(ProvenanceApi).value.listActivities({
        agent: filterAgent.value || undefined,
        targetKind: filterTargetKind.value || undefined,
        targetAppId: filterTargetAppId.value || undefined,
        pageSize: limit.value,
      });
      activities.value = rows ?? [];
      hasMore.value = rows.length >= limit.value;
    } catch (error) {
      activities.value = [];
      hasMore.value = false;
      handleError(error, "fetching activity log");
    } finally {
      isLoading.value = false;
    }
  }

  function applyFilters() {
    limit.value = LOAD_BATCH;
    load();
  }

  function loadMore() {
    limit.value += LOAD_BATCH;
    load();
  }

  function resetFilters() {
    filterAgent.value = "";
    filterTargetKind.value = "";
    filterTargetAppId.value = "";
    applyFilters();
  }

  load();

  return {
    activities,
    isLoading,
    hasMore,
    filterAgent,
    filterTargetKind,
    filterTargetAppId,
    applyFilters,
    loadMore,
    resetFilters,
    refresh: applyFilters,
  };
}
