package gov.hhs.cms.bluebutton.datapipeline.rif.extract.s3;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.DeleteObjectsRequest.KeyVersion;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import gov.hhs.cms.bluebutton.datapipeline.rif.extract.s3.DataSetManifest.DataSetManifestEntry;
import gov.hhs.cms.bluebutton.datapipeline.rif.model.RifFilesEvent;

/**
 * <p>
 * Acts as a worker {@link Runnable} for {@link DataSetMonitor}. It is expected
 * that this will be run on a repeating basis, via a
 * {@link ScheduledExecutorService} .
 * </p>
 * <p>
 * When executed via {@link #run()}, the {@link DataSetMonitorWorker} will scan
 * the specified Amazon S3 bucket. It will look for <code>manifest.xml</code>
 * objects/files and select the oldest one available. If such a manifest is
 * found, it will then wait for all of the objects in the data set represented
 * by it to become available. Once they're all available, it will kick off the
 * processing of the data set, and block until that processing has completed.
 * </p>
 */
final class DataSetMonitorWorker implements Runnable {
	private static final Logger LOGGER = LoggerFactory.getLogger(DataSetMonitorWorker.class);

	/**
	 * The directory name that pending/incoming RIF data sets will be pulled
	 * from in S3.
	 */
	public static final String S3_PREFIX_PENDING_DATA_SETS = "Incoming";

	/**
	 * The directory name that completed/done RIF data sets will be moved to in
	 * S3.
	 */
	public static final String S3_PREFIX_COMPLETED_DATA_SETS = "Done";

	private static final Pattern REGEX_PENDING_MANIFEST = Pattern
			.compile("^" + S3_PREFIX_PENDING_DATA_SETS + "\\/(.*)\\/manifest\\.xml$");

	private final String bucketName;
	private final DataSetMonitorListener listener;
	private final AmazonS3 s3Client = new AmazonS3Client(new DefaultAWSCredentialsProviderChain());

	/**
	 * Tracks the {@link DataSetManifest#getTimestamp()} values of the most
	 * recently processed data sets, to ensure that the same data set isn't
	 * processed more than once. It's constrained to a fixed number of items, to
	 * keep it from becoming a memory leak.
	 */
	private CircularFifoQueue<Instant> recentlyProcessedManifests;

	/**
	 * Constructs a new {@link DataSetMonitorWorker} instance.
	 * 
	 * @param bucketName
	 *            the name of the AWS S3 bucket to monitor
	 * @param listener
	 *            the {@link DataSetMonitorListener} to send events to
	 */
	public DataSetMonitorWorker(String bucketName, DataSetMonitorListener listener) {
		this.bucketName = bucketName;
		this.listener = listener;
		this.recentlyProcessedManifests = new CircularFifoQueue<>(10);
	}

	/**
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		LOGGER.info("Scanning for data sets to process...");

		/*
		 * Request a list of all objects in the configured bucket and directory.
		 * (In the results, we'll be looking for the oldest manifest file, if
		 * any.)
		 */
		ListObjectsRequest bucketListRequest = new ListObjectsRequest();
		bucketListRequest.setBucketName(bucketName);

		/*
		 * S3 will return results in separate pages. Loop through all of the
		 * pages, looking for the oldest manifest file that is available.
		 */
		String manifestToProcessKey = null;
		ObjectListing objectListing = s3Client.listObjects(bucketListRequest);
		do {
			for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
				String key = objectSummary.getKey();
				if (REGEX_PENDING_MANIFEST.matcher(key).matches()) {
					/*
					 * We've got an object that *looks like* it might be a
					 * manifest file. But we also need to ensure that it starts
					 * with a valid timestamp, and if so, check to see if that
					 * timestamp is the oldest one we've encountered so far. If
					 * so, we mark it as the "current oldest" and continue
					 * looking through the other keys.
					 */
					Instant dataSetTimestamp = parseDataSetTimestamp(key);
					if (dataSetTimestamp == null)
						continue;

					// Don't process the same data set more than once.
					if (recentlyProcessedManifests.contains(dataSetTimestamp)) {
						LOGGER.debug("Skipping already-processed data set: {}", dataSetTimestamp);
						continue;
					}

					if (manifestToProcessKey == null)
						manifestToProcessKey = key;
					else if (dataSetTimestamp.compareTo(parseDataSetTimestamp(manifestToProcessKey)) < 0)
						manifestToProcessKey = key;
				}
			}

			objectListing = s3Client.listNextBatchOfObjects(objectListing);
		} while (objectListing.isTruncated());

		// If no manifest was found, we're done (until next time).
		if (manifestToProcessKey == null) {
			LOGGER.info("No data sets to process found.");
			listener.noDataAvailable();
			return;
		}

		// We've found the oldest manifest. Now go download and parse it.
		DataSetManifest dataSetManifest = readManifest(manifestToProcessKey);
		LOGGER.info("Found data set to process at '{}': '{}'. Waiting for it to finish uploading...",
				manifestToProcessKey, dataSetManifest.toString());

		/*
		 * We've got a dataset to process. However, it might still be uploading
		 * to S3, so we need to wait for that to complete before we start
		 * processing it.
		 */
		boolean dataSetComplete = false;
		do {
			if (dataSetIsAvailable(dataSetManifest))
				dataSetComplete = true;

			/*
			 * We're very patient here, so we keep looping, but it's prudent to
			 * pause between each iteration. TODO should eventually time out,
			 * once we know how long transfers might take
			 */
			try {
				Thread.sleep(1000 * 1);
			} catch (InterruptedException e) {
				/*
				 * Many Java applications use InterruptedExceptions to signal
				 * that a thread should stop what it's doing ASAP. This app
				 * doesn't, so this is unexpected, and accordingly, we don't
				 * know what to do. Safest bet is to blow up.
				 */
				throw new RuntimeException(e);
			}
		} while (!dataSetComplete);

		/*
		 * Huzzah! We've got a data set to process and we've verified it's all
		 * there and ready to go. Now we can hand that off to the
		 * DataSetMonitorListener, to do the *real* work of actually processing
		 * that data set. It's important that we block until it's completed, in
		 * order to ensure that we don't end up processing multiple data sets in
		 * parallel (which would lead to data consistency problems).
		 */
		LOGGER.info("Data set finished uploading and ready to process.");
		Set<S3RifFile> rifFiles = dataSetManifest.getEntries().stream().map(e -> {
			String key = String.format("%s/%s/%s", S3_PREFIX_PENDING_DATA_SETS,
					DateTimeFormatter.ISO_INSTANT.format(dataSetManifest.getTimestamp()), e.getName());
			return new S3RifFile(s3Client, e.getType(), new GetObjectRequest(bucketName, key));
		}).collect(Collectors.toSet());
		RifFilesEvent rifFilesEvent = new RifFilesEvent(dataSetManifest.getTimestamp(), new HashSet<>(rifFiles));
		listener.dataAvailable(rifFilesEvent);

		/*
		 * Now that the data set has been processed, we need to ensure that we
		 * don't end up processing it again. We ensure this two ways: 1) we keep
		 * a list of the data sets most recently processed, and 2) we rename the
		 * S3 objects that comprise that data set. (#1 is required as S3
		 * deletes/moves are only *eventually* consistent, so #2 may not take
		 * effect right away.)
		 */
		rifFiles.stream().forEach(f -> f.cleanupTempFile());
		recentlyProcessedManifests.add(dataSetManifest.getTimestamp());
		markDataSetCompleteInS3(dataSetManifest);
	}

	/**
	 * @param key
	 *            the S3 object key of a manifest file
	 * @return the timestamp of the data set represented by the specified
	 *         manifest object key
	 */
	private static Instant parseDataSetTimestamp(String key) {
		Matcher manifestKeyMatcher = REGEX_PENDING_MANIFEST.matcher(key);
		manifestKeyMatcher.matches();

		String dataSetId = manifestKeyMatcher.group(1);

		try {
			Instant dataSetTimestamp = Instant.parse(dataSetId);
			return dataSetTimestamp;
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	/**
	 * @param manifestToProcessKey
	 *            the {@link S3Object#getKey()} of the S3 object for the
	 *            manifest to be read
	 * @return the {@link DataSetManifest} that was contained in the specified
	 *         S3 object
	 */
	private DataSetManifest readManifest(String manifestToProcessKey) {
		try (S3Object manifestObject = s3Client.getObject(bucketName, manifestToProcessKey)) {
			JAXBContext jaxbContext = JAXBContext.newInstance(DataSetManifest.class);
			Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();

			DataSetManifest manifest = (DataSetManifest) jaxbUnmarshaller.unmarshal(manifestObject.getObjectContent());

			return manifest;
		} catch (JAXBException e) {
			// This is not a recoverable error. Stop the world.
			throw new RuntimeException(e);
		} catch (AmazonServiceException e) {
			/*
			 * This could likely be retried, but we don't currently support
			 * that. For now, just go boom.
			 */
			throw new RuntimeException(e);
		} catch (AmazonClientException e) {
			/*
			 * This could likely be retried, but we don't currently support
			 * that. For now, just go boom.
			 */
			throw new RuntimeException(e);
		} catch (IOException e) {
			/*
			 * This could likely be retried, but we don't currently support
			 * that. For now, just go boom.
			 */
			throw new RuntimeException(e);
		}
	}

	/**
	 * @param manifest
	 *            the {@link DataSetManifest} that lists the objects to verify
	 *            the presence of
	 * @return <code>true</code> if all of the objects listed in the specified
	 *         manifest can be found in S3, <code>false</code> if not
	 */
	private boolean dataSetIsAvailable(DataSetManifest manifest) {
		/*
		 * There are two ways to do this: 1) list all the objects in the data
		 * set and verify the ones we're looking for are there after, or 2) try
		 * to grab the metadata for each object. Option #2 *should* be simpler,
		 * but isn't, because each missing object will result in an exception.
		 * Exceptions-as-control-flow is a poor design choice, so we'll go with
		 * option #1.
		 */

		String dataSetKeyPrefix = String.format("%s/%s/", S3_PREFIX_PENDING_DATA_SETS,
				DateTimeFormatter.ISO_INSTANT.format(manifest.getTimestamp()));

		ListObjectsRequest bucketListRequest = new ListObjectsRequest();
		bucketListRequest.setBucketName(bucketName);
		bucketListRequest.setPrefix(dataSetKeyPrefix);

		Set<String> dataSetObjectNames = new HashSet<>();
		ObjectListing objectListing = s3Client.listObjects(bucketListRequest);
		do {
			/*
			 * Pull the object names from the keys that were returned, by
			 * stripping the timestamp prefix and slash from each of them.
			 */
			Set<String> namesForObjectsInPage = objectListing.getObjectSummaries().stream().map(s -> s.getKey())
					.peek(s -> LOGGER.debug("Found object: '{}'", s)).map(k -> k.substring(dataSetKeyPrefix.length()))
					.collect(Collectors.toSet());
			dataSetObjectNames.addAll(namesForObjectsInPage);

			// On to the next page! (If any.)
			objectListing = s3Client.listNextBatchOfObjects(objectListing);
		} while (objectListing.isTruncated());

		for (DataSetManifestEntry manifestEntry : manifest.getEntries()) {
			if (!dataSetObjectNames.contains(manifestEntry.getName()))
				return false;
		}

		return true;
	}

	/**
	 * Deletes the (now complete) data set in S3, after copying it to a
	 * "<code>Done</code>" folder in the same bucket.
	 * 
	 * @param dataSetManifest
	 *            the {@link DataSetManifest} of the data set to mark as
	 *            complete
	 */
	private void markDataSetCompleteInS3(DataSetManifest dataSetManifest) {
		/*
		 * S3 doesn't support batch/transactional operations, or an atomic move
		 * operation. Instead, we have to first copy all of the objects to their
		 * new location, and then remove the old objects. If something blows up
		 * in the middle of this method, orphaned S3 object WILL be created.
		 * That's an unlikely enough occurrence, though, that we're not going to
		 * engineer around it right now.
		 */

		// First, get a list of all the object keys to work on.
		List<String> s3KeySuffixesToMove = dataSetManifest.getEntries()
				.stream().map(e -> String.format("%s/%s",
						DateTimeFormatter.ISO_INSTANT.format(dataSetManifest.getTimestamp()), e.getName()))
				.collect(Collectors.toList());
		s3KeySuffixesToMove.add(
				String.format("%s/manifest.xml", DateTimeFormatter.ISO_INSTANT.format(dataSetManifest.getTimestamp())));

		/*
		 * Then, loop through each of those objects and copy them (S3 has no
		 * bulk copy operation).
		 */
		for (String s3KeySuffixToMove : s3KeySuffixesToMove) {
			String sourceKey = String.format("%s/%s", S3_PREFIX_PENDING_DATA_SETS, s3KeySuffixToMove);
			String targetKey = String.format("%s/%s", S3_PREFIX_COMPLETED_DATA_SETS, s3KeySuffixToMove);

			/*
			 * The S3 API will automatically ensure that the copied object is
			 * encrypted with the same key (if any) as the source object.
			 */
			s3Client.copyObject(bucketName, sourceKey, bucketName, targetKey);
		}
		LOGGER.debug("Data set copied in S3 (step 1 of move).");

		DeleteObjectsRequest deleteObjectsRequest = new DeleteObjectsRequest(bucketName);
		deleteObjectsRequest
				.setKeys(s3KeySuffixesToMove.stream().map(k -> String.format("%s/%s", S3_PREFIX_PENDING_DATA_SETS, k))
						.map(k -> new KeyVersion(k)).collect(Collectors.toList()));
		s3Client.deleteObjects(deleteObjectsRequest);
		LOGGER.debug("Data set deleted in S3 (step 2 of move).");

		LOGGER.info("Data set renamed in S3, now that processing is complete.");
	}
}