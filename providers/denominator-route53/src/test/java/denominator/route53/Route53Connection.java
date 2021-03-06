package denominator.route53;

import static com.google.common.base.Strings.emptyToNull;
import static denominator.CredentialsConfiguration.credentials;
import static java.lang.System.getProperty;
import denominator.DNSApiManager;
import denominator.Denominator;

public class Route53Connection {

    final DNSApiManager manager;
    final String mutableZone;

    Route53Connection() {
        String accesskey = emptyToNull(getProperty("route53.accesskey"));
        String secretkey = emptyToNull(getProperty("route53.secretkey"));
        if (accesskey != null && secretkey != null) {
            Route53Provider provider = new Route53Provider(emptyToNull(getProperty("route53.url")));
            manager = Denominator.create(provider, credentials(accesskey, secretkey));
        } else {
            manager = null;
        }
        mutableZone = emptyToNull(getProperty("route53.zone"));
    }
}
