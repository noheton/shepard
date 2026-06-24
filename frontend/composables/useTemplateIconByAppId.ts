/**
 * TEMPLATE-ICONS-2-FE-RENDER-POINTS-EXPAND — template icon cache for
 * list-view render sites that have `attachedTemplateAppId` but not the
 * full template object.
 *
 * Call once per component; pass a reactive list of template appIds.
 * The composable fetches each unknown appId exactly once (deduplicates
 * concurrent requests with an in-flight set) and exposes `iconFor(appId,
 * kindHint)` — a pure lookup that falls back to `defaultIconForKind`
 * when the template hasn't loaded yet or carries no iconKey.
 *
 * Design: aidocs/integrations/122 §6.2.
 */

import { ref, watch, type Ref } from "vue";
import { TemplatesApi, type ShepardTemplate } from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";
import { useTemplateIcon } from "~/composables/useTemplateIcon";

export function useTemplateIconByAppId(
  appIds: Ref<(string | null | undefined)[]>,
) {
  const cache = ref<Map<string, ShepardTemplate>>(new Map());
  const inFlight = new Set<string>();
  const templatesApi = useV2ShepardApi(TemplatesApi);

  watch(
    appIds,
    (ids) => {
      const unique = [...new Set(
        ids.filter((id): id is string => !!id),
      )];
      for (const id of unique) {
        if (cache.value.has(id) || inFlight.has(id)) continue;
        inFlight.add(id);
        templatesApi.value
          .getTemplate({ appId: id })
          .then((tpl) => {
            cache.value = new Map(cache.value).set(id, tpl);
          })
          .catch(() => { /* fail-soft — iconFor falls back to per-kind default */ })
          .finally(() => { inFlight.delete(id); });
      }
    },
    { immediate: true },
  );

  /**
   * Resolve the icon for an entity identified by its attached template appId.
   * Returns the template's iconKey when present; otherwise falls back to the
   * per-kind default for `kindHint`. Safe to call before the template has
   * loaded — the fallback icon is shown until the cache populates reactively.
   */
  function iconFor(
    appId: string | null | undefined,
    kindHint?: string | null,
  ): string {
    const tpl = appId ? cache.value.get(appId) : undefined;
    return useTemplateIcon(tpl, kindHint);
  }

  return { iconFor };
}
