package org.sagebionetworks.bridge.hibernate;

import javax.persistence.PersistenceException;

public class TemplatePersistenceExceptionConverter implements PersistenceExceptionConverter {

    @Override
    public RuntimeException convert(PersistenceException exception, Object entity) {
        return exception;
    }

}
