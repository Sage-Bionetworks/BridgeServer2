package org.sagebionetworks.bridge.hibernate;

import javax.persistence.PersistenceException;

import com.amazonaws.util.Throwables;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;

@Component
public class OrganizationPersistenceExceptionConverter implements PersistenceExceptionConverter {
    static final String STUDY_CONSTRAINT = "Cannot delete organization (it currently sponsors a study).";
    static final String ACCOUNT_CONSTRAINT = "Cannot delete organization (it currently contains one or more accounts).";
    
    @Override
    public RuntimeException convert(PersistenceException exception, Object entity) {
        exception.printStackTrace();
        Throwable throwable = PersistenceExceptionConverter.getConstraintViolation(exception);
        if (throwable != null) {
            String message = Throwables.getRootCause(throwable).getMessage();
            if (message.matches(".*a foreign key constraint fails.*fk_os_organization.*")) {
                message = STUDY_CONSTRAINT;
            } else if (message.matches(".*a foreign key constraint fails.*accounts_ibfk_1.*")) {
                message = ACCOUNT_CONSTRAINT;
            }
            return new ConstraintViolationException.Builder().withMessage(message).build();
        }
        return exception;
    }

}
