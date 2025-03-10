package gov.cms.bfd.server.war.stu3.providers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.codahale.metrics.MetricRegistry;
import gov.cms.bfd.data.fda.lookup.FdaDrugCodeDisplayLookup;
import gov.cms.bfd.model.codebook.data.CcwCodebookVariable;
import gov.cms.bfd.model.rif.CarrierClaim;
import gov.cms.bfd.model.rif.DMEClaim;
import gov.cms.bfd.model.rif.HHAClaim;
import gov.cms.bfd.model.rif.HospiceClaim;
import gov.cms.bfd.model.rif.InpatientClaim;
import gov.cms.bfd.model.rif.OutpatientClaim;
import gov.cms.bfd.model.rif.PartDEvent;
import gov.cms.bfd.model.rif.RifRecordBase;
import gov.cms.bfd.model.rif.SNFClaim;
import gov.cms.bfd.model.rif.samples.StaticRifResourceGroup;
import gov.cms.bfd.server.war.ServerTestUtils;
import gov.cms.bfd.server.war.commons.CCWUtils;
import gov.cms.bfd.server.war.commons.IcdCode;
import gov.cms.bfd.server.war.commons.TransformerConstants;
import gov.cms.bfd.server.war.commons.TransformerContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.ExplanationOfBenefit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Verifies that transformations that contain SAMHSA codes are filtered as expected. */
public class SamhsaMatcherFromClaimTransformerTest {

  private Stu3EobSamhsaMatcher samhsaMatcher;
  private static final String DRG_SYSTEM =
      CCWUtils.calculateVariableReferenceUrl(CcwCodebookVariable.CLM_DRG_CD);
  private static final String BLACKLISTED_HCPCS_CODE = "G2215";
  private static final String NON_SAMHSA_HCPCS_CODE = "11111";
  private static final String BLACKLISTED_IC9_DIAGNOSIS_CODE = "291.0";
  private static final String BLACKLISTED_IC10_DIAGNOSIS_CODE = "F10.10";
  private static final String BLACKLISTED_IC9_PROCEDURE_CODE = "94.45";
  private static final String BLACKLISTED_IC10_PROCEDURE_CODE = "HZ2ZZZZ";
  private static final String NON_BLACKLISTED_IC_CODE = "111111";
  private static final String BLACKLISTED_DRG_DIAGNOSIS_CODE = "522";
  private static final String NON_BLACKLISTED_DRG_DIAGNOSIS_CODE = "1111111";

  private static final String PART_D_EVENT_CLAIM = "PDE";
  private static final String DME_CLAIM = "DME";
  private static final String HHA_CLAIM = "HHA";
  private static final String HOSPICE_CLAIM = "HOSPICE";
  private static final String CARRIER_CLAIM = "CARRIER";

  /** Sets up the test. */
  @BeforeEach
  public void setup() {
    samhsaMatcher = new Stu3EobSamhsaMatcher();
  }

  /**
   * Data collection.
   *
   * @return the collection
   */
  public static Stream<Arguments> data() {

    // Load and transform the various claim types for testing

    ExplanationOfBenefit inpatientEob =
        InpatientClaimTransformer.transform(
            new TransformerContext(
                new MetricRegistry(),
                Optional.empty(),
                FdaDrugCodeDisplayLookup.createDrugCodeLookupForTesting()),
            getClaim(InpatientClaim.class));

    String inpatientClaimType = TransformerUtils.getClaimType(inpatientEob).toString();

    ExplanationOfBenefit outpatientEob =
        OutpatientClaimTransformer.transform(
            new TransformerContext(
                new MetricRegistry(),
                Optional.empty(),
                FdaDrugCodeDisplayLookup.createDrugCodeLookupForTesting()),
            getClaim(OutpatientClaim.class));

    String outpatientClaimType = TransformerUtils.getClaimType(outpatientEob).toString();

    ExplanationOfBenefit dmeEob =
        DMEClaimTransformer.transform(
            new TransformerContext(
                new MetricRegistry(),
                Optional.empty(),
                FdaDrugCodeDisplayLookup.createDrugCodeLookupForTesting()),
            getClaim(DMEClaim.class));

    String dmeClaimType = TransformerUtils.getClaimType(dmeEob).toString();

    ExplanationOfBenefit hhaEob =
        HHAClaimTransformer.transform(
            new TransformerContext(
                new MetricRegistry(),
                Optional.empty(),
                FdaDrugCodeDisplayLookup.createDrugCodeLookupForTesting()),
            getClaim(HHAClaim.class));
    String hhaClaimType = TransformerUtils.getClaimType(hhaEob).toString();

    ExplanationOfBenefit hospiceEob =
        HospiceClaimTransformer.transform(
            new TransformerContext(
                new MetricRegistry(),
                Optional.empty(),
                FdaDrugCodeDisplayLookup.createDrugCodeLookupForTesting()),
            getClaim(HospiceClaim.class));
    String hospiceClaimType = TransformerUtils.getClaimType(hospiceEob).toString();

    ExplanationOfBenefit snfEob =
        SNFClaimTransformer.transform(
            new TransformerContext(
                new MetricRegistry(),
                Optional.empty(),
                FdaDrugCodeDisplayLookup.createDrugCodeLookupForTesting()),
            getClaim(SNFClaim.class));
    String snfClaimType = TransformerUtils.getClaimType(snfEob).toString();

    ExplanationOfBenefit carrierEob =
        CarrierClaimTransformer.transform(
            new TransformerContext(
                new MetricRegistry(),
                Optional.empty(),
                FdaDrugCodeDisplayLookup.createDrugCodeLookupForTesting()),
            getClaim(CarrierClaim.class));
    String carrierClaimType = TransformerUtils.getClaimType(carrierEob).toString();

    ExplanationOfBenefit pdeEob =
        PartDEventTransformer.transform(
            new TransformerContext(
                new MetricRegistry(),
                Optional.empty(),
                FdaDrugCodeDisplayLookup.createDrugCodeLookupForTesting()),
            getClaim(PartDEvent.class));

    String pdeClaimType = TransformerUtils.getClaimType(pdeEob).toString();
    return Stream.of(
        arguments(dmeClaimType, dmeEob),
        arguments(hhaClaimType, hhaEob),
        arguments(hospiceClaimType, hospiceEob),
        arguments(snfClaimType, snfEob),
        arguments(carrierClaimType, carrierEob),
        arguments(pdeClaimType, pdeEob));
  }

  /**
   * Verifies that when transforming a SAMHSA claim into an ExplanationOfBenefit (where the
   * ExplanationOfBenefit then contains an item[n].productOrService.coding[n].system =
   * TransformerConstants.CODING_SYSTEM_HCPCS) and the CPT code is blacklisted, the SAMHSA matcher's
   * test method will successfully identify this as a SAMHSA related ExplanationOfBenefit.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("data")
  public void testSamhsaMatcherWhenTransformedClaimHasItemWithHcpcsCodeAndMatchingCptExpectMatch(
      String claimType, ExplanationOfBenefit loadedExplanationOfBenefit) {
    boolean expectMatch = true;

    // PDE has no SAMHSA, so expect no match on SAMSHA filter
    if (PART_D_EVENT_CLAIM.equals(claimType)) {
      expectMatch = false;
    }

    verifySamhsaMatcherForItemWithSingleCoding(
        TransformerConstants.CODING_SYSTEM_HCPCS,
        BLACKLISTED_HCPCS_CODE,
        expectMatch,
        loadedExplanationOfBenefit);
  }

  /**
   * Verifies that when transforming a SAMHSA claim into an ExplanationOfBenefit (where the
   * ExplanationOfBenefit then contains an item[n].productOrService.coding[n].system =
   * TransformerConstants.CODING_SYSTEM_HCPCS) and the code is not included in the blacklist, the
   * SAMHSA matcher's test method will not identify this as a SAMHSA related ExplanationOfBenefit.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("data")
  public void
      testSamhsaMatcherWhenTransformedClaimHasItemWithHcpcsCodeAndNonMatchingCptExpectNoMatch(
          String claimType, ExplanationOfBenefit loadedExplanationOfBenefit) {
    // When/Then/
    verifySamhsaMatcherForItemWithSingleCoding(
        TransformerConstants.CODING_SYSTEM_HCPCS,
        NON_SAMHSA_HCPCS_CODE,
        false,
        loadedExplanationOfBenefit);
  }

  /**
   * Verifies that when the item[n].productOrService.coding[n].system is an unknown/unexpected
   * value, the matcher will identify this as a SAMHSA related ExplanationOfBenefit using its
   * fallback logic to assume the claim is SAMHSA.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("data")
  public void testSamhsaMatcherWhenTransformedClaimHasItemWithUnknownSystemExpectFallbackMatch(
      String claimType, ExplanationOfBenefit loadedExplanationOfBenefit) {
    boolean expectMatch = true;

    // PDE has no SAMHSA, so expect no match on SAMSHA filter
    if (PART_D_EVENT_CLAIM.equals(claimType)) {
      expectMatch = false;
    }

    verifySamhsaMatcherForItemWithSingleCoding(
        "unknknown/system/value", NON_SAMHSA_HCPCS_CODE, expectMatch, loadedExplanationOfBenefit);
  }

  /**
   * Verifies that when transforming a claim into an ExplanationOfBenefit which has no
   * item[n].productOrService.coding[n].system (procedure code) values which =
   * TransformerConstants.CODING_SYSTEM_HCPCS, has no eob.diagnosis SAMHSA code, and has no
   * eob.procedure SAMHSA code, then the SAMHSA matcher's test method will not identify this as a
   * SAMHSA related ExplanationOfBenefit.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("data")
  public void testSamhsaMatcherWhenTransformedClaimHasItemWithNoCodesExpectNoMatch(
      String claimType, ExplanationOfBenefit loadedExplanationOfBenefit) {
    verifyNoItemCodingsTriggersSamhsaFiltering(loadedExplanationOfBenefit, false);
  }

  /**
   * Verifies that when transforming a SAMHSA claim into an ExplanationOfBenefit (where the
   * ExplanationOfBenefit then contains a diagnosis[n].coding[n].system =
   * IcdCode.CODING_SYSTEM_ICD_9) and the ICD code is blacklisted the SAMHSA matcher's test method
   * will identify this as a SAMHSA related ExplanationOfBenefit.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("data")
  public void testSamhsaMatcherWhenTransformedClaimHasDiagnosisWithBlacklistedIcd9CodeExpectMatch(
      String claimType, ExplanationOfBenefit loadedExplanationOfBenefit) {
    boolean expectMatch = true;

    // PDE has no SAMHSA, so expect no match on SAMSHA filter
    if (PART_D_EVENT_CLAIM.equals(claimType)) {
      expectMatch = false;
    }

    verifySamhsaMatcherForDiagnosisIcd(
        IcdCode.CODING_SYSTEM_ICD_9,
        BLACKLISTED_IC9_DIAGNOSIS_CODE,
        expectMatch,
        loadedExplanationOfBenefit);
  }

  /**
   * Verifies that when transforming a SAMHSA claim into an ExplanationOfBenefit (where the
   * ExplanationOfBenefit then contains a diagnosis[n].coding[n].system =
   * IcdCode.CODING_SYSTEM_ICD_9) and the ICD code is blacklisted the SAMHSA matcher's test method
   * will identify this as a SAMHSA related ExplanationOfBenefit.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("data")
  public void testSamhsaMatcherWhenTransformedClaimHasDiagnosisWithUnknownIcd9SystemExpectMatch(
      String claimType, ExplanationOfBenefit loadedExplanationOfBenefit) {
    boolean expectMatch = true;

    // PDE has no SAMHSA, so expect no match on SAMSHA filter
    if (PART_D_EVENT_CLAIM.equals(claimType)) {
      expectMatch = false;
    }

    verifySamhsaMatcherForDiagnosisIcd(
        "not valid icd9 system", NON_BLACKLISTED_IC_CODE, expectMatch, loadedExplanationOfBenefit);
  }

  /**
   * Verifies that when transforming a SAMHSA claim into an ExplanationOfBenefit (where the
   * ExplanationOfBenefit then contains a diagnosis[n].coding[n].system =
   * IcdCode.CODING_SYSTEM_ICD_9) and the ICD code is not blacklisted the SAMHSA matcher's test
   * method will not identify this as a SAMHSA related ExplanationOfBenefit.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("data")
  public void
      testSamhsaMatcherWhenTransformedClaimHasDiagnosisWithNonBlacklistedIcd9CodeExpectNoMatch(
          String claimType, ExplanationOfBenefit loadedExplanationOfBenefit) {
    verifySamhsaMatcherForDiagnosisIcd(
        IcdCode.CODING_SYSTEM_ICD_9, NON_BLACKLISTED_IC_CODE, false, loadedExplanationOfBenefit);
  }

  /**
   * Verifies that when transforming a SAMHSA claim into an ExplanationOfBenefit (where the
   * ExplanationOfBenefit then contains a diagnosis[n].coding[n].system =
   * IcdCode.CODING_SYSTEM_ICD_10) and the ICD code is blacklisted the SAMHSA matcher's test method
   * will identify this as a SAMHSA related ExplanationOfBenefit.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("data")
  public void testSamhsaMatcherWhenTransformedClaimHasDiagnosisWithBlacklistedIcd10CodeExpectMatch(
      String claimType, ExplanationOfBenefit loadedExplanationOfBenefit) {
    boolean expectMatch = true;

    // PDE has no SAMHSA, so expect no match on SAMSHA filter
    if (PART_D_EVENT_CLAIM.equals(claimType)) {
      expectMatch = false;
    }

    verifySamhsaMatcherForDiagnosisIcd(
        IcdCode.CODING_SYSTEM_ICD_10,
        BLACKLISTED_IC10_DIAGNOSIS_CODE,
        expectMatch,
        loadedExplanationOfBenefit);
  }

  /**
   * Verifies that when transforming a SAMHSA claim into an ExplanationOfBenefit (where the
   * ExplanationOfBenefit then contains a diagnosis[n].coding[n].system =
   * IcdCode.CODING_SYSTEM_ICD_10) and the ICD code is blacklisted the SAMHSA matcher's test method
   * will identify this as a SAMHSA related ExplanationOfBenefit.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("data")
  public void testSamhsaMatcherWhenTransformedClaimHasDiagnosisWithUnknownIcd10SystemExpectMatch(
      String claimType, ExplanationOfBenefit loadedExplanationOfBenefit) {
    boolean expectMatch = true;

    // PDE has no SAMHSA, so expect no match on SAMSHA filter
    if (PART_D_EVENT_CLAIM.equals(claimType)) {
      expectMatch = false;
    }

    verifySamhsaMatcherForDiagnosisIcd(
        "not valid icd10 system", NON_BLACKLISTED_IC_CODE, expectMatch, loadedExplanationOfBenefit);
  }

  /**
   * Verifies that when transforming a SAMHSA claim into an ExplanationOfBenefit (where the
   * ExplanationOfBenefit then contains a diagnosis[n].coding[n].system =
   * IcdCode.CODING_SYSTEM_ICD_10) and the ICD code is not blacklisted the SAMHSA matcher's test
   * method will not identify this as a SAMHSA related ExplanationOfBenefit.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("data")
  public void
      testSamhsaMatcherWhenTransformedClaimHasDiagnosisWithNonBlacklistedIcd10CodeExpectNoMatch(
          String claimType, ExplanationOfBenefit loadedExplanationOfBenefit) {
    verifySamhsaMatcherForDiagnosisIcd(
        IcdCode.CODING_SYSTEM_ICD_10, NON_BLACKLISTED_IC_CODE, false, loadedExplanationOfBenefit);
  }

  /**
   * Verifies that when transforming a SAMHSA claim into an ExplanationOfBenefit where the Diagnosis
   * contains a blacklisted package DRG code, the matcher returns a SAMHSA match.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("data")
  public void
      testSamhsaMatcherWhenTransformedClaimHasDiagnosisPackageWithBlacklistedDrgCodeExpectMatch(
          String claimType, ExplanationOfBenefit loadedExplanationOfBenefit) {
    boolean expectMatch = true;

    // PDE has no SAMHSA, so expect no match on SAMSHA filter
    if (PART_D_EVENT_CLAIM.equals(claimType)) {
      expectMatch = false;
    }

    verifySamhsaMatcherForDiagnosisPackage(
        DRG_SYSTEM, BLACKLISTED_DRG_DIAGNOSIS_CODE, expectMatch, loadedExplanationOfBenefit);
  }

  /**
   * Verifies that when transforming a SAMHSA claim into an ExplanationOfBenefit where the Diagnosis
   * contains a non-blacklisted package DRG code, the matcher returns false.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("data")
  public void
      testSamhsaMatcherWhenTransformedClaimHasDiagnosisPackageWithNonBlacklistedDrgCodeExpectNoMatch(
          String claimType, ExplanationOfBenefit loadedExplanationOfBenefit) {
    verifySamhsaMatcherForDiagnosisPackage(
        DRG_SYSTEM, NON_BLACKLISTED_DRG_DIAGNOSIS_CODE, false, loadedExplanationOfBenefit);
  }

  /**
   * Verifies that when transforming a SAMHSA claim into an ExplanationOfBenefit where the Diagnosis
   * contains an unknown package system, the matcher returns a SAMHSA match. This is a fallback
   * mechanism to ensure unknown systems are filtered.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("data")
  public void
      testSamhsaMatcherWhenTransformedClaimHasDiagnosisPackageWithUnknownPackageSystemExpectMatch(
          String claimType, ExplanationOfBenefit loadedExplanationOfBenefit) {
    boolean expectMatch = true;

    // PDE has no SAMHSA, so expect no match on SAMSHA filter
    if (PART_D_EVENT_CLAIM.equals(claimType)) {
      expectMatch = false;
    }

    verifySamhsaMatcherForDiagnosisPackage(
        "UNKNOWN", NON_BLACKLISTED_DRG_DIAGNOSIS_CODE, expectMatch, loadedExplanationOfBenefit);
  }

  /**
   * Verifies that when transforming a SAMHSA claim into an ExplanationOfBenefit (where the
   * ExplanationOfBenefit then contains a procedure[n].coding[n].system =
   * IcdCode.CODING_SYSTEM_ICD_9) and the ICD code is blacklisted the SAMHSA matcher's test method
   * will identify this as a SAMHSA related ExplanationOfBenefit.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("data")
  public void testSamhsaMatcherWhenTransformedClaimHasProcedureWithBlacklistedIcd9CodeExpectMatch(
      String claimType, ExplanationOfBenefit loadedExplanationOfBenefit) {
    boolean expectMatch = true;

    // PDE has no SAMHSA, so expect no match on SAMSHA filter
    // DME, HHA, Hospice, Carrier does not look at procedure so it wont match
    if (PART_D_EVENT_CLAIM.equals(claimType)
        || DME_CLAIM.equals(claimType)
        || HHA_CLAIM.equals(claimType)
        || HOSPICE_CLAIM.equals(claimType)
        || CARRIER_CLAIM.equals(claimType)) {
      expectMatch = false;
    }

    verifySamhsaMatcherForProcedureIcd(
        IcdCode.CODING_SYSTEM_ICD_9,
        BLACKLISTED_IC9_PROCEDURE_CODE,
        expectMatch,
        loadedExplanationOfBenefit);
  }

  /**
   * Verifies that when transforming a SAMHSA claim into an ExplanationOfBenefit (where the
   * ExplanationOfBenefit then contains a procedure[n].coding[n].system =
   * IcdCode.CODING_SYSTEM_ICD_9) and the ICD code is blacklisted the SAMHSA matcher's test method
   * will identify this as a SAMHSA related ExplanationOfBenefit.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("data")
  public void testSamhsaMatcherWhenTransformedClaimHasProcedureWithUnknownIcd9SystemExpectMatch(
      String claimType, ExplanationOfBenefit loadedExplanationOfBenefit) {
    boolean expectMatch = true;

    // PDE has no SAMHSA, so expect no match on SAMSHA filter
    // DME, HHA, Hospice, Carrier does not look at procedure so it wont match
    if (PART_D_EVENT_CLAIM.equals(claimType)
        || DME_CLAIM.equals(claimType)
        || HHA_CLAIM.equals(claimType)
        || HOSPICE_CLAIM.equals(claimType)
        || CARRIER_CLAIM.equals(claimType)) {
      expectMatch = false;
    }

    verifySamhsaMatcherForProcedureIcd(
        "not valid icd9 system", NON_BLACKLISTED_IC_CODE, expectMatch, loadedExplanationOfBenefit);
  }

  /**
   * Verifies that when transforming a SAMHSA claim into an ExplanationOfBenefit (where the
   * ExplanationOfBenefit then contains a procedure[n].coding[n].system =
   * IcdCode.CODING_SYSTEM_ICD_9) and the ICD code is not blacklisted the SAMHSA matcher's test
   * method will not identify this as a SAMHSA related ExplanationOfBenefit.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("data")
  public void
      testSamhsaMatcherWhenTransformedClaimHasProcedureWithNonBlacklistedIcd9CodeExpectNoMatch(
          String claimType, ExplanationOfBenefit loadedExplanationOfBenefit) {
    verifySamhsaMatcherForProcedureIcd(
        IcdCode.CODING_SYSTEM_ICD_9, NON_BLACKLISTED_IC_CODE, false, loadedExplanationOfBenefit);
  }

  /**
   * Verifies that when transforming a SAMHSA claim into an ExplanationOfBenefit (where the
   * ExplanationOfBenefit then contains a procedure[n].coding[n].system =
   * IcdCode.CODING_SYSTEM_ICD_10) and the ICD code is blacklisted the SAMHSA matcher's test method
   * will identify this as a SAMHSA related ExplanationOfBenefit.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("data")
  public void testSamhsaMatcherWhenTransformedClaimHasProcedureWithBlacklistedIcd10CodeExpectMatch(
      String claimType, ExplanationOfBenefit loadedExplanationOfBenefit) {
    boolean expectMatch = true;

    // PDE has no SAMHSA, so expect no match on SAMSHA filter
    // DME, HHA, Hospice, Carrier does not look at procedure so it wont match
    if (PART_D_EVENT_CLAIM.equals(claimType)
        || DME_CLAIM.equals(claimType)
        || HHA_CLAIM.equals(claimType)
        || HOSPICE_CLAIM.equals(claimType)
        || CARRIER_CLAIM.equals(claimType)) {
      expectMatch = false;
    }

    verifySamhsaMatcherForProcedureIcd(
        IcdCode.CODING_SYSTEM_ICD_10,
        BLACKLISTED_IC10_PROCEDURE_CODE,
        expectMatch,
        loadedExplanationOfBenefit);
  }

  /**
   * Verifies that when transforming a SAMHSA claim into an ExplanationOfBenefit (where the
   * ExplanationOfBenefit then contains a procedure[n].coding[n].system =
   * IcdCode.CODING_SYSTEM_ICD_10) and the ICD code is blacklisted the SAMHSA matcher's test method
   * will identify this as a SAMHSA related ExplanationOfBenefit.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("data")
  public void testSamhsaMatcherWhenTransformedClaimHasProcedureWithUnknownIcd10SystemExpectMatch(
      String claimType, ExplanationOfBenefit loadedExplanationOfBenefit) {
    boolean expectMatch = true;

    // PDE has no SAMHSA, so expect no match on SAMSHA filter
    // DME, HHA, Hospice, Carrier does not look at procedure so it wont match
    if (PART_D_EVENT_CLAIM.equals(claimType)
        || DME_CLAIM.equals(claimType)
        || HHA_CLAIM.equals(claimType)
        || HOSPICE_CLAIM.equals(claimType)
        || CARRIER_CLAIM.equals(claimType)) {
      expectMatch = false;
    }

    verifySamhsaMatcherForProcedureIcd(
        "not valid icd10 system", NON_BLACKLISTED_IC_CODE, expectMatch, loadedExplanationOfBenefit);
  }

  /**
   * Verifies that when transforming a SAMHSA claim into an ExplanationOfBenefit (where the
   * ExplanationOfBenefit then contains a procedure[n].coding[n].system =
   * IcdCode.CODING_SYSTEM_ICD_10) and the ICD code is not blacklisted the SAMHSA matcher's test
   * method will not identify this as a SAMHSA related ExplanationOfBenefit.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("data")
  public void
      testSamhsaMatcherWhenTransformedClaimHasProcedureWithNonBlacklistedIcd10CodeExpectNoMatch(
          String claimType, ExplanationOfBenefit loadedExplanationOfBenefit) {
    verifySamhsaMatcherForProcedureIcd(
        IcdCode.CODING_SYSTEM_ICD_10, NON_BLACKLISTED_IC_CODE, false, loadedExplanationOfBenefit);
  }

  /**
   * Verifies that a claim with no samhsa diagnosis, procedure, or item-level HCPCS codes does
   * trigger filtering because the code array is empty and therefore does not contain known systems.
   *
   * @param expectMatch if the test is expecting a filtering match
   * @param explanationOfBenefit the loaded benefit to use for the test
   */
  private void verifyNoItemCodingsTriggersSamhsaFiltering(
      ExplanationOfBenefit explanationOfBenefit, boolean expectMatch) {

    ExplanationOfBenefit modifiedEob = explanationOfBenefit.copy();

    // Set Top level diagnosis and package code to null and coding to empty
    for (ExplanationOfBenefit.DiagnosisComponent diagnosisComponent : modifiedEob.getDiagnosis()) {
      if (diagnosisComponent != null && diagnosisComponent.getDiagnosisCodeableConcept() != null) {
        diagnosisComponent.getDiagnosisCodeableConcept().setCoding(new ArrayList<>());
        diagnosisComponent.setPackageCode(null);
      }
    }

    // Set procedure to empty
    modifiedEob.setProcedure(new ArrayList<>());

    // Set item level codings to non-SAMHSA
    modifiedEob.getItem().get(0).setService(null);

    // When
    boolean isMatch = samhsaMatcher.test(modifiedEob);

    // Then
    assertEquals(expectMatch, isMatch);
  }

  /**
   * Verify SAMHSA matcher for item with the given system, code and if the expectation is that there
   * should be a match for this combination.
   *
   * @param system the system value
   * @param code the code
   * @param shouldMatch if the matcher should match on this combination
   * @param explanationOfBenefit the explanation of benefit
   */
  private void verifySamhsaMatcherForItemWithSingleCoding(
      String system, String code, boolean shouldMatch, ExplanationOfBenefit explanationOfBenefit) {

    ExplanationOfBenefit modifiedEob = explanationOfBenefit.copy();

    // Set Top level diagnosis and package code to null so we can test item logic
    for (ExplanationOfBenefit.DiagnosisComponent diagnosisComponent : modifiedEob.getDiagnosis()) {
      CodeableConcept codeableConcept = diagnosisComponent.getDiagnosisCodeableConcept();
      if (codeableConcept != null) {
        codeableConcept.setCoding(new ArrayList<>());
        diagnosisComponent.setPackageCode(null);
      }
    }

    List<Coding> codings = new ArrayList<>();
    Coding coding = new Coding();
    coding.setSystem(system);
    coding.setCode(code);
    codings.add(coding);
    modifiedEob.getItem().get(0).getService().setCoding(codings);

    assertEquals(shouldMatch, samhsaMatcher.test(modifiedEob));
  }

  /**
   * Verify SAMHSA matcher for item with the given system, code and if the expectation is that there
   * should be a match for this combination.
   *
   * @param system the system value of the first coding
   * @param code the code of the first coding
   * @param system2 the system value of the second coding
   * @param code2 the code of the second coding
   * @param shouldMatch if the matcher should match on this combination
   * @param explanationOfBenefit the explanation of benefit
   */
  private void verifySamhsaMatcherForItemWithMultiCoding(
      String system,
      String code,
      String system2,
      String code2,
      boolean shouldMatch,
      ExplanationOfBenefit explanationOfBenefit) {

    ExplanationOfBenefit modifiedEob = explanationOfBenefit.copy();

    // Set Top level diagnosis and package code to null so we can test item logic
    for (ExplanationOfBenefit.DiagnosisComponent diagnosisComponent : modifiedEob.getDiagnosis()) {
      CodeableConcept codeableConcept = diagnosisComponent.getDiagnosisCodeableConcept();
      codeableConcept.setCoding(new ArrayList<>());
      diagnosisComponent.setPackageCode(null);
    }

    List<Coding> codings = new ArrayList<>();
    Coding coding = new Coding();
    coding.setSystem(system);
    coding.setCode(code);
    Coding coding2 = new Coding();
    coding2.setSystem(system2);
    coding2.setCode(code2);
    codings.add(coding);
    codings.add(coding2);
    modifiedEob.getItem().get(0).getService().setCoding(codings);

    assertEquals(shouldMatch, samhsaMatcher.test(modifiedEob));
  }

  /**
   * Verify SAMHSA matcher for ICD item with the given system, code and if the expectation is that
   * there should be a match for this combination.
   *
   * @param system the system value
   * @param code the code
   * @param shouldMatch if the matcher should match on this combination
   */
  private void verifySamhsaMatcherForDiagnosisIcd(
      String system, String code, boolean shouldMatch, ExplanationOfBenefit explanationOfBenefit) {

    ExplanationOfBenefit modifiedEob = explanationOfBenefit.copy();

    // Set diagnosis
    for (ExplanationOfBenefit.DiagnosisComponent diagnosisComponent : modifiedEob.getDiagnosis()) {
      CodeableConcept codeableConcept = diagnosisComponent.getDiagnosisCodeableConcept();
      if (codeableConcept != null) {
        ArrayList<Coding> codingList = new ArrayList<>();
        codingList.add(new Coding().setSystem(system).setCode(code));
        codeableConcept.setCoding(codingList);
        diagnosisComponent.setPackageCode(null);
      }
    }

    // Set procedure to empty so we dont check it for matches
    modifiedEob
        .getProcedure()
        .forEach(c -> c.getProcedureCodeableConcept().setCoding(new ArrayList<>()));

    // Set item coding to non-SAMHSA so we dont check it for matches
    List<Coding> codings = new ArrayList<>();
    Coding coding = new Coding();
    coding.setSystem(TransformerConstants.CODING_SYSTEM_HCPCS);
    coding.setCode(NON_SAMHSA_HCPCS_CODE);
    codings.add(coding);
    modifiedEob.getItem().get(0).getService().setCoding(codings);

    assertEquals(shouldMatch, samhsaMatcher.test(modifiedEob));
  }

  /**
   * Verify SAMHSA matcher for package with the given system, code and if the expectation is that
   * there should be a match for this combination.
   *
   * @param system the system value
   * @param code the code
   * @param shouldMatch if the matcher should match on this combination
   */
  private void verifySamhsaMatcherForDiagnosisPackage(
      String system, String code, boolean shouldMatch, ExplanationOfBenefit explanationOfBenefit) {

    ExplanationOfBenefit modifiedEob = explanationOfBenefit.copy();

    // Set diagnosis DRG
    for (ExplanationOfBenefit.DiagnosisComponent diagnosisComponent : modifiedEob.getDiagnosis()) {
      if (diagnosisComponent.getDiagnosisCodeableConcept() != null) {
        diagnosisComponent.getDiagnosisCodeableConcept().setCoding(new ArrayList<>());
      }
      CodeableConcept codeableConcept = new CodeableConcept();
      Coding coding = new Coding(system, code, null);
      codeableConcept.setCoding(Collections.singletonList(coding));
      diagnosisComponent.setPackageCode(codeableConcept);
    }

    // Set procedure to empty so we dont check it for matches
    for (ExplanationOfBenefit.ProcedureComponent diagnosisComponent : modifiedEob.getProcedure()) {
      CodeableConcept codeableConcept = diagnosisComponent.getProcedureCodeableConcept();
      ArrayList<Coding> codingList = new ArrayList<>();
      codeableConcept.setCoding(codingList);
    }

    // Set item coding to non-SAMHSA so we dont check it for matches
    List<Coding> codings = new ArrayList<>();
    Coding coding = new Coding();
    coding.setSystem(TransformerConstants.CODING_SYSTEM_HCPCS);
    coding.setCode(NON_SAMHSA_HCPCS_CODE);
    codings.add(coding);
    modifiedEob.getItem().get(0).getService().setCoding(codings);

    assertEquals(shouldMatch, samhsaMatcher.test(modifiedEob));
  }

  /**
   * Verify SAMHSA matcher for ICD item with the given system, code and if the expectation is that
   * there should be a match for this combination.
   *
   * @param system the system value
   * @param code the code
   * @param shouldMatch if the matcher should match on this combination
   */
  private void verifySamhsaMatcherForProcedureIcd(
      String system, String code, boolean shouldMatch, ExplanationOfBenefit explanationOfBenefit) {

    ExplanationOfBenefit modifiedEob = explanationOfBenefit.copy();

    // Set diagnosis to empty so we dont check it for matches
    for (ExplanationOfBenefit.DiagnosisComponent diagnosisComponent : modifiedEob.getDiagnosis()) {
      CodeableConcept codeableConcept = diagnosisComponent.getDiagnosisCodeableConcept();
      ArrayList<Coding> codingList = new ArrayList<>();
      if (codeableConcept != null) {
        codeableConcept.setCoding(codingList);
        diagnosisComponent.setPackageCode(null);
      }
    }

    // Set procedure
    for (ExplanationOfBenefit.ProcedureComponent diagnosisComponent : modifiedEob.getProcedure()) {
      CodeableConcept codeableConcept = diagnosisComponent.getProcedureCodeableConcept();
      ArrayList<Coding> codingList = new ArrayList<>();
      codingList.add(new Coding().setSystem(system).setCode(code));
      codeableConcept.setCoding(codingList);
    }

    // Set item coding to non-SAMHSA so we dont check it for matches
    List<Coding> codings = new ArrayList<>();
    Coding coding = new Coding();
    coding.setSystem(TransformerConstants.CODING_SYSTEM_HCPCS);
    coding.setCode(NON_SAMHSA_HCPCS_CODE);
    codings.add(coding);
    modifiedEob.getItem().get(0).getService().setCoding(codings);

    assertEquals(shouldMatch, samhsaMatcher.test(modifiedEob));
  }

  /**
   * Generates the Claim object to be used in a test.
   *
   * @param type the type
   * @return the claim to be used for the test, should match the input type
   */
  public static RifRecordBase getClaim(Class<? extends RifRecordBase> type) {
    List<Object> parsedRecords =
        ServerTestUtils.parseData(Arrays.asList(StaticRifResourceGroup.SAMPLE_A.getResources()));

    RifRecordBase claim =
        parsedRecords.stream().filter(type::isInstance).map(type::cast).findFirst().orElse(null);

    if (claim != null) {
      claim.setLastUpdated(Instant.now());
    } else {
      throw new IllegalStateException(
          "Test setup issue, did not find expected InpatientClaim in sample record.");
    }

    return claim;
  }
}
