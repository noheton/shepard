import {
  CreateSubscriptionRequest,
  DeleteSubscriptionRequest,
  GetAllSubscriptionsRequest,
  GetSubscriptionRequest,
  SubscriptionApi,
} from "@dlr-shepard/shepard-client";
import { getConfiguration } from "./serviceHelper";

export default class SubscriptionService {
  static getSubscription(params: GetSubscriptionRequest) {
    const api = new SubscriptionApi(getConfiguration());
    return api.getSubscription(params);
  }

  static getAllSubscriptions(params: GetAllSubscriptionsRequest) {
    const api = new SubscriptionApi(getConfiguration());
    return api.getAllSubscriptions(params);
  }

  static createSubscription(params: CreateSubscriptionRequest) {
    const api = new SubscriptionApi(getConfiguration());
    return api.createSubscription(params);
  }

  static deleteSubscription(params: DeleteSubscriptionRequest) {
    const api = new SubscriptionApi(getConfiguration());
    return api.deleteSubscription(params);
  }
}
