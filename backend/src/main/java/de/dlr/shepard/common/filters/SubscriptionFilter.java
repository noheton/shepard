package de.dlr.shepard.common.filters;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.neo4j.io.HasIdIO;
import de.dlr.shepard.common.subscription.entities.Subscription;
import de.dlr.shepard.common.subscription.io.EventIO;
import de.dlr.shepard.common.subscription.io.SubscriptionIO;
import de.dlr.shepard.common.subscription.services.SubscriptionService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.common.util.RequestMethod;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.PathSegment;
import jakarta.ws.rs.ext.Provider;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

@Subscribable
@Provider
@RequestScoped
public class SubscriptionFilter implements ContainerResponseFilter {

  @Inject
  ExecutorFactory executorFactory;

  @Inject
  PermissionsService permissionsService;

  @Inject
  SubscriptionService subscriptionService;

  @Override
  public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
    // request not successful
    var status = responseContext.getStatus();
    if (!(status >= 200 && status < 300)) {
      Log.debug("Skip subscriptions since the http status code is not between 200 and 299");
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

    List<Subscription> subs = subscriptionService.getMatchingSubscriptions(event.getRequestMethod());
    if (subs.isEmpty()) return;

    List<Subscription> urlMatched = new ArrayList<>(subs.size());
    for (Subscription sub : subs) {
      Pattern pattern = Pattern.compile(sub.getSubscribedURL());
      if (pattern.matcher(event.getUrl()).matches()) {
        urlMatched.add(sub);
      }
    }
    if (urlMatched.isEmpty()) return;

    Set<String> allowedUsernames = resolveAllowedUsernames(requestContext, urlMatched);
    for (Subscription sub : urlMatched) {
      String username = sub.getCreatedBy() == null ? null : sub.getCreatedBy().getUsername();
      if (username == null || !allowedUsernames.contains(username)) continue;
      EventIO e = new EventIO(event);
      e.setSubscription(new SubscriptionIO(sub));
      Log.debugf("%s was triggered with %s", sub, e);
      executorFactory.getInstance().execute(() -> sendCallback(sub, e));
    }
  }

  /**
   * One batched permission check for all url-matched subscriptions. Falls back to a single
   * {@link PermissionsService#isAllowed} call when the request path is not a numeric entity path
   * (the only branch where the permission verdict varies by username).
   */
  private Set<String> resolveAllowedUsernames(ContainerRequestContext requestContext, List<Subscription> urlMatched) {
    LinkedHashSet<String> usernames = new LinkedHashSet<>();
    for (Subscription sub : urlMatched) {
      if (sub.getCreatedBy() != null && sub.getCreatedBy().getUsername() != null) {
        usernames.add(sub.getCreatedBy().getUsername());
      }
    }
    if (usernames.isEmpty()) return Set.of();

    Long entityId = extractEntityId(requestContext);
    if (entityId != null) {
      return permissionsService.filterAllowedUsers(entityId, AccessType.Read, usernames);
    }
    String probe = usernames.iterator().next();
    return permissionsService.isAllowed(requestContext, AccessType.Read, probe) ? usernames : Set.of();
  }

  /**
   * Mirrors the numeric-entity branch of {@link PermissionsService#isAllowed}: returns the entity
   * id only when the path actually routes to the per-user permission check, and {@code null}
   * otherwise (the result is then username-independent).
   */
  private static Long extractEntityId(ContainerRequestContext requestContext) {
    List<PathSegment> segments = requestContext.getUriInfo().getPathSegments();
    if (segments.size() < 2) return null;
    String first = segments.getFirst().getPath();
    String idSegment = segments.get(1).getPath();
    if (idSegment == null || idSegment.isBlank()) return null;
    if (first.equals("temp") && idSegment.equals("migrations")) return null;
    if (first.equals(Constants.LAB_JOURNAL_ENTRIES)) return null;
    if (first.equals(Constants.USERS)) return null;
    if (!StringUtils.isNumeric(idSegment)) return null;
    return Long.parseLong(idSegment);
  }

  private void sendCallback(Subscription sub, EventIO event) {
    var client = ClientBuilder.newClient();
    var webTarget = client.target(sub.getCallbackURL());

    try {
      var entity = Entity.entity(event, MediaType.APPLICATION_JSON);
      var response = webTarget.request().buildPost(entity).invoke();
      Log.infof("Notification has been send to %s with response code: %s", sub.getCallbackURL(), response.getStatus());
    } catch (ProcessingException e) {
      Log.error("Could not execute notification request");
    } finally {
      client.close();
    }
  }
}
