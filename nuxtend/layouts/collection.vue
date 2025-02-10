<template>
  <DefaultLayout>
    <v-container fluid class="pa-0 fill-height align-start overflow-x-auto">
      <v-row no-gutters class="fill-height">
        <v-col cols="3">
          <CollectionSidebar
            style="position: fixed; width: 100%; max-width: inherit"
          />
        </v-col>
        <v-col cols="9" class="pa-8"><slot /></v-col>
      </v-row>
    </v-container>
  </DefaultLayout>
</template>
