package gov.cms.bfd.pipeline.ccw.rif.extract.s3;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import gov.cms.bfd.pipeline.ccw.rif.CcwRifLoadJob;
import gov.cms.bfd.pipeline.ccw.rif.DataSetManifestFactory;
import gov.cms.bfd.pipeline.ccw.rif.extract.ExtractionOptions;
import gov.cms.bfd.pipeline.ccw.rif.extract.s3.DataSetManifest.DataSetManifestId;
import gov.cms.bfd.pipeline.ccw.rif.extract.s3.task.S3TaskManager;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.bind.JAXBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

/** Represents and manages the queue of data sets in S3 to be processed. */
public final class DataSetQueue {
  private static final Logger LOGGER = LoggerFactory.getLogger(DataSetQueue.class);

  private final MetricRegistry appMetrics;
  private final ExtractionOptions options;
  private final S3TaskManager s3TaskManager;

  /**
   * The {@link DataSetManifest}s waiting to be processed, ordered by their {@link
   * DataSetManifestId} in ascending order such that the first element represents the {@link
   * DataSetManifest} that should be processed next.
   */
  private final SortedSet<DataSetManifest> manifestsToProcess;

  /**
   * Tracks the {@link DataSetManifest#getId()} values of the most recently processed data sets, to
   * ensure that the same data set isn't processed more than once.
   */
  private final Set<DataSetManifestId> recentlyProcessedManifests;

  /**
   * Tracks the {@link DataSetManifestId}s of data sets that are known to be invalid. Typically,
   * these are data sets from a new schema version that aren't supported yet. This may seem
   * unnecessary (i.e. "don't let admins push things until they're supported"), but it's proven to
   * be very useful, operationally.
   */
  private final Set<DataSetManifestId> knownInvalidManifests;

  private Integer completedManifestsCount;

  /**
   * Constructs a new {@link DataSetQueue} instance.
   *
   * @param appMetrics the {@link MetricRegistry} for the overall application
   * @param options the {@link ExtractionOptions} to use
   * @param s3TaskManager the {@link S3TaskManager} to use
   */
  public DataSetQueue(
      MetricRegistry appMetrics, ExtractionOptions options, S3TaskManager s3TaskManager) {
    this.appMetrics = appMetrics;
    this.options = options;
    this.s3TaskManager = s3TaskManager;

    this.manifestsToProcess = new TreeSet<>();
    this.recentlyProcessedManifests = new HashSet<>();
    this.knownInvalidManifests = new HashSet<>();
  }

  /**
   * Updates {@link #manifestsToProcess}, listing the manifests available in S3 right now, then
   * adding those that weren't found before and removing those that are no longer pending.
   */
  public void updatePendingDataSets() {
    // Find the pending manifests.
    Set<DataSetManifestId> manifestIdsPendingNow = listPendingManifests();

    /*
     * Add any newly discovered manifests to the list of those to be
     * processed. Ignore those that are already known to be invalid or
     * complete, and watch out for newly-discovered-to-be-invalid ones.
     */
    Set<DataSetManifestId> newManifests = new HashSet<>(manifestIdsPendingNow);
    newManifests.removeAll(knownInvalidManifests);
    newManifests.removeAll(recentlyProcessedManifests);
    newManifests.removeAll(
        manifestsToProcess.stream().map(DataSetManifest::getId).collect(Collectors.toSet()));
    // Add manifests from Incoming
    newManifests.forEach(
        manifestId ->
            addManifestToList(
                manifestId, manifestId.computeS3Key(CcwRifLoadJob.S3_PREFIX_PENDING_DATA_SETS)));
    // Add manifests from Synthetic/Incoming
    newManifests.forEach(
        manifestId ->
            addManifestToList(
                manifestId,
                manifestId.computeS3Key(CcwRifLoadJob.S3_PREFIX_PENDING_SYNTHETIC_DATA_SETS)));

    /*
     * Any manifests that weren't found have presumably been processed and
     * we should clean up the state that relates to them, to prevent memory
     * leaks.
     */
    for (Iterator<DataSetManifest> manifestsToProcessIterator = manifestsToProcess.iterator();
        manifestsToProcessIterator.hasNext(); ) {
      DataSetManifestId manifestId = manifestsToProcessIterator.next().getId();
      if (!manifestIdsPendingNow.contains(manifestId)) {
        manifestsToProcessIterator.remove();
        knownInvalidManifests.remove(manifestId);
        recentlyProcessedManifests.remove(manifestId);
        s3TaskManager.cleanupOldDataSet(manifestId);
      }
    }
  }

  /**
   * Adds a manifest to the list of manifests to load if it meets filtering criteria and is not a
   * future manifest.
   *
   * @param manifestId the manifest id
   * @param manifestS3Key the manifest s3 key
   */
  private void addManifestToList(
      DataSetManifest.DataSetManifestId manifestId, String manifestS3Key) {
    DataSetManifest manifest;

    // If the keyspace we're scanning doesnt exist, bail early
    if (!s3TaskManager.getS3Client().doesObjectExist(options.getS3BucketName(), manifestS3Key)) {
      LOGGER.warn(
          "Unable to find keyspace {} in bucket {} while scanning for manifests.",
          manifestS3Key,
          options.getS3BucketName());
      return;
    }

    try {
      manifest = readManifest(s3TaskManager.getS3Client(), options, manifestS3Key);
    } catch (JAXBException | SAXException e) {
      /*
       * We want to terminate the ETL load process if an invalid manifest was found
       * such as a incorrect version number
       */
      LOGGER.error(
          "Found data set with invalid manifest at '{}'. Load service will terminating. Error: {}",
          manifestS3Key,
          e.toString());
      knownInvalidManifests.add(manifestId);
      throw new RuntimeException(e);
    }

    // Skip future dates, so we can hold (synthetic) data to load in the future
    if (manifestId.isFutureManifest()) {
      // Don't log to avoid noise from hundreds of skipped pending future files
      return;
    }

    // Finally, ensure that the manifest passes the options filter.
    if (!options.getDataSetFilter().test(manifest)) {
      LOGGER.debug("Skipping data set that doesn't pass filter: {}", manifest.toString());
      return;
    }

    // Everything checks out. Add it to the list!
    manifestsToProcess.add(manifest);
  }

  /**
   * @return the {@link DataSetManifestId}s for the manifests that are found in S3 under the {@value
   *     CcwRifLoadJob#S3_PREFIX_PENDING_DATA_SETS} key prefix, sorted in expected processing order.
   */
  private Set<DataSetManifestId> listPendingManifests() {
    Timer.Context timerS3Scanning =
        appMetrics.timer(MetricRegistry.name(getClass().getSimpleName(), "s3Scanning")).time();
    LOGGER.debug("Scanning for data sets in S3...");
    Set<DataSetManifestId> manifestIds = new HashSet<>();

    /*
     * Request a list of all objects in the configured bucket and directory.
     * (In the results, we'll be looking for the oldest manifest file, if
     * any.)
     */
    ListObjectsV2Request s3BucketListRequest = new ListObjectsV2Request();
    s3BucketListRequest.setBucketName(options.getS3BucketName());
    if (options.getS3ListMaxKeys().isPresent())
      s3BucketListRequest.setMaxKeys(options.getS3ListMaxKeys().get());

    /*
     * S3 will return results in separate pages. Loop through all of the
     * pages, looking for manifests.
     */
    int completedManifestsCount = 0;
    ListObjectsV2Result s3ObjectListing;
    do {
      s3ObjectListing = s3TaskManager.getS3Client().listObjectsV2(s3BucketListRequest);

      for (S3ObjectSummary objectSummary : s3ObjectListing.getObjectSummaries()) {
        String key = objectSummary.getKey();
        if (CcwRifLoadJob.REGEX_PENDING_MANIFEST.matcher(key).matches()) {
          /*
           * We've got an object that *looks like* it might be a
           * manifest file. But we need to parse the key to ensure
           * that it starts with a valid timestamp.
           */
          DataSetManifestId manifestId = DataSetManifestId.parseManifestIdFromS3Key(key);
          if (manifestId != null) {
            manifestIds.add(manifestId);
          }
        } else if (CcwRifLoadJob.REGEX_COMPLETED_MANIFEST.matcher(key).matches()) {
          completedManifestsCount++;
        }
      }

      s3BucketListRequest.setContinuationToken(s3ObjectListing.getNextContinuationToken());
    } while (s3ObjectListing.isTruncated());

    this.completedManifestsCount = completedManifestsCount;

    LOGGER.debug("Scanned for data sets in S3. Found '{}'.", manifestsToProcess.size());
    timerS3Scanning.close();

    return manifestIds;
  }

  /**
   * @param s3Client the {@link AmazonS3} client to use
   * @param options the {@link ExtractionOptions} to use
   * @param manifestToProcessKey the {@link S3Object#getKey()} of the S3 object for the manifest to
   *     be read
   * @return the {@link DataSetManifest} that was contained in the specified S3 object
   * @throws JAXBException Any {@link JAXBException}s that are encountered will be bubbled up. These
   *     generally indicate that the {@link DataSetManifest} could not be parsed because its content
   *     was invalid in some way. Note: As of 2017-03-24, this has been observed multiple times in
   *     production, and care should be taken to account for its possibility.
   * @throws SAXException Any {@link SAXException}s that are encountered will be bubbled up. These
   *     generally indicate that the {@link DataSetManifest} could not be parsed because its content
   *     was invalid in some way. Note: As of 2017-03-24, this has been observed multiple times in
   *     production, and care should be taken to account for its possibility.
   */
  public static DataSetManifest readManifest(
      AmazonS3 s3Client, ExtractionOptions options, String manifestToProcessKey)
      throws JAXBException, SAXException {
    try (S3Object manifestObject =
        s3Client.getObject(options.getS3BucketName(), manifestToProcessKey)) {

      DataSetManifest manifest =
          DataSetManifestFactory.newInstance().parseManifest(manifestObject.getObjectContent());
      // Setup the manifest incoming/outgoing location
      if (manifestToProcessKey.contains(CcwRifLoadJob.S3_PREFIX_PENDING_SYNTHETIC_DATA_SETS)) {
        manifest.setManifestKeyIncomingLocation(
            CcwRifLoadJob.S3_PREFIX_PENDING_SYNTHETIC_DATA_SETS);
        manifest.setManifestKeyDoneLocation(CcwRifLoadJob.S3_PREFIX_COMPLETED_SYNTHETIC_DATA_SETS);
      } else {
        manifest.setManifestKeyIncomingLocation(CcwRifLoadJob.S3_PREFIX_PENDING_DATA_SETS);
        manifest.setManifestKeyDoneLocation(CcwRifLoadJob.S3_PREFIX_COMPLETED_DATA_SETS);
      }

      return manifest;

    } catch (AmazonServiceException e) {
      /*
       * This could likely be retried, but we don't currently support
       * that. For now, just go boom.
       */
      throw new RuntimeException("Error reading manifest: " + manifestToProcessKey, e);
    } catch (AmazonClientException e) {
      /*
       * This could likely be retried, but we don't currently support
       * that. For now, just go boom.
       */
      throw new RuntimeException("Error reading manifest: " + manifestToProcessKey, e);
    } catch (IOException e) {
      /*
       * This could likely be retried, but we don't currently support
       * that. For now, just go boom.
       */
      throw new RuntimeException("Error reading manifest: " + manifestToProcessKey, e);
    }
  }

  /**
   * @return the {@link Stream} that {@link QueuedDataSet}s should be pulled from, when requested
   */
  private Stream<DataSetManifest> getManifestsToProcess() {
    return manifestsToProcess.stream()
        .filter(manifest -> !recentlyProcessedManifests.contains(manifest.getId()));
  }

  /**
   * @return <code>false</code> if there is at least one pending {@link DataSetManifest} to process,
   *     <code>true</code> if not
   */
  public boolean isEmpty() {
    return getManifestsToProcess().count() == 0;
  }

  /** @return the {@link DataSetManifest} for the next data set that should be processed, if any */
  public Optional<DataSetManifest> getNextDataSetToProcess() {
    return getManifestsToProcess().findFirst();
  }

  /**
   * @return the {@link DataSetManifest} for the next-but-one data set that should be processed, if
   *     any
   */
  public Optional<DataSetManifest> getSecondDataSetToProcess() {
    return getManifestsToProcess().skip(1).findFirst();
  }

  /**
   * @return the count of {@link DataSetManifest}s found for data sets that need to be processed
   *     (including those known to be invalid)
   */
  public int getPendingManifestsCount() {
    return manifestsToProcess.size()
        + knownInvalidManifests.size()
        - recentlyProcessedManifests.size();
  }

  /**
   * @return the count of {@link DataSetManifest}s found for data sets that have already been
   *     processed
   */
  public Optional<Integer> getCompletedManifestsCount() {
    return completedManifestsCount == null
        ? Optional.empty()
        : Optional.of(recentlyProcessedManifests.size() + completedManifestsCount);
  }

  /**
   * Marks the specified {@link DataSetManifest} as processed, removing it from the list of pending
   * data sets in this {@link DataSetQueue}.
   *
   * @param manifest the {@link DataSetManifest} for the data set that has been successfully
   *     processed
   */
  public void markProcessed(DataSetManifest manifest) {
    this.recentlyProcessedManifests.add(manifest.getId());
  }
}
