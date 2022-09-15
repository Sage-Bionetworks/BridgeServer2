package org.sagebionetworks.bridge.models.permissions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.sagebionetworks.bridge.Roles;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.sagebionetworks.bridge.models.permissions.AccessLevel.ADMIN;
import static org.sagebionetworks.bridge.models.permissions.AccessLevel.DELETE;
import static org.sagebionetworks.bridge.models.permissions.AccessLevel.EDIT;
import static org.sagebionetworks.bridge.models.permissions.AccessLevel.LIST;
import static org.sagebionetworks.bridge.models.permissions.AccessLevel.READ;
import static org.sagebionetworks.bridge.models.permissions.EntityType.ASSESSMENT_LIBRARY;
import static org.sagebionetworks.bridge.models.permissions.EntityType.MEMBERS;
import static org.sagebionetworks.bridge.models.permissions.EntityType.ORGANIZATION;
import static org.sagebionetworks.bridge.models.permissions.EntityType.PARTICIPANTS;
import static org.sagebionetworks.bridge.models.permissions.EntityType.SPONSORED_STUDIES;

public class RolesToPermissionsMapGenerator {
    
    private static final List<AccessLevel> listRead = ImmutableList.of(LIST, READ);
    private static final List<AccessLevel> listThroughEdit = ImmutableList.of(LIST, READ, EDIT);
    private static final List<AccessLevel> listThroughDelete = ImmutableList.of(LIST, READ, EDIT, DELETE);
    private static final List<AccessLevel> listThroughAdmin = ImmutableList.of(LIST, READ, EDIT, DELETE, ADMIN);
    private static final List<AccessLevel> listReadAdmin = ImmutableList.of(LIST, READ, ADMIN);
    
    private static final Map<EntityType, List<AccessLevel>> devMap = ImmutableMap.of(
            ASSESSMENT_LIBRARY, listThroughDelete,
            MEMBERS, listRead,
            ORGANIZATION, listRead,
            SPONSORED_STUDIES, listThroughDelete
    );
    
    private static final Map<EntityType, List<AccessLevel>> researcherMap = ImmutableMap.of(
            ASSESSMENT_LIBRARY, listRead,
            MEMBERS, listRead,
            ORGANIZATION, listRead,
            PARTICIPANTS, listThroughDelete,
            SPONSORED_STUDIES, listThroughEdit
    );
    
    private static final Map<EntityType, List<AccessLevel>> orgAdminMap = ImmutableMap.of(
            ASSESSMENT_LIBRARY, listReadAdmin,
            MEMBERS, listThroughAdmin,
            ORGANIZATION, listThroughAdmin,
            SPONSORED_STUDIES, listReadAdmin
    );
    
    private static final Map<EntityType, List<AccessLevel>> adminMap = ImmutableMap.of(
            ASSESSMENT_LIBRARY, listThroughAdmin,
            MEMBERS, listThroughAdmin,
            ORGANIZATION, listThroughAdmin,
            PARTICIPANTS, listThroughAdmin,
            SPONSORED_STUDIES, listThroughAdmin
    );
    
    private static final Map<Roles, Map<EntityType, List<AccessLevel>>> referenceMap = ImmutableMap
            .<Roles, Map<EntityType, List<AccessLevel>>>builder()
            .put(Roles.DEVELOPER, devMap)
            .put(Roles.STUDY_DESIGNER, devMap)
            .put(Roles.RESEARCHER, researcherMap)
            .put(Roles.STUDY_COORDINATOR, researcherMap)
            .put(Roles.ORG_ADMIN, orgAdminMap)
            .put(Roles.ADMIN, adminMap)
            .build();
    
    public static final RolesToPermissionsMapGenerator INSTANCE = new RolesToPermissionsMapGenerator();
    
    public Map<EntityType, Map<String, Set<AccessLevel>>> generate(Set<Roles> roles, String orgId, Set<String> studyIds) {
        Map<EntityType, Map<String, Set<AccessLevel>>> permissionsMap = new HashMap<>();
        
        if (!roles.isEmpty()) {
            for (Roles role : roles) {
                if (referenceMap.containsKey(role)) {
                    Map<EntityType, List<AccessLevel>> roleMap = referenceMap.get(role);
                    for (Map.Entry<EntityType, List<AccessLevel>> entry : roleMap.entrySet()) {
                        EntityType entityType = entry.getKey();
                        Map<String, Set<AccessLevel>> entityMap = permissionsMap.getOrDefault(entityType, new HashMap<>());
                        for (AccessLevel accessLevel : entry.getValue()) {
                            if (entityType.equals(PARTICIPANTS)) {
                                if (!studyIds.isEmpty()) {
                                    for (String studyId : studyIds) {
                                        Set<AccessLevel> accessLevelSet = entityMap.getOrDefault(studyId, new HashSet<>());
                                        accessLevelSet.add(accessLevel);
                                        entityMap.put(studyId, accessLevelSet);
                                    }
                                    permissionsMap.put(entityType, entityMap);
                                }
                            } else {
                                if (orgId != null) {
                                    Set<AccessLevel> accessLevelSet = entityMap.getOrDefault(orgId, new HashSet<>());
                                    accessLevelSet.add(accessLevel);
                                    entityMap.put(orgId, accessLevelSet);
                                    permissionsMap.put(entityType, entityMap);
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return permissionsMap;
    }
}