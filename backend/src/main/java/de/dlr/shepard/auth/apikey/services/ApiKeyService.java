package de.dlr.shepard.auth.apikey.services;

import de.dlr.shepard.auth.apikey.daos.ApiKeyDAO;
import de.dlr.shepard.auth.apikey.entities.ApiKey;
import de.dlr.shepard.auth.apikey.io.ApiKeyIO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.PKIHelper;
import io.jsonwebtoken.Jwts;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;

@RequestScoped
public class ApiKeyService {

  @Inject
  ApiKeyDAO apiKeyDAO;

  @Inject
  UserService userService;

  @Inject
  DateHelper dateHelper;

  @Inject
  PKIHelper pkiHelper;

  /**
   * Searches the neo4j database for all ApiKeys associated with a given user.
   *
   * @param username Identifies the associated user
   * @return The list of ApiKeys associated with the given user
   * @throws InvalidPathException if the user of this name does not exist
   * @throws InvalidAuthException if the username does not match the user making the request
   */
  public List<ApiKey> getAllApiKeys(String username) {
    userService.assertCurrentUserEquals(username);
    User user = userService.getUser(username);
    return user.getApiKeys();
  }

  /**
   * Searches the neo4j database for an ApiKey
   *
   * @param username of the user owning the api key
   * @param apiKeyUid Identifies the ApiKey to be searched
   * @return The ApiKey with the given uid or null
   * @throws InvalidPathException if the ApiKey or the user of this name does not exist
   * @throws InvalidAuthException if the username does not match the user making the request or the ApiKey does not belong to the user
   */
  public ApiKey getApiKey(String username, UUID apiKeyUid) {
    userService.getUser(username);
    userService.assertCurrentUserEquals(username);

    ApiKey requestedKey = apiKeyDAO.find(apiKeyUid);

    if (requestedKey == null) {
      throw new InvalidPathException("ID ERROR - ApiKey does not exist");
    }
    if (!requestedKey.getBelongsTo().getUsername().equals(username)) {
      throw new InvalidAuthException("You do not have permissions for this ApiKey.");
    }

    return apiKeyDAO.find(apiKeyUid);
  }

  /**
   * Searches the neo4j database for an ApiKey.
   *
   * @param apiKeyUid Identifies the ApiKey to be searched
   * @return The ApiKey with the given uid or null
   */
  public ApiKey getApiKey(UUID apiKeyUid) {
    ApiKey requestedKey = apiKeyDAO.find(apiKeyUid);

    if (requestedKey == null) {
      throw new InvalidPathException("ID ERROR - ApiKey does not exist");
    }

    return requestedKey;
  }

  /**
   * Creates an ApiKey and stores it in neo4j
   *
   * @param apiKey   The ApiKey to be stored
   * @param username The user who wants to create an apiKey
   * @param baseUri  The current Uri
   * @return The created ApiKey
   * @throws InvalidPathException if the user of this name does not exist
   * @throws InvalidAuthException if the username does not match the user making the request
   */
  public ApiKey createApiKey(ApiKeyIO apiKey, String username, String baseUri) {
    var user = userService.getUser(username);
    userService.assertCurrentUserEquals(username);

    var now = dateHelper.getDate();
    var validUntil = apiKey.getValidUntil();
    if (validUntil != null && !validUntil.after(now)) {
      throw new InvalidRequestException("validUntil must be in the future");
    }

    var toCreate = new ApiKey();
    toCreate.setBelongsTo(user);
    toCreate.setCreatedAt(now);
    toCreate.setName(apiKey.getName());
    toCreate.setValidUntil(validUntil);

    var createdApiKey = apiKeyDAO.createOrUpdate(toCreate);
    createdApiKey.setJws(generateJws(createdApiKey, baseUri));
    return apiKeyDAO.createOrUpdate(createdApiKey);
  }

  /**
   * Deletes an ApiKey from neo4j
   *
   * @param apiKeyUid Identifies the ApiKey to be deleted
   * @return A boolean to identify whether the ApiKey was successfully removed
   * @throws InvalidPathException if the ApiKey or the user of this name does not exist
   * @throws InvalidAuthException if the username does not match the user making the request or the ApiKey does not belong to the user
   */
  public boolean deleteApiKey(String username, UUID apiKeyUid) {
    userService.assertCurrentUserEquals(username);
    getApiKey(username, apiKeyUid);

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
    var currentDate = dateHelper.getDate();
    var builder = Jwts.builder()
      .setSubject(apiKey.getBelongsTo().getUsername())
      .setIssuer(baseUri)
      .setNotBefore(currentDate)
      .setIssuedAt(currentDate)
      .setId(apiKey.getUid().toString());
    if (apiKey.getValidUntil() != null) {
      builder.setExpiration(apiKey.getValidUntil());
    }
    return builder.signWith(pkiHelper.getPrivateKey()).compact();
  }
}
