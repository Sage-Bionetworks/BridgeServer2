package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.ADHERENCE_RANGE_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.LABEL_FILTER_COUNT_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.LABEL_FILTER_LENGTH_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NEGATIVE;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.models.AdherenceReportSearch;
import org.springframework.validation.Errors;

public class AdherenceReportSearchValidator extends AbstractValidator {
    
    public static final AdherenceReportSearchValidator INSTANCE = new AdherenceReportSearchValidator();

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
        List<String> labelFilters = search.getLabelFilters();
        if (labelFilters != null) {
            if (labelFilters.size() > 50) {
                errors.rejectValue("labelFilters", LABEL_FILTER_COUNT_ERROR);   
            } else {
                for (int i=0; i < labelFilters.size(); i++) {
                    String value = labelFilters.get(i);
                    if (StringUtils.isBlank(value)) {
                        errors.rejectValue("labelFilters["+i+"]", Validate.CANNOT_BE_BLANK);
                    } else if (value.length() > 100) {
                        errors.rejectValue("labelFilters["+i+"]", LABEL_FILTER_LENGTH_ERROR);
                    }
                }
            }
        }
        Integer adherenceMin = search.getAdherenceMin();
        Integer adherenceMax = search.getAdherenceMax();
        if (adherenceMax < adherenceMin) {
            errors.rejectValue("adherenceMax", "cannot be less than adherenceMin");
        }
        if ((adherenceMin < 0 ||  adherenceMin > 100)) {
            errors.rejectValue("adherenceMin", ADHERENCE_RANGE_ERROR);   
        }
        if ((adherenceMax < 0 ||  adherenceMax > 100)) {
            errors.rejectValue("adherenceMax", ADHERENCE_RANGE_ERROR);   
        }
    }
}
