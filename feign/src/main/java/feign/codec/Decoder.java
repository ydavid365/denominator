package feign.codec;

import java.io.IOException;
import java.io.Reader;

import com.google.common.io.Closer;
import com.google.common.reflect.TypeToken;

import feign.Request;
import feign.Response;

/**
 * Decodes an HTTP response into a given type.
 * <p/>
 * Ex.
 * 
 * <pre>
 * public class GsonDecoder extends Decoder {
 *     private final Gson gson;
 * 
 *     public GsonDecoder(Gson gson) {
 *         this.gson = gson;
 *     }
 * 
 *     &#064;Override
 *     protected Object decode(Request request, Reader reader, TypeToken&lt;?&gt; type) {
 *         return gson.fromJson(reader, type.getType());
 *     }
 * }
 * </pre>
 * 
 * 
 */
public abstract class Decoder {

    /**
     * Override this method in order to consider the HTTP {@link Response} as
     * opposed to just the {@link Response.Body} when decoding into a new
     * instance of {@link type}.
     * 
     * @param request
     *            HTTP request that created the response.
     * @param response
     *            HTTP response.
     * @param type
     *            Target object type.
     * @return instance of {@code type}
     * @throws IOException
     *             if there was a network error reading the response.
     */
    public Object decode(Request request, Response in, TypeToken<?> type) throws IOException {
        Response.Body body = in.body().orNull();
        if (body == null)
            return null;
        Closer closer = Closer.create();
        try {
            Reader reader = closer.register(body.asReader());
            if (type.getRawType() == void.class) {
                return null;
            }
            return decode(request, reader, type);
        } catch (Throwable e) {
            throw closer.rethrow(e);
        } finally {
            closer.close();
        }
    }

    /**
     * Implement this to decode a {@code Reader} to an object of the specified
     * type.
     * 
     * @param request
     *            HTTP request that created the response.
     * @param reader
     *            no need to close this, as {@link #decode(Response, TypeToken)}
     *            manages resources.
     * @param type
     *            Target object type.
     * @return instance of {@code type}
     * @throws Throwable
     *             will be propagated safely to the caller.
     */
    protected abstract Object decode(Request request, Reader reader, TypeToken<?> type) throws Throwable;
}