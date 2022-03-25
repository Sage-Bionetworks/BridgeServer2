package org.sagebionetworks.bridge.models.permissions;

/**
 * The level of administrative access to an entity granted to a user through
 * a permission record.
 */
public enum AccessLevel {
    /**
     * Grants a user access to see the existence of an entity and summary details.
     * For instance, LIST access to a study would allow a user to see that
     * a study exists but not the implementation details such as the schedule.
     */
    LIST,
    /**
     * Grants a user access to read all of an entity. For instance, READ access to 
     * a study would allow a user to see the study design.
     */
    READ,
    /**
     * Grants a user access to edit an entity. For instance, EDIT access to a study
     * would allow a user to change the study design.
     */
    EDIT,
    /**
     * Grants a user access to delete an entity. For instance, DELETE access
     * to a study would allow a user to delete that study.
     */
    DELETE,
    /**
     * Grants a user access to administrate users' access to an entity.
     * For instance, ADMIN access to a study would allow a user to create,
     * update, and remove other users' permissions to that study.
     */
    ADMIN
    
}
