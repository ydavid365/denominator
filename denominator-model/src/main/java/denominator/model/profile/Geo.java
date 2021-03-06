package denominator.model.profile;

import static com.google.common.base.Preconditions.checkNotNull;

import java.beans.ConstructorProperties;
import java.util.Map;

import com.google.common.collect.ForwardingMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

/**
 * Record sets with this profile are visible to the regions specified.
 * 
 * <h4>Example</h4>
 * 
 * <pre>
 * Geo profile = Geo.create(&quot;md&quot;, ImmutableMultimap.of(&quot;United States (US)&quot;, &quot;Maryland&quot;));
 * </pre>
 */
public class Geo extends ForwardingMap<String, Object> {

    /**
     * @param group corresponds to {@link #getGroup()}
     * @param regions corresponds to {@link #getRegions()}
     */
    public static Geo create(String group, Multimap<String, String> regions) {
        return new Geo(group, regions);
    }

    private final String type = "geo";
    private final String group;
    private final Multimap<String, String> regions;

    @ConstructorProperties({ "group", "regions"})
    private Geo(String group, Multimap<String, String> regions) {
        this.group = checkNotNull(group, "group");
        checkNotNull(regions, "regions");
        this.regions = ImmutableMultimap.copyOf(regions);
        this.delegate = ImmutableMap.<String, Object> builder()
                                    .put("type", type)
                                    .put("group", group)
                                    .put("regions", regions).build();
    }

    /**
     * user-defined name for the group of regions that represent the traffic
     * desired. For example, {@code "US-West"} or {@code "Non-EU"}.
     */
    public String getGroup() {
        return group;
    }

    /**
     * a filtered view of
     * {@code denominator.profile.GeoResourceRecordSetApi.getSupportedRegions()}, which
     * describes the traffic desired for this profile.
     */
    public Multimap<String, String> getRegions() {
        return regions;
    }

    // transient to avoid serializing by default, for example in json
    private final transient ImmutableMap<String, Object> delegate;
    
    @Override
    protected Map<String, Object> delegate() {
        return delegate;
    }
}
