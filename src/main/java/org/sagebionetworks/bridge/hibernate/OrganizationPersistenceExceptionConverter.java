package org.sagebionetworks.bridge.hibernate;

import javax.persistence.PersistenceException;

import com.amazonaws.util.Throwables;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;

@Component
public class OrganizationPersistenceExceptionConverter implements PersistenceExceptionConverter {
    @Override
    public RuntimeException convert(PersistenceException exception, Object entity) {
        Throwable throwable = exception.getCause();
        if (throwable instanceof org.hibernate.exception.ConstraintViolationException) {
            String message = Throwables.getRootCause(throwable).getMessage();
            if (message.matches(".*a foreign key constraint fails.*fk_os_organization.*")) {
                message = "Cannot delete organization (it currently sponsors a study).";
            }
            return new ConstraintViolationException.Builder().withMessage(message).build();
        }
        return exception;
    }

}
