package org.sagebionetworks.bridge.hibernate;

import javax.persistence.OptimisticLockException;
import javax.persistence.PersistenceException;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;

/**
 * The current exception converter model is difficult to maintain because a new table with 
 * new foreign-key constraints needs to update the exception handling of all those foreign
 * key models, because the new dependency will throw exceptions their converters will have 
 * to handle. There is a method to the madness of foreign key constraint exceptions, what 
 * we need is one converter that will work for all Hibernate + MySQL persisted models.
 */
@Component
public class BasicPersistenceExceptionConverter implements PersistenceExceptionConverter {
    @Override
    public RuntimeException convert(PersistenceException exception, Object entity) {
        if (exception instanceof OptimisticLockException) {
            return new ConcurrentModificationException(
                    BridgeUtils.getTypeName(entity.getClass()) + 
                    " has the wrong version number; it may have been saved in the background.");
        }
        Throwable throwable = exception.getCause();
        if (throwable instanceof org.hibernate.exception.ConstraintViolationException) {
            return new ConstraintViolationException.Builder()
                    .withMessage(throwable.getMessage())
                    .build();
        }
        return exception;
    }
}