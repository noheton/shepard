package de.dlr.shepard.testing.fixtures;

import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.common.neo4j.entities.BasicEntity;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared typed builders for the most-stubbed shepard primitives.
 *
 * <p>Per {@code aidocs/16} DX2: these fluent builders shrink the
 * {@code new Collection() {{ setName(...); setShepardId(...); ... }}}
 * boilerplate that clusters in {@code *ServiceTest} files. Defaults are
 * "sensible enough that a test that doesn't care about a field doesn't
 * have to set it" — a non-null name, a fresh {@code appId} per call,
 * a unique {@code shepardId}, and {@code createdAt} stamped to now.
 *
 * <p>Usage:
 * <pre>{@code
 * import static de.dlr.shepard.testing.fixtures.ShepardTestFixtures.*;
 *
 * User alice = aUser().username("alice").build();
 * Collection coll = aCollection().named("My data").ownedBy(alice).build();
 * DataObject obj = aDataObject().inCollection(coll).named("rev-1").build();
 * }</pre>
 *
 * <p>The class is intentionally narrow in v1 — one canonical reference
 * type ({@link BasicReference}) sets the pattern; widening to other
 * reference / payload kinds is later DX work.
 */
public final class ShepardTestFixtures {

  /**
   * Monotonic counter for default {@code shepardId} / {@code id} values
   * so each builder call produces a unique entity by default. Starts at
   * a number high enough that tests pinning specific small IDs (5L,
   * 15L, etc.) won't collide.
   */
  private static final AtomicLong ID_SEQUENCE = new AtomicLong(1_000_000L);

  /**
   * Fixed default for {@code createdAt} so two builders called in quick
   * succession produce equal Dates — equality-based stubbing stays
   * deterministic. Tests that care about the wall-clock value override
   * via {@code .createdAt(...)}.
   */
  private static final Date DEFAULT_CREATED_AT = new Date(0L);

  private ShepardTestFixtures() {
    // utility class
  }

  /** @return a fresh long id, unique per call within the JVM. */
  public static long nextId() {
    return ID_SEQUENCE.incrementAndGet();
  }

  // ---------- Entry points ----------

  public static UserBuilder aUser() {
    return new UserBuilder();
  }

  public static CollectionBuilder aCollection() {
    return new CollectionBuilder();
  }

  public static DataObjectBuilder aDataObject() {
    return new DataObjectBuilder();
  }

  public static PermissionsBuilder permissionsFor(BasicEntity entity) {
    return new PermissionsBuilder(entity);
  }

  public static BasicReferenceBuilder aBasicReference() {
    return new BasicReferenceBuilder();
  }

  // ---------- User ----------

  /**
   * Builder for {@link User}. Defaults to a unique synthetic
   * username and empty name fields (matching the
   * {@code new User(username)} convenience constructor).
   */
  public static final class UserBuilder {

    private String username = "user-" + nextId();
    private String firstName = "";
    private String lastName = "";
    private String email = "";
    private String orcid;
    private String displayName;
    private String appId = AppIdGenerator.next();

    public UserBuilder username(String username) {
      this.username = username;
      return this;
    }

    public UserBuilder firstName(String firstName) {
      this.firstName = firstName;
      return this;
    }

    public UserBuilder lastName(String lastName) {
      this.lastName = lastName;
      return this;
    }

    public UserBuilder email(String email) {
      this.email = email;
      return this;
    }

    public UserBuilder orcid(String orcid) {
      this.orcid = orcid;
      return this;
    }

    public UserBuilder displayName(String displayName) {
      this.displayName = displayName;
      return this;
    }

    public UserBuilder appId(String appId) {
      this.appId = appId;
      return this;
    }

    public User build() {
      User user = new User(username, firstName, lastName, email);
      user.setOrcid(orcid);
      user.setDisplayName(displayName);
      user.setAppId(appId);
      return user;
    }
  }

  // ---------- Collection ----------

  /**
   * Builder for {@link Collection}. Defaults: unique numeric id +
   * matching {@code shepardId}, a fresh {@code appId}, a non-null
   * name, {@code createdAt = now}.
   */
  public static final class CollectionBuilder {

    private Long id = nextId();
    private Long shepardId;
    private String appId = AppIdGenerator.next();
    private String name = "collection-" + nextId();
    private String description;
    private Map<String, String> attributes = new HashMap<>();
    private Date createdAt = DEFAULT_CREATED_AT;
    private User createdBy;
    private Date updatedAt;
    private User updatedBy;
    private boolean deleted;
    private Permissions permissions;

    public CollectionBuilder id(long id) {
      this.id = id;
      return this;
    }

    public CollectionBuilder shepardId(long shepardId) {
      this.shepardId = shepardId;
      return this;
    }

    public CollectionBuilder appId(String appId) {
      this.appId = appId;
      return this;
    }

    public CollectionBuilder named(String name) {
      this.name = name;
      return this;
    }

    public CollectionBuilder withDescription(String description) {
      this.description = description;
      return this;
    }

    public CollectionBuilder withAttributes(Map<String, String> attributes) {
      this.attributes = new HashMap<>(attributes);
      return this;
    }

    public CollectionBuilder createdAt(Date createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public CollectionBuilder ownedBy(User user) {
      this.createdBy = user;
      return this;
    }

    public CollectionBuilder updatedAt(Date updatedAt) {
      this.updatedAt = updatedAt;
      return this;
    }

    public CollectionBuilder updatedBy(User user) {
      this.updatedBy = user;
      return this;
    }

    public CollectionBuilder deleted(boolean deleted) {
      this.deleted = deleted;
      return this;
    }

    public CollectionBuilder withPermissions(Permissions permissions) {
      this.permissions = permissions;
      return this;
    }

    public Collection build() {
      Collection collection = new Collection(id);
      collection.setShepardId(shepardId != null ? shepardId : id);
      collection.setAppId(appId);
      collection.setName(name);
      collection.setDescription(description);
      collection.setAttributes(attributes);
      collection.setCreatedAt(createdAt);
      collection.setCreatedBy(createdBy);
      collection.setUpdatedAt(updatedAt);
      collection.setUpdatedBy(updatedBy);
      collection.setDeleted(deleted);
      collection.setPermissions(permissions);
      return collection;
    }
  }

  // ---------- DataObject ----------

  /**
   * Builder for {@link DataObject}. Defaults: unique numeric id +
   * matching {@code shepardId}, a fresh {@code appId}, a non-null
   * name, {@code createdAt = now}, no parent / predecessors /
   * collection (callers wire those up explicitly).
   */
  public static final class DataObjectBuilder {

    private Long id = nextId();
    private Long shepardId;
    private String appId = AppIdGenerator.next();
    private String name = "data-object-" + nextId();
    private String description;
    private Map<String, String> attributes = new HashMap<>();
    private Date createdAt = DEFAULT_CREATED_AT;
    private User createdBy;
    private Date updatedAt;
    private User updatedBy;
    private boolean deleted;
    private Collection collection;
    private DataObject parent;
    private List<DataObject> predecessors = new ArrayList<>();

    public DataObjectBuilder id(long id) {
      this.id = id;
      return this;
    }

    public DataObjectBuilder shepardId(long shepardId) {
      this.shepardId = shepardId;
      return this;
    }

    public DataObjectBuilder appId(String appId) {
      this.appId = appId;
      return this;
    }

    public DataObjectBuilder named(String name) {
      this.name = name;
      return this;
    }

    public DataObjectBuilder withDescription(String description) {
      this.description = description;
      return this;
    }

    public DataObjectBuilder withAttributes(Map<String, String> attributes) {
      this.attributes = new HashMap<>(attributes);
      return this;
    }

    public DataObjectBuilder createdAt(Date createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public DataObjectBuilder ownedBy(User user) {
      this.createdBy = user;
      return this;
    }

    public DataObjectBuilder updatedAt(Date updatedAt) {
      this.updatedAt = updatedAt;
      return this;
    }

    public DataObjectBuilder updatedBy(User user) {
      this.updatedBy = user;
      return this;
    }

    public DataObjectBuilder deleted(boolean deleted) {
      this.deleted = deleted;
      return this;
    }

    public DataObjectBuilder inCollection(Collection collection) {
      this.collection = collection;
      return this;
    }

    public DataObjectBuilder withParent(DataObject parent) {
      this.parent = parent;
      return this;
    }

    public DataObjectBuilder withPredecessors(List<DataObject> predecessors) {
      this.predecessors = new ArrayList<>(predecessors);
      return this;
    }

    public DataObject build() {
      DataObject dataObject = new DataObject(id);
      dataObject.setShepardId(shepardId != null ? shepardId : id);
      dataObject.setAppId(appId);
      dataObject.setName(name);
      dataObject.setDescription(description);
      dataObject.setAttributes(attributes);
      dataObject.setCreatedAt(createdAt);
      dataObject.setCreatedBy(createdBy);
      dataObject.setUpdatedAt(updatedAt);
      dataObject.setUpdatedBy(updatedBy);
      dataObject.setDeleted(deleted);
      dataObject.setCollection(collection);
      dataObject.setParent(parent);
      dataObject.setPredecessors(predecessors);
      return dataObject;
    }
  }

  // ---------- Permissions ----------

  /**
   * Builder for {@link Permissions}. Owner-less by default; callers
   * typically chain {@code .ownedBy(user)} (or use the
   * {@code permissionsFor(entity).owner(user)...} entry).
   */
  public static final class PermissionsBuilder {

    private final BasicEntity entity;
    private User owner;
    private PermissionType permissionType = PermissionType.Private;
    private final List<User> reader = new ArrayList<>();
    private final List<User> writer = new ArrayList<>();
    private final List<User> manager = new ArrayList<>();
    private String appId = AppIdGenerator.next();

    private PermissionsBuilder(BasicEntity entity) {
      this.entity = entity;
    }

    public PermissionsBuilder ownedBy(User owner) {
      this.owner = owner;
      return this;
    }

    public PermissionsBuilder type(PermissionType permissionType) {
      this.permissionType = permissionType;
      return this;
    }

    public PermissionsBuilder reader(User user) {
      this.reader.add(user);
      return this;
    }

    public PermissionsBuilder writer(User user) {
      this.writer.add(user);
      return this;
    }

    public PermissionsBuilder manager(User user) {
      this.manager.add(user);
      return this;
    }

    public PermissionsBuilder appId(String appId) {
      this.appId = appId;
      return this;
    }

    public Permissions build() {
      Permissions permissions = new Permissions(entity, owner, permissionType);
      permissions.setReader(new ArrayList<>(reader));
      permissions.setWriter(new ArrayList<>(writer));
      permissions.setManager(new ArrayList<>(manager));
      permissions.setAppId(appId);
      return permissions;
    }
  }

  // ---------- BasicReference ----------

  /**
   * Builder for {@link BasicReference} — the canonical example for
   * reference-type fixtures. Other reference kinds (file, structured,
   * timeseries, ...) follow the same shape and can land as later
   * builders without churning this class's surface.
   */
  public static final class BasicReferenceBuilder {

    private Long id = nextId();
    private Long shepardId;
    private String appId = AppIdGenerator.next();
    private String name = "reference-" + nextId();
    private Date createdAt = DEFAULT_CREATED_AT;
    private User createdBy;
    private Date updatedAt;
    private User updatedBy;
    private boolean deleted;
    private DataObject dataObject;

    public BasicReferenceBuilder id(long id) {
      this.id = id;
      return this;
    }

    public BasicReferenceBuilder shepardId(long shepardId) {
      this.shepardId = shepardId;
      return this;
    }

    public BasicReferenceBuilder appId(String appId) {
      this.appId = appId;
      return this;
    }

    public BasicReferenceBuilder named(String name) {
      this.name = name;
      return this;
    }

    public BasicReferenceBuilder createdAt(Date createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public BasicReferenceBuilder ownedBy(User user) {
      this.createdBy = user;
      return this;
    }

    public BasicReferenceBuilder updatedAt(Date updatedAt) {
      this.updatedAt = updatedAt;
      return this;
    }

    public BasicReferenceBuilder updatedBy(User user) {
      this.updatedBy = user;
      return this;
    }

    public BasicReferenceBuilder deleted(boolean deleted) {
      this.deleted = deleted;
      return this;
    }

    public BasicReferenceBuilder onDataObject(DataObject dataObject) {
      this.dataObject = dataObject;
      return this;
    }

    public BasicReference build() {
      BasicReference reference = new BasicReference(id);
      reference.setShepardId(shepardId != null ? shepardId : id);
      reference.setAppId(appId);
      reference.setName(name);
      reference.setCreatedAt(createdAt);
      reference.setCreatedBy(createdBy);
      reference.setUpdatedAt(updatedAt);
      reference.setUpdatedBy(updatedBy);
      reference.setDeleted(deleted);
      reference.setDataObject(dataObject);
      return reference;
    }
  }
}
