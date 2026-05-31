import {
  AdminPublicationsApi,
  type AdminPublicationItemIO,
  type AdminPublicationListIO,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";

const PAGE_SIZE = 25;

/**
 * RDM-003 — composable for the instance-wide PID audit list.
 *
 * Calls GET /v2/admin/publications (instance-admin only).
 * Supports pagination + optional filter by retired state.
 */
export function useAdminPublications() {
  const items = ref<AdminPublicationItemIO[]>([]);
  const isLoading = ref(false);
  const totalCount = ref(0);
  const page = ref(0);
  const hasMore = ref(false);

  // Filter: show retired rows alongside active rows
  const showRetired = ref(true);

  // Filter: narrow by entity kind (null = all)
  const filterEntityKind = ref<string | null>(null);

  // Filter: narrow by minter id
  const filterMinterId = ref<string | null>(null);

  async function load() {
    isLoading.value = true;
    try {
      const result: AdminPublicationListIO = await useV2ShepardApi(AdminPublicationsApi).value.listAdminPublications({
        page: page.value,
        size: PAGE_SIZE,
      });

      // Apply client-side retired + kind + minter filters (server returns all rows).
      let filtered = result.items ?? [];
      if (!showRetired.value) {
        filtered = filtered.filter((p) => p.digitalObjectMutability !== "retired");
      }
      if (filterEntityKind.value) {
        filtered = filtered.filter((p) => p.entityKind === filterEntityKind.value);
      }
      if (filterMinterId.value) {
        filtered = filtered.filter((p) => p.minterId === filterMinterId.value);
      }

      items.value = filtered;
      totalCount.value = result.totalCount ?? 0;
      hasMore.value = result.items.length === PAGE_SIZE;
    } catch (error) {
      items.value = [];
      totalCount.value = 0;
      hasMore.value = false;
      handleError(error, "fetching admin publications list");
    } finally {
      isLoading.value = false;
    }
  }

  function applyFilters() {
    page.value = 0;
    load();
  }

  function nextPage() {
    if (hasMore.value) {
      page.value += 1;
      load();
    }
  }

  function prevPage() {
    if (page.value > 0) {
      page.value -= 1;
      load();
    }
  }

  function refresh() {
    page.value = 0;
    load();
  }

  load();

  return {
    items,
    isLoading,
    totalCount,
    page,
    hasMore,
    showRetired,
    filterEntityKind,
    filterMinterId,
    applyFilters,
    nextPage,
    prevPage,
    refresh,
  };
}
