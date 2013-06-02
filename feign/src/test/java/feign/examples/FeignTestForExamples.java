package feign.examples;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.io.IOException;
import java.net.URI;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableTable;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;

import dagger.Provides;
import feign.Feign;
import feign.Feign.Config;
import feign.codec.Decoder;
import feign.codec.ToStringDecoder;
import feign.examples.CloudDNSExample.CloudDNS;
import feign.examples.DynECTExample.DynECT;
import feign.examples.IAMExample.IAM;
import feign.examples.Route53Example.Route53;
import feign.examples.UltraDNSExample.UltraDNS;
/**
 * tests are stored here to avoid marking example api interfaces public.
 * 
 */
public abstract class FeignTestForExamples {
    @Test
    public void canOverrideWithSetBindingOnClass() throws IOException, InterruptedException {
        @dagger.Module(overrides = true)
        class Overrides {
            @Provides(type = Provides.Type.SET)
            Config<Decoder> decoder() {
                return Config.<Decoder> create("IAM", new ToStringDecoder());
            }
        }

        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(200).setBody("foo"));
        server.play();

        try {
            IAM api = Feign.create(IAM.class, server.getUrl("").toString(), new Overrides());

            assertEquals(api.arn(), "foo");
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void canOverrideWithSetBindingOnMethod() throws IOException, InterruptedException {
        @dagger.Module(overrides = true)
        class Overrides {
            @Provides(type = Provides.Type.SET)
            Config<Decoder> decoder() {
                return Config.<Decoder> create("IAM#arn()", new ToStringDecoder());
            }
        }

        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(200).setBody("foo"));
        server.play();

        try {
            IAM api = Feign.create(IAM.class, server.getUrl("").toString(), new Overrides());

            assertEquals(api.arn(), "foo");
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void returnsStringWhenRegexPatternMatches() throws IOException, InterruptedException {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type: text/xml").setBody(""//
                + "<GetUserResponse xmlns=\"https://api.amazonaws.com/doc/2010-05-08/\">\n" //
                + "  <GetUserResult>\n" //
                + "    <User>\n" //
                + "      <UserId>12345678</UserId>\n" //
                + "      <Arn>arn:aws:api::12345678:root</Arn>\n" //
                + "      <CreateDate>2009-03-06T21:47:48Z</CreateDate>\n" //
                + "    </User>\n" //
                + "  </GetUserResult>\n" //
                + "  <ResponseMetadata>\n" //
                + "    <RequestId>08db7cd5-caf0-11e2-8684-ef9515906a45</RequestId>\n" //
                + "  </ResponseMetadata>\n" //
                + "</GetUserResponse>"));
        server.play();

        try {
            IAM api = Feign.create(IAM.class, server.getUrl("").toString());

            assertEquals(api.arn(), "arn:aws:api::12345678:root");
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void returnsNullWhenRegexPatternMisses() throws IOException, InterruptedException {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type: text/xml").setBody(""//
                + "<GetUserResponse xmlns=\"https://api.amazonaws.com/doc/2010-05-08/\">\n" //
                + "  <ResponseMetadata>\n" //
                + "    <RequestId>08db7cd5-caf0-11e2-8684-ef9515906a45</RequestId>\n" //
                + "  </ResponseMetadata>\n" //
                + "</GetUserResponse>"));
        server.play();

        try {
            IAM api = Feign.create(IAM.class, server.getUrl("").toString());

            assertNull(api.arn());
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void returnsListWhenRegexPatternMatches() throws IOException, InterruptedException {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type: application/json").setBody(""//
                + "{\n"//
                + "    \"status\": \"success\",\n"//
                + "    \"data\": [\"/REST/Zone/zone1.denominator.io/\", \"/REST/Zone/zone2.denominator.io/\"],\n"//
                + "    \"job_id\": 368433320,\n"//
                + "    \"msgs\": [{\n"//
                + "            \"INFO\": \"get: Your 4 zones\",\n"//
                + "            \"SOURCE\": \"BLL\",\n"//
                + "            \"ERR_CD\": null,\n"//
                + "            \"LVL\": \"INFO\"\n"//
                + "        }\n"//
                + "    ]\n"//
                + "}"));
        server.play();

        try {
            DynECT api = Feign.create(DynECT.class, server.getUrl("").toString());

            assertEquals(api.zones("TOKEN"), ImmutableList.of("zone1.denominator.io", "zone2.denominator.io"));

        } finally {
            server.shutdown();
        }
    }

    @Test
    public void returnsEmptyListWhenRegexPatternMisses() throws IOException, InterruptedException {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type: application/json").setBody(""//
                + "{\n"//
                + "    \"status\": \"success\",\n"//
                + "    \"data\": [],\n"//
                + "    \"job_id\": 368433320,\n"//
                + "    \"msgs\": [{\n"//
                + "            \"INFO\": \"get: Your 4 zones\",\n"//
                + "            \"SOURCE\": \"BLL\",\n"//
                + "            \"ERR_CD\": null,\n"//
                + "            \"LVL\": \"INFO\"\n"//
                + "        }\n"//
                + "    ]\n"//
                + "}"));
        server.play();

        try {
            DynECT api = Feign.create(DynECT.class, server.getUrl("").toString());

            assertEquals(api.zones("TOKEN"), ImmutableList.of());

        } finally {
            server.shutdown();
        }
    }

    @Test
    public void returnsTableWhenRegexPatternMatches() throws IOException, InterruptedException {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type: application/json")
                .setBody(
                        "{\n"//
                                + "    \"status\": \"success\",\n"//
                                + "    \"data\": [\"/REST/SOARecord/zone1.denominator.io/zone1.denominator.io/52845188\", \"/REST/ARecord/zone1.denominator.io/www1.zone1.denominator.io/53734845\"],\n"//
                                + "    \"job_id\": 368433330,\n"//
                                + "    \"msgs\": [{\n"//
                                + "            \"INFO\": \"get_tree: Here is your zone tree\",\n"//
                                + "            \"SOURCE\": \"BLL\",\n"//
                                + "            \"ERR_CD\": null,\n"//
                                + "            \"LVL\": \"INFO\"\n"//
                                + "        }\n"//
                                + "    ]\n"//
                                + "}"));
        server.play();

        try {
            DynECT api = Feign.create(DynECT.class, server.getUrl("").toString());

            assertEquals(api.idNameType("TOKEN", "zone1.denominator.io"), ImmutableTable
                    .<String, String, String> builder()//
                    .put("52845188", "zone1.denominator.io", "SOA")//
                    .put("53734845", "www1.zone1.denominator.io", "A").build());

        } finally {
            server.shutdown();
        }
    }

    @Test
    public void returnsEmptyTableWhenRegexPatternMisses() throws IOException, InterruptedException {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type: application/json")
                .setBody("{\n"//
                        + "    \"status\": \"success\",\n"//
                        + "    \"data\": [],\n"//
                        + "    \"job_id\": 368433320,\n"//
                        + "    \"msgs\": [{\n"//
                        + "            \"INFO\": \"get: Your 4 zones\",\n"//
                        + "            \"SOURCE\": \"BLL\",\n"//
                        + "            \"ERR_CD\": null,\n"//
                        + "            \"LVL\": \"INFO\"\n"//
                        + "        }\n"//
                        + "    ]\n"//
                        + "}\n"));
        server.play();

        try {
            DynECT api = Feign.create(DynECT.class, server.getUrl("").toString());

            assertEquals(api.idNameType("TOKEN", "zone1.denominator.io"), ImmutableTable.of());

        } finally {
            server.shutdown();
        }
    }

    @Test
    public void toKeyClassFormatsAsExpected() throws Exception {
        assertEquals(Config.toKey(IAM.class), "IAM");
        assertEquals(Config.toKey(CloudDNS.class), "CloudDNS");
        assertEquals(Config.toKey(UltraDNS.class), "UltraDNS");
        assertEquals(Config.toKey(Route53.class), "Route53");
        assertEquals(Config.toKey(UltraDNS.class), "UltraDNS");
    }

    @Test
    public void toKeyMethodFormatsAsExpected() throws Exception {
        assertEquals(Config.toKey(IAM.class.getDeclaredMethod("arn")), "IAM#arn()");
        assertEquals(Config.toKey(CloudDNS.class.getDeclaredMethod("nameToIds", URI.class, String.class)),
                "CloudDNS#nameToIds(URI,String)");
        assertEquals(Config.toKey(DynECT.class.getDeclaredMethod("zones", String.class)), "DynECT#zones(String)");
        assertEquals(Config.toKey(Route53.class.getDeclaredMethod("nameToIds")), "Route53#nameToIds()");
        assertEquals(Config.toKey(UltraDNS.class.getDeclaredMethod("idNameType", String.class)),
                "UltraDNS#idNameType(String)");
    }
}
