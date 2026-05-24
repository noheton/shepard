<script setup lang="ts">
/**
 * UnauthorizedView — shown in place of a section's content when the current
 * user lacks the role required to view it. Keeps the URL stable (shareable
 * link still resolves; the visiting user just sees a polite explanation).
 *
 * Closes UI-2026-05-24-004 (silent /admin → /me bounce for non-admins).
 */
withDefaults(
  defineProps<{
    title?: string;
    message?: string;
    requiredRole?: string;
  }>(),
  {
    title: "You don't have access to this section",
    message: "Your account doesn't have the role required to view this page. If you think this is a mistake, ask an instance admin to grant you the required role.",
    requiredRole: undefined,
  },
);

const { signOut } = useAuth();

async function signOutAndRetry() {
  // Send the user through the OIDC flow again — useful when they're logged
  // in as the wrong account.
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
