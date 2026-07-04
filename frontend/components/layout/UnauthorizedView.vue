<script setup lang="ts">
/**
 * UnauthorizedView — shown in place of a section's content when the current
 * user lacks the role required to view it. Keeps the URL stable (shareable
 * link still resolves; the visiting user just sees a polite explanation).
 *
 * Closes UI-2026-05-24-004 (silent /admin → /me bounce for non-admins).
 *
 * ROLE-GRANT-STALE-SESSION-03 (2026-05-29): when `requiredRole` is set the
 * view surfaces a "Did you just get the grant? Sign out + back in to refresh"
 * hint. The backend caches role claims in the JWT at parse time
 * (`JwtTokenAuthService.resolveDualSourceRoles`); a freshly granted role won't
 * apply until the user re-authenticates.
 *
 * ROLE-GRANT-STALE-SESSION-02 (2026-05-31): the backend now actively rejects
 * stale-role tokens with HTTP 401 + body `{"error":"role_changed", ...}`.
 * `useStaleRoleSession()` flips a global flag on that response shape; pages
 * that already render this view (admin landing, instance-registry, provenance)
 * read the flag and pass `stale-session-reason="role-changed"` so the hint
 * upgrades from "did you just get the grant?" (speculative) to "your role
 * just changed — sign out + back in" (definitive).
 */
const props = withDefaults(
  defineProps<{
    title?: string;
    message?: string;
    requiredRole?: string;
    /**
     * When true (default whenever a `requiredRole` is set), surfaces the
     * stale-session hint + dedicated "Sign out + back in" button. Set to
     * `false` to suppress (e.g. for generic 401 contexts unrelated to a
     * fresh role grant).
     */
    showStaleSessionHint?: boolean;
    /**
     * ROLE-GRANT-STALE-SESSION-02 — when set to "role-changed" the hint
     * copy upgrades from the speculative "Did you just get the grant?"
     * default to a definitive "Your role just changed". Set by callers
     * that have observed a backend `error: "role_changed"` 401 (via the
     * `useStaleRoleSession()` shared flag). Undefined = the speculative
     * default from -03.
     */
    staleSessionReason?: "role-changed";
    /**
     * Optional list of section-feature labels (no links) shown so the user
     * understands what lives behind the gate. Used by `/admin` for task #242:
     * a non-admin researcher should see the admin tile catalogue rather than
     * a flat 403. The caller passes labels only — clicks intentionally do
     * nothing because the user can't actually reach the feature.
     */
    featureLabels?: readonly string[];
  }>(),
  {
    title: "You don't have access to this section",
    message: "Your account doesn't have the role required to view this page. If you think this is a mistake, ask an instance admin to grant you the required role.",
    requiredRole: undefined,
    showStaleSessionHint: undefined,
    staleSessionReason: undefined,
    featureLabels: () => [],
  },
);

const { signOut } = useAuth();

// Hint defaults to ON whenever a specific role is required (admin contexts)
// OR when the caller has observed a definitive `role_changed` 401 (per
// ROLE-GRANT-STALE-SESSION-02). Generic UnauthorizedView use without
// either signal does not surface the hint by default. An explicit
// `showStaleSessionHint` boolean overrides both signals.
const hintEnabled = computed(() =>
  props.showStaleSessionHint
    ?? (Boolean(props.requiredRole) || Boolean(props.staleSessionReason)),
);

// Hint copy is upgraded when the backend has definitively told us the role
// set changed (`error: "role_changed"`). Pre-02 default ("did you just get
// the grant?") stays as the fallback for the speculative case (a 403 fires
// on an admin route but no specific signal arrived).
const hintTitle = computed(() =>
  props.staleSessionReason === "role-changed"
    ? "Your role just changed"
    : "Did you just get the grant?",
);
const hintBody = computed(() =>
  props.staleSessionReason === "role-changed"
    ? "An admin updated your roles. Your active session was issued before that change. Sign out and back in to continue."
    : "Your active session caches the role set from your last sign-in. Sign out and back in to refresh.",
);

async function signOutAndRetry() {
  // Send the user through the OIDC flow again — useful when they're logged
  // in as the wrong account.
  await signOut({ callbackUrl: "/" });
}

async function signOutToRefreshRoles() {
  // ROLE-GRANT-STALE-SESSION-03: re-auth so a freshly granted role lands in
  // the JWT. Same call as signOutAndRetry; separate handler keeps the intent
  // legible (button label + Activity-log attribution differ).
  await signOut({ callbackUrl: "/" });
}
</script>

<template>
  <!-- LAYOUT-4K-GATE-EMPTY-001 / L7: at 4K the previous tight pa-6
       wrapper rendered the (intentionally narrow) gate card as a tiny
       dot in a vast grey canvas. Wrap in a full-bleed soft-background
       container with min-height so the gutter feels deliberate; card
       width stays at 640px for readable line-length. -->
  <div class="unauthorized-view pa-6 d-flex align-center justify-center">
    <v-card variant="outlined" class="mx-auto" max-width="640">
      <v-card-item>
        <template #prepend>
          <v-icon icon="mdi-lock-outline" size="40" color="warning" />
        </template>
        <v-card-title class="text-h5">{{ title }}</v-card-title>
        <v-card-subtitle v-if="requiredRole" class="mt-2">
          Required role: <code>{{ requiredRole }}</code>
        </v-card-subtitle>
      </v-card-item>

      <v-card-text class="text-body-1">
        {{ message }}
      </v-card-text>

      <!-- task #242: show feature catalogue behind the gate (admin tiles)
           so the user knows what they're missing, not just that they're
           blocked. Labels only — no links. -->
      <v-card-text
        v-if="featureLabels.length > 0"
        class="pt-0"
        data-testid="feature-labels-list"
      >
        <div class="text-subtitle-2 mb-2">What's behind this gate</div>
        <v-chip-group column class="feature-labels-chips">
          <v-chip
            v-for="label in featureLabels"
            :key="label"
            size="small"
            variant="tonal"
            :ripple="false"
          >
            {{ label }}
          </v-chip>
        </v-chip-group>
      </v-card-text>

      <!-- ROLE-GRANT-STALE-SESSION-03: hint for a freshly granted role.
           ROLE-GRANT-STALE-SESSION-02: copy is upgraded to a definitive
           "your role just changed" when a `role_changed` 401 was observed
           on this session (caller passes `stale-session-reason`). -->
      <v-card-text
        v-if="hintEnabled"
        class="pt-0"
        data-testid="stale-session-hint"
        :data-stale-session-reason="staleSessionReason ?? 'speculative'"
      >
        <v-alert
          type="info"
          variant="tonal"
          density="compact"
          icon="mdi-key-change"
        >
          <strong>{{ hintTitle }}</strong>
          {{ hintBody }}
        </v-alert>
      </v-card-text>

      <v-card-actions>
        <v-btn
          color="primary"
          variant="flat"
          to="/"
          prepend-icon="mdi-home-outline"
        >
          Back to home
        </v-btn>
        <v-btn
          v-if="hintEnabled"
          color="info"
          variant="tonal"
          prepend-icon="mdi-logout-variant"
          data-testid="sign-out-refresh-roles"
          @click="signOutToRefreshRoles"
        >
          Sign out + back in
        </v-btn>
        <v-btn
          variant="outlined"
          prepend-icon="mdi-account-switch-outline"
          @click="signOutAndRetry"
        >
          Sign in as another user
        </v-btn>
      </v-card-actions>
    </v-card>
  </div>
</template>

<style lang="scss" scoped>
/* LAYOUT-4K-GATE-EMPTY-001 — full-bleed wrapper so the gate doesn't
   render as a tiny card in a vast grey canvas at 4K. Soft tonal
   background + min-height fills the viewport deliberately while the
   inner card stays at 640px for readable line-length. */
.unauthorized-view {
  min-height: calc(100vh - 64px); /* viewport minus header bar */
  background:
    radial-gradient(
      circle at 50% 35%,
      rgba(var(--v-theme-primary), 0.04) 0%,
      transparent 60%
    );
}
</style>
