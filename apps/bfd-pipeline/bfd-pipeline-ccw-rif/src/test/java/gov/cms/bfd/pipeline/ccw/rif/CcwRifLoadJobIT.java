package gov.cms.bfd.pipeline.ccw.rif;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.Bucket;
import gov.cms.bfd.model.rif.RifFileType;
import gov.cms.bfd.model.rif.samples.StaticRifResource;
import gov.cms.bfd.pipeline.PipelineTestUtils;
import gov.cms.bfd.pipeline.ccw.rif.extract.ExtractionOptions;
import gov.cms.bfd.pipeline.ccw.rif.extract.s3.DataSetManifest;
import gov.cms.bfd.pipeline.ccw.rif.extract.s3.DataSetManifest.DataSetManifestEntry;
import gov.cms.bfd.pipeline.ccw.rif.extract.s3.DataSetTestUtilities;
import gov.cms.bfd.pipeline.ccw.rif.extract.s3.MockDataSetMonitorListener;
import gov.cms.bfd.pipeline.ccw.rif.extract.s3.S3Utilities;
import gov.cms.bfd.pipeline.ccw.rif.extract.s3.task.S3TaskManager;
import java.net.URL;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Integration tests for {@link CcwRifLoadJob}. */
public final class CcwRifLoadJobIT {
  private static final Logger LOGGER = LoggerFactory.getLogger(CcwRifLoadJobIT.class);

  /**
   * Tests {@link CcwRifLoadJob} when run against an empty bucket.
   *
   * @throws Exception (exceptions indicate test failure)
   */
  @Test
  public void emptyBucketTest() throws Exception {
    AmazonS3 s3Client = S3Utilities.createS3Client(new ExtractionOptions("foo"));
    Bucket bucket = null;
    try {
      // Create the (empty) bucket to run against.
      bucket = DataSetTestUtilities.createTestBucket(s3Client);
      ExtractionOptions options = new ExtractionOptions(bucket.getName());
      LOGGER.info(
          "Bucket created: '{}:{}'",
          s3Client.getS3AccountOwner().getDisplayName(),
          bucket.getName());

      // Run the job.
      MockDataSetMonitorListener listener = new MockDataSetMonitorListener();
      S3TaskManager s3TaskManager =
          new S3TaskManager(
              PipelineTestUtils.get().getPipelineApplicationState().getMetrics(), options);
      CcwRifLoadJob ccwJob =
          new CcwRifLoadJob(
              PipelineTestUtils.get().getPipelineApplicationState().getMetrics(),
              options,
              s3TaskManager,
              listener);
      ccwJob.call();

      // Verify that no data sets were generated.
      assertEquals(1, listener.getNoDataAvailableEvents());
      assertEquals(0, listener.getDataEvents().size());
      assertEquals(0, listener.getErrorEvents().size());
    } finally {
      if (bucket != null) s3Client.deleteBucket(bucket.getName());
    }
  }

  /**
   * Tests {@link CcwRifLoadJob} when run against a bucket with a single data set.
   *
   * @throws Exception (exceptions indicate test failure)
   */
  @Test
  public void singleDataSetTest() throws Exception {
    validateLoadAtLocations(
        CcwRifLoadJob.S3_PREFIX_PENDING_DATA_SETS, CcwRifLoadJob.S3_PREFIX_COMPLETED_DATA_SETS);
  }

  /**
   * Tests {@link CcwRifLoadJob} when run against a bucket with a single data set within
   * Synthetic/Incoming.
   *
   * @throws Exception (exceptions indicate test failure)
   */
  @Test
  public void singleSyntheticDataSetTest() throws Exception {
    validateLoadAtLocations(
        CcwRifLoadJob.S3_PREFIX_PENDING_SYNTHETIC_DATA_SETS,
        CcwRifLoadJob.S3_PREFIX_COMPLETED_SYNTHETIC_DATA_SETS);
  }

  /**
   * Tests {@link CcwRifLoadJob} when run with data in the Synthetic/Incoming and Incoming folders.
   * Data should be read and moved into the respective Done and Synthetic/Done folders.
   *
   * @throws Exception (exceptions indicate test failure)
   */
  @Test
  public void multipleDataSetsWithSyntheticTest() throws Exception {
    AmazonS3 s3Client = S3Utilities.createS3Client(new ExtractionOptions("foo"));
    Bucket bucket = null;
    try {
      /*
       * Create the (empty) bucket to run against, and populate it with
       * two data sets.
       */
      bucket = DataSetTestUtilities.createTestBucket(s3Client);
      ExtractionOptions options =
          new ExtractionOptions(bucket.getName(), Optional.empty(), Optional.of(1));
      LOGGER.info(
          "Bucket created: '{}:{}'",
          s3Client.getS3AccountOwner().getDisplayName(),
          bucket.getName());

      DataSetManifest manifest =
          new DataSetManifest(
              Instant.now(),
              0,
              false,
              CcwRifLoadJob.S3_PREFIX_PENDING_DATA_SETS,
              CcwRifLoadJob.S3_PREFIX_COMPLETED_DATA_SETS,
              new DataSetManifestEntry("beneficiaries.rif", RifFileType.BENEFICIARY));
      DataSetManifest manifestSynthetic =
          new DataSetManifest(
              Instant.now().minus(1, ChronoUnit.DAYS),
              0,
              true,
              CcwRifLoadJob.S3_PREFIX_PENDING_DATA_SETS,
              CcwRifLoadJob.S3_PREFIX_COMPLETED_DATA_SETS,
              new DataSetManifestEntry("carrier.rif", RifFileType.CARRIER),
              new DataSetManifestEntry("inpatient.rif", RifFileType.INPATIENT));

      // Add files to each location the test wants them in
      putSampleFilesInTestBucket(
          s3Client,
          bucket,
          CcwRifLoadJob.S3_PREFIX_PENDING_DATA_SETS,
          manifest,
          List.of(StaticRifResource.SAMPLE_A_BENES.getResourceUrl()));
      putSampleFilesInTestBucket(
          s3Client,
          bucket,
          CcwRifLoadJob.S3_PREFIX_PENDING_SYNTHETIC_DATA_SETS,
          manifestSynthetic,
          List.of(
              StaticRifResource.SAMPLE_A_CARRIER.getResourceUrl(),
              StaticRifResource.SAMPLE_A_INPATIENT.getResourceUrl()));

      // Run the job.
      MockDataSetMonitorListener listener = new MockDataSetMonitorListener();
      S3TaskManager s3TaskManager =
          new S3TaskManager(
              PipelineTestUtils.get().getPipelineApplicationState().getMetrics(), options);
      CcwRifLoadJob ccwJob =
          new CcwRifLoadJob(
              PipelineTestUtils.get().getPipelineApplicationState().getMetrics(),
              options,
              s3TaskManager,
              listener);
      // Process both sets
      ccwJob.call();
      ccwJob.call();

      // Verify what was handed off to the DataSetMonitorListener.
      assertEquals(0, listener.getNoDataAvailableEvents());
      assertEquals(2, listener.getDataEvents().size());
      assertEquals(0, listener.getErrorEvents().size());

      /*
       * Verify that the datasets were moved to their respective locations.
       */
      DataSetTestUtilities.waitForBucketObjectCount(
          s3Client,
          bucket,
          CcwRifLoadJob.S3_PREFIX_PENDING_DATA_SETS,
          0,
          java.time.Duration.ofSeconds(10));
      DataSetTestUtilities.waitForBucketObjectCount(
          s3Client,
          bucket,
          CcwRifLoadJob.S3_PREFIX_PENDING_SYNTHETIC_DATA_SETS,
          0,
          java.time.Duration.ofSeconds(10));
      DataSetTestUtilities.waitForBucketObjectCount(
          s3Client,
          bucket,
          CcwRifLoadJob.S3_PREFIX_COMPLETED_DATA_SETS,
          1 + manifest.getEntries().size(),
          java.time.Duration.ofSeconds(10));
      DataSetTestUtilities.waitForBucketObjectCount(
          s3Client,
          bucket,
          CcwRifLoadJob.S3_PREFIX_COMPLETED_SYNTHETIC_DATA_SETS,
          1 + manifestSynthetic.getEntries().size(),
          java.time.Duration.ofSeconds(10));
      assertTrue(
          s3Client.doesObjectExist(
              bucket.getName(),
              CcwRifLoadJob.S3_PREFIX_COMPLETED_SYNTHETIC_DATA_SETS
                  + "/"
                  + manifestSynthetic.getTimestampText()
                  + "/0_manifest.xml"));
      assertTrue(
          s3Client.doesObjectExist(
              bucket.getName(),
              CcwRifLoadJob.S3_PREFIX_COMPLETED_SYNTHETIC_DATA_SETS
                  + "/"
                  + manifestSynthetic.getTimestampText()
                  + "/carrier.rif"));
      assertTrue(
          s3Client.doesObjectExist(
              bucket.getName(),
              CcwRifLoadJob.S3_PREFIX_COMPLETED_SYNTHETIC_DATA_SETS
                  + "/"
                  + manifestSynthetic.getTimestampText()
                  + "/inpatient.rif"));
      assertTrue(
          s3Client.doesObjectExist(
              bucket.getName(),
              CcwRifLoadJob.S3_PREFIX_COMPLETED_DATA_SETS
                  + "/"
                  + manifest.getTimestampText()
                  + "/0_manifest.xml"));
      assertTrue(
          s3Client.doesObjectExist(
              bucket.getName(),
              CcwRifLoadJob.S3_PREFIX_COMPLETED_DATA_SETS
                  + "/"
                  + manifest.getTimestampText()
                  + "/beneficiaries.rif"));

    } finally {
      if (bucket != null) DataSetTestUtilities.deleteObjectsAndBucket(s3Client, bucket);
    }
  }

  /**
   * Tests {@link CcwRifLoadJob} when run against an empty bucket.
   *
   * @throws Exception (exceptions indicate test failure)
   */
  @Test
  public void multipleDataSetsTest() throws Exception {
    AmazonS3 s3Client = S3Utilities.createS3Client(new ExtractionOptions("foo"));
    Bucket bucket = null;
    try {
      /*
       * Create the (empty) bucket to run against, and populate it with
       * two data sets.
       */
      bucket = DataSetTestUtilities.createTestBucket(s3Client);
      ExtractionOptions options =
          new ExtractionOptions(bucket.getName(), Optional.empty(), Optional.of(1));
      LOGGER.info(
          "Bucket created: '{}:{}'",
          s3Client.getS3AccountOwner().getDisplayName(),
          bucket.getName());
      DataSetManifest manifestA =
          new DataSetManifest(
              Instant.now().minus(1L, ChronoUnit.HOURS),
              0,
              true,
              CcwRifLoadJob.S3_PREFIX_PENDING_DATA_SETS,
              CcwRifLoadJob.S3_PREFIX_COMPLETED_DATA_SETS,
              new DataSetManifestEntry("beneficiaries.rif", RifFileType.BENEFICIARY));
      s3Client.putObject(DataSetTestUtilities.createPutRequest(bucket, manifestA));
      s3Client.putObject(
          DataSetTestUtilities.createPutRequest(
              bucket,
              manifestA,
              manifestA.getEntries().get(0),
              StaticRifResource.SAMPLE_A_BENES.getResourceUrl()));
      DataSetManifest manifestB =
          new DataSetManifest(
              manifestA.getTimestampText(),
              1,
              true,
              CcwRifLoadJob.S3_PREFIX_PENDING_DATA_SETS,
              CcwRifLoadJob.S3_PREFIX_COMPLETED_DATA_SETS,
              new DataSetManifestEntry("pde.rif", RifFileType.PDE));
      s3Client.putObject(DataSetTestUtilities.createPutRequest(bucket, manifestB));
      s3Client.putObject(
          DataSetTestUtilities.createPutRequest(
              bucket,
              manifestB,
              manifestB.getEntries().get(0),
              StaticRifResource.SAMPLE_A_BENES.getResourceUrl()));
      DataSetManifest manifestC =
          new DataSetManifest(
              Instant.now(),
              0,
              true,
              CcwRifLoadJob.S3_PREFIX_PENDING_DATA_SETS,
              CcwRifLoadJob.S3_PREFIX_COMPLETED_DATA_SETS,
              new DataSetManifestEntry("carrier.rif", RifFileType.CARRIER));
      s3Client.putObject(DataSetTestUtilities.createPutRequest(bucket, manifestC));
      s3Client.putObject(
          DataSetTestUtilities.createPutRequest(
              bucket,
              manifestC,
              manifestC.getEntries().get(0),
              StaticRifResource.SAMPLE_A_CARRIER.getResourceUrl()));

      // Run the job.
      MockDataSetMonitorListener listener = new MockDataSetMonitorListener();
      S3TaskManager s3TaskManager =
          new S3TaskManager(
              PipelineTestUtils.get().getPipelineApplicationState().getMetrics(), options);
      CcwRifLoadJob ccwJob =
          new CcwRifLoadJob(
              PipelineTestUtils.get().getPipelineApplicationState().getMetrics(),
              options,
              s3TaskManager,
              listener);
      ccwJob.call();

      // Verify what was handed off to the DataSetMonitorListener.
      assertEquals(0, listener.getNoDataAvailableEvents());
      assertEquals(1, listener.getDataEvents().size());
      assertEquals(manifestA.getTimestamp(), listener.getDataEvents().get(0).getTimestamp());
      assertEquals(
          manifestA.getEntries().size(), listener.getDataEvents().get(0).getFileEvents().size());
      assertEquals(0, listener.getErrorEvents().size());

      /*
       * Verify that the first data set was renamed and the second is
       * still there.
       */
      DataSetTestUtilities.waitForBucketObjectCount(
          s3Client,
          bucket,
          CcwRifLoadJob.S3_PREFIX_PENDING_DATA_SETS,
          1 + manifestB.getEntries().size() + 1 + manifestC.getEntries().size(),
          java.time.Duration.ofSeconds(10));
      DataSetTestUtilities.waitForBucketObjectCount(
          s3Client,
          bucket,
          CcwRifLoadJob.S3_PREFIX_COMPLETED_DATA_SETS,
          1 + manifestA.getEntries().size(),
          java.time.Duration.ofSeconds(10));
    } finally {
      if (bucket != null) DataSetTestUtilities.deleteObjectsAndBucket(s3Client, bucket);
    }
  }

  /**
   * Tests {@link CcwRifLoadJob} when run against a bucket with a single data set that should be
   * skipped (per {@link ExtractionOptions#getDataSetFilter()}).
   *
   * @throws Exception (exceptions indicate test failure)
   */
  @Test
  public void skipDataSetTest() throws Exception {
    AmazonS3 s3Client = S3Utilities.createS3Client(new ExtractionOptions("foo"));
    Bucket bucket = null;
    try {
      /*
       * Create the (empty) bucket to run against, and populate it with a
       * data set.
       */
      bucket = DataSetTestUtilities.createTestBucket(s3Client);
      ExtractionOptions options =
          new ExtractionOptions(bucket.getName(), Optional.of(RifFileType.PDE));
      LOGGER.info(
          "Bucket created: '{}:{}'",
          s3Client.getS3AccountOwner().getDisplayName(),
          bucket.getName());
      DataSetManifest manifest =
          new DataSetManifest(
              Instant.now(),
              0,
              true,
              CcwRifLoadJob.S3_PREFIX_PENDING_DATA_SETS,
              CcwRifLoadJob.S3_PREFIX_COMPLETED_DATA_SETS,
              new DataSetManifestEntry("beneficiaries.rif", RifFileType.BENEFICIARY),
              new DataSetManifestEntry("carrier.rif", RifFileType.CARRIER));
      s3Client.putObject(DataSetTestUtilities.createPutRequest(bucket, manifest));
      s3Client.putObject(
          DataSetTestUtilities.createPutRequest(
              bucket,
              manifest,
              manifest.getEntries().get(0),
              StaticRifResource.SAMPLE_A_BENES.getResourceUrl()));
      s3Client.putObject(
          DataSetTestUtilities.createPutRequest(
              bucket,
              manifest,
              manifest.getEntries().get(1),
              StaticRifResource.SAMPLE_A_CARRIER.getResourceUrl()));

      // Run the job.
      MockDataSetMonitorListener listener = new MockDataSetMonitorListener();
      S3TaskManager s3TaskManager =
          new S3TaskManager(
              PipelineTestUtils.get().getPipelineApplicationState().getMetrics(), options);
      CcwRifLoadJob ccwJob =
          new CcwRifLoadJob(
              PipelineTestUtils.get().getPipelineApplicationState().getMetrics(),
              options,
              s3TaskManager,
              listener);
      ccwJob.call();

      // Verify what was handed off to the DataSetMonitorListener.
      assertEquals(1, listener.getNoDataAvailableEvents());
      assertEquals(0, listener.getDataEvents().size());
      assertEquals(0, listener.getErrorEvents().size());

      // Verify that the data set was not renamed.
      DataSetTestUtilities.waitForBucketObjectCount(
          s3Client,
          bucket,
          CcwRifLoadJob.S3_PREFIX_PENDING_DATA_SETS,
          1 + manifest.getEntries().size(),
          java.time.Duration.ofSeconds(10));
      DataSetTestUtilities.waitForBucketObjectCount(
          s3Client,
          bucket,
          CcwRifLoadJob.S3_PREFIX_COMPLETED_DATA_SETS,
          0,
          java.time.Duration.ofSeconds(10));
    } finally {
      if (bucket != null) DataSetTestUtilities.deleteObjectsAndBucket(s3Client, bucket);
    }
  }

  /**
   * Tests {@link CcwRifLoadJob} when run against a bucket with a single data set that should be
   * skipped due to a future date.
   *
   * @throws Exception (exceptions indicate test failure)
   */
  @Test
  public void skipDataSetTestForFutureManifestDate() throws Exception {
    AmazonS3 s3Client = S3Utilities.createS3Client(new ExtractionOptions("foo"));
    Bucket bucket = null;
    try {
      /*
       * Create the (empty) bucket to run against, and populate it with a
       * data set.
       */
      bucket = DataSetTestUtilities.createTestBucket(s3Client);
      ExtractionOptions options = new ExtractionOptions(bucket.getName());
      LOGGER.info(
          "Bucket created: '{}:{}'",
          s3Client.getS3AccountOwner().getDisplayName(),
          bucket.getName());
      DataSetManifest manifest =
          new DataSetManifest(
              Instant.now().plus(3, ChronoUnit.DAYS),
              0,
              true,
              CcwRifLoadJob.S3_PREFIX_PENDING_DATA_SETS,
              CcwRifLoadJob.S3_PREFIX_COMPLETED_DATA_SETS,
              new DataSetManifestEntry("beneficiaries.rif", RifFileType.BENEFICIARY),
              new DataSetManifestEntry("carrier.rif", RifFileType.CARRIER));
      s3Client.putObject(DataSetTestUtilities.createPutRequest(bucket, manifest));
      s3Client.putObject(
          DataSetTestUtilities.createPutRequest(
              bucket,
              manifest,
              manifest.getEntries().get(0),
              StaticRifResource.SAMPLE_A_BENES.getResourceUrl()));
      s3Client.putObject(
          DataSetTestUtilities.createPutRequest(
              bucket,
              manifest,
              manifest.getEntries().get(1),
              StaticRifResource.SAMPLE_A_CARRIER.getResourceUrl()));

      // Run the job.
      MockDataSetMonitorListener listener = new MockDataSetMonitorListener();
      S3TaskManager s3TaskManager =
          new S3TaskManager(
              PipelineTestUtils.get().getPipelineApplicationState().getMetrics(), options);
      CcwRifLoadJob ccwJob =
          new CcwRifLoadJob(
              PipelineTestUtils.get().getPipelineApplicationState().getMetrics(),
              options,
              s3TaskManager,
              listener);
      ccwJob.call();

      // Verify what was handed off to the DataSetMonitorListener.
      assertEquals(1, listener.getNoDataAvailableEvents());
      assertEquals(0, listener.getDataEvents().size());
      assertEquals(0, listener.getErrorEvents().size());

      // Verify that the data set was not renamed.
      DataSetTestUtilities.waitForBucketObjectCount(
          s3Client,
          bucket,
          CcwRifLoadJob.S3_PREFIX_PENDING_DATA_SETS,
          1 + manifest.getEntries().size(),
          java.time.Duration.ofSeconds(10));
      DataSetTestUtilities.waitForBucketObjectCount(
          s3Client,
          bucket,
          CcwRifLoadJob.S3_PREFIX_COMPLETED_DATA_SETS,
          0,
          java.time.Duration.ofSeconds(10));
    } finally {
      if (bucket != null) DataSetTestUtilities.deleteObjectsAndBucket(s3Client, bucket);
    }
  }

  /**
   * Validate load given the input location to load files and output location to look for the files
   * once they're loaded.
   *
   * @param inputLocation the input location (bucket key) where files should be placed initially
   * @param expectedOutputLocation the expected output location (bucket key) where files are
   *     expected to be moved after processing
   * @throws Exception the exception
   */
  private void validateLoadAtLocations(String inputLocation, String expectedOutputLocation)
      throws Exception {
    AmazonS3 s3Client = S3Utilities.createS3Client(new ExtractionOptions("foo"));
    Bucket bucket = null;
    try {
      /*
       * Create the (empty) bucket to run against, and populate it with a
       * data set.
       */
      bucket = DataSetTestUtilities.createTestBucket(s3Client);
      ExtractionOptions options = new ExtractionOptions(bucket.getName());
      LOGGER.info(
          "Bucket created: '{}:{}'",
          s3Client.getS3AccountOwner().getDisplayName(),
          bucket.getName());

      DataSetManifest manifest =
          new DataSetManifest(
              Instant.now(),
              0,
              false,
              inputLocation,
              expectedOutputLocation,
              new DataSetManifestEntry("beneficiaries.rif", RifFileType.BENEFICIARY),
              new DataSetManifestEntry("carrier.rif", RifFileType.CARRIER));

      // Add files to each location the test wants them in
      putSampleFilesInTestBucket(
          s3Client,
          bucket,
          inputLocation,
          manifest,
          List.of(
              StaticRifResource.SAMPLE_A_BENES.getResourceUrl(),
              StaticRifResource.SAMPLE_A_CARRIER.getResourceUrl()));

      // Run the job.
      MockDataSetMonitorListener listener = new MockDataSetMonitorListener();
      S3TaskManager s3TaskManager =
          new S3TaskManager(
              PipelineTestUtils.get().getPipelineApplicationState().getMetrics(), options);
      CcwRifLoadJob ccwJob =
          new CcwRifLoadJob(
              PipelineTestUtils.get().getPipelineApplicationState().getMetrics(),
              options,
              s3TaskManager,
              listener);
      ccwJob.call();

      // Verify what was handed off to the DataSetMonitorListener.
      assertEquals(0, listener.getNoDataAvailableEvents());
      assertEquals(1, listener.getDataEvents().size());
      assertEquals(manifest.getTimestamp(), listener.getDataEvents().get(0).getTimestamp());
      assertEquals(
          manifest.getEntries().size(), listener.getDataEvents().get(0).getFileEvents().size());
      assertEquals(0, listener.getErrorEvents().size());

      // Verify that the data set was renamed.
      DataSetTestUtilities.waitForBucketObjectCount(
          s3Client, bucket, inputLocation, 0, java.time.Duration.ofSeconds(10));

      DataSetTestUtilities.waitForBucketObjectCount(
          s3Client,
          bucket,
          expectedOutputLocation,
          1 + manifest.getEntries().size(),
          java.time.Duration.ofSeconds(10));

    } finally {
      if (bucket != null) DataSetTestUtilities.deleteObjectsAndBucket(s3Client, bucket);
    }
  }

  /**
   * Put sample files in test specified bucket and key in s3.
   *
   * @param s3Client the s3 client
   * @param bucket the bucket to use for the test
   * @param location the key under which to put the file
   * @param manifest the manifest to use for the load files
   * @param resourcesToAdd the resource URLs to add to the bucket, see {@link StaticRifResource} for
   *     resource lists, should be in the order of the manifest
   */
  private void putSampleFilesInTestBucket(
      AmazonS3 s3Client,
      Bucket bucket,
      String location,
      DataSetManifest manifest,
      List<URL> resourcesToAdd) {
    s3Client.putObject(DataSetTestUtilities.createPutRequest(bucket, manifest, location));
    int index = 0;
    for (URL resource : resourcesToAdd) {
      s3Client.putObject(
          DataSetTestUtilities.createPutRequest(
              bucket, manifest, manifest.getEntries().get(index), resource, location));
      index++;
    }
  }
}
