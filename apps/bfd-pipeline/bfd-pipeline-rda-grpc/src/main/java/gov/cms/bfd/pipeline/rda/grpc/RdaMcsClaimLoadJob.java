package gov.cms.bfd.pipeline.rda.grpc;

import com.codahale.metrics.MetricRegistry;
import gov.cms.bfd.model.rda.RdaMcsClaim;
import gov.cms.bfd.sharedutils.interfaces.ThrowingFunction;
import gov.cms.mpsm.rda.v1.McsClaimChange;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PipelineJob requires that the class of the job be used to define the PipelineJobType. This class
 * is a simple wrapper to ensure that {@link RdaMcsClaim} processing has a unique PipelineJobType
 * value based on its class.
 */
public class RdaMcsClaimLoadJob extends AbstractRdaLoadJob<McsClaimChange, RdaChange<RdaMcsClaim>> {
  private static final Logger LOGGER = LoggerFactory.getLogger(RdaMcsClaimLoadJob.class);

  public RdaMcsClaimLoadJob(
      Config config,
      Callable<RdaSource<McsClaimChange, RdaChange<RdaMcsClaim>>> preJobTaskFactory,
      Callable<RdaSource<McsClaimChange, RdaChange<RdaMcsClaim>>> sourceFactory,
      ThrowingFunction<
              RdaSink<McsClaimChange, RdaChange<RdaMcsClaim>>, SinkTypePreference, Exception>
          sinkFactory,
      MetricRegistry appMetrics) {
    super(config, preJobTaskFactory, sourceFactory, sinkFactory, appMetrics, LOGGER);
  }
}
