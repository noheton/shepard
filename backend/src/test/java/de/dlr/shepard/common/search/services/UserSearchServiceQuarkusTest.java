package de.dlr.shepard.common.search.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.search.io.UserSearchBody;
import de.dlr.shepard.common.search.io.UserSearchParams;
import de.dlr.shepard.common.search.io.UserSearchResult;
import de.dlr.shepard.integrationtests.WireMockResource;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@WithTestResource(WireMockResource.class)
public class UserSearchServiceQuarkusTest {

  @Inject
  UserService userService;

  @Inject
  UserSearchService userSearchService;

  User user1;
  User user2;

  @BeforeEach
  public void setUp() {
    if (user1 == null) {
      user1 = new User("user1" + System.currentTimeMillis());
      user1 = userService.createOrUpdateUser(user1);
    }
    if (user2 == null) {
      user2 = new User("user2" + System.currentTimeMillis());
      user2 = userService.createOrUpdateUser(user2);
    }
  }

  @Test
  @Transactional
  public void findUser1ByUsernameEquality() {
    String JSONquery = "{\"property\": \"username\", \"value\": \"" + user1.getUsername() + "\", \"operator\": \"eq\"}";
    UserSearchParams params = new UserSearchParams(JSONquery);
    UserSearchBody searchBody = new UserSearchBody(params);
    UserSearchResult found = userSearchService.search(searchBody);
    assertEquals(1, found.getResults().length);
    assertEquals(user1.getUsername(), found.getResults()[0].getUsername());
  }

  @Test
  @Transactional
  public void findUser1ByUsernameRegMatch() {
    String JSONquery =
      "{\"property\": \"username\", \"value\": \"" + user1.getUsername() + "\", \"operator\": \"regmatch\"}";
    UserSearchParams params = new UserSearchParams(JSONquery);
    UserSearchBody searchBody = new UserSearchBody(params);
    UserSearchResult found = userSearchService.search(searchBody);
    assertEquals(1, found.getResults().length);
    assertEquals(user1.getUsername(), found.getResults()[0].getUsername());
  }

  @Test
  @Transactional
  public void findUser1ByUser1RegMatch() {
    String JSONquery = "{\"property\": \"username\", \"value\": \"user1.*\", \"operator\": \"regmatch\"}";
    UserSearchParams params = new UserSearchParams(JSONquery);
    UserSearchBody searchBody = new UserSearchBody(params);
    UserSearchResult found = userSearchService.search(searchBody);
    boolean user1found = false;
    for (int i = 0; i < found.getResults().length; i++) {
      if (found.getResults()[i].getUsername().equals(user1.getUsername())) user1found = true;
    }
    assertEquals(true, user1found);
  }

  @Test
  @Transactional
  public void findUser1User2ByRegMatch() {
    String JSONquery = "{\"property\": \"username\", \"value\": \"user[12].*\", \"operator\": \"regmatch\"}";
    UserSearchParams params = new UserSearchParams(JSONquery);
    UserSearchBody searchBody = new UserSearchBody(params);
    UserSearchResult found = userSearchService.search(searchBody);
    boolean user1found = false;
    boolean user2found = false;
    for (int i = 0; i < found.getResults().length; i++) {
      if (found.getResults()[i].getUsername().equals(user1.getUsername())) user1found = true;
      if (found.getResults()[i].getUsername().equals(user2.getUsername())) user2found = true;
    }
    assertEquals(true, user1found);
    assertEquals(true, user2found);
  }
}
