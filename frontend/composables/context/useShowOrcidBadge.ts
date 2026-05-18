import { MeApi } from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";
import { handleError } from "~/utils/errorBus";

/**
 * useShowOrcidBadge — preference toggle controlling whether the
 * ORCID badge overlay appears on the user's avatar.
 *
 * Default ON: an ORCID-equipped user shows the badge unless they
 * explicitly opt out. The badge is gamification + research-
 * provenance encouragement — visible by default so it does its
 * social-proof job, but the user can hide it from their own avatar
 * via the Display settings switch (privacy-respecting).
 *
 * Backed by the U1d preference key `ui.showOrcidBadge` ("true" /
 * "false"). Stored server-side via PATCH /v2/users/me/preferences
 * so the choice persists across browsers / sessions.
 */
const PREF_KEY = "ui.showOrcidBadge";

const showOrcidBadge = ref(true); // default ON
const isSaving = ref(false);
let loaded = false;

export function useShowOrcidBadge() {
  const api = useV2ShepardApi(MeApi);

  async function load() {
    if (loaded) return;
    loaded = true;
    try {
      const prefs = await api.value.getPreferences();
      // Default true unless the user has explicitly set "false".
      showOrcidBadge.value = prefs[PREF_KEY] !== "false";
    } catch {
      showOrcidBadge.value = true;
    }
  }

  async function setShowOrcidBadge(enabled: boolean) {
    isSaving.value = true;
    try {
      await api.value.patchPreferences({ [PREF_KEY]: enabled ? "true" : "false" });
      showOrcidBadge.value = enabled;
    } catch (error) {
      handleError(error, "saving ORCID badge preference");
    } finally {
      isSaving.value = false;
    }
  }

  load();
  return { showOrcidBadge, isSaving, setShowOrcidBadge };
}
