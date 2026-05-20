package de.dlr.shepard.common.neo4j;

import de.dlr.shepard.auth.apikey.entities.ApiKey;
import de.dlr.shepard.auth.bootstrap.BootstrapState;
import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.role.entities.Role;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.subscription.entities.Subscription;
import de.dlr.shepard.common.util.IConnector;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.labJournal.entities.LabJournalEntry;
import de.dlr.shepard.context.references.dataobject.entities.CollectionReference;
import de.dlr.shepard.context.references.file.entities.FileBundleReference;
import de.dlr.shepard.context.references.structureddata.entities.StructuredDataReference;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.references.uri.entities.URIReference;
import de.dlr.shepard.context.semantic.entities.AnnotatableTimeseries;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.snapshot.entities.Snapshot;
import de.dlr.shepard.context.version.entities.Version;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.structureddata.entities.StructuredData;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.provenance.entities.Activity;
import de.dlr.shepard.publish.entities.Publication;
import de.dlr.shepard.spi.payload.PayloadKind;
import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.v2.admin.ror.entities.InstanceRorConfig;
import de.dlr.shepard.v2.admin.sqltimeseries.entities.SqlTimeseriesConfig;
import de.dlr.shepard.v2.timeseries.model.TimeseriesAnnotation;
import de.dlr.shepard.v2.timeseriescontainer.entities.TimeseriesContainerChartView;
import de.dlr.shepard.v2.watches.entities.Watch;
import io.quarkus.logging.Log;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import org.eclipse.microprofile.config.ConfigProvider;
import org.neo4j.ogm.config.Configuration;
import org.neo4j.ogm.exception.ConnectionException;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;
import org.neo4j.ogm.session.SessionFactory;

/**
 * Connector for read and write access to the Neo4J database. The class
 * represents the lowest level of data access to the Neo4J database.
 *
 */
public class NeoConnector implements IConnector {

  private SessionFactory sessionFactory = null;
  private static NeoConnector instance = null;

  /**
   * Private constructor
   */
  private NeoConnector() {}

  /**
   * Returns the one and only instance of a NeoConnector
   *
   * @return NeoConnector
   */
  public static NeoConnector getInstance() {
    if (instance == null) {
      instance = new NeoConnector();
    }
    return instance;
  }

  /**
   * Establishes a connection to the Neo4J server by using the URL saved in the
   * config.properties file returned by the DatabaseHelper. This will block until
   * a connection could be established.
   */
  @Override
  public boolean connect() {
    String username = ConfigProvider.getConfig().getValue("neo4j.username", String.class);
    String password = ConfigProvider.getConfig().getValue("neo4j.password", String.class);
    String host = ConfigProvider.getConfig().getValue("neo4j.host", String.class);
    Configuration configuration = new Configuration.Builder()
      .uri("neo4j://" + host)
      .credentials(username, password)
      .verifyConnection(true)
      .useNativeTypes()
      .build();
    Duration timeout = ConfigProvider.getConfig()
      .getOptionalValue(MigrationsRunner.CONNECTION_WAIT_TIMEOUT_PROPERTY, Duration.class)
      .orElse(MigrationsRunner.DEFAULT_CONNECTION_WAIT_TIMEOUT);
    MigrationsRunner.awaitConnectivity(
      () -> {
        List<String> packages = new ArrayList<>(List.of(
          AnnotatableTimeseries.class.getPackageName(),
          ApiKey.class.getPackageName(),
          BootstrapState.class.getPackageName(),
          Collection.class.getPackageName(),
          CollectionReference.class.getPackageName(),
          FileContainer.class.getPackageName(),
          FileBundleReference.class.getPackageName(),
          LabJournalEntry.class.getPackageName(),
          Snapshot.class.getPackageName(),
          Permissions.class.getPackageName(),
          Role.class.getPackageName(),
          SemanticAnnotation.class.getPackageName(),
          StructuredData.class.getPackageName(),
          StructuredDataReference.class.getPackageName(),
          Subscription.class.getPackageName(),
          Timeseries.class.getPackageName(),
          TimeseriesReference.class.getPackageName(),
          // TS_CHART_VIEW1 — per-container chart-overview persistence
          TimeseriesContainerChartView.class.getPackageName(),
          // WATCH1 — Collection -> Container watch links
          Watch.class.getPackageName(),
          // ROR1 — instance-level Research Organization Registry config singleton.
          // Without this register call the OGM session can't load
          // :InstanceRorConfig nodes and admin PATCH calls 500 with
          // "Unable to find database label for entity ...InstanceRorConfig".
          InstanceRorConfig.class.getPackageName(),
          // SQL timeseries admin config (parallel to ROR1).
          SqlTimeseriesConfig.class.getPackageName(),
          // /v2/timeseries-references annotation entity.
          TimeseriesAnnotation.class.getPackageName(),
          URIReference.class.getPackageName(),
          User.class.getPackageName(),
          Version.class.getPackageName(),
          // PROV1a — provenance activity rows. Without this provenance
          // recording silently fails (save throws IAE, filter swallows it).
          Activity.class.getPackageName(),
          // Publication and ShepardTemplate have @NodeEntity and are persisted
          // via GenericDAO — must be in the scan path.
          // AasRegistration moved to shepard-plugin-aas; registered via AasPayloadKind.
          Publication.class.getPackageName(),
          ShepardTemplate.class.getPackageName()
        ));
        for (PayloadKind kind : ServiceLoader.load(PayloadKind.class)) {
          Log.infof("NeoConnector: registering entity packages from PayloadKind '%s': %s",
            kind.name(), kind.entityPackages());
          packages.addAll(kind.entityPackages());
        }
        sessionFactory = new SessionFactory(configuration, packages.toArray(String[]::new));
      },
      timeout,
      Thread::sleep,
      System::nanoTime
    );
    return true;
  }

  @Override
  public boolean disconnect() {
    if (sessionFactory != null) sessionFactory.close();
    return true;
  }

  @Override
  public boolean alive() {
    Result result;
    try {
      result = sessionFactory.openSession().query("MATCH (n) RETURN count(*) as count", Collections.emptyMap());
    } catch (ConnectionException ex) {
      return false;
    }
    return result.iterator().hasNext() && result.iterator().next().containsKey("count");
  }

  /**
   * Returns the internal neo4j session
   *
   * @return the internal neo4j session
   */
  public Session getNeo4jSession() {
    if (sessionFactory == null) {
      return null;
    }
    return sessionFactory.openSession();
  }
}
