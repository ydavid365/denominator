package feign.codec;

import java.io.Reader;

import com.google.common.io.CharStreams;
import com.google.common.reflect.TypeToken;

import feign.Request;

public class ToStringDecoder extends Decoder {
    @Override
    protected Object decode(Request request, Reader reader, TypeToken<?> type) throws Throwable {
        return CharStreams.toString(reader);
    }
}