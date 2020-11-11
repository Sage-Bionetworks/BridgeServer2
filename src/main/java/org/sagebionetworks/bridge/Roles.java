package org.sagebionetworks.bridge;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableMap;

public enum Roles {
    DEVELOPER,
    RESEARCHER,
    ADMIN,
    WORKER,
    SUPERADMIN;
    
    /**
     * This user has a role that marks the user as a user of the non-participant APIs (they have 
     * a role assigned to human administrative accounts).
     */
    public static final Set<Roles> ADMINISTRATIVE_ROLES = EnumSet.of(DEVELOPER, RESEARCHER, ADMIN);
    
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
        .build();
}
