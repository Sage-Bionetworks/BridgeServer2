package org.sagebionetworks.bridge.hibernate;

import java.util.List;

import javax.persistence.PersistenceException;

import com.google.common.base.Throwables;

import org.hibernate.exception.ConstraintViolationException;

public interface PersistenceExceptionConverter {
    
    /**
     * If this PersistenceException wraps a Hibernate ConstraintViolationException, it returns that 
     * exception, or it returns null (with Hibernate, it appears that PersistenceException wraps 
     * HQL queries, but not native queries, leading to inconsistent exception unwrapping in the 
     * converters). If a DAO mixes the two types of queries, the converter should work for both.
     */
    public static ConstraintViolationException getConstraintViolation(PersistenceException exception) {
        List<Throwable> chain = Throwables.getCausalChain(exception);
        for (int i=0; i < chain.size(); i++) {
            Throwable throwable = chain.get(i);
            if (throwable instanceof ConstraintViolationException) {
                return (ConstraintViolationException)throwable;
            }
        }
        return null;
    }
    
    /**
     * Return a domain-specific exception for this persistence exception. If the converter 
     * does not wish to convert an exception, it should return the original persistence
     * exception.
     *   
     * @param exception
     *      the persistence exception thrown by Hibernate
     * @param entity
     *      the entity passed to HibernateHelper
     * @return
     *      return new exception if converted, or the original exception if no conversion is to take place.
     */
    RuntimeException convert(PersistenceException exception, Object entity);
}
