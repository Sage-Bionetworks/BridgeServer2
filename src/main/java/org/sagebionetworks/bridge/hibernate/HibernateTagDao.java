package org.sagebionetworks.bridge.hibernate;

import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.TagDao;
import org.sagebionetworks.bridge.models.Tag;

@Component
public class HibernateTagDao implements TagDao {

    private HibernateHelper hibernateHelper;
    
    @Resource(name = "mysqlHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }
    
    @Override
    public Map<String, List<String>> getTags() {
        ListMultimap<String, String> multimap = MultimapBuilder.hashKeys().arrayListValues().build();
        
        List<Tag> tags = hibernateHelper.queryGet("from Tag", null, null, null, Tag.class);
        for (Tag tag : tags) {
            String[] elements = tag.getValue().split(":", 2);
            if (elements.length == 1) {
                multimap.put("default", elements[0]);
            } else {
                multimap.put(elements[0], elements[1]);
            }
        }
        return Multimaps.asMap(multimap);
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
