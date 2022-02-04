${logic.hsql-only} create table carrier_claims_bfd1485 (
${logic.hsql-only} 	clm_id                                   bigint not null,                          
${logic.hsql-only} 	bene_id                                  bigint not null,                          
${logic.hsql-only} 	clm_grp_id                               bigint not null,                          
${logic.hsql-only} 	last_updated                             timestamp with time zone,                 
${logic.hsql-only} 	clm_from_dt                              date not null,                            
${logic.hsql-only} 	clm_thru_dt                              date not null,                            
${logic.hsql-only} 	clm_clncl_tril_num                       character varying(8),                     
${logic.hsql-only} 	clm_disp_cd                              character varying(2) not null,            
${logic.hsql-only} 	clm_pmt_amt                              numeric(10,2) not null,                   
${logic.hsql-only} 	carr_clm_cntl_num                        character varying(23),                    
${logic.hsql-only} 	carr_clm_entry_cd                        character(1) not null,                    
${logic.hsql-only} 	carr_clm_hcpcs_yr_cd                     character(1),                             
${logic.hsql-only} 	carr_clm_pmt_dnl_cd                      character varying(2) not null,            
${logic.hsql-only} 	carr_clm_prvdr_asgnmt_ind_sw             character(1),                             
${logic.hsql-only} 	carr_clm_rfrng_pin_num                   character varying(14) not null, 
${logic.hsql-only} 	carr_clm_cash_ddctbl_apld_amt            numeric(10,2) not null,
${logic.hsql-only} 	carr_clm_prmry_pyr_pd_amt                numeric(10,2) not null, 
${logic.hsql-only} 	carr_num                                 character varying(5) not null,            
${logic.hsql-only} 	final_action                             character(1) not null,                    
${logic.hsql-only} 	nch_carr_clm_alowd_amt                   numeric(10,2) not null,                   
${logic.hsql-only} 	nch_carr_clm_sbmtd_chrg_amt              numeric(10,2) not null,                   
${logic.hsql-only} 	nch_clm_bene_pmt_amt                     numeric(10,2) not null,                   
${logic.hsql-only} 	nch_clm_prvdr_pmt_amt                    numeric(10,2) not null,                       
${logic.hsql-only} 	nch_clm_type_cd                          character varying(2) not null,            
${logic.hsql-only} 	nch_near_line_rec_ident_cd               character(1) not null,                                
${logic.hsql-only} 	nch_wkly_proc_dt                         date not null,                            
${logic.hsql-only} 	prncpal_dgns_cd                          character varying(7),                     
${logic.hsql-only} 	prncpal_dgns_vrsn_cd                     character(1),                             
${logic.hsql-only} 	rfr_physn_npi                            character varying(12),                    
${logic.hsql-only} 	rfr_physn_upin                           character varying(12),                    
${logic.hsql-only} 	icd_dgns_cd1                             character varying(7),                     
${logic.hsql-only} 	icd_dgns_cd2                             character varying(7),                     
${logic.hsql-only} 	icd_dgns_cd3                             character varying(7),                     
${logic.hsql-only} 	icd_dgns_cd4                             character varying(7),                     
${logic.hsql-only} 	icd_dgns_cd5                             character varying(7),                     
${logic.hsql-only} 	icd_dgns_cd6                             character varying(7),                     
${logic.hsql-only} 	icd_dgns_cd7                             character varying(7),                     
${logic.hsql-only} 	icd_dgns_cd8                             character varying(7),                     
${logic.hsql-only} 	icd_dgns_cd9                             character varying(7),                     
${logic.hsql-only} 	icd_dgns_cd10                            character varying(7),                     
${logic.hsql-only} 	icd_dgns_cd11                            character varying(7),                     
${logic.hsql-only} 	icd_dgns_cd12                            character varying(7),                     
${logic.hsql-only} 	icd_dgns_vrsn_cd1                        character(1),                             
${logic.hsql-only} 	icd_dgns_vrsn_cd2                        character(1),                             
${logic.hsql-only} 	icd_dgns_vrsn_cd3                        character(1),                             
${logic.hsql-only} 	icd_dgns_vrsn_cd4                        character(1),                             
${logic.hsql-only} 	icd_dgns_vrsn_cd5                        character(1),                             
${logic.hsql-only} 	icd_dgns_vrsn_cd6                        character(1),                             
${logic.hsql-only} 	icd_dgns_vrsn_cd7                        character(1),                             
${logic.hsql-only} 	icd_dgns_vrsn_cd8                        character(1),                             
${logic.hsql-only} 	icd_dgns_vrsn_cd9                        character(1),                             
${logic.hsql-only} 	icd_dgns_vrsn_cd10                       character(1),                             
${logic.hsql-only} 	icd_dgns_vrsn_cd11                       character(1),                             
${logic.hsql-only} 	icd_dgns_vrsn_cd12                       character(1), 
${logic.hsql-only} 	constraint carrier_claims_tmp_pkey
${logic.hsql-only} 	primary key (clm_id)
${logic.hsql-only} );                            

${logic.psql-only} SET max_parallel_workers = 24;
${logic.psql-only} SET max_parallel_workers_per_gather = 20;
${logic.psql-only} SET parallel_leader_participation = off;
${logic.psql-only} SET parallel_tuple_cost = 0;
${logic.psql-only} SET parallel_setup_cost = 0;
${logic.psql-only} SET min_parallel_table_scan_size = 0;

${logic.hsql-only} insert into carrier_claims_bfd1485(
${logic.hsql-only} 	clm_id,
${logic.hsql-only} 	bene_id,
${logic.hsql-only} 	clm_grp_id,
${logic.hsql-only} 	last_updated,
${logic.hsql-only} 	clm_from_dt,
${logic.hsql-only} 	clm_thru_dt,
${logic.hsql-only} 	clm_clncl_tril_num,
${logic.hsql-only} 	clm_disp_cd,
${logic.hsql-only} 	clm_pmt_amt,
${logic.hsql-only} 	carr_clm_cntl_num,
${logic.hsql-only} 	carr_clm_entry_cd,
${logic.hsql-only} 	carr_clm_hcpcs_yr_cd,
${logic.hsql-only} 	carr_clm_pmt_dnl_cd,
${logic.hsql-only} 	carr_clm_prvdr_asgnmt_ind_sw,
${logic.hsql-only} 	carr_clm_rfrng_pin_num,
${logic.hsql-only} 	carr_clm_cash_ddctbl_apld_amt,
${logic.hsql-only} 	carr_clm_prmry_pyr_pd_amt,
${logic.hsql-only} 	carr_num,
${logic.hsql-only} 	final_action,
${logic.hsql-only} 	nch_carr_clm_alowd_amt,
${logic.hsql-only} 	nch_carr_clm_sbmtd_chrg_amt,
${logic.hsql-only} 	nch_clm_bene_pmt_amt,
${logic.hsql-only} 	nch_clm_prvdr_pmt_amt,
${logic.hsql-only} 	nch_clm_type_cd,
${logic.hsql-only} 	nch_near_line_rec_ident_cd,
${logic.hsql-only} 	nch_wkly_proc_dt,
${logic.hsql-only} 	prncpal_dgns_cd,
${logic.hsql-only} 	prncpal_dgns_vrsn_cd,
${logic.hsql-only} 	rfr_physn_npi,
${logic.hsql-only} 	rfr_physn_upin,
${logic.hsql-only} 	icd_dgns_cd1,
${logic.hsql-only} 	icd_dgns_cd2,
${logic.hsql-only} 	icd_dgns_cd3,
${logic.hsql-only} 	icd_dgns_cd4,
${logic.hsql-only} 	icd_dgns_cd5,
${logic.hsql-only} 	icd_dgns_cd6,
${logic.hsql-only} 	icd_dgns_cd7,
${logic.hsql-only} 	icd_dgns_cd8,
${logic.hsql-only} 	icd_dgns_cd9,
${logic.hsql-only} 	icd_dgns_cd10,
${logic.hsql-only} 	icd_dgns_cd11,
${logic.hsql-only} 	icd_dgns_cd12,
${logic.hsql-only} 	icd_dgns_vrsn_cd1,
${logic.hsql-only} 	icd_dgns_vrsn_cd2,
${logic.hsql-only} 	icd_dgns_vrsn_cd3,
${logic.hsql-only} 	icd_dgns_vrsn_cd4,
${logic.hsql-only} 	icd_dgns_vrsn_cd5,
${logic.hsql-only} 	icd_dgns_vrsn_cd6,
${logic.hsql-only} 	icd_dgns_vrsn_cd7,
${logic.hsql-only} 	icd_dgns_vrsn_cd8,
${logic.hsql-only} 	icd_dgns_vrsn_cd9,
${logic.hsql-only} 	icd_dgns_vrsn_cd10,
${logic.hsql-only} 	icd_dgns_vrsn_cd11,
${logic.hsql-only} 	icd_dgns_vrsn_cd12
${logic.hsql-only} )
${logic.psql-only} create table carrier_claims_bfd1485 as
${logic.psql-only} select
	${logic.psql-only} cast(clm_id as bigint),
	${logic.psql-only} cast(bene_id as bigint),
	${logic.psql-only} cast(clm_grp_id as bigint),
 ${logic.hsql-only} select   
    ${logic.hsql-only} convert(clm_id, SQL_BIGINT),
	${logic.hsql-only} convert(bene_id, SQL_BIGINT),
	${logic.hsql-only} convert(clm_grp_id, SQL_BIGINT),
	last_updated,
	clm_from_dt,
	clm_thru_dt,
	clm_clncl_tril_num,
	clm_disp_cd,
	${logic.hsql-only} clm_pmt_amt,
	${logic.psql-only} cast(clm_pmt_amt as numeric(10,2)),
	carr_clm_cntl_num,
	carr_clm_entry_cd,
	carr_clm_hcpcs_yr_cd,
	carr_clm_pmt_dnl_cd,
	carr_clm_prvdr_asgnmt_ind_sw,
	carr_clm_rfrng_pin_num,
	${logic.hsql-only} carr_clm_cash_ddctbl_apld_amt,
	${logic.psql-only} cast(carr_clm_cash_ddctbl_apld_amt as numeric(10,2)),
	${logic.hsql-only} carr_clm_prmry_pyr_pd_amt,
	${logic.psql-only} cast(carr_clm_prmry_pyr_pd_amt as numeric(10,2)),
	carr_num,
	final_action,
	${logic.hsql-only} nch_carr_clm_alowd_amt,
	${logic.psql-only} cast(nch_carr_clm_alowd_amt as numeric(10,2)),
	${logic.hsql-only} nch_carr_clm_sbmtd_chrg_amt,
	${logic.psql-only} cast(nch_carr_clm_sbmtd_chrg_amt as numeric(10,2)),
	${logic.hsql-only} nch_clm_bene_pmt_amt,
	${logic.psql-only} cast(nch_clm_bene_pmt_amt as numeric(10,2)),
	${logic.hsql-only} nch_clm_prvdr_pmt_amt,
	${logic.psql-only} cast(nch_clm_prvdr_pmt_amt as numeric(10,2)),
	nch_clm_type_cd,
	nch_near_line_rec_ident_cd,
	nch_wkly_proc_dt,
	prncpal_dgns_cd,
	prncpal_dgns_vrsn_cd,
	rfr_physn_npi,
	rfr_physn_upin,
	icd_dgns_cd1,
	icd_dgns_cd2,
	icd_dgns_cd3,
	icd_dgns_cd4,
	icd_dgns_cd5,
	icd_dgns_cd6,
	icd_dgns_cd7,
	icd_dgns_cd8,
	icd_dgns_cd9,
	icd_dgns_cd10,
	icd_dgns_cd11,
	icd_dgns_cd12,
	icd_dgns_vrsn_cd1,
	icd_dgns_vrsn_cd2,
	icd_dgns_vrsn_cd3,
	icd_dgns_vrsn_cd4,
	icd_dgns_vrsn_cd5,
	icd_dgns_vrsn_cd6,
	icd_dgns_vrsn_cd7,
	icd_dgns_vrsn_cd8,
	icd_dgns_vrsn_cd9,
	icd_dgns_vrsn_cd10,
	icd_dgns_vrsn_cd11,
	icd_dgns_vrsn_cd12
from
	carrier_claims;

${logic.psql-only} alter table carrier_claims_bfd1485
${logic.psql-only}     alter column clm_id SET NOT NULL,
${logic.psql-only}     alter column nch_carr_clm_alowd_amt SET NOT NULL,
${logic.psql-only}     alter column bene_id SET NOT NULL,
${logic.psql-only}     alter column carr_clm_cash_ddctbl_apld_amt SET NOT NULL,
${logic.psql-only}     alter column nch_clm_bene_pmt_amt SET NOT NULL,
${logic.psql-only}     alter column carr_num SET NOT NULL,
${logic.psql-only}     alter column clm_disp_cd SET NOT NULL,
${logic.psql-only}     alter column carr_clm_entry_cd SET NOT NULL,
${logic.psql-only}     alter column clm_grp_id SET NOT NULL,
${logic.psql-only}     alter column nch_clm_type_cd SET NOT NULL,
${logic.psql-only}     alter column clm_from_dt SET NOT NULL,
${logic.psql-only}     alter column clm_thru_dt SET NOT NULL,
${logic.psql-only}     alter column nch_near_line_rec_ident_cd SET NOT NULL,
${logic.psql-only}     alter column clm_pmt_amt SET NOT NULL,
${logic.psql-only}     alter column carr_clm_pmt_dnl_cd SET NOT NULL,
${logic.psql-only}     alter column carr_clm_prmry_pyr_pd_amt SET NOT NULL,
${logic.psql-only}     alter column nch_clm_prvdr_pmt_amt SET NOT NULL,
${logic.psql-only}     alter column carr_clm_rfrng_pin_num SET NOT NULL,
${logic.psql-only}     alter column nch_carr_clm_sbmtd_chrg_amt SET NOT NULL,
${logic.psql-only}     alter column nch_wkly_proc_dt SET NOT NULL,
${logic.psql-only}     alter column final_action SET NOT NULL;

${logic.psql-only} alter table carrier_claims_bfd1485
${logic.psql-only}     add CONSTRAINT carrier_claims_bfd1485_pkey PRIMARY KEY (clm_id);
