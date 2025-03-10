package gov.cms.bfd.pipeline.ccw.rif.extract.s3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gov.cms.bfd.model.rif.RifFileType;
import gov.cms.bfd.pipeline.ccw.rif.CcwRifLoadJob;
import gov.cms.bfd.pipeline.ccw.rif.DataSetManifestFactory;
import gov.cms.bfd.pipeline.ccw.rif.extract.s3.DataSetManifest.DataSetManifestEntry;
import gov.cms.bfd.pipeline.ccw.rif.extract.s3.DataSetManifest.DataSetManifestId;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoField;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.UnmarshalException;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/** Unit tests for {@link DataSetManifest}. */
public final class DataSetManifestTest {
  /**
   * Verifies that {@link DataSetManifest} can be unmarshalled, as expected; the sample-a XML does
   * not contain the syntheticData attribute whuch means that the data will not be treated as
   * synthetic data.
   *
   * @throws JAXBException (indicates test failure)
   */
  @Test
  public void jaxbUnmarshallingForSampleA() throws JAXBException, SAXException {
    InputStream manifestStream =
        Thread.currentThread().getContextClassLoader().getResourceAsStream("manifest-sample-a.xml");

    DataSetManifest manifest = DataSetManifestFactory.newInstance().parseManifest(manifestStream);

    assertNotNull(manifest);
    assertEquals(
        1994,
        LocalDateTime.ofInstant(manifest.getTimestamp(), ZoneId.systemDefault())
            .get(ChronoField.YEAR));
    assertEquals(1, manifest.getSequenceId());
    // xml did not contain the syntheticData attribute; therefore the isSyntheticData should be
    // false.
    assertEquals(false, manifest.isSyntheticData());
    assertEquals(2, manifest.getEntries().size());
    assertEquals("sample-a-beneficiaries.txt", manifest.getEntries().get(0).getName());
    assertEquals(RifFileType.BENEFICIARY, manifest.getEntries().get(0).getType());
  }

  /**
   * Verifies that {@link DataSetManifest} cannot be unmarshalled, as expected. The invalid-sample-a
   * XML defines the syntheticData with a value of "junk"; this should trigger a {@link
   * UnmarshalException} which contains a linked exception {@link SAXParseException},
   *
   * <p>The error messsage should identify the XML attribute in error and the value that it
   * attempted to use.
   *
   * <p>UnmarshalException (indicates test success)
   */
  @Test
  public void jaxbUnmarshallingForInvalidSampleA() {
    UnmarshalException thrown =
        assertThrows(
            UnmarshalException.class,
            () -> {
              InputStream manifestStream =
                  Thread.currentThread()
                      .getContextClassLoader()
                      .getResourceAsStream("manifest-invalid-sample-a.xml");

              DataSetManifestFactory.newInstance().parseManifest(manifestStream);
            },
            "UnmarshalException message was expected");

    SAXParseException saxParseException =
        assertInstanceOf(SAXParseException.class, thrown.getLinkedException());
    assertEquals(
        "cvc-datatype-valid.1.2.1: 'junk' is not a valid value for 'boolean'.",
        saxParseException.getLocalizedMessage());
  }

  /**
   * Verifies that {@link DataSetManifest} cannot be unmarshalled, as expected. The
   * malformed-sample-a XML is not well-formed; this should trigger a JAXBException.
   *
   * <p>JAXBException (indicates test success)
   */
  @Test
  public void jaxbUnmarshallingForMalformedSampleA() {
    assertThrows(
        JAXBException.class,
        () -> {
          InputStream manifestStream =
              Thread.currentThread()
                  .getContextClassLoader()
                  .getResourceAsStream("manifest-malformed-sample-a.xml");

          DataSetManifestFactory.newInstance().parseManifest(manifestStream);
        });
  }

  /**
   * Verifies that {@link DataSetManifest} can be unmarshalled, as expected. The sample XML document
   * used here was produced by Scott Koerselman on 2016-12-19.
   *
   * @throws JAXBException (indicates test failure)
   */
  @Test
  public void jaxbUnmarshallingForSampleB() throws JAXBException, SAXException {
    InputStream manifestStream =
        Thread.currentThread().getContextClassLoader().getResourceAsStream("manifest-sample-b.xml");
    DataSetManifest manifest = DataSetManifestFactory.newInstance().parseManifest(manifestStream);

    assertNotNull(manifest);
    assertNotNull(manifest.getTimestamp());
    assertEquals(
        2016,
        LocalDateTime.ofInstant(manifest.getTimestamp(), ZoneId.systemDefault())
            .get(ChronoField.YEAR));
    assertEquals(1, manifest.getSequenceId());
    assertEquals(false, manifest.isSyntheticData());
    assertEquals(9, manifest.getEntries().size());
    assertEquals("bene.txt", manifest.getEntries().get(0).getName());
    for (int i = 0; i < manifest.getEntries().size(); i++) {
      DataSetManifestEntry entry = manifest.getEntries().get(i);
      assertNotNull(entry, "Null entry: " + i);
      assertNotNull(entry.getName(), "Null entry name: " + i);
      assertNotNull(entry.getType(), "Null entry type: " + i);
    }
    assertEquals(RifFileType.BENEFICIARY, manifest.getEntries().get(0).getType());
  }

  /**
   * Verifies that {@link DataSetManifest} can be unmarshalled, as expected; the sample-d XML
   * contains the syntheticData attribute set to true which causes the data set to be treated as
   * synthetic data.
   *
   * @throws JAXBException (indicates test failure)
   */
  @Test
  public void jaxbUnmarshallingForSampleD() throws JAXBException, SAXException {
    InputStream manifestStream =
        Thread.currentThread().getContextClassLoader().getResourceAsStream("manifest-sample-d.xml");

    DataSetManifest manifest = DataSetManifestFactory.newInstance().parseManifest(manifestStream);

    assertNotNull(manifest);
    assertEquals(
        1994,
        LocalDateTime.ofInstant(manifest.getTimestamp(), ZoneId.systemDefault())
            .get(ChronoField.YEAR));
    assertEquals(1, manifest.getSequenceId());
    // xml did not contain the syntheticData attribute; therefore the isSyntheticData should be
    // false.
    assertEquals(true, manifest.isSyntheticData());
    assertEquals(2, manifest.getEntries().size());
    assertEquals("sample-a-beneficiaries.txt", manifest.getEntries().get(0).getName());
    assertEquals(RifFileType.BENEFICIARY, manifest.getEntries().get(0).getType());
  }

  /**
   * Verifies that {@link DataSetManifest} can be unmarshalled, as expected, even when the <code>
   * timestamp</code> attribute has unexpected leading whitespace. This is a regression test case
   * for <a href="http://issues.hhsdevcloud.us/browse/CBBD-207">CBBD-207: Invalid manifest timestamp
   * causes ETL service to fail</a>.
   *
   * @throws JAXBException (indicates test failure)
   */
  @Test
  public void jaxbUnmarshallingForTimestampsWithLeadingWhitespace()
      throws JAXBException, SAXException {
    InputStream manifestStream =
        Thread.currentThread().getContextClassLoader().getResourceAsStream("manifest-sample-c.xml");
    DataSetManifest manifest = DataSetManifestFactory.newInstance().parseManifest(manifestStream);

    assertNotNull(manifest);
    assertNotNull(manifest.getTimestamp());
    assertEquals(121212, manifest.getSequenceId());
  }

  /**
   * Verifies that {@link DataSetManifest} with a future date correctly returns {@link
   * DataSetManifestId#isFutureManifest} as {@code true}.
   *
   * @throws JAXBException failure reading in test code
   */
  @Test
  public void whenFutureTimestampExpectFutureManifestTrue() throws JAXBException, SAXException {
    InputStream manifestStream =
        Thread.currentThread().getContextClassLoader().getResourceAsStream("manifest-sample-e.xml");
    DataSetManifest manifest = DataSetManifestFactory.newInstance().parseManifest(manifestStream);

    assertTrue(manifest.getId().isFutureManifest());
  }

  /**
   * Verifies that {@link DataSetManifest} with a past date correctly returns {@link
   * DataSetManifestId#isFutureManifest} as {@code false}.
   *
   * @throws JAXBException failure reading in test code
   */
  @Test
  public void whenPastTimestampExpectFutureManifestFalse() throws JAXBException, SAXException {
    InputStream manifestStream =
        Thread.currentThread().getContextClassLoader().getResourceAsStream("manifest-sample-a.xml");
    DataSetManifest manifest = DataSetManifestFactory.newInstance().parseManifest(manifestStream);

    assertFalse(manifest.getId().isFutureManifest());
  }

  /**
   * Verifies that {@link DataSetManifestId}s can be round-tripped, as expected. A regression test
   * case for <a href="http://issues.hhsdevcloud.us/browse/CBBD-298">CBBD-298: Error reading some
   * data set manifests in S3: "AmazonS3Exception: The specified key does not exist"</a>.
   */
  @Test
  public void manifestIdRoundtrip() {
    String s3Key =
        CcwRifLoadJob.S3_PREFIX_PENDING_DATA_SETS + "/2017-07-11T00:00:00.000Z/1_manifest.xml";
    DataSetManifestId manifestId = DataSetManifestId.parseManifestIdFromS3Key(s3Key);

    assertEquals(s3Key, manifestId.computeS3Key(CcwRifLoadJob.S3_PREFIX_PENDING_DATA_SETS));
  }

  /**
   * Verifies that {@link DataSetManifestId}s can be round-tripped as expected when using the
   * synthetic load key.
   */
  @Test
  public void manifestSyntheticIdRoundtrip() {
    String s3Key =
        CcwRifLoadJob.S3_PREFIX_PENDING_SYNTHETIC_DATA_SETS
            + "/2017-07-11T00:00:00.000Z/1_manifest.xml";
    DataSetManifestId manifestId = DataSetManifestId.parseManifestIdFromS3Key(s3Key);

    assertEquals(
        s3Key, manifestId.computeS3Key(CcwRifLoadJob.S3_PREFIX_PENDING_SYNTHETIC_DATA_SETS));
  }

  /**
   * Just a simple little app that will use JAXB to marshall a sample {@link DataSetManifest} to
   * XML. This was used as the basis for the test resources used in these tests.
   *
   * @param args (not used)
   * @throws JAXBException (programmer error)
   */
  public static void main(String[] args) throws JAXBException {
    DataSetManifest manifest =
        new DataSetManifest(
            Instant.now(),
            0,
            true,
            CcwRifLoadJob.S3_PREFIX_PENDING_DATA_SETS,
            CcwRifLoadJob.S3_PREFIX_COMPLETED_DATA_SETS,
            new DataSetManifestEntry("foo.xml", RifFileType.BENEFICIARY),
            new DataSetManifestEntry("bar.xml", RifFileType.PDE));

    JAXBContext jaxbContext = JAXBContext.newInstance(DataSetManifest.class);
    Marshaller jaxbMarshaller = jaxbContext.createMarshaller();

    jaxbMarshaller.marshal(manifest, System.out);
  }
}
