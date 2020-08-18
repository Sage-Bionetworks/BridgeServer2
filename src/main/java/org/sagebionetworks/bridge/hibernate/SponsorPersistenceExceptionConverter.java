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
    static final String DUPLICATE_ERROR = "Duplicate entry 'api-study1-org2' for key 'PRIMARY'";
    static final String ORG_ERROR = "Cannot add or update a child row: a foreign key constraint fails "
            +"(`bridgedb`.`organizationsstudies`, CONSTRAINT `fk_os_organization` FOREIGN KEY (`appId`, "
            +"`orgId`) REFERENCES `Organizations` (`appId`, `identifier`))";
    static final String STUDY_ERROR = "Cannot add or update a child row: a foreign key constraint fails "
            +"(`bridgedb`.`organizationsstudies`, CONSTRAINT `fk_os_study` FOREIGN KEY (`studyId`, `appId`) "
            +"REFERENCES `Substudies` (`id`, `studyId`))";
    
    static final String DUPLICATE_MSG = "Organization is already a sponsor of this study";
    static final String ORG_MSG = "Organization not found.";
    static final String STUDY_MSG = "Organization not found.";

    @Override
    public RuntimeException convert(PersistenceException exception, Object entity) {
        Throwable throwable = exception.getCause();
        if (throwable instanceof org.hibernate.exception.ConstraintViolationException) {
            Throwable root = Throwables.getRootCause(exception);
            String message = root.getMessage();
            if (message.matches("Duplicate entry '.*' for key 'PRIMARY'")) {
                message = DUPLICATE_MSG;
            } else if (message.matches("Cannot add or update a child row.*fk_os_organization.*")) {
                return new EntityNotFoundException(Organization.class);
            } else if (message.matches("Cannot add or update a child row.*fk_os_study.*")) {
                return new EntityNotFoundException(Study.class);
            }
            return new ConstraintViolationException.Builder().withMessage(message).build();
        }
        return exception;
    }

}
