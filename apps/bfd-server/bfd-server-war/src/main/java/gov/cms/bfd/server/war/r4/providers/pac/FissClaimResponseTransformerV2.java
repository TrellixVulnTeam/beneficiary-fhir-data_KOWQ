package gov.cms.bfd.server.war.r4.providers.pac;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.newrelic.api.agent.Trace;
import gov.cms.bfd.model.rda.RdaFissClaim;
import gov.cms.bfd.server.war.commons.BBCodingSystems;
import gov.cms.bfd.server.war.r4.providers.pac.common.AbstractTransformerV2;
import gov.cms.bfd.server.war.r4.providers.pac.common.FissTransformerV2;
import gov.cms.bfd.sharedutils.exceptions.BadCodeMonkeyException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.codesystems.ClaimType;

/** Transforms FISS/MCS instances into FHIR {@link ClaimResponse} resources. */
public class FissClaimResponseTransformerV2 extends AbstractTransformerV2 {

  private static final String METRIC_NAME =
      MetricRegistry.name(FissClaimResponseTransformerV2.class.getSimpleName(), "transform");

  /**
   * The known status codes and their associated {@link ClaimResponse.RemittanceOutcome} mappings
   */
  private static final Map<Character, ClaimResponse.RemittanceOutcome> STATUS_TO_OUTCOME =
      Map.ofEntries(
          Map.entry(' ', ClaimResponse.RemittanceOutcome.QUEUED),
          Map.entry('a', ClaimResponse.RemittanceOutcome.QUEUED),
          Map.entry('d', ClaimResponse.RemittanceOutcome.COMPLETE),
          Map.entry('f', ClaimResponse.RemittanceOutcome.PARTIAL),
          Map.entry('i', ClaimResponse.RemittanceOutcome.PARTIAL),
          Map.entry('m', ClaimResponse.RemittanceOutcome.PARTIAL),
          Map.entry('p', ClaimResponse.RemittanceOutcome.COMPLETE),
          Map.entry('r', ClaimResponse.RemittanceOutcome.COMPLETE),
          Map.entry('s', ClaimResponse.RemittanceOutcome.PARTIAL),
          Map.entry('t', ClaimResponse.RemittanceOutcome.PARTIAL),
          Map.entry('u', ClaimResponse.RemittanceOutcome.COMPLETE));

  private FissClaimResponseTransformerV2() {}

  /**
   * @param metricRegistry the {@link MetricRegistry} to use
   * @param claimEntity the FISS {@link RdaFissClaim} to transform
   * @return a FHIR {@link ClaimResponse} resource that represents the specified claim
   */
  @Trace
  static ClaimResponse transform(MetricRegistry metricRegistry, Object claimEntity) {
    if (!(claimEntity instanceof RdaFissClaim)) {
      throw new BadCodeMonkeyException();
    }

    try (Timer.Context ignored = metricRegistry.timer(METRIC_NAME).time()) {
      return transformClaim((RdaFissClaim) claimEntity);
    }
  }

  /**
   * Transforms an {@link RdaFissClaim} to a FHIR {@link ClaimResponse}.
   *
   * @param claimGroup the {@link RdaFissClaim} to transform
   * @return a FHIR {@link ClaimResponse} resource that represents the specified {@link
   *     RdaFissClaim}
   */
  private static ClaimResponse transformClaim(RdaFissClaim claimGroup) {
    ClaimResponse claim = new ClaimResponse();

    claim.setId("f-" + claimGroup.getDcn());
    claim.setContained(List.of(FissTransformerV2.getContainedPatient(claimGroup)));
    claim.getIdentifier().add(createClaimIdentifier(BBCodingSystems.FISS.DCN, claimGroup.getDcn()));
    claim.setExtension(getExtension(claimGroup));
    claim.setStatus(ClaimResponse.ClaimResponseStatus.ACTIVE);
    claim.setOutcome(getOutcome(claimGroup.getCurrStatus()));
    claim.setType(createCodeableConcept(ClaimType.INSTITUTIONAL));
    claim.setUse(ClaimResponse.Use.CLAIM);
    claim.setInsurer(new Reference().setIdentifier(new Identifier().setValue("CMS")));
    claim.setPatient(new Reference("#patient"));
    claim.setRequest(new Reference(String.format("Claim/f-%s", claimGroup.getDcn())));

    claim.setMeta(new Meta().setLastUpdated(Date.from(claimGroup.getLastUpdated())));
    claim.setCreated(new Date());

    return claim;
  }

  /**
   * Builds a list of {@link Extension} objects using data from the given {@link RdaFissClaim}.
   *
   * @param claimGroup The {@link RdaFissClaim} to pull associated data from.
   * @return A list of {@link Extension} objects build from the given {@link RdaFissClaim} data.
   */
  private static List<Extension> getExtension(RdaFissClaim claimGroup) {
    List<Extension> extensions = new ArrayList<>();
    addExtension(extensions, BBCodingSystems.FISS.CURR_STATUS, "" + claimGroup.getCurrStatus());
    addExtension(extensions, BBCodingSystems.FISS.RECD_DT_CYMD, claimGroup.getReceivedDate());
    addExtension(extensions, BBCodingSystems.FISS.CURR_TRAN_DT_CYMD, claimGroup.getCurrTranDate());

    return extensions;
  }

  /**
   * Maps the given status code to an associated {@link ClaimResponse.RemittanceOutcome}. Unknown
   * status codes are mapped to {@link ClaimResponse.RemittanceOutcome#PARTIAL}.
   *
   * @param statusCode The statusCode from the {@link RdaFissClaim}.
   * @return The {@link ClaimResponse.RemittanceOutcome} associated with the given status code.
   */
  private static ClaimResponse.RemittanceOutcome getOutcome(char statusCode) {
    return STATUS_TO_OUTCOME.getOrDefault(
        Character.toLowerCase(statusCode), ClaimResponse.RemittanceOutcome.PARTIAL);
  }
}
