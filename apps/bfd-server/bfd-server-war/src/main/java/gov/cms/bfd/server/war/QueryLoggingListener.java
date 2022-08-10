package gov.cms.bfd.server.war;

import gov.cms.bfd.server.sharedutils.BfdMDC;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This {@link QueryExecutionListener} records query performance data in {@link BfdMDC}. */
public final class QueryLoggingListener implements QueryExecutionListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(QueryLoggingListener.class);

  private static final String MDC_KEY_PREFIX = "database_query";

  /** {@inheritDoc} */
  @Override
  public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
    /*
     * Note: Somewhat surprisingly -- and fortuitously -- this event gets fired on
     * whichever thread called into JPA and/or the DataSource. This means that the
     * MDC entries added here will be attached to the HTTP response threads (when
     * relevant), and thus included in the access log.
     */

    if (queryInfoList.isEmpty()) return;

    /*
     * Most of the time, we don't want to include the full SQL queries as they add a tremendous
     * amount of bloat to the logs. But, sometimes we do...
     */
    boolean logFullQuery = false;
    if (LOGGER.isTraceEnabled()) {
      logFullQuery = true;
    }

    String mdcKeyPrefix;
    if (queryInfoList.size() == 1) {
      QueryType queryType = QueryType.computeQueryType(queryInfoList.get(0));
      mdcKeyPrefix = queryType.getQueryTypeId();

      if (queryType == QueryType.UNKNOWN) {
        logFullQuery = true;
      }
      if (execInfo.getElapsedTime() >= 1000) {
        logFullQuery = true;
      }

      if (logFullQuery)
        BfdMDC.put(
            BfdMDC.computeMDCKey(MDC_KEY_PREFIX, mdcKeyPrefix, "query"),
            queryInfoList.get(0).getQuery());
    } else {
      mdcKeyPrefix = "group";
      logFullQuery = true;

      StringBuilder queryIds = new StringBuilder();
      if (queryInfoList.size() > 1) queryIds.append('[');
      for (QueryInfo queryInfo : queryInfoList) {
        queryIds.append(QueryType.computeQueryType(queryInfo).getQueryTypeId());
        queryIds.append(',');
      }
      if (queryIds.charAt(queryIds.length() - 1) == ',')
        queryIds.deleteCharAt(queryIds.length() - 1);
      if (queryInfoList.size() > 1) queryIds.append(']');
      BfdMDC.put(BfdMDC.computeMDCKey(MDC_KEY_PREFIX, mdcKeyPrefix, "ids"), queryIds.toString());

      StringBuilder queries = new StringBuilder();
      if (queryInfoList.size() > 1) queries.append('[');
      for (QueryInfo queryInfo : queryInfoList) {
        queries.append('[');
        queries.append(queryInfo.getQuery());
        queries.append("],");
      }
      if (queries.charAt(queries.length() - 1) == ',') queries.deleteCharAt(queries.length() - 1);
      if (queryInfoList.size() > 1) queries.append(']');
      BfdMDC.put(BfdMDC.computeMDCKey(MDC_KEY_PREFIX, mdcKeyPrefix, "queries"), queries.toString());
    }
    BfdMDC.put(
        BfdMDC.computeMDCKey(MDC_KEY_PREFIX, mdcKeyPrefix, "size"),
        String.valueOf(queryInfoList.size()));

    BfdMDC.put(
        BfdMDC.computeMDCKey(MDC_KEY_PREFIX, mdcKeyPrefix, "duration_milliseconds"),
        String.valueOf(execInfo.getElapsedTime()));
    BfdMDC.put(
        BfdMDC.computeMDCKey(MDC_KEY_PREFIX, mdcKeyPrefix, "success"),
        String.valueOf(execInfo.isSuccess()));
    BfdMDC.put(
        BfdMDC.computeMDCKey(MDC_KEY_PREFIX, mdcKeyPrefix, "type"),
        execInfo.getStatementType().name());
    BfdMDC.put(
        BfdMDC.computeMDCKey(MDC_KEY_PREFIX, mdcKeyPrefix, "batch"),
        String.valueOf(execInfo.isBatch()));
    BfdMDC.put(
        BfdMDC.computeMDCKey(MDC_KEY_PREFIX, mdcKeyPrefix, "batch_size"),
        String.valueOf(execInfo.getBatchSize()));
    BfdMDC.put(
        BfdMDC.computeMDCKey(MDC_KEY_PREFIX, mdcKeyPrefix, "datasource_name"),
        execInfo.getDataSourceName());
  }

  /** {@inheritDoc} */
  @Override
  public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
    // Nothing to do here.
  }

  /** Enumerates the various query types. */
  static enum QueryType {
    BENE_BY_ID_OMIT_IDENTIFIERS(
        "bene_by_id_omit_hicns_and_mbis",
        (s ->
            s.contains(" from beneficiaries ")
                && s.contains("bene_id=")
                && !s.contains(" join ")
                && !s.contains("bene_crnt_hic_num="))),

    BENE_BY_ID_INCLUDE_IDENTIFIERS(
        "bene_by_id_include_hicns_and_mbis",
        (s ->
            s.contains(" from beneficiaries ")
                && s.contains("bene_id=")
                && s.contains(" join ")
                && !s.contains("bene_crnt_hic_num="))),

    BENE_BY_MBI_HISTORY(
        "bene_by_mbi_mbis_from_beneficiarieshistory",
        (s -> s.contains(" from beneficiaries_history ") && s.contains("mbi_hash="))),

    BENE_BY_HICN_HISTORY(
        "bene_by_hicn_hicns_from_beneficiarieshistory",
        (s -> s.contains(" from beneficiaries_history ") && s.contains("bene_crnt_hic_num="))),

    BENE_BY_HICN_OR_ID_OMIT_IDENTIFIERS(
        "bene_by_hicn_bene_by_hicn_or_id_omit_hicns_and_mbis",
        (s ->
            s.contains(" from beneficiaries ")
                && !s.contains(" join ")
                && s.contains("bene_crnt_hic_num="))),

    BENE_BY_HICN_OR_ID_INCLUDE_IDENTIFIERS(
        "bene_by_hicn_bene_by_hicn_or_id_include_hicns_and_mbis",
        (s ->
            s.contains(" from beneficiaries ")
                && s.contains(" join ")
                && s.contains("bene_crnt_hic_num="))),

    BENE_BY_COVERAGE(
        "bene_by_coverage",
        (s -> s.contains(" from beneficiaries ") && s.contains("where beneficiar0_.ptd_cntrct_"))),

    EOBS_BY_BENE_ID_CARRIER("eobs_by_bene_id_carrier", (s -> s.contains(" from carrier_claims "))),

    EOBS_BY_BENE_ID_DME("eobs_by_bene_id_dme", (s -> s.contains(" from dme_claims "))),

    EOBS_BY_BENE_ID_HHA("eobs_by_bene_id_hha", (s -> s.contains(" from hha_claims "))),

    EOBS_BY_BENE_ID_HOSPICE("eobs_by_bene_id_hospice", (s -> s.contains(" from hospice_claims "))),

    EOBS_BY_BENE_ID_INPATIENT(
        "eobs_by_bene_id_inpatient", (s -> s.contains(" from inpatient_claims "))),

    EOBS_BY_BENE_ID_OUTPATIENT(
        "eobs_by_bene_id_outpatient", (s -> s.contains(" from outpatient_claims "))),

    EOBS_BY_BENE_ID_PDE("eobs_by_bene_id_pde", (s -> s.contains(" from partd_events "))),

    EOBS_BY_BENE_ID_SNF("eobs_by_bene_id_snf", (s -> s.contains(" from snf_claims "))),

    FISS_CLAIM("partially_adjudicated_fiss", s -> s.contains("from rda.fiss")),

    MCS_CLAIM("partially_adjudicated_mcs", s -> s.contains("from rda.mcs")),

    MBI_CACHE("mbi_cache_lookup", s -> s.contains("from rda.mbi_cache")),

    LOADED_BATCH("loaded_batch", (s -> s.contains(" from loaded_batches "))),

    LOADED_FILE("loaded_file", (s -> s.contains(" from loaded_files "))),

    UNKNOWN("unknown", null);

    private final String id;
    private final Predicate<String> queryTextRegex;

    /**
     * Enum constant contructor.
     *
     * @param id the value to use for {@link #getQueryTypeId()}
     * @param the {@link Predicate} that should return <code>true</code> if a given {@link
     *     QueryInfo#getQuery()} represents this {@link QueryType}
     */
    private QueryType(String id, Predicate<String> queryTextRegex) {
      this.id = id;
      this.queryTextRegex = queryTextRegex;
    }

    /** @return a unique identifier for this {@link QueryType}, suitable for use in logs and such */
    public String getQueryTypeId() {
      return id;
    }

    /**
     * @param queryInfo the {@link QueryInfo} to compute a {@link QueryType} for
     * @return the {@link QueryType} that matches the specified {@link QueryInfo}, or {@link
     *     #UNKNOWN} if no match could be determined
     */
    public static QueryType computeQueryType(QueryInfo queryInfo) {
      List<QueryType> matchingQueryTypes = new LinkedList<>();
      for (QueryType queryType : values()) {
        if (queryType.queryTextRegex == null) continue;

        if (queryType.queryTextRegex.test(queryInfo.getQuery())) matchingQueryTypes.add(queryType);
      }

      if (matchingQueryTypes.size() == 1) return matchingQueryTypes.get(0);
      else if (matchingQueryTypes.size() > 1)
        LOGGER.warn(
            "Too many matching query types '{}' for query: {}",
            matchingQueryTypes,
            queryInfo.getQuery());
      else LOGGER.warn("No matching query type for query: {}", queryInfo.getQuery());

      return UNKNOWN;
    }
  }
}
