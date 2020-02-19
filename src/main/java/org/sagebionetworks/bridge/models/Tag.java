package org.sagebionetworks.bridge.models;

import java.util.Objects;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Generic tag implementation for tagged entities. Each field that implements a set of 
 * tags should set a different category on the tag so they can be stored in the same 
 * table. 
 */
@Entity
@Table(name = "Tags")
public final class Tag {
    @Id
    private String value;
    
    private String category;
    
    public Tag() {}
    
    public Tag(String value, String category) {
        this.value = value;
        this.category = category;
    }
    
    public String getValue() {
        return value;
    }
    public void setValue(String value) {
        this.value = value;
    }
    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    @Override
    public int hashCode() {
        return Objects.hash(category, value);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Tag other = (Tag) obj;
        return Objects.equals(category, other.category) &&
                Objects.equals(value, other.value);
    }
}
