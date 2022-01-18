package gov.cms.bfd.pipeline.rda.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.Arrays;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

public class RdaSinkTest {
  private final RdaSink<Integer> sink = new TestSink();

  @Test
  public void batchSuccessful() throws Exception {
    int count = sink.writeBatch(Arrays.asList(1, 2, 3, 4));
    assertEquals(4, count);
  }

  @Test
  public void batchFailures() throws Exception {
    try {
      sink.writeBatch(Arrays.asList(1, 2, 5, 4));
      fail("sink should have thrown");
    } catch (ProcessingException ex) {
      assertEquals(12, ex.getProcessedCount());
      MatcherAssert.assertThat(ex.getCause(), Matchers.instanceOf(IOException.class));
    }
    try {
      sink.writeBatch(Arrays.asList(1, 2, 6, 5));
      fail("sink should have thrown");
    } catch (ProcessingException ex) {
      assertEquals(2, ex.getProcessedCount());
      MatcherAssert.assertThat(ex.getCause(), Matchers.instanceOf(RuntimeException.class));
    }
  }

  private static class TestSink implements RdaSink<Integer> {
    @Override
    public int writeObject(Integer object) throws ProcessingException {
      if (object == 5) {
        throw new ProcessingException(new IOException("oops"), 10);
      }
      if (object == 6) {
        throw new RuntimeException("oops");
      }
      return 1;
    }

    @Override
    public void close() throws Exception {}
  }
}
