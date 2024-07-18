// TODO: Activate filter again
/*
package de.dlr.shepard.filters;

import de.dlr.shepard.neo4Core.entities.Subscription;
import de.dlr.shepard.neo4Core.io.EventIO;
import de.dlr.shepard.neo4Core.io.HasIdIO;
import de.dlr.shepard.neo4Core.io.SubscriptionIO;
import de.dlr.shepard.neo4Core.services.SubscriptionService;
import de.dlr.shepard.security.PermissionsUtil;
import de.dlr.shepard.util.AccessType;
import de.dlr.shepard.util.HasId;
import de.dlr.shepard.util.RequestMethod;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Subscribable
@Provider
@Slf4j
public class SubscriptionFilter implements ContainerResponseFilter {

  private Executor executor;

  */
/**
 * Default constructor
 *//*

  public SubscriptionFilter() {
    this.executor = Executors.newCachedThreadPool();
  }

  */
/**
 * Constructor to inject your own executor service
 *
 * @param executor Your own Executor Service
 *//*

  public SubscriptionFilter(Executor executor) {
    this.executor = executor;
  }

  @Override
  public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
    SubscriptionService subscriptionService = getService();

    // request not successful
    var status = responseContext.getStatus();
    if (!(status >= 200 && status < 300)) {
      log.debug("Skip subscriptions since the http statuscode is not between 200 and 299");
      return;
    }

    // request successful
    EventIO event = new EventIO(
      requestContext.getUriInfo().getAbsolutePath().toString(),
      RequestMethod.valueOf(requestContext.getMethod())
    );

    Object entity = responseContext.getEntity();
    if (entity instanceof HasId hasId) {
      event.setSubscribedObject(new HasIdIO(hasId));
    }

    var permissionsUtil = getPermissionsUtil();
    List<Subscription> subs = subscriptionService.getMatchingSubscriptions(event.getRequestMethod());
    for (Subscription sub : subs) {
      // TODO: This could develop into a bottleneck
      Pattern pattern = Pattern.compile(sub.getSubscribedURL());
      if (
        pattern.matcher(event.getUrl()).matches() &&
        permissionsUtil.isAllowed(
          requestContext.getUriInfo().getPathSegments(),
          AccessType.Read,
          sub.getCreatedBy().getUsername()
        )
      ) {
        EventIO e = new EventIO(event);
        e.setSubscription(new SubscriptionIO(sub));
        log.debug("{} was triggered with {}", sub, e);
        executor.execute(() -> sendCallback(sub, e));
      }
    }
  }

  private void sendCallback(Subscription sub, EventIO event) {
    var client = ClientBuilder.newClient();
    var webTarget = client.target(sub.getCallbackURL());

    try {
      var entity = Entity.entity(event, MediaType.APPLICATION_JSON);
      var response = webTarget.request().buildPost(entity).invoke();
      log.info("Notification has been send to {} with response code: {}", sub.getCallbackURL(), response.getStatus());
    } catch (ProcessingException e) {
      log.error("Could not execute notification request");
    } finally {
      client.close();
    }
  }

  protected SubscriptionService getService() {
    return new SubscriptionService();
  }

  protected PermissionsUtil getPermissionsUtil() {
    return new PermissionsUtil();
  }
}
*/
