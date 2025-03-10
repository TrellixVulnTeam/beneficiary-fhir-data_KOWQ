package gov.cms.bfd.pipeline;

import com.codahale.metrics.MetricRegistry;
import com.zaxxer.hikari.HikariDataSource;
import gov.cms.bfd.DatabaseTestUtils;
import gov.cms.bfd.model.rda.RdaFissClaim;
import gov.cms.bfd.model.rda.RdaFissProcCode;
import gov.cms.bfd.model.rif.Beneficiary;
import gov.cms.bfd.model.rif.BeneficiaryHistory;
import gov.cms.bfd.model.rif.BeneficiaryMonthly;
import gov.cms.bfd.model.rif.CarrierClaim;
import gov.cms.bfd.model.rif.CarrierClaimLine;
import gov.cms.bfd.model.rif.DMEClaim;
import gov.cms.bfd.model.rif.DMEClaimLine;
import gov.cms.bfd.model.rif.HHAClaim;
import gov.cms.bfd.model.rif.HHAClaimLine;
import gov.cms.bfd.model.rif.HospiceClaim;
import gov.cms.bfd.model.rif.HospiceClaimLine;
import gov.cms.bfd.model.rif.InpatientClaim;
import gov.cms.bfd.model.rif.InpatientClaimLine;
import gov.cms.bfd.model.rif.LoadedBatch;
import gov.cms.bfd.model.rif.LoadedFile;
import gov.cms.bfd.model.rif.MedicareBeneficiaryIdHistory;
import gov.cms.bfd.model.rif.OutpatientClaim;
import gov.cms.bfd.model.rif.OutpatientClaimLine;
import gov.cms.bfd.model.rif.PartDEvent;
import gov.cms.bfd.model.rif.RifFile;
import gov.cms.bfd.model.rif.RifFileType;
import gov.cms.bfd.model.rif.RifFilesEvent;
import gov.cms.bfd.model.rif.SNFClaim;
import gov.cms.bfd.model.rif.SNFClaimLine;
import gov.cms.bfd.model.rif.SkippedRifRecord;
import gov.cms.bfd.pipeline.sharedutils.PipelineApplicationState;
import gov.cms.bfd.sharedutils.database.DatabaseSchemaManager;
import gov.cms.bfd.sharedutils.database.DatabaseUtils;
import gov.cms.bfd.sharedutils.exceptions.BadCodeMonkeyException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Table;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contains utilities that are useful when testing with or against the BFD Pipeline application.
 *
 * <p>This is being left in <code>src/main</code> so that it can be used from other modules' tests,
 * without having to delve into classpath dark arts.
 */
public final class PipelineTestUtils {
  /**
   * A reasonable (though not terribly performant) suggested default value for {@link
   * DatabaseOptions#getMaxPoolSize()}. Effectively, it's double the default value that will be used
   * in tests for <code>LoadAppOptions#getLoaderThreads()</code>.
   */
  private static final int DEFAULT_MAX_POOL_SIZE =
      Math.max(1, (Runtime.getRuntime().availableProcessors() - 1)) * 2 * 2;

  private static final Logger LOGGER = LoggerFactory.getLogger(PipelineTestUtils.class);

  /** The singleton {@link PipelineTestUtils} instance to use everywhere. */
  private static PipelineTestUtils SINGLETON;

  /**
   * The {@link PipelineApplicationState} that should be used across all of the tests, which most
   * notably contains the {@link HikariDataSource} and {@link EntityManagerFactory} to use.
   */
  private final PipelineApplicationState pipelineApplicationState;

  /**
   * Constructs a new {@link PipelineTestUtils} instance. Marked <code>private</code>; use {@link
   * #get()}, instead.
   */
  private PipelineTestUtils() {
    MetricRegistry testMetrics = new MetricRegistry();
    DatabaseSchemaManager.createOrUpdateSchema(DatabaseTestUtils.initUnpooledDataSource());
    this.pipelineApplicationState =
        new PipelineApplicationState(
            testMetrics,
            DatabaseTestUtils.get().getUnpooledDataSource(),
            DEFAULT_MAX_POOL_SIZE,
            PipelineApplicationState.PERSISTENCE_UNIT_NAME,
            Clock.systemUTC());
  }

  /** @return the singleton {@link PipelineTestUtils} instance to use everywhere */
  public static synchronized PipelineTestUtils get() {
    /*
     * Why are we using a singleton and caching all of these fields? Because creating some of the
     * fields stored in the PipelineApplicationState is EXPENSIVE (it maintains a DB connection
     * pool), so we don't want to have to re-create it for every test.
     */

    if (SINGLETON == null) {
      SINGLETON = new PipelineTestUtils();
    }

    return SINGLETON;
  }

  /**
   * @return {@link PipelineApplicationState} that should be used across all of the tests, which
   *     most notably contains the {@link HikariDataSource} and {@link EntityManagerFactory} to use
   */
  public PipelineApplicationState getPipelineApplicationState() {
    return pipelineApplicationState;
  }

  /**
   * Runs a <code>TRUNCATE</code> for all tables in the {@link
   * DatabaseTestUtils#getUnpooledDataSource()} database.
   */
  public void truncateTablesInDataSource() {
    List<Class<?>> entityTypes =
        Arrays.asList(
            PartDEvent.class,
            SNFClaimLine.class,
            SNFClaim.class,
            OutpatientClaimLine.class,
            OutpatientClaim.class,
            InpatientClaimLine.class,
            InpatientClaim.class,
            HospiceClaimLine.class,
            HospiceClaim.class,
            HHAClaimLine.class,
            HHAClaim.class,
            DMEClaimLine.class,
            DMEClaim.class,
            CarrierClaimLine.class,
            CarrierClaim.class,
            BeneficiaryHistory.class,
            MedicareBeneficiaryIdHistory.class,
            BeneficiaryMonthly.class,
            Beneficiary.class,
            LoadedBatch.class,
            LoadedFile.class,
            RdaFissClaim.class,
            RdaFissProcCode.class,
            SkippedRifRecord.class);

    try (Connection connection = pipelineApplicationState.getPooledDataSource().getConnection(); ) {
      // Disable auto-commit and remember the default schema name.
      connection.setAutoCommit(false);
      Optional<String> defaultSchemaName = Optional.ofNullable(connection.getSchema());
      if (defaultSchemaName.isEmpty()) {
        throw new BadCodeMonkeyException("Unable to determine default schema name.");
      }

      // Loop over every @Entity type.
      for (Class<?> entityType : entityTypes) {
        Optional<Table> entityTableAnnotation =
            Optional.ofNullable(entityType.getAnnotation(Table.class));

        // First, make sure we found an @Table annotation.
        if (entityTableAnnotation.isEmpty()) {
          throw new BadCodeMonkeyException(
              "Unable to determine table metadata for entity: " + entityType.getCanonicalName());
        }

        // Then, make sure we have a table name specified.
        if (entityTableAnnotation.get().name() == null
            || entityTableAnnotation.get().name().isEmpty()) {
          throw new BadCodeMonkeyException(
              "Unable to determine table name for entity: " + entityType.getCanonicalName());
        }
        String tableNameSpecifier = normalizeTableName(entityTableAnnotation.get().name());

        // Then, switch to the appropriate schema.
        if (entityTableAnnotation.get().schema() != null
            && !entityTableAnnotation.get().schema().isEmpty()) {
          String schemaNameSpecifier =
              normalizeSchemaName(connection, entityTableAnnotation.get().schema());
          connection.setSchema(schemaNameSpecifier);
        } else {
          connection.setSchema(defaultSchemaName.get());
        }

        /*
         * Finally, run the TRUNCATE. On Postgres the cascade option is required due to the
         * presence of FK constraints.
         */
        String truncateTableSql = String.format("TRUNCATE TABLE %s", tableNameSpecifier);
        if (DatabaseUtils.isPostgresConnection(connection)) {
          truncateTableSql = truncateTableSql + " CASCADE";
        }
        connection.createStatement().execute(truncateTableSql);

        connection.setSchema(defaultSchemaName.get());
      }

      connection.commit();
      LOGGER.info("Removed all application data from database.");
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * For compatibility with HSQLDB and Postgresql, all schema names must have case preserved but any
   * quotes in the name must be removed.
   *
   * @param schemaNameSpecifier name of a schema from a hibernate annotation
   * @return value compatible with call to {@link Connection#setSchema(String)}
   */
  private String normalizeSchemaName(Connection connection, String schemaNameSpecifier)
      throws SQLException {
    String sql = schemaNameSpecifier.replaceAll("`", "");
    if (isSchemaNameUppercaseRequired(connection)) {
      sql = sql.toUpperCase();
    }
    return sql;
  }

  /**
   * Table names that use mixed case use quotes and have their original case preserved but those
   * without quotes are converted to upper case to be compatible with Hibernate/HSQLDB.
   *
   * @param tableNameSpecifier name of a table from a hibernate annotation
   * @return value compatible with call to {@link java.sql.Statement#execute(String)}
   */
  private String normalizeTableName(String tableNameSpecifier) {
    if (tableNameSpecifier.startsWith("`")) {
      tableNameSpecifier = tableNameSpecifier.replaceAll("`", "");
    } else {
      tableNameSpecifier = tableNameSpecifier.toUpperCase();
    }
    return tableNameSpecifier;
  }

  /**
   * We're stuck in a painful situation where postgresql only recognizes the schema in its original
   * case but HSQLDB only recognizes the schema in all uppercase.
   *
   * @param connection database connection used to make the decision
   * @return true if the schema name should be converted to upper case
   * @throws SQLException in case of failure
   */
  private boolean isSchemaNameUppercaseRequired(Connection connection) throws SQLException {
    return DatabaseUtils.isHsqlConnection(connection);
  }

  /**
   * A wrapper for the entity manager logic and action. The consumer is called within a transaction
   * to which is rolled back.
   *
   * @param consumer to call with an entity manager.
   */
  public void doTestWithDb(BiConsumer<DataSource, EntityManager> consumer) {
    EntityManager entityManager = null;
    try {
      entityManager = pipelineApplicationState.getEntityManagerFactory().createEntityManager();
      consumer.accept(pipelineApplicationState.getPooledDataSource(), entityManager);
    } finally {
      if (entityManager != null && entityManager.isOpen()) {
        entityManager.close();
      }
    }
  }

  /**
   * Get the list of loaded files from the passed in db, latest first
   *
   * @param entityManager to use
   * @return the list of loaded files in the db
   */
  public List<LoadedFile> findLoadedFiles(EntityManager entityManager) {
    entityManager.clear();
    return entityManager
        .createQuery("select f from LoadedFile f order by f.created desc", LoadedFile.class)
        .getResultList();
  }

  /**
   * Return a Files Event with a single dummy file
   *
   * @return a new RifFilesEvent
   */
  public RifFilesEvent createDummyFilesEvent() {
    RifFile dummyFile =
        new RifFile() {

          @Override
          public InputStream open() {
            return null;
          }

          @Override
          public RifFileType getFileType() {
            return RifFileType.BENEFICIARY;
          }

          @Override
          public String getDisplayName() {
            return "Dummy.txt";
          }

          @Override
          public Charset getCharset() {
            return StandardCharsets.UTF_8;
          }
        };

    return new RifFilesEvent(Instant.now(), false, Arrays.asList(dummyFile));
  }

  /**
   * Pause of a number milliseconds.
   *
   * @param millis to sleap
   */
  public void pauseMillis(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ex) {
    }
  }
}
