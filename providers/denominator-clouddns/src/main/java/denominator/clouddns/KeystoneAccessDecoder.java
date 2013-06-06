package denominator.clouddns;

import java.io.Reader;

import com.google.common.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import denominator.clouddns.RackspaceApis.TokenIdAndPublicURL;
import feign.Request;

/**
 * returns a token and public url of the configured service type.
 */
class KeystoneAccessDecoder extends feign.codec.Decoder {

    private String serviceType;

    KeystoneAccessDecoder(String serviceType) {
        this.serviceType = serviceType;
    }

    @Override
    protected TokenIdAndPublicURL decode(Request request, Reader ireader, TypeToken<?> type) throws Throwable {
        JsonReader reader = new JsonReader(ireader);
        reader.beginObject();
        if (!reader.hasNext() || !"access".equals(reader.nextName())) {
            reader.close();
            return null;
        }
        reader.beginObject();

        TokenIdAndPublicURL access = new TokenIdAndPublicURL();
        while (reader.hasNext()) {
            String nextName = reader.nextName();
            if ("token".equals(nextName)) {
                reader.beginObject();
                while (reader.hasNext()) {
                    if ("id".equals(reader.nextName())) {
                        access.tokenId = reader.nextString();
                    } else {
                        reader.skipValue();
                    }
                }
                reader.endObject();
            } else if ("serviceCatalog".equals(nextName)) {
                reader.beginArray();
                while (reader.hasNext()) {
                    reader.beginObject();
                    String currentType = null;
                    String currentPublicUrl = null;
                    while (reader.hasNext()) {
                        String key = reader.nextName();
                        if ("type".equals(key)) {
                            currentType = reader.nextString();
                        } else if ("endpoints".equals(key)) {
                            reader.beginArray();
                            while (reader.hasNext()) {
                                reader.beginObject();
                                while (reader.hasNext()) {
                                    String endpointKey = reader.nextName();
                                    if ("publicURL".equals(endpointKey)) {
                                        currentPublicUrl = reader.nextString();
                                    } else {
                                        reader.skipValue();
                                    }
                                }
                                reader.endObject();
                            }
                            reader.endArray();
                        } else {
                            reader.skipValue();
                        }
                    }
                    if (serviceType.equals(currentType)) {
                        access.publicURL = currentPublicUrl;
                    }
                    reader.endObject();
                }
                reader.endArray();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        reader.endObject();
        reader.close();
        return access;
    }
}