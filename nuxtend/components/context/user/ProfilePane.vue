<script setup lang="ts">
import { useFetchUserProfile } from "~/composables/context/useFetchUserProfile";

const { user, isLoading } = useFetchUserProfile();
</script>

<template>
  <div class="d-flex flex-column ga-4">
    <h4 class="text-h4">Profile</h4>

    <centered-loading-spinner v-if="isLoading" />
    <v-table v-if="user && !isLoading" class="table">
      <tbody>
        <tr>
          <th>Username</th>
          <td>{{ user.username }}</td>
        </tr>
        <tr>
          <th>First Name</th>
          <td>{{ user.firstName }}</td>
        </tr>
        <tr>
          <th>Last Name</th>
          <td>{{ user.lastName }}</td>
        </tr>
        <tr>
          <th>E-Mail</th>
          <td>{{ user.email }}</td>
        </tr>
      </tbody>
    </v-table>
  </div>
</template>

<style scoped lang="scss"></style>
