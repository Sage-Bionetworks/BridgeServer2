package org.sagebionetworks.bridge.hibernate;

import javax.persistence.PersistenceException;

import com.amazonaws.util.Throwables;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.organizations.Organization;
import org.sagebionetworks.bridge.models.studies.Study;

@Component
public class SponsorPersistenceExceptionConverter implements PersistenceExceptionConverter {
    static final String DUPLICATE_MSG = "Organization is already a sponsor of this study";

    @Override
    public RuntimeException convert(PersistenceException exception, Object entity) {
        Throwable throwable = PersistenceExceptionConverter.getConstraintViolation(exception);
        if (throwable != null) {
            String message = Throwables.getRootCause(throwable).getMessage();
            if (message.matches(".*Duplicate entry.*for key 'PRIMARY'")) {
                message = DUPLICATE_MSG;
            } else if (message.matches(".*Cannot add or update a child row.*fk_os_organization.*")) {
                return new EntityNotFoundException(Organization.class);
            } else if (message.matches(".*Cannot add or update a child row.*fk_os_study.*")) {
                return new EntityNotFoundException(Study.class);
            }
            return new ConstraintViolationException.Builder().withMessage(message).build();
        }
        return exception;
    }
}
