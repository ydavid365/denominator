package feign.examples;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import java.util.List;

import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

import feign.Feign;
import feign.Request;
import feign.RequestTemplate;
import feign.RequestTemplate.Body;
import feign.Target;
import feign.codec.RegexDecoder.Regex;

public class DynECTExample {

    interface DynECT {

        @POST
        @Path("/Session")
        @Regex(pattern = "token\":\\s*\"([^\"]+)\"")
        @Body("%7B\"customer_name\": \"{customer_name}\", \"user_name\": \"{user_name}\", \"password\": \"{password}\"%7D")
        String login(@FormParam("customer_name") String customer, @FormParam("user_name") String user,
                @FormParam("password") String password);

        @GET
        @Path("/Zone")
        @Regex(pattern = "/REST/Zone/([^/]+)/")
        List<String> zones(@HeaderParam("Auth-Token") String token);

        @GET
        @Path("/AllRecord/{zone}")
        @Regex(pattern = "/REST/([a-zA-Z]+)Record/([^/]+)/([^/]+)/([0-9]+)", groups = { 4, 3, 1 })
        Table<String, String, String> idNameType(@HeaderParam("Auth-Token") String token, @PathParam("zone") String zone);

        @DELETE
        @Path("/Session")
        void logout(@HeaderParam("Auth-Token") String token);
    }

    public static void main(String... args) {

        DynECT api = Feign.create(new DynECTTarget());
        String token = api.login(args[0], args[1], args[2]);
        try {
            String table = "%-40s %-12s %-64s %s%n";
            System.out.printf(table, "zone", "recordId", "dname", "type");
            for (String zone : api.zones(token)) {
                for (Cell<String, String, String> rrset : api.idNameType(token, zone).cellSet())
                    System.out.printf(table, zone, rrset.getRowKey(), rrset.getColumnKey(), rrset.getValue());
            }
        } finally {
            api.logout(token);
        }
    }

    static class DynECTTarget implements Target<DynECT> {
        @Override
        public Class<DynECT> type() {
            return DynECT.class;
        }

        @Override
        public String name() {
            return "dynect";
        }

        @Override
        public String url() {
            return "https://api2.dynect.net/REST";
        }

        @Override
        public Request apply(RequestTemplate input) {
            input.header("API-Version", "3.5.0");
            input.header(CONTENT_TYPE, APPLICATION_JSON);
            input.insert(0, url());
            return input.request();
        }
    };
}
