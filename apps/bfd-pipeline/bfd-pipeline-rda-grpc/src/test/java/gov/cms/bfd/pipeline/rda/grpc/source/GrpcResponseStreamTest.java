package gov.cms.bfd.pipeline.rda.grpc.source;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.doThrow;

import gov.cms.bfd.pipeline.rda.grpc.source.GrpcResponseStream.DroppedConnectionException;
import gov.cms.bfd.pipeline.rda.grpc.source.GrpcResponseStream.StreamInterruptedException;
import io.grpc.ClientCall;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.Iterator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link GrpcResponseStream}. */
public class GrpcResponseStreamTest {
  @Mock private Iterator<Integer> iterator;
  @Mock private ClientCall<Integer, Integer> clientCall;
  private GrpcResponseStream<Integer> stream;

  @BeforeEach
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    stream = new GrpcResponseStream<>(clientCall, iterator);
  }

  /** Verify that {@link GrpcResponseStream#hasNext()} passes through non-interrupt exceptions. */
  @Test
  public void testThatHasNextPassesThroughNonInterrupts() {
    StatusRuntimeException status = Status.INVALID_ARGUMENT.asRuntimeException();
    doThrow(status).when(iterator).hasNext();
    try {
      stream.hasNext();
      fail("exception should have been thrown");
    } catch (Throwable ex) {
      assertSame(status, ex);
    }
  }

  /** Verify that {@link GrpcResponseStream#next()} passes through non-interrupt exceptions. */
  @Test
  public void testThatNextPassesThroughNonInterrupts() {
    StatusRuntimeException status = Status.INTERNAL.asRuntimeException();
    doThrow(status).when(iterator).next();
    try {
      stream.next();
      fail("exception should have been thrown");
    } catch (Throwable ex) {
      assertSame(status, ex);
    }
  }

  /** Verify that {@link GrpcResponseStream#hasNext()} wraps {@link InterruptedException}s. */
  @Test
  public void testThatHasNextWrapsInterrupts() throws DroppedConnectionException {
    StatusRuntimeException status =
        Status.CANCELLED.withCause(new InterruptedException()).asRuntimeException();
    doThrow(status).when(iterator).hasNext();
    try {
      stream.hasNext();
      fail("exception should have been thrown");
    } catch (StreamInterruptedException ex) {
      assertSame(status, ex.getCause());
    }
  }

  /** Verify that {@link GrpcResponseStream#next()} wraps {@link InterruptedException}s. */
  @Test
  public void testThatNextWrapsInterrupts() throws DroppedConnectionException {
    StatusRuntimeException status =
        Status.CANCELLED.withCause(new InterruptedException()).asRuntimeException();
    doThrow(status).when(iterator).next();
    try {
      stream.next();
      fail("exception should have been thrown");
    } catch (StreamInterruptedException ex) {
      assertSame(status, ex.getCause());
    }
  }

  /**
   * Verify that {@link GrpcResponseStream#hasNext()} wraps exceptions that indicate a supported
   * type of dropped connection.
   */
  @Test
  public void testThatHasNextWrapsDroppedConnections() throws StreamInterruptedException {
    StatusRuntimeException status =
        new StatusRuntimeException(
            Status.INTERNAL.withDescription(GrpcResponseStream.STREAM_RESET_ERROR_MESSAGE));
    doThrow(status).when(iterator).hasNext();
    try {
      stream.hasNext();
      fail("exception should have been thrown");
    } catch (DroppedConnectionException ex) {
      assertSame(status, ex.getCause());
    }
  }

  /**
   * Verify that {@link GrpcResponseStream#hasNext()} wraps exceptions that indicate a supported
   * type of dropped connection.
   */
  @Test
  public void testThatNextWrapsDroppedConnections() throws StreamInterruptedException {
    StatusRuntimeException status = new StatusRuntimeException(Status.DEADLINE_EXCEEDED);
    doThrow(status).when(iterator).next();
    try {
      stream.next();
      fail("exception should have been thrown");
    } catch (DroppedConnectionException ex) {
      assertSame(status, ex.getCause());
    }
  }

  /**
   * Verify that criteria for recognizing need to throw {@link
   * gov.cms.bfd.pipeline.rda.grpc.source.GrpcResponseStream.DroppedConnectionException} works
   * properly.
   */
  @Test
  public void testThatDroppedConnectionCriteriaWorkCorrectly() {
    var exception =
        new StatusRuntimeException(Status.INTERNAL.withDescription("some other message"));
    assertFalse(GrpcResponseStream.isStreamResetException(exception));

    exception =
        new StatusRuntimeException(
            Status.INTERNAL.withDescription(GrpcResponseStream.STREAM_RESET_ERROR_MESSAGE));
    assertTrue(GrpcResponseStream.isStreamResetException(exception));

    exception = new StatusRuntimeException(Status.DEADLINE_EXCEEDED);
    assertTrue(GrpcResponseStream.isStreamResetException(exception));
  }
}
