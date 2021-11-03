package org.sagebionetworks.bridge.models;

import static org.sagebionetworks.bridge.BridgeUtils.getElement;
import static org.testng.Assert.assertEquals;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

import org.testng.annotations.Test;

public class TagUtilsTest {

    @Test
    public void toTagSetStringSetNull() {
        Set<Tag> retValue = TagUtils.toTagSet(null);
        assertEquals(retValue.size(), 0);
    }
    
    @Test
    public void toTagSetStringSetEmpty() {
        Set<Tag> retValue = TagUtils.toTagSet(new HashSet<>());
        assertEquals(retValue.size(), 0);
    }
    
    @Test
    public void toTagSet() {
        Set<Tag> retValue = TagUtils.toTagSet(ImmutableSet.of("A", "B", "C"));
        assertEquals(retValue.size(), 3);
        
        Optional<Tag> tagA = getElement(retValue, Tag::getValue, "A");
        Optional<Tag> tagB = getElement(retValue, Tag::getValue, "B");
        Optional<Tag> tagC = getElement(retValue, Tag::getValue, "C");
        
        assertEquals(tagA.get().getValue(), "A");
        assertEquals(tagB.get().getValue(), "B");
        assertEquals(tagC.get().getValue(), "C");
    }
    
    @Test
    public void toStringSetTagSetNull() {
        Set<String> retValue = TagUtils.toStringSet(null);
        assertEquals(retValue.size(), 0);
    }
    
    @Test
    public void toStringSetTagSetEmpty() {
        Set<String> retValue = TagUtils.toStringSet(new HashSet<>());
        assertEquals(retValue.size(), 0);
    }
    
    @Test
    public void toStringSet() {
        Set<Tag> tags = ImmutableSet.of(new Tag("A"), new Tag("B"), new Tag("C"));
        Set<String> retValue = TagUtils.toStringSet(tags);
        assertEquals(retValue, ImmutableSet.of("A", "B", "C"));
    }    
}
