package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.ValidatorUtilsTest.getInvalidStringLengthMessage;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.TEXT_SIZE;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.sagebionetworks.bridge.models.assessments.config.AssessmentConfig;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class AssessmentConfigValidatorTest {
    
    public static final String TEST_JSON = "{" + 
            "    \"guid\": \"f2637b2a-b473-4f84-b645-1431a3b4418e\"," + 
            "    \"createdOn\": \"2019-11-04T22:39:44.130Z\"," + 
            "    \"modifiedOn\": \"2019-11-04T22:44:44.859Z\"," + 
            "    \"version\": 5," + 
            "    \"name\": \"Demographics\"," + 
            "    \"identifier\": \"Demographics\"," + 
            "    \"published\": true," + 
            "    \"deleted\": false," + 
            "    \"schemaRevision\": 1," + 
            "    \"elements\": [" + 
            "        {" + 
            "            \"surveyCompoundKey\": \"f2637b2a-b473-4f84-b645-1431a3b4418e:1572907184130\"," + 
            "            \"guid\": \"1d1d2eff-6d2a-4b7b-8943-83845e719617\"," + 
            "            \"identifier\": \"studyBurstCompletion\"," + 
            "            \"type\": \"SurveyInfoScreen\"," + 
            "            \"beforeRules\": [" + 
            "                {" + 
            "                    \"operator\": \"any\"," + 
            "                    \"dataGroups\": [" + 
            "                        \"parkinsons\"," + 
            "                        \"shown_demographics\"," + 
            "                        \"control\"" + 
            "                    ]," + 
            "                    \"skipTo\": \"introduction\"," + 
            "                    \"type\": \"SurveyRule\"" + 
            "                }" + 
            "            ]," + 
            "            \"afterRules\": []," + 
            "            \"prompt\": \"You just completed the first day of your Study Burst.\"," + 
            "            \"promptDetail\": \"The scientists are now starting to analyze your data.\"" +
            "        }" + 
            "    ]" + 
            "}";
    
    private AssessmentConfigValidator validator;

    @BeforeMethod
    public void beforeMethod() {
        validator = new AssessmentConfigValidator.Builder()
                .addValidator("*", new AbstractValidator() {
                    public void validate(Object target, Errors errors) {
                        JsonNode node = (JsonNode)target;
                        if (!node.has("identifier")) {
                            errors.rejectValue("identifier", "is missing");
                        }
                        if (!node.has("type")) {
                            errors.rejectValue("type", "is missing");
                        }
                    }
                }).build();
    }
    
    @Test
    public void valid() throws Exception {
        ObjectNode obj = (ObjectNode)TestUtils.getClientData();
        obj.put("identifier", "asdf");
        obj.put("type", "SimpleType");
        
        AssessmentConfig config = new AssessmentConfig();
        config.setConfig(obj);
        Validate.entityThrowingException(validator, config);
    }
    
    @Test
    public void test() throws Exception {
        AssessmentConfig config = new AssessmentConfig();
        config.setConfig(BridgeObjectMapper.get().readTree(TEST_JSON));
        
        assertValidatorMessage(validator, config, 
                "config.elements[0].beforeRules[0].identifier", "is missing");
        assertValidatorMessage(validator, config, "config.type", "is missing");
    }
    
    @Test
    public void testNullConfig() throws Exception {
        AssessmentConfig config = new AssessmentConfig();
        assertValidatorMessage(validator, config, "config", "is required");
    }
    
    @Test
    public void testPluggability() throws Exception {
        AssessmentConfig config = new AssessmentConfig();
        config.setConfig(BridgeObjectMapper.get().readTree(TEST_JSON));
        
        // A validator that always fails...
        Validator val = new AbstractValidator() {
            public void validate(Object target, Errors errors) {
                errors.rejectValue("foo", "is missing");
            }
        };
        // set to fail when it encounters a SurveyRule node
        AssessmentConfigValidator validator = new AssessmentConfigValidator.Builder()
                .addValidator("SurveyRule", val).build();
        
        assertValidatorMessage(validator, config, "config.elements[0].beforeRules[0].foo", "is missing");
    }
    
    @Test
    public void stringLengthValidation_config() {
        ObjectNode obj = (ObjectNode) ValidatorUtilsTest.getExcessivelyLargeClientData();
        obj.put("identifier", "asdf");
        obj.put("type", "SimpleType");
    
        AssessmentConfig config = new AssessmentConfig();
        config.setConfig(obj);
        assertValidatorMessage(validator, config, "config", getInvalidStringLengthMessage(TEXT_SIZE));
    }
}
