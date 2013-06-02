package feign;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.io.Closer;

/**
 * Writes http headers and body. Plumb to your favorite log impl.
 */
public abstract class Wire {
    /**
     * logs to the category {@link Wire} at {@link Level#FINE}
     */
    public static class ErrorWire extends Wire {
        final Logger logger = Logger.getLogger(Wire.class.getName());

        @Override
        protected void log(String format, Object... args) {
            System.err.printf(format + "%n", args);
        }
    }

    /**
     * logs to the category {@link Wire} at {@link Level#FINE}
     */
    public static class LoggingWire extends Wire {
        final Logger logger = Logger.getLogger(Wire.class.getName());

        @Override
        protected void log(String format, Object... args) {
            logger.fine(String.format(format, args));
        }
    }

    public static class NoOpWire extends Wire {
        void wireRequest(Request request) {

        }

        Response wireAndRebufferResponse(Response response) throws IOException {
            return response;
        }

        @Override
        protected void log(String format, Object... args) {
        }
    }

    protected abstract void log(String format, Object... args);

    void wireRequest(Request request) {
        log(">> %s %s HTTP/1.1", request.method(), request.url());

        for (Entry<String, String> header : request.headers().entries()) {
            log(">> %s: %s", header.getKey(), header.getValue());
        }

        if (request.body().isPresent()) {
            log(">> "); // CRLF
            log(">> %s", request.body().get());
        }
    }

    Response wireAndRebufferResponse(Response response) throws IOException {
        log("<< HTTP/1.1 %s %s", response.status(), response.reason());

        for (Entry<String, String> header : response.headers().entries()) {
            log("<< %s: %s", header.getKey(), header.getValue());
        }

        if (response.body().isPresent()) {
            log("<< "); // CRLF
            Closer closer = Closer.create();
            try {
                StringBuilder body = new StringBuilder();
                BufferedReader reader = new BufferedReader(closer.register(response.body().get().asReader()));
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                    log("<< %s", line);
                }
                return Response.create(response.status(), response.reason(), response.headers(), body.toString());
            } catch (Throwable e) {
                throw closer.rethrow(e);
            } finally {
                closer.close();
            }
        }
        return response;
    }
}