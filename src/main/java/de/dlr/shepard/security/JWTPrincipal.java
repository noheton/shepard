package de.dlr.shepard.security;

import java.security.Principal;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
public class JWTPrincipal implements Principal {

	@Getter
	private String audience;
	@Getter
	private String issuedFor;
	@Getter
	private String username;
	@Getter
	private String firstName;
	@Getter
	private String lastName;
	@Getter
	private String email;
	@Getter
	private String keyId;
	@Setter
	@Getter
	private String[] roles;

	public JWTPrincipal(String username, String keyId) {
		super();
		this.username = username;
		this.keyId = keyId;
		this.roles = new String[0];
	}

	@Override
	public String getName() {
		return username;
	}

}
