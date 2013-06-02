package feign;

import java.io.IOException;

import com.google.common.reflect.TypeToken;

import feign.codec.Decoder;
import feign.codec.ToStringDecoder;

/**
 * Origin exception type for all HttpApis.
 */
public class FeignException extends RuntimeException {
    static FeignException errorReading(Request request, Response response, Throwable cause) {
        return new FeignException(String.format("error %s reading %s %s", cause.getMessage(), request.method(),
                request.url(), 0));
    }

    private static final Decoder toString = new ToStringDecoder();
    private static final TypeToken<String> stringToken = TypeToken.of(String.class);

    static FeignException errorStatus(Request request, Response response) {
        String message = String.format("status %s reading %s %s", response.status(), request.method(), request.url());
        try {
            Object body = toString.decode(request, response, stringToken);
            if (body != null) {
                response = Response.create(response.status(), response.reason(), response.headers(), body.toString());
                message += "; content:\n" + body;
            }
        } catch (IOException ignored) {

        }
        return new FeignException(message);
    }

    static FeignException errorExecuting(Request request, IOException cause) {
        return new FeignException(String.format("error %s executing %s %s", cause.getMessage(), request.method(),
                request.url()), cause);
    }

    private FeignException(String message, Throwable cause) {
        super(message, cause);
    }

    private FeignException(String message) {
        super(message);
    }

    private static final long serialVersionUID = 0;
}