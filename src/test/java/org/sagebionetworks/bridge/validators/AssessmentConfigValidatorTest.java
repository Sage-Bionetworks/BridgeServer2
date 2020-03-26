package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.AssessmentConfigValidator.INSTANCE;

import java.util.Map;

import com.google.common.collect.ImmutableMap;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.assessments.AssessmentConfig;

public class AssessmentConfigValidatorTest {
    
    private static final String TEST_JSON = "{" + 
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

    @Test
    public void test() throws Exception {
        AssessmentConfig config = new AssessmentConfig();
        config.setConfig(BridgeObjectMapper.get().readTree(TEST_JSON));
        
        assertValidatorMessage(INSTANCE, config, 
                "elements[0].beforeRules[0].identifier", "is missing");
        assertValidatorMessage(INSTANCE, config, "type", "is missing");
    }
    
    @Test
    public void testPluggability() throws Exception {
        // We are not normally going to set further validators on the AssessmentConfigValidator.
        // We'll set them in the constructor so we can use the INSTANCE constant (they don't 
        // change unless we add them). But we want to validate that we can plug in validation 
        // for further nodes in a simple and scalable manner.
        AssessmentConfig config = new AssessmentConfig();
        config.setConfig(BridgeObjectMapper.get().readTree(TEST_JSON));
        
        // A validator that fails on the survey rule
        Map<String, Validator> map = ImmutableMap.of("SurveyRule", new Validator() {
            @Override
            public boolean supports(Class<?> clazz) {
                return false;
            }
            @Override
            public void validate(Object target, Errors errors) {
                errors.rejectValue("foo", "is missing");
            }
        });

        AssessmentConfigValidator validator = new AssessmentConfigValidator();
        validator.setValidators(map);
        
        assertValidatorMessage(validator, config, "elements[0].beforeRules[0].foo", "is missing");
    }
}
