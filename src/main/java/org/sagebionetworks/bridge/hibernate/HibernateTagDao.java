package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.TagDao;
import org.sagebionetworks.bridge.models.Tag;

@Component
public class HibernateTagDao implements TagDao {

    private HibernateHelper hibernateHelper;
    
    @Resource(name = "basicHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }
    
    int getPageSize() {
        return API_MAXIMUM_PAGE_SIZE;
    }
    
    @Override
    public Map<String, List<String>> getTags() {
        Map<String, List<String>> byGroup = new HashMap<>();
        List<Tag> page = null;
        int offset = 0;
        do {
            page = hibernateHelper.queryGet("from Tag", null, offset, getPageSize(), Tag.class);
            for (Tag tag : page) {
                addByNameSpace(byGroup, tag);
            }
            offset += getPageSize();
        } while(page.size() == getPageSize());
        
        return byGroup;
    }
    
    private static void addByNameSpace(Map<String, List<String>> byGroup, Tag tag) {
        String[] elements = tag.getValue().split(":", 2);
        String ns = (elements.length == 1) ? "default" : elements[0];
        String tagValue = (elements.length == 1) ? elements[0] : elements[1];
        
        byGroup.putIfAbsent(ns, new ArrayList<>());
        byGroup.get(ns).add(tagValue);
    }

    @Override
    public void addTag(String tagValue) {
        Tag tag = new Tag(tagValue);
        
        hibernateHelper.executeWithExceptionHandling(tag, (session) -> {
            session.saveOrUpdate(tag);
            return null;
        });
    }

    @Override
    public void deleteTag(String tagValue) {
        Tag tag = new Tag(tagValue);
        
        hibernateHelper.executeWithExceptionHandling(tag, (session) -> {
            session.remove(tag);
            return null;
        });
    }
}
