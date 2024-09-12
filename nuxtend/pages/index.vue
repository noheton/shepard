<script setup lang="ts"></script>

<template>
  <!-- Main Content -->
  <v-container>
    <v-row>
      <v-col cols="12" md="6">
        <v-card class="mx-auto my-5" elevation="4">
          <v-img
            src="https://picsum.photos/500/300"
            height="200px"
            class="white--text"
            gradient="to bottom, rgba(0,0,0,.1), rgba(0,0,0,.5)"
          ></v-img>
          <v-card-title>Beautiful Scenery</v-card-title>
          <v-card-subtitle class="pb-0">Nature & Serenity</v-card-subtitle>

          <v-card-text>
            Explore some of the most beautiful places in the world. Sit back,
            relax, and take in the beauty of nature.
          </v-card-text>

          <v-card-actions>
            <v-btn color="primary">Learn More</v-btn>
            <v-btn color="secondary">Book Now</v-btn>
          </v-card-actions>
        </v-card>
      </v-col>

      <v-col cols="12" md="6">
        <v-card class="mx-auto my-5" elevation="4">
          <v-card-title>Contact Us</v-card-title>
          <v-card-text>
            <v-form>
              <v-text-field label="Full Name" outlined></v-text-field>
              <v-text-field label="Email" outlined></v-text-field>
              <v-textarea label="Message" rows="4" outlined />
            </v-form>
          </v-card-text>

          <v-card-actions>
            <v-btn color="primary">Send</v-btn>
          </v-card-actions>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<style scoped>
.v-card-title {
  font-weight: bold;
}
</style>
