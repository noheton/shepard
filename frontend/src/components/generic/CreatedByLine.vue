<script setup lang="ts">
import type { User } from "@/generated/openapi";
import { onMounted, onUpdated, ref } from "vue";
import { createVuexHelpers } from "vue2-helpers";

const props = defineProps({
  createdBy: {
    type: String,
    default: undefined,
  },
  createdAt: {
    type: Date,
    default: undefined,
  },
  updated: {
    type: Boolean,
    default: false,
  },
  tooltip: {
    type: Boolean,
    default: false,
  },
});

const id = ref<number>(Math.random());

const { useGetters, useActions } = createVuexHelpers();
const userCacheGetters = useGetters("userCache", [
  "isUserInCache",
  "getUserFromCache",
]);
const userCacheActions = useActions("userCache", ["fetchUser"]);
const isUserInCache: (username: string) => boolean =
  userCacheGetters.isUserInCache.value;
const getUserFromCache: (username: string) => User =
  userCacheGetters.getUserFromCache.value;
const fetchUser: (username: string) => void = userCacheActions.fetchUser;

function retrieveUser() {
  if (!isUserInCache(props.createdBy)) {
    fetchUser(props.createdBy);
  }
}

onMounted(() => {
  retrieveUser();
});

onUpdated(() => {
  retrieveUser();
});
</script>

<template>
  <div>
    <small v-if="updated">updated</small>
    <small v-else>created</small>
    <small v-if="createdAt" :id="'at' + id">
      at {{ createdAt.toDateString() }}
    </small>
    <b-tooltip
      v-if="tooltip && createdAt"
      :target="'at' + id"
      :delay="{ show: 500, hide: 100 }"
    >
      {{ createdAt.toString() }}
    </b-tooltip>
    <small v-if="getUserFromCache(createdBy)" :id="'by' + id">
      by {{ getUserFromCache(createdBy).lastName }},
      {{ getUserFromCache(createdBy).firstName }}
    </small>
    <b-tooltip
      v-if="tooltip && getUserFromCache(createdBy)"
      :target="'by' + id"
      :delay="{ show: 500, hide: 100 }"
    >
      {{ createdBy }}
    </b-tooltip>
  </div>
</template>
