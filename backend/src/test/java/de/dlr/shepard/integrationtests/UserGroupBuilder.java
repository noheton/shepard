package de.dlr.shepard.integrationtests;

import de.dlr.shepard.auth.apikey.entities.ApiKey;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.entities.UserGroup;
import org.eclipse.microprofile.config.ConfigProvider;
import org.neo4j.ogm.config.Configuration;
import org.neo4j.ogm.session.Session;
import org.neo4j.ogm.session.SessionFactory;

public class UserGroupBuilder {

  private SessionFactory sessionFactory;

  private Session session;

  private UserGroup userGroup;

  String neo4jConnectionString;

  public UserGroupBuilder() {
    String username = ConfigProvider.getConfig().getValue("neo4j.username", String.class);
    String password = ConfigProvider.getConfig().getValue("neo4j.password", String.class);
    String host = ConfigProvider.getConfig().getValue("neo4j.host", String.class);
    neo4jConnectionString = "bolt://" + username + ":" + password + "@" + host;
    session = openSession(neo4jConnectionString);
  }

  public UserGroupBuilder withUserGroup(String name) {
    userGroup = generateUserGroup(name);
    return this;
  }

  public UserGroup build() {
    sessionFactory.close();
    return userGroup;
  }

  private Session openSession(String connectionString) {
    Configuration configuration = new Configuration.Builder().uri(connectionString).build();
    sessionFactory = new SessionFactory(configuration, User.class.getPackageName(), ApiKey.class.getPackageName());
    return sessionFactory.openSession();
  }

  private UserGroup generateUserGroup(String name) {
    userGroup = new UserGroup();
    userGroup.setName(name);
    session.save(userGroup);
    return userGroup;
  }
}
