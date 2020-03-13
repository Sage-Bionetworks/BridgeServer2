package org.sagebionetworks.bridge.services;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.TagDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;

@Component
public class TagService {
    
    TagDao tagDao;
    
    @Autowired
    final void setTagDao(TagDao tagDao) {
        this.tagDao = tagDao;
    }

    public Map<String, List<String>> getTags() {
        return tagDao.getTags();
    }
    
    public void addTag(String tagValue) {
        if (isBlank(tagValue)) {
            throw new BadRequestException("A tag value is required");
        }
        if (tagValue.startsWith("default:")) {
            tagValue = tagValue.substring(8);
        }
        tagDao.addTag(tagValue);
    }
    
    public void deleteTag(String tagValue) {
        if (isBlank(tagValue)) {
            throw new BadRequestException("A tag value is required");
        }
        if (tagValue.startsWith("default:")) {
            tagValue = tagValue.substring(8);
        }
        tagDao.deleteTag(tagValue);
    }
}
