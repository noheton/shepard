package de.dlr.shepard.integrationtests;

import java.security.PrivateKey;
import java.util.Date;

import org.neo4j.ogm.config.Configuration;
import org.neo4j.ogm.session.Session;
import org.neo4j.ogm.session.SessionFactory;

import de.dlr.shepard.neo4Core.entities.ApiKey;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.util.PKIHelper;
import de.dlr.shepard.util.PropertiesHelper;
import io.jsonwebtoken.Jwts;

public class PrepareDatabase {

	private SessionFactory sessionFactory;

	private Session session;

	private User user;
	private ApiKey apiKey;

	public PrepareDatabase() {
		session = openSession();
	}

	public PrepareDatabase withUser() {
		user = generateUser("test_it");
		return this;
	}

	public PrepareDatabase withUser(String username) {
		user = generateUser(username);
		return this;
	}

	public PrepareDatabase withApiKey() {
		if (user == null)
			user = generateUser("test_it");
		apiKey = generateApiKey(user);
		return this;
	}

	public UserWithApiKey build() {
		sessionFactory.close();
		return new UserWithApiKey(user, apiKey);
	}

	private Session openSession() {
		String username = "", password = "", host = "";
		PropertiesHelper helper = new PropertiesHelper();
		username = helper.getProperty("neo4j.username");
		password = helper.getProperty("neo4j.password");
		host = helper.getProperty("neo4j.host");
		String connectionString = String.format("bolt://%s:%s@%s", username, password, host);
		Configuration configuration = new Configuration.Builder().uri(connectionString).build();
		sessionFactory = new SessionFactory(configuration, "de.dlr.shepard.neo4Core.entities");
		return sessionFactory.openSession();
	}

	private static String generateJws(ApiKey apiKey, String issuer) {
		PKIHelper pkiHelper = new PKIHelper();
		pkiHelper.init();
		PrivateKey key = pkiHelper.getPrivateKey();

		Date currentDate = new Date();
		String jws = Jwts.builder().setSubject(apiKey.getBelongsTo().getUsername()).setIssuer(issuer)
				.setNotBefore(currentDate).setIssuedAt(currentDate).setId(apiKey.getUid().toString()).signWith(key)
				.compact();
		return jws;
	}

	private User generateUser(String userName) {
		User user = session.load(User.class, userName, 2);

		if (user == null) {
			user = new User(userName);
		}
		user.setFirstName("Integration");
		user.setLastName("Test");
		user.setEmail("integration@test.org");
		session.save(user);
		return user;
	}

	private ApiKey generateApiKey(User user) {
		ApiKey apiKey;

		if (user.getApiKeys().isEmpty()) {
			// ApiKey does not exist yet
			apiKey = new ApiKey("IntegrationTestApiKey", new Date(), user);
			session.save(apiKey);
		} else {
			apiKey = user.getApiKeys().get(0);
		}

		// Update Api Key
		apiKey.setJws(generateJws(apiKey, "integraton tests"));
		session.save(apiKey);
		this.user = apiKey.getBelongsTo();

		return apiKey;
	}

}
