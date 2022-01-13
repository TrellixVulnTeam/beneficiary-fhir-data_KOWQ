package gov.cms.bfd.pipeline.rda.grpc;

import com.codahale.metrics.MetricRegistry;
import gov.cms.bfd.model.rda.PartAdjMcsClaim;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PipelineJob requires that the class of the job be used to define the PipelineJobType. This class
 * is a simple wrapper to ensure that PartAdjMcsClaim processing has a unique PipelineJobType value
 * based on its class.
 */
public class RdaMcsClaimLoadJob extends AbstractRdaLoadJob<RdaChange<PartAdjMcsClaim>> {
  private static final Logger LOGGER = LoggerFactory.getLogger(RdaMcsClaimLoadJob.class);

  public RdaMcsClaimLoadJob(
      Config config,
      Callable<RdaSource<RdaChange<PartAdjMcsClaim>>> sourceFactory,
      Callable<RdaSink<RdaChange<PartAdjMcsClaim>>> sinkFactory,
      MetricRegistry appMetrics) {
    super(config, sourceFactory, sinkFactory, appMetrics, LOGGER);
  }
}
