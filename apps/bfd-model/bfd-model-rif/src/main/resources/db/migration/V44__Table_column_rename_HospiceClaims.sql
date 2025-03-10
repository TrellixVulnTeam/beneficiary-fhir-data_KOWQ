-- The V40 through V52 migrations rename our tables and columns for CCW-sourced data, such that:
-- 1. We follow PostgreSQL's general snake_case naming conventions, to improve the developer experience: DB
--    object names won't have to be quoted all over the place, anymore.
-- 2. Column names match those in our upstream source system, the CCW, to improve traceability as data flows
--    through our systems.
-- 3. Rename the "parentXXX" foreign key columns to instead have names that match their target column.

-- Rename tables and table columns; syntax:
--
--      psql: alter table public.beneficiaries rename column "beneficiaryId" to bene_id;
--      hsql: alter table public.beneficiaries alter column  "beneficiaryId" rename to bene_id;
--
--      ${logic.alter-rename-column}
--          psql: "rename column"
--          hsql: "alter column"
--
--      ${logic.rename-to}
--          psql: "to"
--          hsql: "rename to"
--
-- HospiceClaims to hospice_claims
--
alter table public."HospiceClaims" rename to hospice_claims;
alter table public.hospice_claims ${logic.alter-rename-column} "claimId" ${logic.rename-to} clm_id;
alter table public.hospice_claims ${logic.alter-rename-column} "beneficiaryId" ${logic.rename-to} bene_id;
alter table public.hospice_claims ${logic.alter-rename-column} "claimGroupId" ${logic.rename-to} clm_grp_id;
alter table public.hospice_claims ${logic.alter-rename-column} lastupdated ${logic.rename-to} last_updated;
alter table public.hospice_claims ${logic.alter-rename-column} "dateFrom" ${logic.rename-to} clm_from_dt;
alter table public.hospice_claims ${logic.alter-rename-column} "dateThrough" ${logic.rename-to} clm_thru_dt;
alter table public.hospice_claims ${logic.alter-rename-column} "claimFacilityTypeCode" ${logic.rename-to} clm_fac_type_cd;
alter table public.hospice_claims ${logic.alter-rename-column} "claimFrequencyCode" ${logic.rename-to} clm_freq_cd;
alter table public.hospice_claims ${logic.alter-rename-column} "claimHospiceStartDate" ${logic.rename-to} clm_hospc_start_dt_id;
alter table public.hospice_claims ${logic.alter-rename-column} "claimNonPaymentReasonCode" ${logic.rename-to} clm_mdcr_non_pmt_rsn_cd;
alter table public.hospice_claims ${logic.alter-rename-column} "paymentAmount" ${logic.rename-to} clm_pmt_amt;
alter table public.hospice_claims ${logic.alter-rename-column} "claimServiceClassificationTypeCode" ${logic.rename-to} clm_srvc_clsfctn_type_cd;
alter table public.hospice_claims ${logic.alter-rename-column} "utilizationDayCount" ${logic.rename-to} clm_utlztn_day_cnt;
alter table public.hospice_claims ${logic.alter-rename-column} "attendingPhysicianNpi" ${logic.rename-to} at_physn_npi;
alter table public.hospice_claims ${logic.alter-rename-column} "attendingPhysicianUpin" ${logic.rename-to} at_physn_upin;
alter table public.hospice_claims ${logic.alter-rename-column} "hospicePeriodCount" ${logic.rename-to} bene_hospc_prd_cnt;
alter table public.hospice_claims ${logic.alter-rename-column} "fiscalIntermediaryClaimProcessDate" ${logic.rename-to} fi_clm_proc_dt;
alter table public.hospice_claims ${logic.alter-rename-column} "fiDocumentClaimControlNumber" ${logic.rename-to} fi_doc_clm_cntl_num;
alter table public.hospice_claims ${logic.alter-rename-column} "fiscalIntermediaryNumber" ${logic.rename-to} fi_num;
alter table public.hospice_claims ${logic.alter-rename-column} "fiOriginalClaimControlNumber" ${logic.rename-to} fi_orig_clm_cntl_num;
alter table public.hospice_claims ${logic.alter-rename-column} "finalAction" ${logic.rename-to} final_action;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosisExternalFirstCode" ${logic.rename-to} fst_dgns_e_cd;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosisExternalFirstCodeVersion" ${logic.rename-to} fst_dgns_e_vrsn_cd;
alter table public.hospice_claims ${logic.alter-rename-column} "beneficiaryDischargeDate" ${logic.rename-to} nch_bene_dschrg_dt;
alter table public.hospice_claims ${logic.alter-rename-column} "claimTypeCode" ${logic.rename-to} nch_clm_type_cd;
alter table public.hospice_claims ${logic.alter-rename-column} "nearLineRecordIdCode" ${logic.rename-to} nch_near_line_rec_ident_cd;
alter table public.hospice_claims ${logic.alter-rename-column} "claimPrimaryPayerCode" ${logic.rename-to} nch_prmry_pyr_cd;
alter table public.hospice_claims ${logic.alter-rename-column} "primaryPayerPaidAmount" ${logic.rename-to} nch_prmry_pyr_clm_pd_amt;
alter table public.hospice_claims ${logic.alter-rename-column} "patientStatusCd" ${logic.rename-to} nch_ptnt_status_ind_cd;
alter table public.hospice_claims ${logic.alter-rename-column} "weeklyProcessDate" ${logic.rename-to} nch_wkly_proc_dt;
alter table public.hospice_claims ${logic.alter-rename-column} "organizationNpi" ${logic.rename-to} org_npi_num;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosisPrincipalCode" ${logic.rename-to} prncpal_dgns_cd;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosisPrincipalCodeVersion" ${logic.rename-to} prncpal_dgns_vrsn_cd;
alter table public.hospice_claims ${logic.alter-rename-column} "providerNumber" ${logic.rename-to} prvdr_num;
alter table public.hospice_claims ${logic.alter-rename-column} "providerStateCode" ${logic.rename-to} prvdr_state_cd;
alter table public.hospice_claims ${logic.alter-rename-column} "patientDischargeStatusCode" ${logic.rename-to} ptnt_dschrg_stus_cd;
alter table public.hospice_claims ${logic.alter-rename-column} "totalChargeAmount" ${logic.rename-to} clm_tot_chrg_amt;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis1Code" ${logic.rename-to} icd_dgns_cd1;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis2Code" ${logic.rename-to} icd_dgns_cd2;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis3Code" ${logic.rename-to} icd_dgns_cd3;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis4Code" ${logic.rename-to} icd_dgns_cd4;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis5Code" ${logic.rename-to} icd_dgns_cd5;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis6Code" ${logic.rename-to} icd_dgns_cd6;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis7Code" ${logic.rename-to} icd_dgns_cd7;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis8Code" ${logic.rename-to} icd_dgns_cd8;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis9Code" ${logic.rename-to} icd_dgns_cd9;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis10Code" ${logic.rename-to} icd_dgns_cd10;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis11Code" ${logic.rename-to} icd_dgns_cd11;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis12Code" ${logic.rename-to} icd_dgns_cd12;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis13Code" ${logic.rename-to} icd_dgns_cd13;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis14Code" ${logic.rename-to} icd_dgns_cd14;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis15Code" ${logic.rename-to} icd_dgns_cd15;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis16Code" ${logic.rename-to} icd_dgns_cd16;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis17Code" ${logic.rename-to} icd_dgns_cd17;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis18Code" ${logic.rename-to} icd_dgns_cd18;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis19Code" ${logic.rename-to} icd_dgns_cd19;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis20Code" ${logic.rename-to} icd_dgns_cd20;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis21Code" ${logic.rename-to} icd_dgns_cd21;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis22Code" ${logic.rename-to} icd_dgns_cd22;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis23Code" ${logic.rename-to} icd_dgns_cd23;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis24Code" ${logic.rename-to} icd_dgns_cd24;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis25Code" ${logic.rename-to} icd_dgns_cd25;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosisExternal1Code" ${logic.rename-to} icd_dgns_e_cd1;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosisExternal2Code" ${logic.rename-to} icd_dgns_e_cd2;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosisExternal3Code" ${logic.rename-to} icd_dgns_e_cd3;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosisExternal4Code" ${logic.rename-to} icd_dgns_e_cd4;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosisExternal5Code" ${logic.rename-to} icd_dgns_e_cd5;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosisExternal6Code" ${logic.rename-to} icd_dgns_e_cd6;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosisExternal7Code" ${logic.rename-to} icd_dgns_e_cd7;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosisExternal8Code" ${logic.rename-to} icd_dgns_e_cd8;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosisExternal9Code" ${logic.rename-to} icd_dgns_e_cd9;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosisExternal10Code" ${logic.rename-to} icd_dgns_e_cd10;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosisExternal11Code" ${logic.rename-to} icd_dgns_e_cd11;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosisExternal12Code" ${logic.rename-to} icd_dgns_e_cd12;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosisExternal1CodeVersion" ${logic.rename-to} icd_dgns_e_vrsn_cd1;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosisExternal2CodeVersion" ${logic.rename-to} icd_dgns_e_vrsn_cd2;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosisExternal3CodeVersion" ${logic.rename-to} icd_dgns_e_vrsn_cd3;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosisExternal4CodeVersion" ${logic.rename-to} icd_dgns_e_vrsn_cd4;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosisExternal5CodeVersion" ${logic.rename-to} icd_dgns_e_vrsn_cd5;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosisExternal6CodeVersion" ${logic.rename-to} icd_dgns_e_vrsn_cd6;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosisExternal7CodeVersion" ${logic.rename-to} icd_dgns_e_vrsn_cd7;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosisExternal8CodeVersion" ${logic.rename-to} icd_dgns_e_vrsn_cd8;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosisExternal9CodeVersion" ${logic.rename-to} icd_dgns_e_vrsn_cd9;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosisExternal10CodeVersion" ${logic.rename-to} icd_dgns_e_vrsn_cd10;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosisExternal11CodeVersion" ${logic.rename-to} icd_dgns_e_vrsn_cd11;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosisExternal12CodeVersion" ${logic.rename-to} icd_dgns_e_vrsn_cd12;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis1CodeVersion" ${logic.rename-to} icd_dgns_vrsn_cd1;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis2CodeVersion" ${logic.rename-to} icd_dgns_vrsn_cd2;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis3CodeVersion" ${logic.rename-to} icd_dgns_vrsn_cd3;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis4CodeVersion" ${logic.rename-to} icd_dgns_vrsn_cd4;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis5CodeVersion" ${logic.rename-to} icd_dgns_vrsn_cd5;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis6CodeVersion" ${logic.rename-to} icd_dgns_vrsn_cd6;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis7CodeVersion" ${logic.rename-to} icd_dgns_vrsn_cd7;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis8CodeVersion" ${logic.rename-to} icd_dgns_vrsn_cd8;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis9CodeVersion" ${logic.rename-to} icd_dgns_vrsn_cd9;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis10CodeVersion" ${logic.rename-to} icd_dgns_vrsn_cd10;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis11CodeVersion" ${logic.rename-to} icd_dgns_vrsn_cd11;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis12CodeVersion" ${logic.rename-to} icd_dgns_vrsn_cd12;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis13CodeVersion" ${logic.rename-to} icd_dgns_vrsn_cd13;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis14CodeVersion" ${logic.rename-to} icd_dgns_vrsn_cd14;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis15CodeVersion" ${logic.rename-to} icd_dgns_vrsn_cd15;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis16CodeVersion" ${logic.rename-to} icd_dgns_vrsn_cd16;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis17CodeVersion" ${logic.rename-to} icd_dgns_vrsn_cd17;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis18CodeVersion" ${logic.rename-to} icd_dgns_vrsn_cd18;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis19CodeVersion" ${logic.rename-to} icd_dgns_vrsn_cd19;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis20CodeVersion" ${logic.rename-to} icd_dgns_vrsn_cd20;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis21CodeVersion" ${logic.rename-to} icd_dgns_vrsn_cd21;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis22CodeVersion" ${logic.rename-to} icd_dgns_vrsn_cd22;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis23CodeVersion" ${logic.rename-to} icd_dgns_vrsn_cd23;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis24CodeVersion" ${logic.rename-to} icd_dgns_vrsn_cd24;
alter table public.hospice_claims ${logic.alter-rename-column} "diagnosis25CodeVersion" ${logic.rename-to} icd_dgns_vrsn_cd25;
--
-- HospiceClaimLines to hospice_claim_lines
--
alter table public."HospiceClaimLines" rename to hospice_claim_lines;
alter table public.hospice_claim_lines ${logic.alter-rename-column} "parentClaim" ${logic.rename-to} clm_id;
alter table public.hospice_claim_lines ${logic.alter-rename-column} "lineNumber" ${logic.rename-to} clm_line_num;
alter table public.hospice_claim_lines ${logic.alter-rename-column} "revenueCenterCode" ${logic.rename-to} rev_cntr;
alter table public.hospice_claim_lines ${logic.alter-rename-column} "paymentAmount" ${logic.rename-to} rev_cntr_pmt_amt_amt;
alter table public.hospice_claim_lines ${logic.alter-rename-column} "revenueCenterDate" ${logic.rename-to} rev_cntr_dt;
alter table public.hospice_claim_lines ${logic.alter-rename-column} "totalChargeAmount" ${logic.rename-to} rev_cntr_tot_chrg_amt;
alter table public.hospice_claim_lines ${logic.alter-rename-column} "unitCount" ${logic.rename-to} rev_cntr_unit_cnt;
alter table public.hospice_claim_lines ${logic.alter-rename-column} "benficiaryPaymentAmount" ${logic.rename-to} rev_cntr_bene_pmt_amt;
alter table public.hospice_claim_lines ${logic.alter-rename-column} "deductibleCoinsuranceCd" ${logic.rename-to} rev_cntr_ddctbl_coinsrnc_cd;
alter table public.hospice_claim_lines ${logic.alter-rename-column} "nationalDrugCodeQualifierCode" ${logic.rename-to} rev_cntr_ndc_qty_qlfr_cd;
alter table public.hospice_claim_lines ${logic.alter-rename-column} "nationalDrugCodeQuantity" ${logic.rename-to} rev_cntr_ndc_qty;
alter table public.hospice_claim_lines ${logic.alter-rename-column} "nonCoveredChargeAmount" ${logic.rename-to} rev_cntr_ncvrd_chrg_amt;
alter table public.hospice_claim_lines ${logic.alter-rename-column} "providerPaymentAmount" ${logic.rename-to} rev_cntr_prvdr_pmt_amt;
alter table public.hospice_claim_lines ${logic.alter-rename-column} "rateAmount" ${logic.rename-to} rev_cntr_rate_amt;
alter table public.hospice_claim_lines ${logic.alter-rename-column} "hcpcsCode" ${logic.rename-to} hcpcs_cd;
alter table public.hospice_claim_lines ${logic.alter-rename-column} "hcpcsInitialModifierCode" ${logic.rename-to} hcpcs_1st_mdfr_cd;
alter table public.hospice_claim_lines ${logic.alter-rename-column} "hcpcsSecondModifierCode" ${logic.rename-to} hcpcs_2nd_mdfr_cd;
alter table public.hospice_claim_lines ${logic.alter-rename-column} "revenueCenterRenderingPhysicianNPI" ${logic.rename-to} rndrng_physn_npi;
alter table public.hospice_claim_lines ${logic.alter-rename-column} "revenueCenterRenderingPhysicianUPIN" ${logic.rename-to} rndrng_physn_upin;

-- psql only
${logic.psql-only-alter} index if exists  public."HospiceClaimLines_pkey" rename to hospice_claim_lines_pkey;
${logic.psql-only-alter} index if exists  public."HospiceClaims_pkey" rename to hospice_claims_pkey;

${logic.psql-only-alter} table public.hospice_claim_lines rename constraint "HospiceClaimLines_parentClaim_to_HospiceClaims" to hospice_claim_lines_clm_id_to_hospice_claims;
${logic.psql-only-alter} table public.hospice_claims rename constraint "HospiceClaims_beneficiaryId_to_Beneficiaries" to hospice_claims_bene_id_to_beneficiaries;

-- hsql onl
${logic.hsql-only-alter} table public.hospice_claim_lines add constraint hospice_claim_lines_pkey primary key (clm_id, clm_line_num);
${logic.hsql-only-alter} table public.hospice_claims add constraint hospice_claims_pkey primary key (clm_id);

${logic.hsql-only-alter} table public.hospice_claim_lines ADD CONSTRAINT hospice_claim_lines_clm_id_to_hospice_claims FOREIGN KEY (clm_id) REFERENCES public.hospice_claims (clm_id);
${logic.hsql-only-alter} table public.hospice_claims ADD CONSTRAINT hospice_claims_bene_id_to_beneficiaries FOREIGN KEY (bene_id) REFERENCES public.beneficiaries (bene_id);

-- both psql and hsql support non-primary key index renaming
ALTER INDEX "HospiceClaims_beneficiaryId_idx" RENAME TO hospice_claims_bene_id_idx;
