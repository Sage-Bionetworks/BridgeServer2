package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.BridgeUtils.getTypeName;

import java.io.Serializable;
import java.util.Map;

import javax.persistence.OptimisticLockException;
import javax.persistence.PersistenceException;

import com.amazonaws.util.Throwables;
import com.google.common.collect.ImmutableMap;

import org.hibernate.NonUniqueObjectException;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;

/**
 * Maintaining an exception converter for each Hibernate model is difficult to maintain 
 * because a new table with new foreign-key constraints needs to update the exception handling 
 * for all foreign key models. As the pattern has become clearer, we should be able to write
 * one converter that will work for all Hibernate + MySQL persisted models. This is the start 
 * of that effort and I will replace other persistence converters over time.
 * 
 * Note that exceptions from the execution of raw SQL queries vs. HQL queries are reported 
 * differently.
 */
@Component
public class MySQLHibernatePersistenceExceptionConverter implements PersistenceExceptionConverter {

    static final String DEFAULT_CONSTRAINT_MSG = "Cannot update or delete this item because it is in use."; 
    static final String FK_CONSTRAINT_MSG = "This %s cannot be deleted or updated because it is referenced by %s.";
    static final String MISSING_PARENT_INVALID_FK_CONSTRAINT_MSG = "This %s cannot be created or updated because the referenced %s does not exist.";
    static final String UNIQUE_CONSTRAINT_MSG = "Cannot update this %s because it has duplicate %s";
    static final String NON_UNIQUE_MSG = "Another %s has already used a value which must be unique: %s";
    static final String WRONG_VERSION_MSG = "%s has the wrong version number; it may have been saved in the background.";
    
    // All MySQL constraint violations take this format:
    //      javax.persistence.PersistenceException which wraps
    //          org.hibernate.exception.ConstraintViolationException which wraps
    //              java.sql.SQLIntegrityConstraintViolationException
    // And the SQL-specific error message indicates something about the type of constraint and thus, the 
    // appropriate error message. We look for the following keys:
    
    /**
     * Foreign key constraints: the name of the constraint is used to inform the caller about what object is 
     * using the target entity. Constraints that cascade deletes do not need to be added to this map (updates
     * seem to always be handled by Hibernate when they are needed).
     */
    private static final Map<String,String> FOREIGN_KEY_CONSTRAINTS = new ImmutableMap.Builder<String,String>()
            .put("AssessmentRef-Assessment-Constraint", "a scheduling session")
            .put("Schedule-Organization-Constraint", "a schedule")
            .put("Substudies-Schedule-Constraint", "a study")
            .put("`fk_substudy`", "an account")
            .build();
    
    /**
     * Foreign key missing parent constraints: the name of the constraint is used to inform the caller about an attempt
     * to add a reference to a parent entity that does not exist.
     */
    private static final Map<String, String> FOREIGN_KEY_MISSING_PARENT_CONSTRAINTS = new ImmutableMap.Builder<String,String>()
            .put("Permission-User-Constraint", "user account")
            .put("Permission-Assessment-Constraint", "assessment")
            .put("Permission-Organization-Constraint", "organization")
            .put("Permission-Study-Constraint", "study")
            .build();
    
    /**
     * Unique key constraints: the name of the constraint is used to inform the caller about the fields that are 
     * being duplicated.
     */
    private static final Map<String,String> UNIQUE_KEY_CONSTRAINTS = new ImmutableMap.Builder<String,String>()
            .put("PRIMARY", "primary keys")
            .put("TimeWindow-guid-sessionGuid-idx", "time window GUIDs")
            .put("Session-guid-scheduleGuid-idx", "session GUIDs")
            .put("Permissions-UserId-AccessLevel-EntityType-EntityId-Index", "access level permission")
            .build();

    @Override
    public RuntimeException convert(PersistenceException exception, Object entity) {
        String name = (entity == null) ? "item" : getTypeName(entity.getClass());

        if (exception instanceof OptimisticLockException) {
            return new ConcurrentModificationException(String.format(WRONG_VERSION_MSG, name));
        }
        if (exception instanceof NonUniqueObjectException) {
            Serializable identifier = ((NonUniqueObjectException)exception).getIdentifier();
            return new ConstraintViolationException.Builder()
                    .withMessage(String.format(NON_UNIQUE_MSG, name.toLowerCase(), identifier)).build();
        }

        Throwable throwable = Throwables.getRootCause(exception);
        if (throwable instanceof java.sql.SQLIntegrityConstraintViolationException) {
            String rawMessage = throwable.getMessage();
            String displayMessage = DEFAULT_CONSTRAINT_MSG;
            // SQLIntegrityConstraintViolation can contain different constraint violations, 
            // including foreign key and duplicate entry violations
            if (rawMessage.contains("Duplicate entry")) {
                displayMessage = selectMsg(
                        rawMessage, UNIQUE_KEY_CONSTRAINTS, UNIQUE_CONSTRAINT_MSG, name, displayMessage);
            } else if (rawMessage.contains("Cannot add or update a child row: a foreign key constraint fails")) {
                displayMessage = selectMsg(
                        rawMessage, FOREIGN_KEY_MISSING_PARENT_CONSTRAINTS, MISSING_PARENT_INVALID_FK_CONSTRAINT_MSG, name, displayMessage);
            } else if (rawMessage.contains("a foreign key constraint fails")) {
                displayMessage = selectMsg(
                        rawMessage, FOREIGN_KEY_CONSTRAINTS, FK_CONSTRAINT_MSG, name, displayMessage);
            }
            return new ConstraintViolationException.Builder().withMessage(displayMessage).build();
        }
        return exception;
    }
    
    private String selectMsg(String rawMessage, Map<String, String> constraintNames, String message,
            String name, String defaultMessage) {
        for (Map.Entry<String, String> entry : constraintNames.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (rawMessage.contains(key)) {
                return String.format(message, name.toLowerCase(), value); 
            }
        }
        return defaultMessage;
    }
    
}
