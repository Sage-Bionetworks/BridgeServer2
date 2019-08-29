package org.sagebionetworks.bridge.hibernate;

import javax.persistence.OptimisticLockException;
import javax.persistence.PersistenceException;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.models.BridgeEntity;

@Component
public class BasicPersistenceExceptionConverter implements PersistenceExceptionConverter {

    @Override
    public RuntimeException convert(PersistenceException exception, Object entity) {
        if (exception instanceof OptimisticLockException) {
            return new ConcurrentModificationException((BridgeEntity)entity);
        }
        // there are no constraints that would prevent deletion at this point.
        return exception;        
    }

}
