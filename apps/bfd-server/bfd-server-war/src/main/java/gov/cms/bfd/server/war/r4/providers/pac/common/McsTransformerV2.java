package gov.cms.bfd.server.war.r4.providers.pac.common;

import gov.cms.bfd.model.rda.RdaMcsClaim;
import java.util.Map;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;

/** Class for performing common MCS based transformation logic */
public class McsTransformerV2 {

  /** The MCS specific gender mapping to use to map from RDA to FHIR. */
  private static final Map<String, Enumerations.AdministrativeGender> GENDER_MAP =
      Map.of(
          "m", Enumerations.AdministrativeGender.MALE,
          "f", Enumerations.AdministrativeGender.FEMALE,
          "o", Enumerations.AdministrativeGender.UNKNOWN);

  private McsTransformerV2() {}

  /**
   * Creates a {@link Patient} object using the given {@link RdaMcsClaim} information.
   *
   * @param claimGroup The {@link RdaMcsClaim} information to use to build the {@link Patient}
   *     object.
   * @return The constructed {@link Patient} object.
   */
  public static Resource getContainedPatient(RdaMcsClaim claimGroup) {
    AbstractTransformerV2.PatientInfo patientInfo =
        new AbstractTransformerV2.PatientInfo(
            AbstractTransformerV2.ifNotNull(claimGroup.getIdrBeneFirstInit(), s -> s + "."),
            claimGroup.getIdrBeneLast_1_6(),
            AbstractTransformerV2.ifNotNull(claimGroup.getIdrBeneMidInit(), s -> s + "."),
            null, // MCS claims don't contain dob
            claimGroup.getIdrBeneSex(),
            GENDER_MAP,
            "first initial",
            "middle initial",
            "max 6 chars of last");

    return AbstractTransformerV2.getContainedPatient(claimGroup.getIdrClaimMbi(), patientInfo);
  }
}
