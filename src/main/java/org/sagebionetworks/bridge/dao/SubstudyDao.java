package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.substudies.Substudy;

public interface SubstudyDao {
    
    List<Substudy> getSubstudies(String appId, boolean includeDeleted);
    
    Substudy getSubstudy(String appId, String id);
    
    VersionHolder createSubstudy(Substudy substudy);
    
    VersionHolder updateSubstudy(Substudy substudy);
    
    void deleteSubstudyPermanently(String appId, String id);

}
