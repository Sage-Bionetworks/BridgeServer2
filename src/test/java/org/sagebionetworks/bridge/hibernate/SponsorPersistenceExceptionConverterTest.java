package org.sagebionetworks.bridge.hibernate;

public class SponsorPersistenceExceptionConverterTest {

    // duplicate entry is constraint violation
    
    // organization not found
    
    // study not found
    
    // exception not converted
    
    /*
    
    if (exception instanceof org.hibernate.exception.ConstraintViolationException) {
        Throwable root = Throwables.getRootCause(exception);
        String message = root.getMessage();
        if (message.matches("Duplicate entry '.*' for key 'PRIMARY'")) {
            return new ConstraintViolationException.Builder()
                    .withMessage("Organization is already a sponsor of this study").build();
        } else if (message.matches("Cannot add or update a child row.*fk_os_organization.*")) {
            return new EntityNotFoundException(Organization.class);
        } else if (message.matches("Cannot add or update a child row.*fk_os_study.*")) {
            return new EntityNotFoundException(Study.class);
        }
    }
    return exception;
     */
}
