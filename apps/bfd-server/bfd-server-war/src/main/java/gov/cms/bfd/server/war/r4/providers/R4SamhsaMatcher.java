package gov.cms.bfd.server.war.r4.providers;

import gov.cms.bfd.sharedutils.exceptions.BadCodeMonkeyException;
import java.util.List;
import java.util.function.Predicate;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.ExplanationOfBenefit;
import org.hl7.fhir.r4.model.ExplanationOfBenefit.DiagnosisComponent;
import org.hl7.fhir.r4.model.ExplanationOfBenefit.ProcedureComponent;
import org.springframework.stereotype.Component;

/**
 * A {@link Predicate} that, when <code>true</code>, indicates that an {@link ExplanationOfBenefit}
 * (i.e. claim) is SAMHSA-related.
 *
 * <p>See <code>/bluebutton-data-server.git/dev/design-samhsa-filtering.md</code> for details on the
 * design of this feature.
 *
 * <p>This class is designed to be thread-safe, as it's expensive to construct and so should be used
 * as a singleton.
 */
@Component
public final class R4SamhsaMatcher extends BaseSamhsaMatcher<ExplanationOfBenefit> {

  /** @see java.util.function.Predicate#test(java.lang.Object) */
  @Override
  public boolean test(ExplanationOfBenefit eob) {
    ClaimTypeV2 claimType = TransformerUtilsV2.getClaimType(eob);

    boolean containsSamhsa = false;

    switch (TransformerUtilsV2.getClaimType(eob)) {
      case INPATIENT:
      case OUTPATIENT:
      case SNF:
        containsSamhsa = containsSamhsaIcdProcedueCode(eob.getProcedure());
      case CARRIER:
      case DME:
      case HHA:
      case HOSPICE:
        containsSamhsa =
            containsSamhsa
                || containsSamhsaIcdCode(eob.getDiagnosis())
                || containsSamhsaLineItems(eob.getItem());
      case PDE:
        // There are no SAMHSA fields in PDE claims
        break;
      default:
        throw new BadCodeMonkeyException("Unsupported claim type: " + claimType);
    }

    return containsSamhsa;
  }

  /**
   * @param procedure the {@link ProcedureComponent}s to check
   * @return <code>true</code> if any of the specified {@link ProcedureComponent}s match any of the
   *     {@link #icd9ProcedureCodes} or {@link #icd10ProcedureCodes} entries, <code>false</code> if
   *     they all do not
   */
  private boolean containsSamhsaIcdProcedueCode(List<ProcedureComponent> procedure) {
    return procedure.stream().anyMatch(this::isSamhsaIcdProcedure);
  }

  /**
   * @param diagnoses the {@link DiagnosisComponent}s to check
   * @return <code>true</code> if any of the specified {@link DiagnosisComponent}s match any of the
   *     {@link #icd9DiagnosisCodes} or {@link #icd10DiagnosisCodes} entries, <code>false</code> if
   *     they all do not
   */
  private boolean containsSamhsaIcdCode(List<DiagnosisComponent> diagnoses) {
    return diagnoses.stream().anyMatch(this::isSamhsaDiagnosis);
  }

  private boolean containsSamhsaLineItems(List<ExplanationOfBenefit.ItemComponent> items) {
    return items.stream().anyMatch(c -> containsSamhsaProcedureCode(c.getProductOrService()));
  }

  /**
   * @param procedure the {@link ProcedureComponent} to check
   * @return <code>true</code> if the specified {@link ProcedureComponent} matches one of the {@link
   *     BaseSamhsaMatcher#icd9ProcedureCodes} or {@link BaseSamhsaMatcher#icd10ProcedureCodes}
   *     entries, <code>false</code> if it does not
   */
  private boolean isSamhsaIcdProcedure(ProcedureComponent procedure) {
    try {
      return isSamhsaIcdProcedure(procedure.getProcedureCodeableConcept());
    } catch (FHIRException e) {
      /*
       * This will only be thrown if the ProcedureComponent doesn't have a
       * CodeableConcept, which isn't how we build ours.
       */
      throw new BadCodeMonkeyException(e);
    }
  }

  /**
   * @param diagnosis the {@link DiagnosisComponent} to check
   * @return <code>true</code> if the specified {@link DiagnosisComponent} matches one of the {@link
   *     BaseSamhsaMatcher#icd9DiagnosisCodes} or {@link BaseSamhsaMatcher#icd10DiagnosisCodes}, or
   *     {@link BaseSamhsaMatcher#drgCodes} entries, <code>
   *     false</code> if it does not
   */
  private boolean isSamhsaDiagnosis(DiagnosisComponent diagnosis) {
    try {
      return isSamhsaDiagnosis(diagnosis.getDiagnosisCodeableConcept())
          || isSamhsaPackageCode(diagnosis.getPackageCode());
    } catch (FHIRException e) {
      /*
       * This will only be thrown if the DiagnosisComponent doesn't have a
       * CodeableConcept, which isn't how we build ours.
       */
      throw new BadCodeMonkeyException(e);
    }
  }
}
