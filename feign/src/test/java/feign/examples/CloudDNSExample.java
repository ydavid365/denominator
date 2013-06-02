package feign.examples;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import java.util.List;
import java.util.Map.Entry;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import feign.Feign;
import feign.Request;
import feign.RequestTemplate;
import feign.RequestTemplate.Body;
import feign.Target;
import feign.Target.HardCodedTarget;
import feign.codec.RegexDecoder.Regex;

public class CloudDNSExample {

    static class Rackspace {
        private final CloudIdentity cloudIdentity;
        private final CloudDNS cloudDNS;

        @Inject
        Rackspace(CloudIdentity cloudIdentity, CloudDNS cloudDNS) {
            this.cloudIdentity = cloudIdentity;
            this.cloudDNS = cloudDNS;
        }

        public CloudIdentity cloudIdentity() {
            return cloudIdentity;
        }

        public CloudDNS cloudDNS() {
            return cloudDNS;
        }
    }

    interface CloudIdentity {
        @POST
        @Path("/tokens")
        @Regex(pattern = "^.*token[\":{\\s]+id\":\"([^\"]+)\"(.*cloudDNS\"[^\\]]+publicURL\":\\s*\"([^\"]+)\")?", groups = {
                3, 1 })
        @Body("%7B\"auth\":%7B\"RAX-KSKEY:apiKeyCredentials\":%7B\"username\": \"{username}\", \"apiKey\": \"{apiKey}\"%7D%7D%7D")
        @Produces(APPLICATION_JSON)
        List<String> urlAndToken(@FormParam("username") String username, @FormParam("apiKey") String apiKey);
    }

    interface CloudDNS {
        @GET
        @Path("/domains")
        @Regex(pattern = "\"name\"[:\\s]+\"([^\"]+)\"[,:\\s]+\"id\"[:\\s]+([0-9]+)")
        Multimap<String, String> nameToIds();

        @GET
        @Path("/domains/{domainId}/records")
        @Regex(pattern = "\"name\"[:\\s]+\"([^\"]+)\"[,:\\s]+\"id\"[:\\s]++\"([^\"]+)\"[,:\\s]+\"type\"[:\\s]++\"([^\"]+)\"", groups = {
                2, 1, 3 })
        Table<String, String, String> idNameType(@PathParam("domainId") String id);
    }

    public static void main(String... args) {

        Rackspace rackspace = Feign.createObjectGraph(new RackspaceModule(args[0], args[1])).get(Rackspace.class);

        CloudDNS api = rackspace.cloudDNS();

        String table = "%-40s %-12s %-64s %s%n";
        System.out.printf(table, "zone", "recordId", "dname", "type");
        for (Entry<String, String> nameToId : api.nameToIds().entries()) {
            for (Cell<String, String, String> rrset : api.idNameType(nameToId.getValue()).cellSet())
                System.out.printf(table, nameToId.getKey(), rrset.getRowKey(), rrset.getColumnKey(), rrset.getValue());
        }
    }

    // incomplete as needs feign
    @Module(injects = Rackspace.class, complete = false)
    static class RackspaceModule {
        private final String username;
        private final String apiKey;

        private RackspaceModule(String username, String apiKey) {
            this.username = username;
            this.apiKey = apiKey;
        }

        @Provides
        @Singleton
        CloudIdentity cloudIdentity(Feign feign) {
            return feign.newInstance(new HardCodedTarget<CloudIdentity>(CloudIdentity.class,
                    "https://identity.api.rackspacecloud.com/v2.0"));
        }

        /**
         * gets the current endpoint and authorization token from the identity
         * service.
         */
        @Provides
        public List<String> urlAndToken(CloudIdentity identityService) {
            List<String> urlWithSlashesAndToken = identityService.urlAndToken(username, apiKey);
            return ImmutableList.of(urlWithSlashesAndToken.get(0).replace("\\", ""), urlWithSlashesAndToken.get(1));
        }

        @Provides
        @Singleton
        CloudDNS cloudDNS(Feign feign, CloudDNSTarget target) {
            return feign.newInstance(target);
        }
    }

    static class CloudDNSTarget implements Target<CloudDNS> {
        private final Lazy<List<String>> lazyUrlAndToken;

        @Inject
        CloudDNSTarget(Lazy<List<String>> lazyUrlAndToken) {
            this.lazyUrlAndToken = lazyUrlAndToken;
        }

        @Override
        public Class<CloudDNS> type() {
            return CloudDNS.class;
        }

        @Override
        public String name() {
            return "clouddns";
        }

        @Override
        public String url() {
            return lazyUrlAndToken.get().get(0);
        }

        @Override
        public Request apply(RequestTemplate input) {
            List<String> urlAndToken = lazyUrlAndToken.get();
            input.insert(0, urlAndToken.get(0));
            input.header("X-Auth-Token", urlAndToken.get(1));
            return input.request();
        }
    }
}
