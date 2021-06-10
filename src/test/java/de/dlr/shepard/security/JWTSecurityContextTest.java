package de.dlr.shepard.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.Principal;

import javax.ws.rs.core.SecurityContext;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;

public class JWTSecurityContextTest extends BaseTestCase {

	private String[] roles = { "role1", "role2" };
	private JWTPrincipal principal = new JWTPrincipal("MyAudience", "MyIssuedFor", "MyUsername", "MyFirstName",
			"MyLastName", "MyEMail", "MyKeyId", roles);

	private SecurityContext sc = new SecurityContext() {

		@Override
		public boolean isUserInRole(String role) {
			return false;
		}

		@Override
		public boolean isSecure() {
			return false;
		}

		@Override
		public Principal getUserPrincipal() {
			return null;
		}

		@Override
		public String getAuthenticationScheme() {
			return null;
		}
	};

	@Test
	public void testGetUserPrincipal() {
		var context = new JWTSecurityContext(sc, principal);
		assertEquals(principal, context.getUserPrincipal());
	}

	@Test
	public void testUserInRole() {
		var context = new JWTSecurityContext(sc, principal);
		assertTrue(context.isUserInRole("role1"));
		assertFalse(context.isUserInRole("role5"));
	}

	@Test
	public void testIsSecure() {
		var context = new JWTSecurityContext(sc, principal);
		assertEquals(sc.isSecure(), context.isSecure());
	}

	@Test
	public void testAuthenticationScheme() {
		var context = new JWTSecurityContext(sc, principal);
		assertEquals("Token-Based-Auth-Scheme", context.getAuthenticationScheme());
	}

}
