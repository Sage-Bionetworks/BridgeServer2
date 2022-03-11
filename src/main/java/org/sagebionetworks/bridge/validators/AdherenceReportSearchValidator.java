package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NEGATIVE;

import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.models.AdherenceReportSearch;
import org.springframework.validation.Errors;

public class AdherenceReportSearchValidator extends AbstractValidator {
    
    public static final AdherenceReportSearchValidator INSTANCE = new AdherenceReportSearchValidator();

    static final String ADHERENCE_RANGE_ERROR = "% must be from 0-100";
    static final String ADHERENCE_RANGE_ORDER_ERROR = "cannot be less than adherenceMin";
    static final String LABEL_FILTER_COUNT_ERROR = "cannot have over 50 entries";
    static final String LABEL_FILTER_LENGTH_ERROR = "cannot be over 100 characters";

    @Override
    public void validate(Object object, Errors errors) {
        AdherenceReportSearch search = (AdherenceReportSearch)object;
        
        if (search.getOffsetBy() < 0) {
            errors.rejectValue("offsetBy", CANNOT_BE_NEGATIVE);
        }
        // Just set a sane upper limit on this.
        if (search.getPageSize() < API_MINIMUM_PAGE_SIZE || search.getPageSize() > API_MAXIMUM_PAGE_SIZE) {
            errors.rejectValue("pageSize", PAGE_SIZE_ERROR);
        }
        Set<String> labelFilters = search.getLabelFilters();
        if (labelFilters != null) {
            if (labelFilters.size() > 50) {
                errors.rejectValue("labelFilters", LABEL_FILTER_COUNT_ERROR);   
            } else {
                int i=0;
                for (String value : labelFilters) {
                    if (StringUtils.isBlank(value)) {
                        errors.rejectValue("labelFilters["+i+"]", Validate.CANNOT_BE_BLANK);
                    } else if (value.length() > 100) {
                        errors.rejectValue("labelFilters["+i+"]", LABEL_FILTER_LENGTH_ERROR);
                    }
                    i++;
                }
            }
        }
        Integer adherenceMin = search.getAdherenceMin();
        Integer adherenceMax = search.getAdherenceMax();
        
        if (adherenceMin != null && adherenceMax != null && adherenceMax < adherenceMin) {
            errors.rejectValue("adherenceMax", ADHERENCE_RANGE_ORDER_ERROR);
        }
        if (adherenceMin != null && (adherenceMin < 0 ||  adherenceMin > 100)) {
            errors.rejectValue("adherenceMin", ADHERENCE_RANGE_ERROR);   
        }
        if (adherenceMax != null && (adherenceMax < 0 ||  adherenceMax > 100)) {
            errors.rejectValue("adherenceMax", ADHERENCE_RANGE_ERROR);   
        }
    }
}
