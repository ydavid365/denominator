package feign;

import static com.google.common.base.Objects.equal;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.lang.reflect.Method;
import java.util.Set;

import com.google.common.annotations.Beta;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import dagger.ObjectGraph;
import dagger.Provides;
import feign.Target.HardCodedTarget;
import feign.Wire.NoOpWire;
import feign.codec.BodyEncoder;
import feign.codec.Decoder;
import feign.codec.FormEncoder;

/**
 * Feign's purpose is to ease development against http apis that feign
 * restfulness.
 * <p/>
 * In implementation, Feign is a {@link Feign#newInstance factory} for
 * generating {@link Target targeted} http apis.
 * 
 */
public abstract class Feign {

    /**
     * Returns a new instance of an HTTP API, defined by annotations in the
     * {@link Feign Contract}, for the specified {@code target}. You should
     * cache this result.
     */
    public abstract <T> T newInstance(Target<T> target);

    public static <T> T create(Class<T> apiType, String url, Object... modules) {
        return create(new HardCodedTarget<T>(apiType, url), modules);
    }

    /**
     * Shortcut to {@link #newInstance(Target) create} a single {@code targeted}
     * http api using {@link ReflectiveFeign reflection}.
     */
    public static <T> T create(Target<T> target, Object... modules) {
        return create(modules).newInstance(target);
    }

    /**
     * Returns a {@link ReflectiveFeign reflective} factory for generating
     * {@link Target targeted} http apis.
     */
    public static Feign create(Object... modules) {
        Object[] modulesForGraph = ImmutableList.builder() //
                .add(new Defaults()) //
                .add(new ReflectiveFeign.Module()) //
                .add(Optional.fromNullable(modules).or(new Object[] {})).build().toArray();
        return ObjectGraph.create(modulesForGraph).get(Feign.class);
    }

    /**
     * Returns an {@link ObjectGraph Dagger ObjectGraph} that can inject a
     * {@link ReflectiveFeign reflective} Feign.
     */
    public static ObjectGraph createObjectGraph(Object... modules) {
        Object[] modulesForGraph = ImmutableList.builder() //
                .add(new Defaults()) //
                .add(new ReflectiveFeign.Module()) //
                .add(Optional.fromNullable(modules).or(new Object[] {})).build().toArray();
        return ObjectGraph.create(modulesForGraph);
    }

    @dagger.Module(complete = false,// Config
    injects = Feign.class, library = true// provides Feign
    )
    public static class Defaults {
        @Provides
        Wire noOp() {
            return new NoOpWire();
        }

        /**
         * To override, use SetBinding.
         * 
         * <p/>
         * 
         * <pre>
         * &#064;Provides(type = Provides.Type.SET)
         * Config&lt;Decoder&gt; decoder(Target target, MyDefaultDecoder in) {
         *     return Config.create(target.type(), in);
         * }
         * </pre>
         */
        @Provides
        Set<Config<Decoder>> noDecoders() {
            return ImmutableSet.of();
        }

        /**
         * To override, use SetBinding.
         * 
         * <p/>
         * 
         * <pre>
         * &#064;Provides(type = Provides.Type.SET)
         * Config&lt;BodyEncoder&gt; bodyEncoder(Target target, MyDefaultBodyEncoder in) {
         *     return Config.create(target.type(), in);
         * }
         * </pre>
         */
        @Provides
        Set<Config<BodyEncoder>> noBodyEncoders() {
            return ImmutableSet.of();
        }

        /**
         * To override, use SetBinding.
         * 
         * <p/>
         * 
         * <pre>
         * &#064;Provides(type = Provides.Type.SET)
         * Config&lt;FormEncoder&gt; formEncoder(Target target, MyDefaultFormEncoder in) {
         *     return Config.create(target.type(), in);
         * }
         * </pre>
         */
        @Provides
        Set<Config<FormEncoder>> noFormEncoders() {
            return ImmutableSet.of();
        }
    }

    /**
     * This class is used to adapt Map Binding to {@link Provides.Type#SET
     * Dagger Set Binding}.
     * <p/>
     * {@link key() Configuration keys} are formatted as unresolved <a href=
     * "http://docs.oracle.com/javase/6/docs/jdk/api/javadoc/doclet/com/sun/javadoc/SeeTag.html"
     * >see tags</a>.
     * 
     * For example.
     * <ul>
     * <li>{@code Route53}: would match a class such as
     * {@code denominator.route53.Route53}
     * <li>{@code Route53#list()}: would match a method such as
     * {@code denominator.route53.Route53#list()}
     * <li>{@code Route53#listAt(Marker)}: would match a method such as
     * {@code denominator.route53.Route53#listAt(denominator.route53.Marker)}
     * <li>{@code Route53#listByNameAndType(String,String)}: would match a
     * method such as {@code denominator.route53.Route53#listAt(String, String)}
     * </ul>
     * 
     * Note that there is no whitespace expected in a key!
     */
    // until dagger has a map binder
    @Beta
    public static class Config<C> {

        public static String toKey(Class<?> type) {
            return type.getSimpleName();
        }

        public static String toKey(Method method) {
            StringBuilder builder = new StringBuilder();
            builder.append(method.getDeclaringClass().getSimpleName());
            builder.append('#').append(method.getName()).append('(');
            for (Class<?> param : method.getParameterTypes())
                builder.append(param.getSimpleName()).append(',');
            if (method.getParameterTypes().length > 0)
                builder.deleteCharAt(builder.length() - 1);
            return builder.append(')').toString();
        }

        static <T> boolean existsForMethodOrClass(Set<Config<T>> configs, String methodKey) {
            String classKey = toClassKey(methodKey);
            for (Config<?> config : configs) {
                if (methodKey.equals(config.key()))
                    return true;
                else if (classKey.equals(config.key()))
                    return true;
            }
            return false;
        }

        /**
         * throws IllegalStateException if there's no config for the method or
         * class associated.
         */
        static <T> T forMethodOrClass(Set<Config<T>> configs, String methodKey) {
            String classKey = toClassKey(methodKey);
            Config<T> classConfig = null;
            for (Config<T> config : configs) {
                if (methodKey.equals(config.key()))
                    return config.value();
                else if (classKey.equals(config.key()))
                    classConfig = config;
            }
            checkState(classConfig != null, "no configuration for %s or %s present!", methodKey, classKey);
            return classConfig.value();
        }

        public static String toClassKey(String methodOrClass) {
            return methodOrClass.substring(0, methodOrClass.indexOf('#'));
        }

        public static <C> Config<C> create(Class<?> type, C config) {
            return new Config<C>(toKey(type), config);
        }

        public static <C> Config<C> create(Method method, C config) {
            return new Config<C>(toKey(method), config);
        }

        public static <C> Config<C> create(String key, C config) {
            return new Config<C>(key, config);
        }

        private final String key;
        private final C config;

        private Config(String key, C config) {
            this.key = checkNotNull(key, "key");
            this.config = checkNotNull(config, "config");
        }

        public String key() {
            return key;
        }

        public C value() {
            return config;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(key, config);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (Response.class != obj.getClass())
                return false;
            Config<?> that = Config.class.cast(obj);
            return equal(this.key, that.key) && equal(this.config, that.config);
        }

        @Override
        public String toString() {
            return Objects.toStringHelper("").add("key", key).add("config", config).toString();
        }
    }

    Feign() {

    }
}