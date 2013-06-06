package denominator.clouddns;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterators.emptyIterator;
import static com.google.common.collect.Iterators.filter;
import static com.google.common.collect.Ordering.usingToString;
import static denominator.model.ResourceRecordSets.nameEqualTo;

import java.util.Collection;
import java.util.Iterator;

import javax.inject.Inject;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

import denominator.ResourceRecordSetApi;
import denominator.clouddns.RackspaceApis.CloudDNS;
import denominator.clouddns.RackspaceApis.ListWithNext;
import denominator.clouddns.RackspaceApis.Record;
import denominator.model.ResourceRecordSet;
import feign.FeignException;

public final class CloudDNSResourceRecordSetApi implements denominator.ResourceRecordSetApi {

    private final CloudDNS api;
    private final Collection<String> domainIds;

    CloudDNSResourceRecordSetApi(CloudDNS api, Collection<String> domainIds) {
        this.api = api;
        this.domainIds = domainIds;
    }

    @Override
    public Iterator<ResourceRecordSet<?>> list() {
        Function<String, ListWithNext<Record>> listFn = new Function<String, ListWithNext<Record>>() {
            public ListWithNext<Record> apply(String arg0) {
                return api.records(arg0);
            }
        };
        return new GroupByRecordNameAndTypeIterator(toSortedIterator(listFn));
    }

    @Override
    public Iterator<ResourceRecordSet<?>> listByName(String name) {
        checkNotNull(name, "name was null");
        return filter(list(), nameEqualTo(name));
    }

    @Override
    public Optional<ResourceRecordSet<?>> getByNameAndType(final String name, final String type) {
        checkNotNull(name, "name was null");
        checkNotNull(type, "type was null");
        Function<String, ListWithNext<Record>> listFn = new Function<String, ListWithNext<Record>>() {
            public ListWithNext<Record> apply(String arg0) {
                return api.recordsByNameAndType(arg0, name, type);
            }
        };
        GroupByRecordNameAndTypeIterator it = new GroupByRecordNameAndTypeIterator(toSortedIterator(listFn));
        return it.hasNext() ? Optional.<ResourceRecordSet<?>> of(it.next()) : Optional.<ResourceRecordSet<?>> absent();
    }

    static final class Factory implements denominator.ResourceRecordSetApi.Factory {

        private final CloudDNS api;

        @Inject
        Factory(CloudDNS api) {
            this.api = api;
        }

        @Override
        public ResourceRecordSetApi create(final String domainName) {
            Collection<String> domainIds = api.nameToIds().get(domainName);
            checkArgument(!domainIds.isEmpty(), "domain %s not found", domainName);
            return new CloudDNSResourceRecordSetApi(api, domainIds);
        }
    }

    @Override
    public void add(ResourceRecordSet<?> rrset) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void applyTTLToNameAndType(int ttl, String name, String type) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void replace(ResourceRecordSet<?> rrset) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void remove(ResourceRecordSet<?> rrset) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteByNameAndType(String name, String type) {
        throw new UnsupportedOperationException();
    }

    Iterator<Record> toSortedIterator(Function<String, ListWithNext<Record>> listFn) {
        Builder<Record> records = ImmutableList.<Record> builder();
        for (String domain : domainIds) {
            try {
                ListWithNext<Record> list = listFn.apply(domain);
                records.addAll(list);
                while (list.next != null) {
                    list = api.records(list.next);
                    records.addAll(list);
                }
            } catch (FeignException e) {
                if (e.getMessage().indexOf("status 404") != -1) {
                    return emptyIterator();
                }
                throw e;
            }
        }
        return usingToString().sortedCopy(records.build()).iterator();
    }
}
