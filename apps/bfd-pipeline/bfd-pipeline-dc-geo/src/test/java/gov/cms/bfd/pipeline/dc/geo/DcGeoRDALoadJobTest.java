package gov.cms.bfd.pipeline.dc.geo;

import static org.mockito.Mockito.*;

import gov.cms.bfd.pipeline.sharedutils.PipelineJobOutcome;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Callable;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DcGeoRDALoadJobTest {
  private Callable<RDASource<Integer>> sourceFactory;
  private Callable<RDASink<Integer>> sinkFactory;
  private RDASource<Integer> source;
  private RDASink<Integer> sink;
  private DcGeoRDALoadJob<Integer> job;

  @SuppressWarnings("unchecked")
  @Before
  public void setUp() {
    sourceFactory = mock(Callable.class);
    sinkFactory = mock(Callable.class);
    source = mock(RDASource.class);
    sink = mock(RDASink.class);
    DcGeoRDALoadJob.Config config =
        new DcGeoRDALoadJob.Config(Duration.ofSeconds(10), Duration.ofSeconds(25), 5, 3);
    job = new DcGeoRDALoadJob<>(config, sourceFactory, sinkFactory);
  }

  @Test
  public void openSourceFails() throws Exception {
    doThrow(new IOException("oops")).when(sourceFactory).call();
    try {
      job.call();
      Assert.fail("job should have thrown exception");
    } catch (Exception ex) {
      Assert.assertEquals("oops", ex.getMessage());
      MatcherAssert.assertThat(ex, Matchers.instanceOf(IOException.class));
    }
    verifyNoInteractions(sinkFactory);
  }

  @Test
  public void openSinkFails() throws Exception {
    doReturn(source).when(sourceFactory).call();
    doThrow(new IOException("oops")).when(sinkFactory).call();
    try {
      job.call();
      Assert.fail("job should have thrown exception");
    } catch (Exception ex) {
      Assert.assertEquals("oops", ex.getMessage());
      MatcherAssert.assertThat(ex, Matchers.instanceOf(IOException.class));
    }
    verify(source).close();
  }

  @Test
  public void sourceFails() throws Exception {
    doReturn(source).when(sourceFactory).call();
    doReturn(sink).when(sinkFactory).call();
    doThrow(new ProcessingException(new IOException("oops"), 7))
        .when(source)
        .retrieveAndProcessObjects(anyInt(), anyInt(), any(), same(sink));
    try {
      job.call();
      Assert.fail("job should have thrown exception");
    } catch (Exception ex) {
      Assert.assertEquals("oops", ex.getMessage());
      MatcherAssert.assertThat(ex, Matchers.instanceOf(IOException.class));
    }
    verify(source).close();
    verify(sink).close();
  }

  @Test
  public void nothingToDo() throws Exception {
    doReturn(source).when(sourceFactory).call();
    doReturn(sink).when(sinkFactory).call();
    doReturn(0).when(source).retrieveAndProcessObjects(anyInt(), anyInt(), any(), same(sink));
    try {
      PipelineJobOutcome outcome = job.call();
      Assert.assertEquals(PipelineJobOutcome.NOTHING_TO_DO, outcome);
    } catch (Exception ex) {
      Assert.fail("job should NOT have thrown exception");
    }
    verify(source).close();
    verify(sink).close();
  }

  @Test
  public void workDone() throws Exception {
    doReturn(source).when(sourceFactory).call();
    doReturn(sink).when(sinkFactory).call();
    doReturn(25000).when(source).retrieveAndProcessObjects(anyInt(), anyInt(), any(), same(sink));
    try {
      PipelineJobOutcome outcome = job.call();
      Assert.assertEquals(PipelineJobOutcome.WORK_DONE, outcome);
    } catch (Exception ex) {
      Assert.fail("job should NOT have thrown exception");
    }
    verify(source).close();
    verify(sink).close();
  }
}
