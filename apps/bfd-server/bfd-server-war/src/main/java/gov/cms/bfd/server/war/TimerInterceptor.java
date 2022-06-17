package gov.cms.bfd.server.war;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import org.slf4j.MDC;

@Interceptor
public class TimerInterceptor {

  @Hook(Pointcut.SERVER_OUTGOING_RESPONSE)
  public void serverOutgoingResponse(ServletRequestDetails theRequestDetails) {
    // Record the response duration.
    Long requestStartMilliseconds =
        (Long)
            theRequestDetails
                .getServletRequest()
                .getAttribute(RequestResponsePopulateMdcFilter.REQUEST_ATTRIB_START);
    if (requestStartMilliseconds != null)
      MDC.put(
          "http_access.response.server_outgoing_response_duration",
          Long.toString(System.currentTimeMillis() - requestStartMilliseconds));
  }

  @Hook(Pointcut.SERVER_PROCESSING_COMPLETED_NORMALLY)
  public void processingCompletedNormally(ServletRequestDetails theRequestDetails) {
    // Record the response duration.
    Long requestStartMilliseconds =
        (Long)
            theRequestDetails
                .getServletRequest()
                .getAttribute(RequestResponsePopulateMdcFilter.REQUEST_ATTRIB_START);
    if (requestStartMilliseconds != null)
      MDC.put(
          "http_access.response.server_processing_completed_normally_duration",
          Long.toString(System.currentTimeMillis() - requestStartMilliseconds));
  }

  @Hook(Pointcut.SERVER_PROCESSING_COMPLETED)
  public void processingCompleted(ServletRequestDetails theRequestDetails) {
    // Record the response duration.
    Long requestStartMilliseconds =
        (Long)
            theRequestDetails
                .getServletRequest()
                .getAttribute(RequestResponsePopulateMdcFilter.REQUEST_ATTRIB_START);
    if (requestStartMilliseconds != null)
      MDC.put(
          "http_access.response.server_processing_completed_duration",
          Long.toString(System.currentTimeMillis() - requestStartMilliseconds));
  }
}
