package org.sagebionetworks.bridge.models;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toSet;
import static org.sagebionetworks.bridge.BridgeUtils.isEmpty;

import java.util.Set;

import com.google.common.collect.ImmutableSet;

public final class TagUtils {

    public static Set<Tag> toTagSet(Set<String> tags, String category) {
        checkNotNull(category);
        
        if (isEmpty(tags)) {
            return ImmutableSet.of();
        }
        return tags.stream().map(s -> new Tag(s, category)).collect(toSet());
    }
    
    public static Set<String> toStringSet(Set<Tag> tags) {
        if (isEmpty(tags)) {
            return ImmutableSet.of();
        }
        return tags.stream().map(t -> t.getValue()).collect(toSet());
    }

}
