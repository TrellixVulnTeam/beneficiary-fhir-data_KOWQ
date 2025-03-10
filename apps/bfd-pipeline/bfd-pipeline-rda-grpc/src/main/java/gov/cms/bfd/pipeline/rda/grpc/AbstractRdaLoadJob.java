package gov.cms.bfd.pipeline.rda.grpc;

import static gov.cms.bfd.pipeline.sharedutils.PipelineJobOutcome.NOTHING_TO_DO;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import gov.cms.bfd.pipeline.sharedutils.NullPipelineJobArguments;
import gov.cms.bfd.pipeline.sharedutils.PipelineJob;
import gov.cms.bfd.pipeline.sharedutils.PipelineJobOutcome;
import gov.cms.bfd.pipeline.sharedutils.PipelineJobSchedule;
import gov.cms.bfd.sharedutils.interfaces.ThrowingFunction;
import java.io.Serializable;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.slf4j.Logger;

/**
 * General PipelineJob instance that delegates the actual ETL work to two other objects. The
 * RdaSource object handles communication with the source of incoming data. The RdaSink object
 * handles communication with the ultimate storage system. The purpose of this class is to handle
 * general PipelineJob semantics that are common to any source or sink.
 *
 * <p>Since the streaming service can run for extended periods of time this class is designed to be
 * reentrant. If multiple threads invoke the call() method at the same time only the first thread
 * will do any work. The other threads will all immediately return with an indication that they have
 * no work to do.
 */
public abstract class AbstractRdaLoadJob<TResponse, TClaim>
    implements PipelineJob<NullPipelineJobArguments> {

  /**
   * Denotes the preferred execution of a sink
   *
   * <p>This enum is used with sink factories to help determine what type of sink should be created
   * by the factory. When followup actions depend on the outcome of a sink write, synchronous
   * execution may be desired.
   */
  public enum SinkTypePreference {
    NONE,
    SYNCHRONOUS,
    ASYNCHRONOUS
  }

  private final Config config;
  private final Callable<RdaSource<TResponse, TClaim>> preJobTaskFactory;
  private final Callable<RdaSource<TResponse, TClaim>> sourceFactory;
  private final ThrowingFunction<RdaSink<TResponse, TClaim>, SinkTypePreference, Exception>
      sinkFactory;
  private final Logger logger; // each subclass provides its own logger
  private final Metrics metrics;
  // This is used to enforce that this job can only be executed by a single thread at any given
  // time. If multiple threads call the job at the same time only the first will do any work.
  private final Semaphore runningSemaphore;

  AbstractRdaLoadJob(
      Config config,
      Callable<RdaSource<TResponse, TClaim>> preJobTaskFactory,
      Callable<RdaSource<TResponse, TClaim>> sourceFactory,
      ThrowingFunction<RdaSink<TResponse, TClaim>, SinkTypePreference, Exception> sinkFactory,
      MetricRegistry appMetrics,
      Logger logger) {
    this.config = Preconditions.checkNotNull(config);
    this.preJobTaskFactory = Preconditions.checkNotNull(preJobTaskFactory);
    this.sourceFactory = Preconditions.checkNotNull(sourceFactory);
    this.sinkFactory = Preconditions.checkNotNull(sinkFactory);
    this.logger = logger;
    metrics = new Metrics(appMetrics, getClass());
    runningSemaphore = new Semaphore(1);
  }

  /**
   * Invokes the RDA API to download data and store it in the database. Since errors during the call
   * are not exceptional (RDA API downtime for upgrade, network hiccups, etc) we catch any
   * exceptions and return normally. If we let the exception pass through the scheduler will no
   * re-schedule us.
   */
  @Override
  public PipelineJobOutcome call() throws Exception {
    // We only allow one outstanding call at a time.  If this job is already running any other
    // call to the same job exits immediately with NOTHING_TO_DO.
    if (!runningSemaphore.tryAcquire()) {
      logger.warn("job is already running");
      return NOTHING_TO_DO;
    }
    try {
      try (RdaSource<TResponse, TClaim> source = preJobTaskFactory.call();
          RdaSink<TResponse, TClaim> sink = sinkFactory.apply(SinkTypePreference.SYNCHRONOUS)) {
        source.retrieveAndProcessObjects(1, sink);
      }

      int processedCount;
      try {
        processedCount = callRdaServiceAndStoreRecords();
      } catch (ProcessingException ex) {
        processedCount = ex.getProcessedCount();
      }
      return processedCount == 0 ? NOTHING_TO_DO : PipelineJobOutcome.WORK_DONE;
    } finally {
      runningSemaphore.release();
    }
  }

  /**
   * Invokes the RdaSource and RdaSink objects to download data from the RDA API and store it into
   * the database.
   *
   * @return the number of objects written to the sink
   * @throws ProcessingException any error that terminated processing
   */
  @VisibleForTesting
  int callRdaServiceAndStoreRecords() throws ProcessingException {
    logger.info("processing begins");
    final long startMillis = System.currentTimeMillis();
    int processedCount = 0;
    Exception error = null;
    try {
      metrics.calls.mark();
      try (RdaSource<TResponse, TClaim> source = sourceFactory.call();
          RdaSink<TResponse, TClaim> sink = sinkFactory.apply(SinkTypePreference.NONE)) {
        processedCount = source.retrieveAndProcessObjects(config.getBatchSize(), sink);
      }
    } catch (ProcessingException ex) {
      processedCount += ex.getProcessedCount();
      error = ex;
    } catch (Exception ex) {
      error = ex;
    }
    metrics.processed.mark(processedCount);
    final long stopMillis = System.currentTimeMillis();
    logger.info("processed {} objects in {} ms", processedCount, stopMillis - startMillis);
    if (error != null) {
      metrics.failures.mark();
      logger.error("processing aborted by an exception: message={}", error.getMessage(), error);
      throw new ProcessingException(error, processedCount);
    }
    metrics.successes.mark();
    return processedCount;
  }

  /**
   * This job will tend to run for a long time during each execution but has a schedule so that it
   * can be automatically restarted if it exits for any reason. The job detects when it's already
   * running so periodic execution is safe.
   *
   * @return the run interval as a PipelineJobSchedule.
   */
  @Override
  public Optional<PipelineJobSchedule> getSchedule() {
    return Optional.of(
        new PipelineJobSchedule(config.getRunInterval().toMillis(), ChronoUnit.MILLIS));
  }

  @Override
  public boolean isInterruptible() {
    return true;
  }

  @VisibleForTesting
  Metrics getMetrics() {
    return metrics;
  }

  /** Immutable class containing configuration settings used by the DcGeoRDALoadJob class. */
  @EqualsAndHashCode
  public static final class Config implements Serializable {
    private static final long serialVersionUID = 1823137784819917L;

    /**
     * runInterval specifies how often the job should be scheduled. It is used to create a return
     * value for the PipelineJob.getSchedule() method.
     */
    @Getter private final Duration runInterval;

    /**
     * writeThreads specifies the number of threads to be used for writing claims to the database.
     * Setting this to one perform all writes synchronously. Higher numbers use {@link
     * gov.cms.bfd.pipeline.rda.grpc.sink.concurrent.ConcurrentRdaSink} to perform writes
     * asynchronously.
     */
    @Getter private final int writeThreads;

    /**
     * batchSize specifies the number of records per batch sent to the RdaSink for processing. This
     * value will likely be tuned for a specific type of sink object and for performance tuning
     * purposes (i.e. finding most efficient transaction size for a specific database).
     */
    @Getter private final int batchSize;

    /**
     * Optional hard coded starting sequence number for FISS claims. Optional is not Serializable,
     * so we have to store this as a nullable value. *
     */
    @Nullable private final Long startingFissSeqNum;

    /**
     * Optional hard coded starting sequence number for MCS claims. Optional is not Serializable, so
     * we have to store this as a nullable value. *
     */
    @Nullable private final Long startingMcsSeqNum;

    /** Determines if the DLQ should be processed for subsequent job runs. */
    private final boolean processDLQ;

    @Builder
    private Config(
        Duration runInterval,
        int batchSize,
        int writeThreads,
        @Nullable Long startingFissSeqNum,
        @Nullable Long startingMcsSeqNum,
        boolean processDLQ) {
      this.runInterval = Preconditions.checkNotNull(runInterval);
      this.batchSize = batchSize;
      this.writeThreads = writeThreads == 0 ? 1 : writeThreads;
      this.startingFissSeqNum = startingFissSeqNum;
      this.startingMcsSeqNum = startingMcsSeqNum;
      this.processDLQ = processDLQ;
      Preconditions.checkArgument(
          runInterval.toMillis() >= 1_000, "runInterval less than 1s: %s", runInterval);
      Preconditions.checkArgument(
          this.writeThreads >= 1, "writeThreads less than 1: %s", writeThreads);
      Preconditions.checkArgument(batchSize >= 1, "batchSize less than 1: %s", batchSize);
    }

    /**
     * Returns the configured starting FISS sequence number (if it exists) wrapped in an {@link
     * Optional}.
     *
     * @return The configured starting FISS sequence number wrapped in an {@link Optional}
     */
    public Optional<Long> getStartingFissSeqNum() {
      return Optional.ofNullable(startingFissSeqNum);
    }

    /**
     * Returns the configured starting MCS sequence number (if it exists) wrapped in an {@link
     * Optional}.
     *
     * @return The configured starting MCS sequence number wrapped in an {@link Optional}
     */
    public Optional<Long> getStartingMcsSeqNum() {
      return Optional.ofNullable(startingMcsSeqNum);
    }

    /**
     * Returns true if the job has been configured to process the DLQ, false otherwise.
     *
     * @return true if the job has been configured to process the DLQ, false otherwise.
     */
    public boolean shouldProcessDLQ() {
      return processDLQ;
    }
  }

  /**
   * Metrics are tested in unit tests so they need to be easily accessible from tests. Also this
   * class is used to write both MCS and FISS claims so the metric names need to include a claim
   * type to distinguish them.
   */
  @Getter
  @VisibleForTesting
  static class Metrics {
    /** Number of times the job has been called. */
    private final Meter calls;
    /** Number of calls that completed successfully. */
    private final Meter successes;
    /** Number of calls that ended in some sort of failure. */
    private final Meter failures;
    /** Number of objects that have been successfully processed. */
    private final Meter processed;

    private Metrics(MetricRegistry appMetrics, Class<?> jobClass) {
      final String base = jobClass.getSimpleName();
      calls = appMetrics.meter(MetricRegistry.name(base, "calls"));
      successes = appMetrics.meter(MetricRegistry.name(base, "successes"));
      failures = appMetrics.meter(MetricRegistry.name(base, "failures"));
      processed = appMetrics.meter(MetricRegistry.name(base, "processed"));
    }
  }
}
