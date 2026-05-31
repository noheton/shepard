<script setup lang="ts">
/**
 * TOOLS-NAV-01 — top-level Tools landing.
 *
 * Surfaces six research-tooling routes that were previously buried under
 * `/me#semantic` (avatar → /me → Semantic tile → page = 4 clicks). With
 * the new top-level Tools menu in HeaderBar.vue, these routes are 1 click
 * from any page.
 *
 * Closes the SCENEGRAPH-REST-1-UI top-nav-reachability finding from the
 * 2026-05-30 reconciler report (CLAUDE.md "Always: every shipped feature
 * is reachable from the top-nav before beta").
 *
 * SemanticPane.vue stays for one release cycle — removal of the now-
 * duplicate hub-tile pattern is tracked as a follow-up.
 */
import { TOOLS_TILES } from "~/utils/toolsLanding";

useHead({ title: "Tools | shepard" });
</script>

<template>
  <!-- LAYOUT-4K-CENTERED-EMPTY-001 / L2: widened from 1400px and made
       fluid so the tile grid fills the 4K canvas; cap at 2400px keeps
       row length readable. -->
  <v-container class="pa-6" fluid style="max-width: 2400px; margin: 0 auto">
    <h1 class="text-h4 mb-2">Tools</h1>
    <p class="text-body-1 text-medium-emphasis mb-6">
      Research-tooling surfaces — browse vocabularies, query the semantic
      graph, validate shapes, compare snapshots, inspect scene graphs, and
      preview shape renderers.
    </p>

    <v-row>
      <v-col
        v-for="tile in TOOLS_TILES"
        :key="tile.to"
        cols="12"
        sm="6"
        lg="4"
      >
        <v-card
          :to="tile.to"
          variant="outlined"
          hover
          class="h-100"
          :data-testid="`tools-tile-${tile.to.replace(/\//g, '-')}`"
        >
          <v-card-item>
            <template #prepend>
              <v-icon :icon="tile.icon" size="32" color="primary" />
            </template>
            <v-card-title class="text-h6">{{ tile.title }}</v-card-title>
          </v-card-item>
          <v-card-text>{{ tile.description }}</v-card-text>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>
