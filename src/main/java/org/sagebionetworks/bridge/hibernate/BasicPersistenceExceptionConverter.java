package org.sagebionetworks.bridge.hibernate;

import java.util.Map;

import javax.persistence.OptimisticLockException;
import javax.persistence.PersistenceException;

import com.amazonaws.util.Throwables;
import com.google.common.collect.ImmutableMap;

import org.hibernate.NonUniqueObjectException;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;

@Component
public class BasicPersistenceExceptionConverter implements PersistenceExceptionConverter {
    // For reference, here is what a constraint violation exception looks like from MySQL:
    //  Cannot delete or update a parent row: a foreign key constraint fails (`bridgedb`.
    // `assessmentreferences`, CONSTRAINT `AssessmentRef-Constraint` FOREIGN KEY 
    // (`assessmentGuid`) REFERENCES `Assessments` (`guid`))
    
    // Every constraint in the changeset.sql file can have an entry here.
    static final Map<String,String> CONSTRAINT_VIOLATIONS = new ImmutableMap.Builder<String,String>()
        .put("AssessmentRef-Constraint", "a scheduling session")
        .put("AssessmentRef-Session-Constraint", "a scheduling session")
        .put("Message-Session-Constraint", "a scheduling session")
        .put("TimeWindow-Session-Constraint", "a scheduling session")
        .put("Schedule-Constraint", "a schedule")
        .put("Organization-Constraint", "an organization")
        .build();
    
    static final String CONSTRAINT_MSG = "This %s cannot be deleted or updated because it is referenced by %s.";
    
    @Override
    public RuntimeException convert(PersistenceException exception, Object entity) {
        
        for (Throwable throwable = exception; throwable != null; throwable = throwable.getCause()) {
            System.out.println("----------------");
            System.out.println(throwable.getClass().getCanonicalName());
        }
        
        
        if (exception instanceof OptimisticLockException) {
            return new ConcurrentModificationException(
                    BridgeUtils.getTypeName(entity.getClass()) + 
                    " has the wrong version number; it may have been saved in the background.");
        }
        Throwable throwable = Throwables.getRootCause(exception);

        if (throwable instanceof org.hibernate.NonUniqueObjectException) {
            return new ConstraintViolationException.Builder()
                    .withMessage("A unique identifier was used for more than one object: " + 
                            ((NonUniqueObjectException)throwable).getIdentifier()).build();
        }
        
        if (throwable instanceof java.sql.SQLIntegrityConstraintViolationException) {
            String displayMessage = throwable.getMessage();
            String msg = throwable.getMessage();
            
            for (Map.Entry<String, String> entry : CONSTRAINT_VIOLATIONS.entrySet()) {
                String key = entry.getKey();
                String matchMessage = ".*a foreign key constraint fails.*" + key + ".*";
                if (msg.matches(matchMessage)) {
                    String name = BridgeUtils.getTypeName(entity.getClass());
                    displayMessage = String.format(CONSTRAINT_MSG, name.toLowerCase(), entry.getValue()); 
                }
            }
            return new ConstraintViolationException.Builder()
                    .withMessage(displayMessage).build();
        }
        return exception;
    }
}
