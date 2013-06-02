package feign.examples;

import static feign.Contract.parseAndValidatateMetadata;
import static feign.codec.RegexDecoder.LIST_STRING_TOKEN;
import static feign.codec.RegexDecoder.MAP_STRING_TOKEN;
import static feign.codec.RegexDecoder.MULTIMAP_STRING_TOKEN;
import static feign.codec.RegexDecoder.STRING_TOKEN;
import static feign.codec.RegexDecoder.SUPPORTED_REGEX_RETURN_TYPES;
import static feign.codec.RegexDecoder.TABLE_STRING_TOKEN;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Map;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;
import com.google.common.reflect.TypeToken;

import feign.MethodMetadata;
import feign.codec.RegexDecoder.Regex;
import feign.examples.CloudDNSExample.CloudIdentity;
import feign.examples.IAMExample.IAM;
import feign.examples.Route53Example.Route53;
import feign.examples.UltraDNSExample.UltraDNS;

/**
 * tests are stored here to avoid marking example api interfaces public.
 * 
 */
public abstract class ContractTestForExamples {

    @Test
    public void parseSupportedRegexDecoders() throws Exception {
        ImmutableMap<Method, TypeToken<?>> supported = ImmutableMap
                .<Method, TypeToken<?>> builder()
                .put(IAM.class.getDeclaredMethod("arn"), STRING_TOKEN)
                .put(CloudIdentity.class.getDeclaredMethod("urlAndToken", String.class, String.class),
                        LIST_STRING_TOKEN)
                .put(UltraDNS.class.getDeclaredMethod("nameToId", String.class), MAP_STRING_TOKEN)
                .put(Route53.class.getDeclaredMethod("nameToIds"), MULTIMAP_STRING_TOKEN)
                .put(UltraDNS.class.getDeclaredMethod("idNameType", String.class), TABLE_STRING_TOKEN).build();

        for (Map.Entry<Method, TypeToken<?>> entry : supported.entrySet()) {
            Method method = entry.getKey();
            TypeToken<?> returnType = entry.getValue();
            MethodMetadata md = parseAndValidatateMetadata(method);

            assertEquals(md.decodePattern(), method.getAnnotation(Regex.class).pattern(), method.toGenericString());
            assertEquals(md.decodePatternGroups(), Ints.asList(method.getAnnotation(Regex.class).groups()),
                    method.toGenericString());
            assertEquals(md.returnType(), returnType, method.toGenericString());
            assertTrue(SUPPORTED_REGEX_RETURN_TYPES.contains(md.returnType()), method.toGenericString());
        }
    }
}
