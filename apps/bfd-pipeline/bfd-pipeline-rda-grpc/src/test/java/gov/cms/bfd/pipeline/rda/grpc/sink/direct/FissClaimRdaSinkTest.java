package gov.cms.bfd.pipeline.rda.grpc.sink.direct;

import static gov.cms.bfd.pipeline.rda.grpc.RdaPipelineTestUtils.assertGaugeReading;
import static gov.cms.bfd.pipeline.rda.grpc.RdaPipelineTestUtils.assertHistogramReading;
import static gov.cms.bfd.pipeline.rda.grpc.RdaPipelineTestUtils.assertMeterReading;
import static gov.cms.bfd.pipeline.rda.grpc.RdaPipelineTestUtils.assertTimerCount;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableList;
import com.zaxxer.hikari.HikariDataSource;
import gov.cms.bfd.model.rda.Mbi;
import gov.cms.bfd.model.rda.RdaClaimMessageMetaData;
import gov.cms.bfd.model.rda.RdaFissClaim;
import gov.cms.bfd.model.rda.StringList;
import gov.cms.bfd.pipeline.rda.grpc.ProcessingException;
import gov.cms.bfd.pipeline.rda.grpc.RdaChange;
import gov.cms.bfd.pipeline.rda.grpc.source.FissClaimTransformer;
import gov.cms.bfd.pipeline.sharedutils.IdHasher;
import gov.cms.bfd.pipeline.sharedutils.PipelineApplicationState;
import gov.cms.model.dsl.codegen.library.DataTransformer;
import gov.cms.mpsm.rda.v1.FissClaimChange;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class FissClaimRdaSinkTest {
  private static final String VERSION = "version";

  private final Clock clock = Clock.fixed(Instant.ofEpochMilli(60_000L), ZoneOffset.UTC);
  private final IdHasher.Config hasherConfig = new IdHasher.Config(1, "notarealpepper");

  @Mock private HikariDataSource dataSource;
  @Mock private EntityManagerFactory entityManagerFactory;
  @Mock private EntityManager entityManager;
  @Mock private EntityTransaction transaction;
  @Mock private FissClaimTransformer transformer;
  private MetricRegistry appMetrics;
  private FissClaimRdaSink sink;
  private long nextSeq = 0L;

  @BeforeEach
  public void setUp() {
    appMetrics = new MetricRegistry();
    doReturn(entityManager).when(entityManagerFactory).createEntityManager();
    doReturn(transaction).when(entityManager).getTransaction();
    doReturn(MbiCache.computedCache(hasherConfig)).when(transformer).getMbiCache();
    doReturn(transformer).when(transformer).withMbiCache(any());
    doReturn(true).when(entityManager).isOpen();
    PipelineApplicationState appState =
        new PipelineApplicationState(appMetrics, dataSource, entityManagerFactory, clock);
    sink = new FissClaimRdaSink(appState, transformer, true);
    sink.getMetrics().setLatestSequenceNumber(0);
    nextSeq = 0L;
  }

  @Test
  public void metricNames() {
    assertEquals(
        Arrays.asList(
            "FissClaimRdaSink.calls",
            "FissClaimRdaSink.change.latency.millis",
            "FissClaimRdaSink.failures",
            "FissClaimRdaSink.insertCount",
            "FissClaimRdaSink.lastSeq",
            "FissClaimRdaSink.successes",
            "FissClaimRdaSink.transform.failures",
            "FissClaimRdaSink.transform.successes",
            "FissClaimRdaSink.writes.batchSize",
            "FissClaimRdaSink.writes.elapsed",
            "FissClaimRdaSink.writes.merged",
            "FissClaimRdaSink.writes.persisted",
            "FissClaimRdaSink.writes.total"),
        new ArrayList<>(appMetrics.getNames()));
  }

  @Test
  public void mergeSuccessful() throws Exception {
    final List<RdaChange<RdaFissClaim>> batch =
        ImmutableList.of(createClaim("1"), createClaim("2"), createClaim("3"));

    final int count = sink.writeMessages(VERSION, messagesForBatch(batch));
    assertEquals(3, count);

    for (RdaChange<RdaFissClaim> change : batch) {
      verify(entityManager).merge(change.getClaim());
      verify(entityManager).merge(sink.createMetaData(change));
    }
    // the merge transaction will be committed
    verify(transaction).commit();

    final AbstractClaimRdaSink.Metrics metrics = sink.getMetrics();
    assertMeterReading(1, "calls", metrics.getCalls());
    assertMeterReading(0, "persists", metrics.getObjectsPersisted());
    assertMeterReading(3, "merges", metrics.getObjectsMerged());
    assertMeterReading(3, "writes", metrics.getObjectsWritten());
    assertMeterReading(3, "transform successes", metrics.getTransformSuccesses());
    assertMeterReading(0, "transform failures", metrics.getTransformFailures());
    assertMeterReading(1, "successes", metrics.getSuccesses());
    assertMeterReading(0, "failures", metrics.getFailures());
    assertGaugeReading(2, "lastSeq", metrics.getLatestSequenceNumber());
    assertHistogramReading(3, "database batch size", metrics.getDbBatchSize());
    assertHistogramReading(3, "database insert count", metrics.getInsertCount());
    assertTimerCount(1, "database timer count", metrics.getDbUpdateTime());
  }

  @Test
  public void mergeFatalError() {
    final List<RdaChange<RdaFissClaim>> batch =
        ImmutableList.of(createClaim("1"), createClaim("2"), createClaim("3"));
    doReturn(mock(RdaClaimMessageMetaData.class))
        .when(entityManager)
        .merge(any(RdaClaimMessageMetaData.class));
    doReturn(mock(RdaFissClaim.class)).when(entityManager).merge(any(RdaFissClaim.class));
    doThrow(new RuntimeException("oops")).when(entityManager).merge(batch.get(1).getClaim());

    try {
      sink.writeMessages(VERSION, messagesForBatch(batch));
      fail("should have thrown");
    } catch (ProcessingException error) {
      assertEquals(0, error.getProcessedCount());
      assertThat(error.getCause(), CoreMatchers.instanceOf(RuntimeException.class));
    }

    verify(entityManager).merge(batch.get(0).getClaim());
    verify(entityManager).merge(batch.get(1).getClaim());
    verify(entityManager, times(0)).merge(batch.get(2).getClaim()); // not called once a merge fails
    verify(transaction).rollback();

    final AbstractClaimRdaSink.Metrics metrics = sink.getMetrics();
    assertMeterReading(1, "calls", metrics.getCalls());
    assertMeterReading(0, "persists", metrics.getObjectsPersisted());
    assertMeterReading(0, "merges", metrics.getObjectsMerged());
    assertMeterReading(0, "writes", metrics.getObjectsWritten());
    assertMeterReading(3, "transform successes", metrics.getTransformSuccesses());
    assertMeterReading(0, "transform failures", metrics.getTransformFailures());
    assertMeterReading(0, "successes", metrics.getSuccesses());
    assertMeterReading(1, "failures", metrics.getFailures());
    assertGaugeReading(0, "lastSeq", metrics.getLatestSequenceNumber());
    assertHistogramReading(3, "database batch size", metrics.getDbBatchSize());
    assertHistogramReading(1, "database insert count", metrics.getInsertCount());
    assertTimerCount(1, "database timer count", metrics.getDbUpdateTime());
  }

  @Test
  public void closeMethodsAreCalled() throws Exception {
    sink.close();
    verify(entityManager).close();
  }

  @Test
  public void transformClaimFailure() throws Exception {
    final var claims = ImmutableList.of(createClaim("1"), createClaim("2"), createClaim("3"));
    final var messages = messagesForBatch(claims);
    doThrow(
            new DataTransformer.TransformationException(
                "oops", List.of(new DataTransformer.ErrorMessage("field", "oops!"))))
        .when(transformer)
        .transformClaim(messages.get(2));

    try {
      sink.writeMessages(VERSION, messages);
      fail("should have thrown");
    } catch (ProcessingException error) {
      assertEquals(0, error.getProcessedCount());
      assertThat(
          error.getCause(), CoreMatchers.instanceOf(DataTransformer.TransformationException.class));
    }

    verify(transaction, times(1)).begin();
    verify(transaction, times(1)).commit();
    verify(transaction, times(0)).rollback();

    final AbstractClaimRdaSink.Metrics metrics = sink.getMetrics();
    assertMeterReading(0, "calls", metrics.getCalls());
    assertMeterReading(0, "persists", metrics.getObjectsPersisted());
    assertMeterReading(0, "merges", metrics.getObjectsMerged());
    assertMeterReading(0, "writes", metrics.getObjectsWritten());
    assertMeterReading(2, "transform successes", metrics.getTransformSuccesses());
    assertMeterReading(1, "transform failures", metrics.getTransformFailures());
    assertMeterReading(0, "successes", metrics.getSuccesses());
    assertMeterReading(0, "failures", metrics.getFailures());
    assertGaugeReading(0, "lastSeq", metrics.getLatestSequenceNumber());
    assertHistogramReading(0, "database insert count", metrics.getInsertCount());
  }

  /**
   * Verify that meta data records are properly populated by {@link
   * FissClaimRdaSink#createMetaData(RdaChange)}.
   */
  @Test
  public void testCreateMetaData() {
    Mbi mbiRecord = Mbi.builder().mbi("mbi").hash("hash").build();
    Instant changeDate = Instant.ofEpochSecond(1);
    LocalDate transactionDate = LocalDate.of(1970, 2, 3);
    Instant now = Instant.ofEpochSecond(3);
    RdaFissClaim claim =
        RdaFissClaim.builder()
            .dcn("dcn")
            .mbiRecord(mbiRecord)
            .currStatus('A')
            .lastUpdated(now)
            .currLoc1('B')
            .currLoc2("C")
            .currTranDate(transactionDate)
            .build();
    RdaChange<RdaFissClaim> change =
        new RdaChange<>(
            100L,
            RdaChange.Type.UPDATE,
            claim,
            changeDate,
            new RdaChange.Source(
                (short) 1, (short) 0, LocalDate.of(1970, 1, 1), Instant.ofEpochSecond(0)));
    RdaClaimMessageMetaData metaData = sink.createMetaData(change);
    assertEquals(100L, metaData.getSequenceNumber());
    assertEquals('F', metaData.getClaimType());
    assertEquals("dcn", metaData.getClaimId());
    assertSame(mbiRecord, metaData.getMbiRecord());
    assertEquals("A", metaData.getClaimState());
    assertEquals(now, metaData.getReceivedDate());
    assertEquals(StringList.ofNonEmpty("B", "C"), metaData.getLocations());
    assertEquals(transactionDate, metaData.getTransactionDate());
  }

  private List<FissClaimChange> messagesForBatch(List<RdaChange<RdaFissClaim>> batch) {
    final var messages = ImmutableList.<FissClaimChange>builder();
    for (RdaChange<RdaFissClaim> change : batch) {
      var message =
          FissClaimChange.newBuilder()
              .setDcn(change.getClaim().getDcn())
              .setSeq(change.getSequenceNumber())
              .build();
      doReturn(change).when(transformer).transformClaim(message);
      messages.add(message);
    }
    return messages.build();
  }

  private RdaChange<RdaFissClaim> createClaim(String dcn) {
    RdaFissClaim claim = new RdaFissClaim();
    claim.setDcn(dcn);
    claim.setApiSource(VERSION);
    return new RdaChange<>(
        nextSeq++,
        RdaChange.Type.INSERT,
        claim,
        clock.instant().minusMillis(12),
        new RdaChange.Source(
            (short) 1, (short) 0, LocalDate.of(1970, 1, 1), Instant.ofEpochSecond(0)));
  }
}
