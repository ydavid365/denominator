package denominator.clouddns;

import java.io.Reader;
import java.net.URI;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import denominator.clouddns.RackspaceApis.ListWithNext;
import denominator.clouddns.RackspaceApis.Record;
import feign.Request;
import feign.codec.Decoder;

class RecordListDecoder extends Decoder {

    @Override
    protected ListWithNext<Record> decode(Request request, Reader ireader, TypeToken<?> type) throws Throwable {
        Builder<Record> builder = ImmutableList.<Record> builder();
        String nextUrl = null;
        JsonReader reader = new JsonReader(ireader);
        reader.beginObject();
        while (reader.hasNext()) {
            String nextName = reader.nextName();
            if ("records".equals(nextName)) {
                reader.beginArray();
                while (reader.hasNext()) {
                    reader.beginObject();
                    Record record = new Record();
                    while (reader.hasNext()) {
                        String key = reader.nextName();
                        if (key.equals("name")) {
                            record.name = reader.nextString();
                        } else if (key.equals("type")) {
                            record.type = reader.nextString();
                        } else if (key.equals("ttl")) {
                            record.ttl = reader.nextInt();
                        } else if (key.equals("data")) {
                            record.data = reader.nextString();
                        } else if (key.equals("priority")) {
                            record.priority = reader.nextInt();
                        } else {
                            reader.skipValue();
                        }
                    }
                    builder.add(record);
                    reader.endObject();
                }
                reader.endArray();
            } else if ("links".equals(nextName)) {
                reader.beginArray();
                while (reader.hasNext()) {
                    String currentRel = null;
                    String currentHref = null;
                    reader.beginObject();
                    while (reader.hasNext()) {
                        String key = reader.nextName();
                        if ("rel".equals(key)) {
                            currentRel = reader.nextString();
                        } else if ("href".equals(key)) {
                            currentHref = reader.nextString();
                        } else {
                            reader.skipValue();
                        }
                    }
                    if ("next".equals(currentRel))
                        nextUrl = currentHref;
                    reader.endObject();
                }
                reader.endArray();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        reader.close();
        ListWithNext<Record> records = new ListWithNext<Record>();
        records.delegate = builder.build();
        if (nextUrl != null) {
            records.next = URI.create(nextUrl);
        }
        return records;
    }
}