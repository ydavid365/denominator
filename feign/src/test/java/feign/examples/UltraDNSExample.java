package feign.examples;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.HttpHeaders.HOST;
import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;

import java.net.URI;
import java.util.Map;

import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

import com.google.common.base.Function;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

import feign.Feign;
import feign.Request;
import feign.RequestTemplate;
import feign.RequestTemplate.Body;
import feign.Target;
import feign.codec.RegexDecoder.Regex;

public class UltraDNSExample {

    interface UltraDNS {
        @POST
        @Regex(pattern = "accountID=\"([^\"]+)\"")
        @Body("<v01:getAccountsListOfUser/>")
        String accountId();

        @POST
        @Path("/")
        @Body("<v01:getZonesOfAccount><accountId>{accountId}</accountId><zoneType>all</zoneType></v01:getZonesOfAccount>")
        @Regex(pattern = "zoneName=\"([^\"]+)\"[^>]+zoneId=\"([^\"]+)\"")
        Map<String, String> nameToId(@FormParam("accountId") String accountId);

        @POST
        @Body("<v01:getResourceRecordsOfZone><zoneName>{zoneName}</zoneName><rrType>0</rrType></v01:getResourceRecordsOfZone>")
        @Regex(pattern = "Type=\"([0-9]+)\"[^>]+DName=\"([^\"]+)\"[^>]+Guid=\"([0-9A-F]+)\"", groups = { 3, 2, 1 })
        Table<String, String, String> idNameType(@FormParam("zoneName") String zoneName);
    }

    public static void main(final String... args) {

        UltraDNS api = Feign.create(new UltraDNSTarget(args[0], args[1]));

        String table = "%-64s %-16s %-64s %s%n";
        System.out.printf(table, "zone", "recordId", "dname", "type");
        for (String zone : api.nameToId(api.accountId()).keySet()) {
            for (Cell<String, String, String> rrset : api.idNameType(zone).cellSet())
                System.out.printf(table, zone, rrset.getRowKey(), rrset.getColumnKey(), rrset.getValue());
        }
    }

    static class UltraDNSTarget extends SOAPWrapWithPasswordAuth implements Target<UltraDNS> {
        @Override
        public Class<UltraDNS> type() {
            return UltraDNS.class;
        }

        @Override
        public String name() {
            return "ultradns";
        }

        @Override
        public String url() {
            return "https://ultra-api.ultradns.com:8443/UltraDNS_WS/v01";
        }

        private UltraDNSTarget(String username, String password) {
            super(username, password);
        }

        @Override
        public Request apply(RequestTemplate in) {
            in.insert(0, url());
            return super.apply(in);
        }
    }

    static class SOAPWrapWithPasswordAuth implements Function<RequestTemplate, Request> {
        static final String TEMPLATE = "" //
                + "<?xml version=\"1.0\"?>\n"//
                + "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:v01=\"http://webservice.api.ultra.neustar.com/v01/\">\n"//
                + "  <soapenv:Header>\n"//
                + "    <wsse:Security xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" soapenv:mustUnderstand=\"1\">\n"//
                + "      <wsse:UsernameToken>\n"//
                + "        <wsse:Username>%s</wsse:Username>\n"//
                + "        <wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText\">%s</wsse:Password>\n"//
                + "      </wsse:UsernameToken>\n"//
                + "    </wsse:Security>\n"//
                + "  </soapenv:Header>\n"//
                + "  <soapenv:Body>%s</soapenv:Body>\n"//
                + "</soapenv:Envelope>";

        private final String username;
        private final String password;

        public SOAPWrapWithPasswordAuth(String username, String password) {
            this.username = username;
            this.password = password;
        }

        @Override
        public Request apply(RequestTemplate input) {
            input.body(format(TEMPLATE, username, password, input.body().get()));
            input.header(HOST, URI.create(input.url()).getHost());
            input.header(CONTENT_TYPE, APPLICATION_XML);
            return input.request();
        }
    }
}
