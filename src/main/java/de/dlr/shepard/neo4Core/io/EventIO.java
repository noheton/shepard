package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.util.RequestMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode
@Schema(name = "Event")
public class EventIO {

	private SubscriptionIO subscription;

	private HasIdIO subscribedObject;

	private String url;

	private RequestMethod requestMethod;

	public EventIO(String url, RequestMethod requestMethod) {
		this.url = url;
		this.requestMethod = requestMethod;
	}

	/**
	 * Copy constructor
	 *
	 * @param event The event to be copied
	 */
	public EventIO(EventIO event) {
		this.subscription = event.getSubscription();
		this.url = event.getUrl();
		this.requestMethod = event.getRequestMethod();
		this.subscribedObject = event.getSubscribedObject();
	}

	/**
	 * For testing purposes only
	 *
	 * @param url the url that triggered the event
	 */
	public EventIO(String url) {
		this.url = url;
	}

}
