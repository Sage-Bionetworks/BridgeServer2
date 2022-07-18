package org.sagebionetworks.bridge.models.studies;

import java.io.Serializable;

import javax.persistence.Embeddable;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Embeddable
public class DemographicId implements Serializable {
    private String demographicUserId;
    private String categoryName;

    public DemographicId() {
    }

    public DemographicId(String demographicUserId, String categoryName) {
        this.demographicUserId = demographicUserId;
        this.categoryName = categoryName;
    }

    public String getDemographicUserId() {
        return demographicUserId;
    }

    public void setDemographicUserId(String demographicUserId) {
        this.demographicUserId = demographicUserId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((categoryName == null) ? 0 : categoryName.hashCode());
        result = prime * result + ((demographicUserId == null) ? 0 : demographicUserId.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DemographicId other = (DemographicId) obj;
        if (categoryName == null) {
            if (other.categoryName != null)
                return false;
        } else if (!categoryName.equals(other.categoryName))
            return false;
        if (demographicUserId == null) {
            if (other.demographicUserId != null)
                return false;
        } else if (!demographicUserId.equals(other.demographicUserId))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "DemographicId [categoryName=" + categoryName + ", demographicUserId=" + demographicUserId + "]";
    }
}
