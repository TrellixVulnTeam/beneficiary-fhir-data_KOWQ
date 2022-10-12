package gov.cms.bfd.server.war.r4.providers.pac.common;

import gov.cms.bfd.model.rda.RdaFissClaim;
import gov.cms.bfd.model.rda.RdaFissPayer;
import java.util.Map;
import java.util.Optional;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;

/** Class for performing common FISS based transformation logic */
public class FissTransformerV2 {

  /** The FISS specific gender mapping to use to map from RDA to FHIR. */
  private static final Map<String, Enumerations.AdministrativeGender> GENDER_MAP =
      Map.of(
          "m", Enumerations.AdministrativeGender.MALE,
          "f", Enumerations.AdministrativeGender.FEMALE,
          "u", Enumerations.AdministrativeGender.UNKNOWN);

  private FissTransformerV2() {}

  /**
   * Creates a {@link Patient} object using the given {@link RdaFissClaim} information.
   *
   * @param claimGroup The {@link RdaFissClaim} information to use to build the {@link Patient}
   *     object.
   * @return The constructed {@link Patient} object.
   */
  public static Resource getContainedPatient(RdaFissClaim claimGroup) {
    Optional<RdaFissPayer> benePayerOptional =
        claimGroup.getPayers().stream()
            .filter(p -> p.getPayerType() == RdaFissPayer.PayerType.BeneZ)
            .findFirst();

    Patient patient;

    if (benePayerOptional.isPresent()) {
      RdaFissPayer benePayer = benePayerOptional.get();

      patient =
          AbstractTransformerV2.getContainedPatient(
              claimGroup.getMbi(),
              new AbstractTransformerV2.PatientInfo(
                  benePayer.getBeneFirstName(),
                  benePayer.getBeneLastName(),
                  AbstractTransformerV2.ifNotNull(
                      benePayer.getBeneMidInit(), s -> s.charAt(0) + "."),
                  benePayer.getBeneDob(),
                  benePayer.getBeneSex(),
                  GENDER_MAP,
                  "max 10 chars of first",
                  "middle initial",
                  "max 15 chars of last"));
    } else {
      patient = AbstractTransformerV2.getContainedPatient(claimGroup.getMbi(), null);
    }

    return patient;
  }
}
