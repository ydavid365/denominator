package denominator.clouddns;

import static com.google.common.collect.Iterators.emptyIterator;

import java.util.Iterator;

import javax.inject.Inject;

import denominator.clouddns.RackspaceApis.CloudDNS;
import feign.FeignException;

public final class CloudDNSZoneApi implements denominator.ZoneApi {
    private final CloudDNS api;

    @Inject
    CloudDNSZoneApi(CloudDNS api) {
        this.api = api;
    }

    public Iterator<String> list() {
        try {
            return api.nameToIds().keySet().iterator();
        } catch (FeignException e) {
            if (e.getMessage().indexOf("status 404") != -1) {
                return emptyIterator();
            }
            throw e;
        }
    }
}
