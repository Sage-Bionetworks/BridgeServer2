package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.BridgeConstants.ADHERENCE_RANGE_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.LABEL_FILTER_COUNT_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.LABEL_FILTER_LENGTH_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NEGATIVE;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.models.AdherenceReportSearch;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class AdherenceReportSearchValidatorTest {
    
    AdherenceReportSearchValidator validator;
    
    @BeforeMethod
    public void beforeMethod() {
        validator = new AdherenceReportSearchValidator();
    }
    
    @Test
    public void valid() {
        AdherenceReportSearch search = new AdherenceReportSearch();
        Validate.entityThrowingException(validator, search);
    }
    
    @Test
    public void offsetByNegative() { 
        AdherenceReportSearch search = new AdherenceReportSearch();
        search.setOffsetBy(-1);
        
        assertValidatorMessage(validator, search, "offsetBy", CANNOT_BE_NEGATIVE);
    }
    
    @Test
    public void pageSizeBelowMin() { 
        AdherenceReportSearch search = new AdherenceReportSearch();
        search.setPageSize(1);
        
        assertValidatorMessage(validator, search, "pageSize", PAGE_SIZE_ERROR);
    }

    @Test
    public void pageSizeAboveMax() { 
        AdherenceReportSearch search = new AdherenceReportSearch();
        search.setPageSize(300);
        
        assertValidatorMessage(validator, search, "pageSize", PAGE_SIZE_ERROR);
    }

    @Test
    public void tooManyLabelOptions() { 
        List<String> labels = new ArrayList<>();
        for (int i=0; i < 300; i++) {
            labels.add("abc");
        }
        AdherenceReportSearch search = new AdherenceReportSearch();
        search.setLabelFilters(labels);
        
        assertValidatorMessage(validator, search, "labelFilters", LABEL_FILTER_COUNT_ERROR);
    }

    @Test
    public void nullLabelOption() { 
        List<String> labels = new ArrayList<>();
        labels.add(null);
        AdherenceReportSearch search = new AdherenceReportSearch();
        search.setLabelFilters(labels);
        
        assertValidatorMessage(validator, search, "labelFilters[0]", CANNOT_BE_BLANK);
    }

    @Test
    public void blankLabelOption() { 
        List<String> labels = new ArrayList<>();
        labels.add(" ");
        AdherenceReportSearch search = new AdherenceReportSearch();
        search.setLabelFilters(labels);
        
        assertValidatorMessage(validator, search, "labelFilters[0]", CANNOT_BE_BLANK);
    }
    
    @Test
    public void negativeLabelTooLong() { 
        List<String> labels = new ArrayList<>();
        labels.add(StringUtils.repeat("A", 300));
        AdherenceReportSearch search = new AdherenceReportSearch();
        search.setLabelFilters(labels);
        
        assertValidatorMessage(validator, search, "labelFilters[0]", LABEL_FILTER_LENGTH_ERROR);
    }

    @Test
    public void adherenceMinTooLow() { 
        AdherenceReportSearch search = new AdherenceReportSearch();
        search.setAdherenceMin(-1);
        
        assertValidatorMessage(validator, search, "adherenceMin", ADHERENCE_RANGE_ERROR);
    }

    @Test
    public void adherenceMinTooHigh() { 
        AdherenceReportSearch search = new AdherenceReportSearch();
        search.setAdherenceMin(101);
        
        assertValidatorMessage(validator, search, "adherenceMin", ADHERENCE_RANGE_ERROR);
    }

    @Test
    public void adherenceMaxTooLow() { 
        AdherenceReportSearch search = new AdherenceReportSearch();
        search.setAdherenceMax(-1);
        
        assertValidatorMessage(validator, search, "adherenceMax", ADHERENCE_RANGE_ERROR);
    }

    @Test
    public void adherenceMaxTooHigh() { 
        AdherenceReportSearch search = new AdherenceReportSearch();
        search.setAdherenceMax(101);
        
        assertValidatorMessage(validator, search, "adherenceMax", ADHERENCE_RANGE_ERROR);
    }

    @Test
    public void adherenceMaxHigherThanMin() { 
        AdherenceReportSearch search = new AdherenceReportSearch();
        search.setAdherenceMax(101);
        
        assertValidatorMessage(validator, search, "adherenceMax", ADHERENCE_RANGE_ERROR);
    }
}
