package feign;

import static feign.Contract.parseAndValidatateMetadata;
import static org.testng.Assert.assertEquals;

import java.net.URI;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import org.testng.annotations.Test;

import feign.examples.ContractTestForExamples;

public class ContractTest extends ContractTestForExamples {

    private interface WithURIParam {
        @GET
        @Path("/{1}/{2}")
        Response uriParam(@PathParam("1") String one, URI endpoint, @PathParam("1") String two);
    }

    @Test
    public void methodCanHaveUriParam() throws Exception {
        MethodMetadata md = parseAndValidatateMetadata(WithURIParam.class.getDeclaredMethod("uriParam", String.class,
                URI.class, String.class));
        assertEquals(md.urlIndex(), Integer.valueOf(1));
    }
}
