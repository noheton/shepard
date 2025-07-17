import {
  type ResponseError,
  type Subscription,
  SubscriptionApi,
  type User,
  UserApi,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";

export function useFetchSubscriptions() {
  const subscriptionsApi = useShepardApi(SubscriptionApi);
  const userApi = useShepardApi(UserApi);

  const user = ref<User>();
  const subscriptions = ref<Subscription[]>();
  const isLoading = ref<boolean>(true);

  async function fetchSubscriptions() {
    try {
      if (!user.value) {
        user.value = await userApi.value.getCurrentUser();
      }
      subscriptions.value = await subscriptionsApi.value.getAllSubscriptions({
        username: user.value.username,
      });
      subscriptions.value.sort(
        (a, b) => b.createdAt.getTime() - a.createdAt.getTime(),
      );
      isLoading.value = false;
    } catch (e) {
      handleError(e as ResponseError, "fetching subscriptions");
    }
  }

  fetchSubscriptions();

  return { subscriptions, user, fetchSubscriptions, isLoading };
}
