import { ProvenanceApi, type Activity } from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";

const LOAD_BATCH = 100;

export function useFetchAdminActivities() {
  const activities = ref<Activity[]>([]);
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
      const paged = await useV2ShepardApi(ProvenanceApi).value.listActivities({
        agent: filterAgent.value || undefined,
        targetKind: filterTargetKind.value || undefined,
        targetAppId: filterTargetAppId.value || undefined,
        limit: limit.value,
      });
      activities.value = Array.isArray(paged) ? paged : ((paged as unknown as { items?: Activity[] })?.items ?? []);
      hasMore.value = (Array.isArray(paged) ? paged.length : ((paged as unknown as { items?: Activity[] })?.items?.length ?? 0)) >= limit.value;
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
