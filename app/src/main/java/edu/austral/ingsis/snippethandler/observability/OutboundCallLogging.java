package edu.austral.ingsis.snippethandler.observability;

import java.io.IOException;
import java.time.Duration;
import java.util.function.LongSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Logs the calls to other services that went wrong: a 5xx, no response at all, or a response that
 * took too long. Successful calls are not logged, the agent already measures every one of them;
 * logging them too would be noise.
 *
 * <p>Only the path is logged, never the query string: searching users sends what the user typed
 * (part of an email) to Auth0 as a query parameter.
 */
public class OutboundCallLogging implements ClientHttpRequestInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(OutboundCallLogging.class);

    private final long slowMillis;
    private final LongSupplier nanoTime;

    public OutboundCallLogging(Duration slow) {
        this(slow, System::nanoTime);
    }

    OutboundCallLogging(Duration slow, LongSupplier nanoTime) {
        this.slowMillis = slow.toMillis();
        this.nanoTime = nanoTime;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        long start = nanoTime.getAsLong();
        ClientHttpResponse response;
        try {
            response = execution.execute(request, body);
        } catch (IOException e) {
            LOG.warn(
                    "call failed {} {}{} error={} tookMs={}",
                    request.getMethod(),
                    request.getURI().getHost(),
                    request.getURI().getPath(),
                    e.toString(),
                    elapsedMillis(start));
            throw e;
        }
        long took = elapsedMillis(start);
        int status = response.getStatusCode().value();
        if (status >= 500) {
            LOG.warn(
                    "call failed {} {}{} status={} tookMs={}",
                    request.getMethod(),
                    request.getURI().getHost(),
                    request.getURI().getPath(),
                    status,
                    took);
        } else if (took >= slowMillis) {
            LOG.warn(
                    "slow call {} {}{} status={} tookMs={}",
                    request.getMethod(),
                    request.getURI().getHost(),
                    request.getURI().getPath(),
                    status,
                    took);
        }
        return response;
    }

    private long elapsedMillis(long start) {
        return Duration.ofNanos(nanoTime.getAsLong() - start).toMillis();
    }
}
