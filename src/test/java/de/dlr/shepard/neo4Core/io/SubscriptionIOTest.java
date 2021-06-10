package de.dlr.shepard.neo4Core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Date;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.Subscription;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.util.RequestMethod;
import nl.jqno.equalsverifier.EqualsVerifier;

public class SubscriptionIOTest extends BaseTestCase {

	@Test
	public void equalsContract() {
		EqualsVerifier.simple().forClass(SubscriptionIO.class).verify();
	}

	@Test
	public void testConversion() {
		var user = new User("bob");
		var date = new Date();

		var sub = new Subscription(1L);
		sub.setCallbackURL("callbackUrl");
		sub.setCreatedAt(date);
		sub.setCreatedBy(user);
		sub.setName("MySub");
		sub.setRequestMethod(RequestMethod.PUT);
		sub.setSubscribedURL("subUrl");

		var converted = new SubscriptionIO(sub);
		assertEquals(sub.getId(), converted.getId());
		assertEquals(sub.getCallbackURL(), converted.getCallbackURL());
		assertEquals(sub.getCreatedAt(), converted.getCreatedAt());
		assertEquals("bob", converted.getCreatedBy());
		assertEquals(sub.getName(), converted.getName());
		assertEquals(sub.getRequestMethod(), converted.getRequestMethod());
		assertEquals(sub.getSubscribedURL(), converted.getSubscribedURL());
	}

	@Test
	public void testConversionNoUser() {
		var sub = new Subscription(1L);

		var converted = new SubscriptionIO(sub);
		assertNull(converted.getCreatedBy());
	}

}
