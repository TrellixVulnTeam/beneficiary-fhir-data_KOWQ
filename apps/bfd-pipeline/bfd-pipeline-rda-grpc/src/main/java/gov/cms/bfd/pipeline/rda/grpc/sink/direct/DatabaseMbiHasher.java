package gov.cms.bfd.pipeline.rda.grpc.sink.direct;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import gov.cms.bfd.model.rda.PreAdjMbi;
import gov.cms.bfd.pipeline.sharedutils.IdHasher;
import java.util.concurrent.ExecutionException;
import javax.annotation.concurrent.NotThreadSafe;
import javax.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NotThreadSafe
public class DatabaseMbiHasher extends IdHasher implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseMbiHasher.class);

  private final EntityManager entityManager;
  private final Cache<String, String> cache;

  public DatabaseMbiHasher(Config config, EntityManager entityManager) {
    super(config);
    this.entityManager = entityManager;
    cache = CacheBuilder.newBuilder().maximumSize(config.getCacheSize()).build();
  }

  @Override
  public String computeIdentifierHash(String identifier) {
    try {
      return cache.get(identifier, () -> lookup(identifier));
    } catch (ExecutionException ex) {
      final var hash = super.computeIdentifierHash(identifier);
      LOGGER.warn(
          "caught exception while saving generated hash: hash={} message={}",
          hash,
          ex.getCause().getMessage());
      return super.computeIdentifierHash(identifier);
    }
  }

  @Override
  public void close() throws Exception {
    entityManager.close();
  }

  @VisibleForTesting
  String lookup(String mbi) {
    entityManager.getTransaction().begin();
    PreAdjMbi record = entityManager.find(PreAdjMbi.class, mbi);
    if (record == null) {
      record = new PreAdjMbi(mbi, super.computeIdentifierHash(mbi));
      entityManager.merge(record);
    }
    entityManager.getTransaction().commit();
    return record.getMbiHash();
  }
}
