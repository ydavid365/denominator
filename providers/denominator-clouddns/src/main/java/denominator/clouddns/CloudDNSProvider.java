package denominator.clouddns;

import static com.google.common.base.Strings.emptyToNull;

import java.util.Set;

import javax.inject.Singleton;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.common.collect.Multimap;

import dagger.Provides;
import denominator.BasicProvider;
import denominator.DNSApiManager;
import denominator.ResourceRecordSetApi;
import denominator.ZoneApi;
import denominator.clouddns.RackspaceApis.CloudDNS;
import denominator.clouddns.RackspaceApis.CloudIdentity;
import denominator.clouddns.RackspaceApis.TokenIdAndPublicURL;
import denominator.clouddns.RackspaceTargets.CloudDNSTarget;
import denominator.config.GeoUnsupported;
import denominator.config.NothingToClose;
import denominator.config.OnlyNormalResourceRecordSets;
import feign.Feign;
import feign.Feign.Config;
import feign.Feign.Defaults;
import feign.ReflectiveFeign;
import feign.Target.HardCodedTarget;
import feign.codec.Decoder;

public class CloudDNSProvider extends BasicProvider {
    private final String url;

    public CloudDNSProvider() {
        this(null);
    }

    /**
     * @param url
     *            if empty or null use default
     */
    public CloudDNSProvider(String url) {
        url = emptyToNull(url);
        this.url = url != null ? url : "https://identity.api.rackspacecloud.com/v2.0";
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public Multimap<String, String> getCredentialTypeToParameterNames() {
        return ImmutableMultimap.<String, String> builder()
                                .putAll("password", "username", "password")
                                .putAll("apiKey", "username", "apiKey").build();
    }

    @dagger.Module(injects = DNSApiManager.class, complete = false, overrides = true, 
                   includes = { NothingToClose.class, 
                                GeoUnsupported.class, 
                                OnlyNormalResourceRecordSets.class, 
                                Defaults.class,
                                ReflectiveFeign.Module.class })
    public static final class Module {

        @Provides
        @Singleton
        ZoneApi provideZoneApi(CloudDNS api) {
            return new CloudDNSZoneApi(api);
        }

        @Provides
        @Singleton
        ResourceRecordSetApi.Factory provideResourceRecordSetApiFactory(CloudDNS api) {
            return new CloudDNSResourceRecordSetApi.Factory(api);
        }

        @Provides
        @Singleton
        CloudDNS cloudDNS(Feign feign, CloudDNSTarget target) {
            return feign.newInstance(target);
        }

        @Provides
        @Singleton
        CloudIdentity cloudIdentity(Feign feign) {
            return feign.newInstance(new HardCodedTarget<CloudIdentity>(CloudIdentity.class, "cloudidentity",
                    "http://invalid"));
        }

        @Provides
        Set<Config<Decoder>> decoders() {
            Builder<Config<Decoder>> decoders = ImmutableSet.<Config<Decoder>> builder();
            Decoder accessDecoder = new KeystoneAccessDecoder("rax:dns");
            decoders.add(Config.create("CloudIdentity", accessDecoder));
            Decoder recordListDecoder = new RecordListDecoder();
            decoders.add(Config.create("CloudDNS#records(URI)", recordListDecoder));
            decoders.add(Config.create("CloudDNS#records(String)", recordListDecoder));
            decoders.add(Config.create("CloudDNS#recordsByNameAndType(String,String,String)", recordListDecoder));
            return decoders.build();
        }

        @Provides
        public TokenIdAndPublicURL urlAndToken(InvalidatableAuthSupplier supplier) {
            return supplier.get();
        }
    }
}
