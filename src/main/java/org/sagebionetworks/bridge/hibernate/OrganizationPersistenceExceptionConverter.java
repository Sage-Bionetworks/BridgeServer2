package org.sagebionetworks.bridge.hibernate;

import javax.persistence.PersistenceException;

import com.amazonaws.util.Throwables;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;

@Component
public class OrganizationPersistenceExceptionConverter implements PersistenceExceptionConverter {
    static final String CONSTRAINT_ERROR = "Cannot delete organization (it currently sponsors a study).";
    
    @Override
    public RuntimeException convert(PersistenceException exception, Object entity) {
        Throwable throwable = PersistenceExceptionConverter.getConstraintViolation(exception);
        if (throwable != null) {
            String message = Throwables.getRootCause(throwable).getMessage();
            if (message.matches(".*a foreign key constraint fails.*fk_os_organization.*")) {
                message = CONSTRAINT_ERROR;
            }
            return new ConstraintViolationException.Builder().withMessage(message).build();
        }
        return exception;
    }

}
