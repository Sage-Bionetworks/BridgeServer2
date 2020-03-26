package org.sagebionetworks.bridge.dao;

import java.util.List;
import java.util.Map;

public interface TagDao {
    
    public Map<String, List<String>> getTags();
    
    public void addTag(String tag);
    
    public void deleteTag(String tag);

}
