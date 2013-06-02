package feign.examples;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.base.Throwables.propagate;
import static com.google.common.io.BaseEncoding.base64;
import static com.google.common.net.HttpHeaders.DATE;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map.Entry;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import com.google.common.base.Function;
import com.google.common.collect.Multimap;

import feign.Feign;
import feign.Request;
import feign.RequestTemplate;
import feign.Target;
import feign.codec.RegexDecoder.Regex;

public class Route53Example {

    interface Route53 {
        static final String URL = "https://route53.amazonaws.com/2012-12-12";

        @GET
        @Path("/hostedzone")
        @Regex(pattern = "<Id>([\\S&&[^<]]+)</Id>\\s*<Name>([\\S&&[^<]]+)</Name>", groups = { 2, 1 })
        Multimap<String, String> nameToIds();

        @GET
        @Path("{zoneId}/rrset")
        @Regex(pattern = "<Name>([\\S&&[^<]]+)</Name>\\s*<Type>([\\S&&[^<]]+)</Type>")
        Multimap<String, String> nameType(@PathParam("zoneId") String zoneId);
    }

    public static void main(final String... args) {

        Route53 api = Feign.create(new Route53Target(args[0], args[1]));

        String table = "%-40s %-64s %-8s%n";
        System.out.printf(table, "zone", "dname", "type");
        for (Entry<String, String> nameToId : api.nameToIds().entries()) {
            for (Entry<String, String> rrset : api.nameType(nameToId.getValue()).entries())
                System.out.printf(table, nameToId.getKey(), rrset.getKey(), rrset.getValue());
        }
    }

    static class Route53Target extends RestAuthentication implements Target<Route53> {

        @Override
        public Class<Route53> type() {
            return Route53.class;
        }

        @Override
        public String name() {
            return "route53";
        }

        @Override
        public String url() {
            return "https://route53.amazonaws.com/2012-12-12";
        }

        private Route53Target(String accessKey, String secretKey) {
            super(accessKey, secretKey);
        }

        @Override
        public Request apply(RequestTemplate in) {
            in.insert(0, url());
            return super.apply(in);
        }
    }

    static class RestAuthentication implements Function<RequestTemplate, Request> {
        private final String accessKey;
        private final String secretKey;
        private final String token = null;

        public RestAuthentication(String accessKey, String secretKey) {
            this.accessKey = accessKey;
            this.secretKey = secretKey;
        }

        @Override
        public Request apply(RequestTemplate input) {
            String rfc1123Date = new SimpleDateFormat("EEE, dd MMM yyyyy HH:mm:ss Z", Locale.US).format(new Date());
            String signature = sign(rfc1123Date, secretKey);
            String auth = "AWS3-HTTPS AWSAccessKeyId=" + accessKey + ",Algorithm=HmacSHA256,Signature=" + signature;
            input.header(DATE, rfc1123Date);
            input.header("X-Amzn-Authorization", auth);
            // will remove if token is not set
            input.header("X-Amz-Security-Token", token);
            return input.request();
        }

        String sign(String rfc1123Date, String secretKey) {
            try {
                String algorithm = "HmacSHA256";
                Mac mac = Mac.getInstance(algorithm);
                mac.init(new SecretKeySpec(secretKey.getBytes(UTF_8), algorithm));
                byte[] result = mac.doFinal(rfc1123Date.getBytes(UTF_8));
                return base64().encode(result);
            } catch (Exception e) {
                throw propagate(e);
            }
        }
    }
}
