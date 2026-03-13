package de.dlr.shepard.integrationtests;

import de.dlr.shepard.auth.apikey.entities.ApiKey;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.util.PKIHelper;
import io.jsonwebtoken.Jwts;
import java.security.PrivateKey;
import java.util.Date;
import org.eclipse.microprofile.config.ConfigProvider;
import org.neo4j.ogm.config.Configuration;
import org.neo4j.ogm.session.Session;
import org.neo4j.ogm.session.SessionFactory;

public class UserWithApiKeyBuilder {

  private SessionFactory sessionFactory;

  private Session session;

  private User user;
  private ApiKey apiKey;

  String neo4jConnectionString;

  public UserWithApiKeyBuilder() {
    String username = ConfigProvider.getConfig().getValue("neo4j.username", String.class);
    String password = ConfigProvider.getConfig().getValue("neo4j.password", String.class);
    String host = ConfigProvider.getConfig().getValue("neo4j.host", String.class);
    neo4jConnectionString = "bolt://" + username + ":" + password + "@" + host;
    session = openSession(neo4jConnectionString);
  }

  public UserWithApiKeyBuilder withUser() {
    user = generateUser("test_it");
    return this;
  }

  public UserWithApiKeyBuilder withUser(String username) {
    user = generateUser(username);
    return this;
  }

  public UserWithApiKeyBuilder withApiKey() {
    if (user == null) user = generateUser("test_it");
    apiKey = generateApiKey(user);
    return this;
  }

  public UserWithApiKey build() {
    sessionFactory.close();
    return new UserWithApiKey(user, apiKey);
  }

  private Session openSession(String connectionString) {
    Configuration configuration = new Configuration.Builder().uri(connectionString).build();
    sessionFactory = new SessionFactory(configuration, User.class.getPackageName(), ApiKey.class.getPackageName());
    return sessionFactory.openSession();
  }

  private static String generateJws(ApiKey apiKey, String issuer) {
    PKIHelper pkiHelper = new PKIHelper();
    pkiHelper.init();
    PrivateKey key = pkiHelper.getPrivateKey();

    Date currentDate = new Date();
    String jws = Jwts.builder()
      .setSubject(apiKey.getBelongsTo().getUsername())
      .setIssuer(issuer)
      .setNotBefore(currentDate)
      .setIssuedAt(currentDate)
      .setId(apiKey.getUid().toString())
      .signWith(key)
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
      apiKey = user.getApiKeys().getFirst();
    }

    // Update Api Key
    apiKey.setJws(generateJws(apiKey, "integraton tests"));
    session.save(apiKey);
    this.user = apiKey.getBelongsTo();

    return apiKey;
  }
}
