package de.dlr.shepard.common.search.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.security.JWTPrincipal;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.search.endpoints.BasicCollectionAttributes;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.semantic.SemanticRepositoryType;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.semantic.entities.SemanticRepository;
import de.dlr.shepard.context.semantic.io.SemanticAnnotationIO;
import de.dlr.shepard.context.semantic.io.SemanticRepositoryIO;
import de.dlr.shepard.context.semantic.services.SemanticAnnotationService;
import de.dlr.shepard.context.semantic.services.SemanticRepositoryService;
import de.dlr.shepard.integrationtests.WireMockResource;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@WithTestResource(WireMockResource.class)
public class CollectionSearchServiceQuarkusTest {

  @Inject
  CollectionService collectionService;

  @Inject
  CollectionSearchService collectionSearchService;

  @Inject
  SemanticAnnotationService semanticAnnotationService;

  @Inject
  SemanticRepositoryService semanticRepositoryService;

  @Inject
  AuthenticationContext authenticationContext;

  @Inject
  UserService userService;

  private User user;
  private String username = "username";
  private Collection collection1;
  private Collection collectionWithAnnotation;
  private SemanticAnnotation collectionAnnotation;

  @BeforeEach
  public void setUp() {
    if (user == null) {
      User user = new User(username);
      userService.createOrUpdateUser(user);
      authenticationContext.setPrincipal(new JWTPrincipal(username, "key"));
    }
    if (collection1 == null) {
      CollectionIO collection1IO = new CollectionIO();
      collection1IO.setName("collection1" + System.currentTimeMillis());
      collection1 = collectionService.createCollection(collection1IO);
    }
    if (collectionWithAnnotation == null) {
      CollectionIO collectionWithAnnotationIO = new CollectionIO();
      collectionWithAnnotationIO.setName("collectionWithAnnotation" + System.currentTimeMillis());
      collectionWithAnnotation = collectionService.createCollection(collectionWithAnnotationIO);
      SemanticRepositoryIO repToCreate = new SemanticRepositoryIO();
      repToCreate.setName("SemanticRepository");
      repToCreate.setType(SemanticRepositoryType.SPARQL);
      repToCreate.setEndpoint(WireMockResource.getWireMockServerURlWithPath("/sparql"));
      SemanticRepository repository = semanticRepositoryService.createRepository(repToCreate);
      SemanticAnnotationIO AnnoToCreate = new SemanticAnnotationIO();
      AnnoToCreate.setPropertyIRI("http://dbpedia.org/ontology/ingredient");
      AnnoToCreate.setPropertyRepositoryId(repository.getId());
      AnnoToCreate.setValueIRI("http://dbpedia.org/resource/Almond_milk");
      AnnoToCreate.setValueRepositoryId(repository.getId());
      collectionAnnotation = semanticAnnotationService.createAnnotationByShepardId(
        collectionWithAnnotation.getShepardId(),
        AnnoToCreate
      );
    }
  }

  @Test
  @Transactional
  public void findCollection1NotCollectionWithAnnotationsTest() {
    System.out.println("findColl1Test started heute");
    String collectionSearchQuery = "{\"property\": \"name\", \"value\": \"collection1\", \"operator\": \"contains\"}";
    PaginatedCollectionList searchResult = collectionSearchService.search(
      collectionSearchQuery,
      Optional.empty(),
      Optional.empty(),
      BasicCollectionAttributes.createdAt,
      true
    );
    System.out.println("number of found collections: " + searchResult.getResults().size());
    System.out.println("found: " + searchResult.getResults().get(0));
    boolean containsCollection1 = false;
    boolean containsCollectionWithAnnotation = false;
    for (Collection foundCollection : searchResult.getResults()) {
      if (foundCollection.getShepardId() == collection1.getShepardId()) containsCollection1 = true;
      if (foundCollection.getShepardId() == collectionWithAnnotation.getShepardId()) containsCollectionWithAnnotation =
        true;
    }
    assertEquals(true, containsCollection1);
    assertEquals(false, containsCollectionWithAnnotation);
  }

  @Test
  @Transactional
  public void findCollectionWithAnnotationsNotCollectionTest() {
    System.out.println("findAnnoCollTest started");
    String collectionSearchQuery =
      "{\"property\": \"hasAnnotation\", \"value\": \"ingre.*:Almon.*\", \"operator\": \"regmatch\"}";
    PaginatedCollectionList searchResult = collectionSearchService.search(
      collectionSearchQuery,
      Optional.empty(),
      Optional.empty(),
      BasicCollectionAttributes.createdAt,
      true
    );
    boolean containsCollection1 = false;
    boolean containsCollectionWithAnnotation = false;
    for (Collection foundCollection : searchResult.getResults()) {
      if (foundCollection.getShepardId() == collection1.getShepardId()) containsCollection1 = true;
      if (foundCollection.getShepardId() == collectionWithAnnotation.getShepardId()) containsCollectionWithAnnotation =
        true;
    }
    assertEquals(false, containsCollection1);
    assertEquals(true, containsCollectionWithAnnotation);
  }
}
