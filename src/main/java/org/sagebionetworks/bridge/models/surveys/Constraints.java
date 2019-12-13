package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.collect.Lists;

@JsonTypeInfo( use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "dataType")
@JsonSubTypes({
    @Type(name="multivalue", value=MultiValueConstraints.class),
    @Type(name="boolean", value=BooleanConstraints.class),
    @Type(name="integer", value=IntegerConstraints.class),
    @Type(name="decimal", value=DecimalConstraints.class),
    @Type(name="string", value=StringConstraints.class),
    @Type(name="datetime", value=DateTimeConstraints.class),
    @Type(name="date", value=DateConstraints.class),
    @Type(name="time", value=TimeConstraints.class),
    @Type(name="duration", value=DurationConstraints.class),
    @Type(name="bloodpressure", value=BloodPressureConstraints.class),
    @Type(name="height", value=HeightConstraints.class),
    @Type(name="weight",value=WeightConstraints.class),
    @Type(name="yearmonth", value=YearMonthConstraints.class),
    @Type(name="postalcode", value=PostalCodeConstraints.class),
    @Type(name="year", value=YearConstraints.class)
})
public class Constraints {

    private EnumSet<UIHint> hints;
    private List<SurveyRule> rules = Lists.newArrayList();
    private DataType dataType;
    private boolean required;

    @JsonIgnore
    public EnumSet<UIHint> getSupportedHints() {
        return hints;
    }
    public void setSupportedHints(EnumSet<UIHint> hints) {
        this.hints = hints;
    }
    public DataType getDataType() {
        return dataType;
    };
    public void setDataType(DataType dataType) {
        this.dataType = dataType;
    }
    public List<SurveyRule> getRules() {
        return rules;
    }
    public void setRules(List<SurveyRule> rules) {
        this.rules = rules;
    }
    /**
     * Is the question required? By default questions can be skipped: they are optional and there
     * doesn't need to be an answer in the submitted answer set. Note that this might be different 
     * from a question that requires an explicit choice to avoid answering (a no answer entry is 
     * submitted as part of the data set). This is probably better to model as one of a number 
     * of options in a MultiValueConstraint. 
     */
    public boolean isRequired() {
        return required;
    }
    public void setRequired(boolean required) {
        this.required = required;
    }
    
    // NOTE: This shouldn't be necessary as I understand it. When we serialize this,
    // we use the BridgeObjectMapper which adds the "type" property to all objects,
    // or it's supposed to. But SurveyControllerTest says otherwise.
    public String getType() {
        return this.getClass().getSimpleName();
    }
}
