package org.sagebionetworks.bridge.models.permissions;

import java.util.EnumSet;

public enum EntityType {
    
    ASSESSMENT,
    ASSESSMENT_LIBRARY,
    MEMBERS,
    ORGANIZATION,
    PARTICIPANTS,
    SPONSORED_STUDIES,
    STUDY,
    STUDY_PI;
    
    public static final EnumSet<EntityType> ASSESSMENT_TYPES = EnumSet.of(ASSESSMENT);
    public static final EnumSet<EntityType> ORGANIZATION_TYPES = EnumSet.of(ASSESSMENT_LIBRARY, MEMBERS,
            ORGANIZATION, SPONSORED_STUDIES);
    public static final EnumSet<EntityType> STUDY_TYPES = EnumSet.of(PARTICIPANTS, STUDY, STUDY_PI);
}
