package feign;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.net.HttpHeaders.LOCATION;
import static feign.FeignException.errorExecuting;
import static feign.FeignException.errorReading;
import static feign.FeignException.errorStatus;

import java.io.IOException;
import java.net.URI;

import javax.inject.Inject;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;

import feign.Wire.NoOpWire;
import feign.codec.Decoder;

final class MethodHandler {

    static class Factory {

        private final Client client;
        private final Wire wire;

        @Inject
        Factory(Client client, Wire wire) {
            this.client = checkNotNull(client, "client");
            this.wire = checkNotNull(wire, "wire");
        }

        public MethodHandler create(Target<?> target, MethodMetadata md,
                Function<Object[], RequestTemplate> buildTemplateFromArgs, Decoder decoder) {
            return new MethodHandler(target, client, wire, md, buildTemplateFromArgs, decoder);
        }
    }

    private final MethodMetadata metadata;
    private final Target<?> target;
    private final Client client;
    private final Wire wire;

    private final Function<Object[], RequestTemplate> buildTemplateFromArgs;
    private final Decoder decoder;

    // cannot inject wildcards in dagger
    @SuppressWarnings("rawtypes")
    private MethodHandler(Target target, Client client, Wire wire, MethodMetadata metadata,
            Function<Object[], RequestTemplate> buildTemplateFromArgs, Decoder decoder) {
        this.target = checkNotNull(target, "target");
        this.client = checkNotNull(client, "client for %s", target);
        this.wire = checkNotNull(wire, "wire for %s", target);
        this.metadata = checkNotNull(metadata, "metadata for %s", target);
        this.buildTemplateFromArgs = checkNotNull(buildTemplateFromArgs, "metadata for %s", target);
        this.decoder = decoder;
    }

    public Object invoke(Object[] argv) throws Throwable {
        RequestTemplate template = buildTemplateFromArgs.apply(argv);
        return executeAndDecode(template);
    }

    private Object executeAndDecode(RequestTemplate template) throws Throwable {
        // create the request from a mutable copy of the input template.
        Request request = target.apply(new RequestTemplate(template));
        if (wire.getClass() != NoOpWire.class) {
            wire.wireRequest(request);
        }
        Response response = execute(request);
        try {
            if (wire.getClass() != NoOpWire.class) {
                response = wire.wireAndRebufferResponse(response);
            }
            if (response.status() >= 200 && response.status() < 300) {
                TypeToken<?> returnType = metadata.returnType();
                if (returnType.getRawType().equals(Response.class)) {
                    return response;
                } else if (returnType.getRawType() == URI.class && !response.body().isPresent()) {
                    ImmutableList<String> location = response.headers().get(LOCATION);
                    if (!location.isEmpty())
                        return URI.create(location.get(0));
                }
                return decoder.decode(request, response, returnType);
            } else {
                throw errorStatus(request, response);
            }
        } catch (Throwable e) {
            ensureBodyClosed(response);
            if (IOException.class.isInstance(e))
                throw errorReading(request, response, e);
            throw e;
        }
    }

    private void ensureBodyClosed(Response response) {
        if (response.body().isPresent()) {
            try {
                response.body().get().close();
            } catch (IOException ignored) {
            }
        }
    }

    private Response execute(Request request) {
        try {
            return client.execute(request);
        } catch (IOException e) {
            throw errorExecuting(request, e);
        }
    }
}
