<script setup lang="ts">
/**
 * SectionIndexLanding — friendly landing for a section index page.
 *
 * The four section index pages (/me, /admin, /about, /configuration) all use
 * fragment-based sub-navigation (URL hash). When no fragment is set, the
 * content panel was previously blank — fixed here by rendering a card grid of
 * the section's sub-areas. Clicking a card sets the matching hash.
 *
 * Closes UI-2026-05-24-003.
 */
import { buildSectionLandingCards, type SectionLandingCard } from "./sectionLanding";

defineProps<{
  title: string;
  subtitle?: string;
  sections: SectionLandingCard[];
  emptyMessage?: string;
}>();
</script>

<template>
  <div class="section-index-landing pa-6">
    <div class="mb-6">
      <h1 class="text-h4 mb-2">{{ title }}</h1>
      <p v-if="subtitle" class="text-body-1 text-medium-emphasis">
        {{ subtitle }}
      </p>
    </div>

    <v-row v-if="sections.length > 0" dense>
      <v-col
        v-for="card in buildSectionLandingCards(sections)"
        :key="card.fragment"
        cols="12"
        sm="6"
        lg="4"
      >
        <v-card
          :to="card.to"
          variant="outlined"
          hover
          class="section-landing-card h-100"
          :data-fragment="card.fragment"
        >
          <v-card-item>
            <template #prepend>
              <v-icon :icon="card.icon" size="32" color="primary" />
            </template>
            <v-card-title class="text-h6">
              {{ card.title }}
              <v-chip
                v-if="card.badge"
                size="x-small"
                color="primary"
                class="ml-2"
                variant="tonal"
              >
                {{ card.badge }}
              </v-chip>
            </v-card-title>
          </v-card-item>
          <v-card-text>
            {{ card.description }}
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <v-alert v-else type="info" variant="tonal" class="mt-4">
      {{ emptyMessage || "No sub-sections yet — more content coming soon." }}
    </v-alert>

    <div class="mt-6">
      <slot />
    </div>
  </div>
</template>

<style lang="scss" scoped>
.section-index-landing {
  max-width: 1400px;
}

.section-landing-card {
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.section-landing-card:hover {
  border-color: rgb(var(--v-theme-primary));
}
</style>
