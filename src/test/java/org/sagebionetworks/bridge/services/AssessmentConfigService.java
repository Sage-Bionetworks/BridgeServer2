package org.sagebionetworks.bridge.services;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.hibernate.HibernateAssessmentConfigDao;
import org.sagebionetworks.bridge.models.assessments.AssessmentConfig;

@Component
public class AssessmentConfigService {

    private HibernateAssessmentConfigDao dao;
    
    @Autowired
    public final void setHibernateAssessmentConfigDao(HibernateAssessmentConfigDao dao) {
        this.dao = dao;
    }
    
    DateTime getCreatedOn() {
        return DateTime.now();
    }
    
    DateTime getModifiedOn() {
        return DateTime.now();
    }
    
    public void createAssessmentConfig(String guid) {
        AssessmentConfig config = new AssessmentConfig();
        config.setCreatedOn(getCreatedOn());
        config.setModifiedOn(getModifiedOn());
        config.setConfig(JsonNodeFactory.instance.objectNode());
        
        dao.updateAssessmentConfig(guid, config);
    }
    
    public AssessmentConfig getAssessmentConfig(String guid) {
        return null;
    }
    
    public AssessmentConfig updateAssessmentConfig(String guid, AssessmentConfig config) {
        return null;
    }
    
    public void deleteAssessmentConfig(String guid) {
        
    }
    
}
