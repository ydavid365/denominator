package feign;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static feign.Contract.parseAndValidatateMetadata;
import static feign.codec.RegexDecoder.newRegexDecoder;
import static java.util.regex.Pattern.DOTALL;
import static java.util.regex.Pattern.compile;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.inject.Inject;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Maps;
import com.google.common.reflect.AbstractInvocationHandler;
import com.google.common.reflect.Reflection;

import dagger.Provides;
import feign.MethodHandler.Factory;
import feign.codec.BodyEncoder;
import feign.codec.Decoder;
import feign.codec.FormEncoder;
import feign.codec.ToStringDecoder;

@SuppressWarnings("rawtypes")
public class ReflectiveFeign extends Feign {

    private final Function<Target, Map<String, MethodHandler>> targetToHandlersByName;

    @Inject
    ReflectiveFeign(Function<Target, Map<String, MethodHandler>> targetToHandlersByName) {
        this.targetToHandlersByName = targetToHandlersByName;
    }

    /**
     * creates an api binding to the {@code target}. As this invokes reflection,
     * care should be taken to cache the result.
     */
    @Override
    public <T> T newInstance(Target<T> target) {
        Map<String, MethodHandler> nameToHandler = targetToHandlersByName.apply(target);
        Builder<Method, MethodHandler> methodToHandler = ImmutableMap.builder();
        for (Method method : target.type().getDeclaredMethods()) {
            if (method.getDeclaringClass() == Object.class)
                continue;
            methodToHandler.put(method, nameToHandler.get(Config.toKey(method)));
        }
        FeignInvocationHandler handler = new FeignInvocationHandler(target, methodToHandler.build());
        return Reflection.newProxy(target.type(), handler);
    }

    static class FeignInvocationHandler extends AbstractInvocationHandler {

        private final Target target;
        private final Map<Method, MethodHandler> methodToHandler;

        FeignInvocationHandler(Target target, ImmutableMap<Method, MethodHandler> methodToHandler) {
            this.target = checkNotNull(target, "target");
            this.methodToHandler = checkNotNull(methodToHandler, "methodToHandler for %s", target);
        }

        @Override
        protected Object handleInvocation(Object proxy, Method method, Object[] args) throws Throwable {
            return methodToHandler.get(method).invoke(args);
        }

        @Override
        public int hashCode() {
            return target.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (FeignInvocationHandler.class != obj.getClass())
                return false;
            FeignInvocationHandler that = FeignInvocationHandler.class.cast(obj);
            return this.target.equals(that.target);
        }

        @Override
        public String toString() {
            return Objects.toStringHelper("").add("name", target.name()).add("url", target.url()).toString();
        }
    }

    @dagger.Module(complete = false,// Config
    injects = Feign.class, library = true// provides Feign
    )
    public static class Module {

        @Provides
        Feign provideFeign(ReflectiveFeign in) {
            return in;
        }

        @Provides
        Client httpClient() {
            return new Client.Default();
        }

        @Provides
        Function<Target, Map<String, MethodHandler>> targetToHandlersByName(ParseHandlersByName parseHandlersByName) {
            return parseHandlersByName;
        }
    }

    private static IllegalStateException noConfig(String methodKey, Class<?> type) {
        return new IllegalStateException(String.format("no configuration for %s or %s present for %s!", methodKey,
                Config.toClassKey(methodKey), type.getSimpleName()));
    }

    static final class ParseHandlersByName implements Function<Target, Map<String, MethodHandler>> {
        private final Set<Config<BodyEncoder>> bodyEncoderConfig;
        private final Set<Config<FormEncoder>> formEncoderConfig;
        private final Set<Config<Decoder>> decoderConfig;
        private final Factory factory;

        @Inject
        ParseHandlersByName(Set<Config<BodyEncoder>> bodyEncoderConfig, Set<Config<FormEncoder>> formEncoderConfig,
                Set<Config<Decoder>> decoderConfig, Factory factory) {
            this.bodyEncoderConfig = bodyEncoderConfig;
            this.formEncoderConfig = formEncoderConfig;
            this.decoderConfig = decoderConfig;
            this.factory = factory;
        }

        @Override
        public Map<String, MethodHandler> apply(Target key) {
            Set<MethodMetadata> metadata = parseAndValidatateMetadata(key.type());
            ImmutableMap.Builder<String, MethodHandler> builder = ImmutableMap.builder();
            for (MethodMetadata md : metadata) {
                Decoder decoder;
                if (Config.existsForMethodOrClass(decoderConfig, md.methodKey())) {
                    decoder = Config.forMethodOrClass(decoderConfig, md.methodKey());
                } else if (md.decodePattern() != null) {
                    decoder = newRegexDecoder(compile(md.decodePattern(), DOTALL), md.decodePatternGroups(),
                            md.returnType());
                } else if (md.returnType().getRawType() == void.class || md.returnType().getRawType() == Response.class) {
                    decoder = new ToStringDecoder();
                } else {
                    throw noConfig(md.methodKey(), Decoder.class);
                }
                Function<Object[], RequestTemplate> buildTemplateFromArgs;
                if (!md.formParams().isEmpty() && !md.template().bodyTemplate().isPresent()) {
                    FormEncoder formEncoder = Config.forMethodOrClass(formEncoderConfig, md.methodKey());
                    buildTemplateFromArgs = new BuildFormEncodedTemplateFromArgs(md, formEncoder);
                } else if (md.bodyIndex() != null) {
                    BodyEncoder bodyEncoder = Config.forMethodOrClass(bodyEncoderConfig, md.methodKey());
                    buildTemplateFromArgs = new BuildBodyEncodedTemplateFromArgs(md, bodyEncoder);
                } else {
                    buildTemplateFromArgs = new BuildTemplateFromArgs(md);
                }
                builder.put(md.methodKey(), factory.create(key, md, buildTemplateFromArgs, decoder));
            }
            return builder.build();
        }
    }

    private static class BuildTemplateFromArgs implements Function<Object[], RequestTemplate> {
        protected final MethodMetadata metadata;

        private BuildTemplateFromArgs(MethodMetadata metadata) {
            this.metadata = metadata;
        }

        @Override
        public RequestTemplate apply(Object[] argv) {
            RequestTemplate mutable = new RequestTemplate(metadata.template());
            if (metadata.urlIndex() != null) {
                int urlIndex = metadata.urlIndex();
                checkArgument(argv[urlIndex] != null, "URI parameter %s was null", urlIndex);
                mutable.insert(0, String.valueOf(argv[urlIndex]));
            }
            ImmutableMap.Builder<String, Object> varBuilder = ImmutableMap.builder();
            for (Entry<Integer, Collection<String>> entry : metadata.indexToName().asMap().entrySet()) {
                Object value = argv[entry.getKey()];
                if (value != null) { // Null values are skipped.
                    for (String name : entry.getValue())
                        varBuilder.put(name, String.valueOf(value));
                }
            }
            return resolve(argv, mutable, varBuilder.build());
        }

        protected RequestTemplate resolve(Object[] argv, RequestTemplate mutable, ImmutableMap<String, Object> variables) {
            return mutable.resolve(variables);
        }
    }

    private static class BuildFormEncodedTemplateFromArgs extends BuildTemplateFromArgs {
        private final FormEncoder formEncoder;

        private BuildFormEncodedTemplateFromArgs(MethodMetadata metadata, FormEncoder formEncoder) {
            super(metadata);
            this.formEncoder = formEncoder;
        }

        @Override
        protected RequestTemplate resolve(Object[] argv, RequestTemplate mutable, ImmutableMap<String, Object> variables) {
            formEncoder.encodeForm(Maps.filterKeys(variables, Predicates.in(metadata.formParams())), mutable);
            return super.resolve(argv, mutable, variables);
        }
    }

    private static class BuildBodyEncodedTemplateFromArgs extends BuildTemplateFromArgs {
        private final BodyEncoder bodyEncoder;

        private BuildBodyEncodedTemplateFromArgs(MethodMetadata metadata, BodyEncoder bodyEncoder) {
            super(metadata);
            this.bodyEncoder = bodyEncoder;
        }

        @Override
        protected RequestTemplate resolve(Object[] argv, RequestTemplate mutable, ImmutableMap<String, Object> variables) {
            Object body = argv[metadata.bodyIndex()];
            checkArgument(body != null, "Body parameter %s was null", metadata.bodyIndex());
            bodyEncoder.encodeBody(body, mutable);
            return super.resolve(argv, mutable, variables);
        }
    }
}
