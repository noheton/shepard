package de.dlr.shepard.neo4Core.services;

import de.dlr.shepard.neo4Core.dao.ApiKeyDAO;
import de.dlr.shepard.neo4Core.dao.UserDAO;
import de.dlr.shepard.neo4Core.entities.ApiKey;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.io.ApiKeyIO;
import de.dlr.shepard.util.DateHelper;
import de.dlr.shepard.util.PKIHelper;
import io.jsonwebtoken.Jwts;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RequestScoped
public class ApiKeyService {

  @Inject
  ApiKeyDAO apiKeyDAO;

  @Inject
  UserDAO userDAO;

  @Inject
  DateHelper dateHelper;

  @Inject
  PKIHelper pkiHelper;

  /**
   * Searches the neo4j database for all ApiKeys associated with a given user.
   *
   * @param username Identifies the associated user
   * @return The list of ApiKeys associated with the given user
   */
  public List<ApiKey> getAllApiKeys(String username) {
    User user = userDAO.find(username);
    if (user != null) {
      return user.getApiKeys();
    }
    return Collections.emptyList();
  }

  /**
   * Searches the neo4j database for an ApiKey
   *
   * @param apiKeyUid Identifies the ApiKey to be searched
   * @return The ApiKey with the given uid or null
   */
  public ApiKey getApiKey(UUID apiKeyUid) {
    return apiKeyDAO.find(apiKeyUid);
  }

  /**
   * Creates an ApiKey and stores it in neo4j
   *
   * @param apiKey   The ApiKey to be stored
   * @param username The user who wants to create an apiKey
   * @param baseUri  The current Uri
   * @return The created ApiKey
   */
  public ApiKey createApiKey(ApiKeyIO apiKey, String username, String baseUri) {
    var user = userDAO.find(username);

    var toCreate = new ApiKey();
    toCreate.setBelongsTo(user);
    toCreate.setCreatedAt(DateHelper.getDate());
    toCreate.setName(apiKey.getName());

    var createdApiKey = apiKeyDAO.createOrUpdate(toCreate);
    createdApiKey.setJws(generateJws(createdApiKey, baseUri));
    return apiKeyDAO.createOrUpdate(createdApiKey);
  }

  /**
   * Deletes an ApiKey from neo4j
   *
   * @param apiKeyUid Identifies the ApiKey to be deleted
   * @return A boolean to identify whether the ApiKey was successfully removed
   */
  public boolean deleteApiKey(UUID apiKeyUid) {
    return apiKeyDAO.delete(apiKeyUid);
  }

  /**
   * Generates and sets a signed JSON Web Token for the given ApiKey object by
   * using an RSA-Key and the following attributes: username as the JWT claim
   * "subject", the URL of this backend software as the JWT claim "issuer", the id
   * of the apiKey as the JWT claim "id" and the current date for the JWT claims
   * "not before" and "issued at".
   *
   * @param apiKey  The apiKey for which the JSON Web Token should be generated.
   * @param baseUri Contains the context of the request in order to set JWT claim
   *                "issuer"
   */
  private String generateJws(ApiKey apiKey, String baseUri) {
    pkiHelper.init();
    var currentDate = DateHelper.getDate();
    var jws = Jwts.builder()
      .setSubject(apiKey.getBelongsTo().getUsername())
      .setIssuer(baseUri)
      .setNotBefore(currentDate)
      .setIssuedAt(currentDate)
      .setId(apiKey.getUid().toString())
      .signWith(pkiHelper.getPrivateKey())
      .compact();
    return jws;
  }
}
