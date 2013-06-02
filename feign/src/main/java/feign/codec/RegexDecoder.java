package feign.codec;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.io.Reader;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Qualifier;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.google.common.io.CharStreams;
import com.google.common.reflect.TypeToken;

import feign.Request;
import feign.Response;

/**
 * Decodes an HTTP response into a given type from match groups.
 * <p/>
 * Ex.
 * 
 * <pre>
 * &#064;Named(&quot;GetUser&quot;)
 * &#064;GET
 * &#064;Path(&quot;/?Action=GetUser&amp;Version=2010-05-08&quot;)
 * &#064;Regex(pattern = &quot;&lt;Arn&gt;([\\S&amp;&amp;[&circ;&lt;]]+)&lt;/Arn&gt;&quot;)
 * String arn();
 * </pre>
 * 
 * @see Decoder.Regex
 */
public abstract class RegexDecoder extends Decoder {
    @Target({ METHOD, PARAMETER })
    @Retention(RUNTIME)
    @Qualifier
    public static @interface Regex {
        /**
         * Pattern used to process the {@link Response.Body response body}. The
         * number of groups should match the number of type parameters on the
         * return value; Multi-line matching will take place.
         */
        String pattern();

        /**
         * Selects the groups (and order) in relationship to the type parameters
         * of the return type.
         */
        int[] groups() default { 1, 2, 3 };

    }

    @SuppressWarnings("serial")
    public static final TypeToken<?> STRING_TOKEN = new TypeToken<String>() {
    };
    @SuppressWarnings("serial")
    public static final TypeToken<?> LIST_STRING_TOKEN = new TypeToken<List<String>>() {
    };
    @SuppressWarnings("serial")
    public static final TypeToken<?> MAP_STRING_TOKEN = new TypeToken<Map<String, String>>() {
    };
    @SuppressWarnings("serial")
    public static final TypeToken<?> MULTIMAP_STRING_TOKEN = new TypeToken<Multimap<String, String>>() {
    };
    @SuppressWarnings("serial")
    public static final TypeToken<?> TABLE_STRING_TOKEN = new TypeToken<Table<String, String, String>>() {
    };
    public static final List<TypeToken<?>> SUPPORTED_REGEX_RETURN_TYPES = ImmutableList.<TypeToken<?>> of(STRING_TOKEN,
            LIST_STRING_TOKEN, MAP_STRING_TOKEN, MULTIMAP_STRING_TOKEN, TABLE_STRING_TOKEN);

    /**
     * it is presumed that {@link Regex#verify} was called before executing
     * this.
     */
    public static RegexDecoder newRegexDecoder(final Pattern pattern, final List<Integer> groupOrder, TypeToken<?> type) {
        checkNotNull(pattern, "pattern");
        checkNotNull(groupOrder, "groupOrder");
        checkNotNull(type, "type");
        if (STRING_TOKEN.equals(type)) {
            return new RegexDecoder() {
                @Override
                protected Object decode(Request request, Reader reader, TypeToken<?> type) throws Throwable {
                    Matcher matcher = pattern.matcher(CharStreams.toString(reader));
                    if (matcher.find()) {
                        return matcher.group(1);
                    }
                    return null;
                }
            };
        } else if (LIST_STRING_TOKEN.equals(type)) {
            return new RegexDecoder() {
                @Override
                protected Object decode(Request request, Reader reader, TypeToken<?> type) throws Throwable {
                    Matcher matcher = pattern.matcher(CharStreams.toString(reader));
                    ImmutableList.Builder<String> builder = ImmutableList.<String> builder();
                    while (matcher.find()) {
                        for (int i = 0; i < matcher.groupCount(); i++) {
                            if (i < groupOrder.size()) {
                                String val = matcher.group(groupOrder.get(i));
                                if (val != null) {
                                    builder.add(val);
                                }
                            }
                        }
                    }
                    return builder.build();
                }
            };
        } else if (MAP_STRING_TOKEN.equals(type)) {
            return new RegexDecoder() {
                @Override
                protected Object decode(Request request, Reader reader, TypeToken<?> type) throws Throwable {
                    Matcher matcher = pattern.matcher(CharStreams.toString(reader));
                    ImmutableMap.Builder<String, String> builder = ImmutableMap.<String, String> builder();
                    while (matcher.find()) {
                        builder.put(matcher.group(groupOrder.get(0)), matcher.group(groupOrder.get(1)));
                    }
                    return builder.build();
                }
            };
        } else if (MAP_STRING_TOKEN.equals(type)) {
            return new RegexDecoder() {
                @Override
                protected Object decode(Request request, Reader reader, TypeToken<?> type) throws Throwable {
                    Matcher matcher = pattern.matcher(CharStreams.toString(reader));
                    ImmutableMap.Builder<String, String> builder = ImmutableMap.<String, String> builder();
                    while (matcher.find()) {
                        builder.put(matcher.group(groupOrder.get(0)), matcher.group(groupOrder.get(1)));
                    }
                    return builder.build();
                }
            };
        } else if (MULTIMAP_STRING_TOKEN.equals(type)) {
            return new RegexDecoder() {
                @Override
                protected Object decode(Request request, Reader reader, TypeToken<?> type) throws Throwable {
                    Matcher matcher = pattern.matcher(CharStreams.toString(reader));
                    ImmutableMultimap.Builder<String, String> builder = ImmutableMultimap.<String, String> builder();
                    while (matcher.find()) {
                        builder.put(matcher.group(groupOrder.get(0)), matcher.group(groupOrder.get(1)));
                    }
                    return builder.build();
                }
            };
        } else if (TABLE_STRING_TOKEN.equals(type)) {
            return new RegexDecoder() {
                @Override
                protected Object decode(Request request, Reader reader, TypeToken<?> type) throws Throwable {
                    Matcher matcher = pattern.matcher(CharStreams.toString(reader));
                    ImmutableTable.Builder<String, String, String> builder = ImmutableTable
                            .<String, String, String> builder();
                    while (matcher.find()) {
                        builder.put(matcher.group(groupOrder.get(0)), matcher.group(groupOrder.get(1)),
                                matcher.group(groupOrder.get(2)));
                    }
                    return builder.build();
                }
            };
        }
        throw new IllegalStateException("unsupported type: " + type);
    }
}