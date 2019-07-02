package org.sagebionetworks.bridge.hibernate;

import javax.persistence.OptimisticLockException;
import javax.persistence.PersistenceException;

import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;

public class TemplateRevisionPersistenceExceptionConverter implements PersistenceExceptionConverter {

    @Override
    public RuntimeException convert(PersistenceException exception, Object entity) {
        if (exception instanceof OptimisticLockException) {
            return new ConcurrentModificationException((HibernateTemplateRevision)entity);
        }
        // there are no constraints that would prevent deletion at this point.
        return exception;        
    }

}
