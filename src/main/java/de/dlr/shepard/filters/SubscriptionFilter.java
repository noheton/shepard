package de.dlr.shepard.filters;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.dlr.shepard.neo4Core.entities.HasId;
import de.dlr.shepard.neo4Core.entities.Subscription;
import de.dlr.shepard.neo4Core.io.EventIO;
import de.dlr.shepard.neo4Core.io.SubscriptionIO;
import de.dlr.shepard.neo4Core.services.SubscriptionService;
import de.dlr.shepard.util.RequestMethod;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Subscribable
@Provider
@Log4j2
@NoArgsConstructor
public class SubscriptionFilter implements ContainerResponseFilter {

	private Executor executor = Executors.newCachedThreadPool();

	/**
	 * Constructor to inject your own executor service
	 *
	 * @param executor Your own Executor Service
	 */
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
		EventIO event = new EventIO(requestContext.getUriInfo().getAbsolutePath().toString(),
				RequestMethod.valueOf(requestContext.getMethod()));

		Object entity = responseContext.getEntity();
		if (entity instanceof HasId) {
			event.setSubscribedObject((HasId) entity);
		}

		List<Subscription> subs = subscriptionService.getMatchingSubscriptions(event.getRequestMethod());
		for (Subscription sub : subs) {
			// TODO: This could develop into a bottleneck
			Pattern pattern = Pattern.compile(sub.getSubscribedURL());
			if (pattern.matcher(event.getUrl()).matches()) {
				EventIO e = new EventIO(event);
				e.setSubscription(new SubscriptionIO(sub));
				log.debug("{} was triggered with {}", sub, e);
				executor.execute(() -> sendCallback(sub, e));
			}
		}
	}

	private void sendCallback(Subscription sub, EventIO event) {
		CloseableHttpClient httpclient = HttpClientBuilder.create().build();
		HttpPost request = new HttpPost(sub.getCallbackURL());

		try {
			StringEntity body = new StringEntity(new ObjectMapper().writeValueAsString(event));
			request.addHeader("content-type", "application/json");
			request.setEntity(body);
			HttpResponse response = httpclient.execute(request);
			log.info("Notification has been send to {} with response code: {}", request.getURI(),
					response.getStatusLine());
		} catch (UnsupportedEncodingException | JsonProcessingException e) {
			log.error("{}: Could not parse event to json: {}", e.getMessage(), event);
		} catch (IOException e) {
			log.error("{}: Could not execute notification request", e.getMessage());
		} finally {
			// eww :/
			try {
				httpclient.close();
			} catch (IOException e) {
				log.error("IOException while closing HTTPClient");
			}
		}
	}

	protected SubscriptionService getService() {
		return new SubscriptionService();
	}

}
