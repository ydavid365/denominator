package feign;

import java.io.Serializable;
import java.util.List;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.SetMultimap;
import com.google.common.reflect.TypeToken;

public final class MethodMetadata implements Serializable {
    MethodMetadata() {
    }

    private String methodKey;
    private TypeToken<?> returnType;
    private Integer urlIndex;
    private Integer bodyIndex;
    private RequestTemplate template = new RequestTemplate();
    private List<String> formParams = Lists.newArrayList();
    private SetMultimap<Integer, String> indexToName = LinkedHashMultimap.create();
    private String decodePattern;
    private List<Integer> decodePatternGroups = Lists.newArrayList();

    public String methodKey() {
        return methodKey;
    }

    MethodMetadata methodKey(String methodKey) {
        this.methodKey = methodKey;
        return this;
    }

    public TypeToken<?> returnType() {
        return returnType;
    }

    MethodMetadata returnType(TypeToken<?> returnType) {
        this.returnType = returnType;
        return this;
    }

    public String decodePattern() {
        return decodePattern;
    }

    MethodMetadata decodePattern(String decodePattern) {
        this.decodePattern = decodePattern;
        return this;
    }

    public List<Integer> decodePatternGroups() {
        return decodePatternGroups;
    }

    MethodMetadata decodePatternGroups(List<Integer> groupOrder) {
        this.decodePatternGroups = groupOrder;
        return this;
    }

    public Integer urlIndex() {
        return urlIndex;
    }

    MethodMetadata urlIndex(Integer urlIndex) {
        this.urlIndex = urlIndex;
        return this;
    }

    public Integer bodyIndex() {
        return bodyIndex;
    }

    MethodMetadata bodyIndex(Integer bodyIndex) {
        this.bodyIndex = bodyIndex;
        return this;
    }

    public RequestTemplate template() {
        return template;
    }

    List<String> formParams() {
        return formParams;
    }

    public SetMultimap<Integer, String> indexToName() {
        return indexToName;
    }

    private static final long serialVersionUID = 1L;
}