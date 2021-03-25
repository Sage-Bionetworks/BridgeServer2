package org.sagebionetworks.bridge.exceptions;

import org.sagebionetworks.bridge.models.surveys.Survey;

@SuppressWarnings("serial")
@NoStackTraceException
public class PublishedSurveyException extends PublishedEntityException {
    
    public PublishedSurveyException(Survey survey) {
        super(survey);
    }
}
