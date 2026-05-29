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
 * apply until the user re-authenticates. The proper structural fix lives in
 * ROLE-GRANT-STALE-SESSION-02 (server-side role-change timestamp gate); this
 * is the cheap operator-visible workaround until that ships.
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
  }>(),
  {
    title: "You don't have access to this section",
    message: "Your account doesn't have the role required to view this page. If you think this is a mistake, ask an instance admin to grant you the required role.",
    requiredRole: undefined,
    showStaleSessionHint: undefined,
  },
);

const { signOut } = useAuth();

// Hint defaults to ON whenever a specific role is required (admin contexts).
// Generic UnauthorizedView use without a `required-role` (e.g. visibility-
// scoped page) does not surface the hint by default.
const hintEnabled = computed(() =>
  props.showStaleSessionHint ?? Boolean(props.requiredRole),
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
  <div class="unauthorized-view pa-6">
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

      <!-- ROLE-GRANT-STALE-SESSION-03: hint for a freshly granted role -->
      <v-card-text
        v-if="hintEnabled"
        class="pt-0"
        data-testid="stale-session-hint"
      >
        <v-alert
          type="info"
          variant="tonal"
          density="compact"
          icon="mdi-key-change"
        >
          <strong>Did you just get the grant?</strong>
          Your active session caches the role set from your last sign-in.
          Sign out and back in to refresh.
        </v-alert>
      </v-card-text>

      <v-card-actions>
        <v-btn
          color="primary"
          variant="flat"
          to="/"
          prepend-icon="mdi-home-outline"
        >
          Go home
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
