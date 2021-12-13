package org.sagebionetworks.bridge;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableMap;

public enum Roles {
    DEVELOPER,
    RESEARCHER,
    STUDY_COORDINATOR,
    STUDY_DESIGNER,
    ORG_ADMIN,
    ADMIN,
    WORKER,
    SUPERADMIN;
    
    /**
     * This user has a role that marks the user as a user of the non-participant APIs (they have 
     * a role assigned to human administrative accounts).
     */
    public static final Set<Roles> ADMINISTRATIVE_ROLES = EnumSet.of(DEVELOPER, RESEARCHER, STUDY_COORDINATOR, 
            STUDY_DESIGNER, ORG_ADMIN, ADMIN);
    
    /**
     * To assess if an API caller can add or remove a role to/from an account, the caller must 
     * have one of the roles that is mapped to the role through this map. For example, if the 
     * caller wants to add the RESEARCHER role to an account (or remove it), the caller must be 
     * an super-administrator or administrator.
     */
    public static final Map<Roles,EnumSet<Roles>> CAN_BE_EDITED_BY = new ImmutableMap.Builder<Roles, EnumSet<Roles>>()
        .put(SUPERADMIN, EnumSet.of(SUPERADMIN))
        .put(ADMIN, EnumSet.of(SUPERADMIN))
        .put(WORKER, EnumSet.of(SUPERADMIN))
        .put(RESEARCHER, EnumSet.of(SUPERADMIN, ADMIN))
        .put(DEVELOPER, EnumSet.of(SUPERADMIN, ADMIN, RESEARCHER))
        // We want organizational administrators to provision accounts for their own organization,
        // so they can bootstrap these roles, including other organization administrators.
        .put(ORG_ADMIN, EnumSet.of(SUPERADMIN, ADMIN, ORG_ADMIN))
        .put(STUDY_COORDINATOR, EnumSet.of(SUPERADMIN, ADMIN, ORG_ADMIN))
        .put(STUDY_DESIGNER, EnumSet.of(SUPERADMIN, ADMIN, ORG_ADMIN))
        .build();

    public static final Map<Roles,EnumSet<Roles>> PASSES_AS_ROLE = new ImmutableMap.Builder<Roles, EnumSet<Roles>>()
            .put(SUPERADMIN, EnumSet.of(SUPERADMIN, ADMIN, RESEARCHER, DEVELOPER, ORG_ADMIN, STUDY_COORDINATOR, STUDY_DESIGNER))
            .put(ADMIN, EnumSet.of(ADMIN, RESEARCHER, DEVELOPER, ORG_ADMIN, STUDY_COORDINATOR, STUDY_DESIGNER))
            .put(RESEARCHER, EnumSet.of(RESEARCHER))
            .put(DEVELOPER, EnumSet.of(DEVELOPER))
            .put(ORG_ADMIN, EnumSet.of(ORG_ADMIN))
            .put(STUDY_COORDINATOR, EnumSet.of(STUDY_COORDINATOR))
            .put(STUDY_DESIGNER, EnumSet.of(STUDY_DESIGNER))
            .put(WORKER, EnumSet.of(WORKER))
            .build();
}
