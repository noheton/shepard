package de.dlr.shepard.neo4Core.io;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import de.dlr.shepard.neo4Core.entities.Subscription;
import de.dlr.shepard.util.RequestMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(name = "Subscription")
public class SubscriptionIO {

	@Schema(accessMode = AccessMode.READ_ONLY)
	private Long id;

	private String name;

	private String callbackURL;

	private String subscribedURL;

	private RequestMethod requestMethod;

	@Schema(accessMode = AccessMode.READ_ONLY)
	private String createdBy;

	@JsonFormat(shape = JsonFormat.Shape.STRING)
	@Schema(accessMode = AccessMode.READ_ONLY)
	private Date createdAt;

	public SubscriptionIO(Subscription sub) {
		this.id = sub.getId();
		this.name = sub.getName();
		this.callbackURL = sub.getCallbackURL();
		this.subscribedURL = sub.getSubscribedURL();
		this.requestMethod = sub.getRequestMethod();
		this.createdAt = sub.getCreatedAt();
		this.createdBy = sub.getCreatedBy() != null ? sub.getCreatedBy().getUsername() : null;
	}

}
