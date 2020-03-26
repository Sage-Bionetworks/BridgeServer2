package org.sagebionetworks.bridge.validators;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.assessments.AssessmentConfig;

public class AssessmentConfigValidatorTest {
    
    @Test
    public void test() throws Exception {
        AssessmentConfigValidator validator = new AssessmentConfigValidator();
        
        AssessmentConfig config = new AssessmentConfig();
        config.setConfig(BridgeObjectMapper.get().readTree("{\n" + 
                "    \"guid\": \"f2637b2a-b473-4f84-b645-1431a3b4418e\",\n" + 
                "    \"createdOn\": \"2019-11-04T22:39:44.130Z\",\n" + 
                "    \"modifiedOn\": \"2019-11-04T22:44:44.859Z\",\n" + 
                "    \"version\": 5,\n" + 
                "    \"name\": \"Demographics\",\n" + 
                "    \"identifier\": \"Demographics\",\n" + 
                "    \"published\": true,\n" + 
                "    \"deleted\": false,\n" + 
                "    \"schemaRevision\": 1,\n" + 
                "    \"elements\": [\n" + 
                "        {\n" + 
                "            \"surveyCompoundKey\": \"f2637b2a-b473-4f84-b645-1431a3b4418e:1572907184130\",\n" + 
                "            \"guid\": \"1d1d2eff-6d2a-4b7b-8943-83845e719617\",\n" + 
                "            \"identifier\": \"studyBurstCompletion\",\n" + 
                "            \"type\": \"SurveyInfoScreen\",\n" + 
                "            \"beforeRules\": [\n" + 
                "                {\n" + 
                "                    \"operator\": \"any\",\n" + 
                "                    \"dataGroups\": [\n" + 
                "                        \"parkinsons\",\n" + 
                "                        \"shown_demographics\",\n" + 
                "                        \"control\"\n" + 
                "                    ],\n" + 
                "                    \"skipTo\": \"introduction\",\n" + 
                "                    \"type\": \"SurveyRule\"\n" + 
                "                }\n" + 
                "            ],\n" + 
                "            \"afterRules\": [],\n" + 
                "            \"prompt\": \"You just completed the first day of your Study Burst.\\n\",\n" + 
                "            \"promptDetail\": \"The scientists are now starting to analyze your data. In order to complete their analysis, they have a short health survey for you to complete. Don’t worry, this shouldn’t take any more than  4 minutes.\",\n" + 
                "            \"title\": \"Congratulations!\\n\"\n" + 
                "        },\n" + 
                "        {\n" + 
                "            \"surveyCompoundKey\": \"f2637b2a-b473-4f84-b645-1431a3b4418e:1572907184130\",\n" + 
                "            \"guid\": \"7469757b-5037-4b24-9537-b7adf3f418c8\",\n" + 
                "            \"identifier\": \"introduction\",\n" + 
                "            \"type\": \"SurveyInfoScreen\",\n" + 
                "            \"beforeRules\": [],\n" + 
                "            \"afterRules\": [],\n" + 
                "            \"prompt\": \"It is important for us to know that all your answers are up to date. For that reason, please fill out the complete survey.\\n\",\n" + 
                "            \"promptDetail\": \"\\n\",\n" + 
                "            \"title\": \"Tell us about yourself\"\n" + 
                "        },\n" + 
                "        {\n" + 
                "            \"surveyCompoundKey\": \"f2637b2a-b473-4f84-b645-1431a3b4418e:1572907184130\",\n" + 
                "            \"guid\": \"8eb104ce-7357-45bc-9391-2df358ec0779\",\n" + 
                "            \"identifier\": \"birthYear\",\n" + 
                "            \"type\": \"SurveyQuestion\",\n" + 
                "            \"beforeRules\": [],\n" + 
                "            \"afterRules\": [],\n" + 
                "            \"prompt\": \"What year were you born?\\n\",\n" + 
                "            \"promptDetail\": \"(yyyy)\",\n" + 
                "            \"fireEvent\": false,\n" + 
                "            \"constraints\": {\n" + 
                "                \"rules\": [],\n" + 
                "                \"dataType\": \"integer\",\n" + 
                "                \"required\": false,\n" + 
                "                \"unit\": \"years\",\n" + 
                "                \"minValue\": 1900,\n" + 
                "                \"maxValue\": 2020,\n" + 
                "                \"step\": 1,\n" + 
                "                \"type\": \"IntegerConstraints\"\n" + 
                "            },\n" + 
                "            \"uiHint\": \"numberfield\"\n" + 
                "        },\n" + 
                "        {\n" + 
                "            \"surveyCompoundKey\": \"f2637b2a-b473-4f84-b645-1431a3b4418e:1572907184130\",\n" + 
                "            \"guid\": \"53ddcfe3-f6f8-488c-8063-726feb62dbc8\",\n" + 
                "            \"identifier\": \"sex\",\n" + 
                "            \"type\": \"SurveyQuestion\",\n" + 
                "            \"beforeRules\": [],\n" + 
                "            \"afterRules\": [],\n" + 
                "            \"prompt\": \"What is your sex?\",\n" + 
                "            \"fireEvent\": false,\n" + 
                "            \"constraints\": {\n" + 
                "                \"rules\": [],\n" + 
                "                \"dataType\": \"string\",\n" + 
                "                \"required\": false,\n" + 
                "                \"enumeration\": [\n" + 
                "                    {\n" + 
                "                        \"label\": \"Female\",\n" + 
                "                        \"value\": \"female\",\n" + 
                "                        \"type\": \"SurveyQuestionOption\"\n" + 
                "                    },\n" + 
                "                    {\n" + 
                "                        \"label\": \"Male\",\n" + 
                "                        \"value\": \"male\",\n" + 
                "                        \"type\": \"SurveyQuestionOption\"\n" + 
                "                    },\n" + 
                "                    {\n" + 
                "                        \"label\": \"Prefer not to answer\",\n" + 
                "                        \"value\": \"no_answer\",\n" + 
                "                        \"type\": \"SurveyQuestionOption\"\n" + 
                "                    }\n" + 
                "                ],\n" + 
                "                \"allowOther\": false,\n" + 
                "                \"allowMultiple\": false,\n" + 
                "                \"type\": \"MultiValueConstraints\"\n" + 
                "            },\n" + 
                "            \"uiHint\": \"list\"\n" + 
                "        },\n" + 
                "        {\n" + 
                "            \"surveyCompoundKey\": \"f2637b2a-b473-4f84-b645-1431a3b4418e:1572907184130\",\n" + 
                "            \"guid\": \"be8da6e6-08a6-43ec-8a2b-fcaa02d72fee\",\n" + 
                "            \"identifier\": \"diagnosis\",\n" + 
                "            \"type\": \"SurveyQuestion\",\n" + 
                "            \"beforeRules\": [],\n" + 
                "            \"afterRules\": [\n" + 
                "                {\n" + 
                "                    \"operator\": \"eq\",\n" + 
                "                    \"value\": \"parkinsons\",\n" + 
                "                    \"assignDataGroup\": \"parkinsons\",\n" + 
                "                    \"type\": \"SurveyRule\"\n" + 
                "                },\n" + 
                "                {\n" + 
                "                    \"operator\": \"eq\",\n" + 
                "                    \"value\": \"control\",\n" + 
                "                    \"assignDataGroup\": \"control\",\n" + 
                "                    \"type\": \"SurveyRule\"\n" + 
                "                },\n" + 
                "                {\n" + 
                "                    \"operator\": \"always\",\n" + 
                "                    \"value\": \"\",\n" + 
                "                    \"assignDataGroup\": \"shown_demographics\",\n" + 
                "                    \"type\": \"SurveyRule\"\n" + 
                "                }\n" + 
                "            ],\n" + 
                "            \"prompt\": \"Have you been diagnosed with Parkinson’s Disease by a medical professional?\\n\",\n" + 
                "            \"fireEvent\": false,\n" + 
                "            \"constraints\": {\n" + 
                "                \"rules\": [\n" + 
                "                    {\n" + 
                "                        \"operator\": \"eq\",\n" + 
                "                        \"value\": \"parkinsons\",\n" + 
                "                        \"assignDataGroup\": \"parkinsons\",\n" + 
                "                        \"type\": \"SurveyRule\"\n" + 
                "                    },\n" + 
                "                    {\n" + 
                "                        \"operator\": \"eq\",\n" + 
                "                        \"value\": \"control\",\n" + 
                "                        \"assignDataGroup\": \"control\",\n" + 
                "                        \"type\": \"SurveyRule\"\n" + 
                "                    },\n" + 
                "                    {\n" + 
                "                        \"operator\": \"always\",\n" + 
                "                        \"value\": \"\",\n" + 
                "                        \"assignDataGroup\": \"shown_demographics\",\n" + 
                "                        \"type\": \"SurveyRule\"\n" + 
                "                    }\n" + 
                "                ],\n" + 
                "                \"dataType\": \"string\",\n" + 
                "                \"required\": false,\n" + 
                "                \"enumeration\": [\n" + 
                "                    {\n" + 
                "                        \"label\": \"Yes\",\n" + 
                "                        \"value\": \"parkinsons\",\n" + 
                "                        \"type\": \"SurveyQuestionOption\"\n" + 
                "                    },\n" + 
                "                    {\n" + 
                "                        \"label\": \"No\",\n" + 
                "                        \"value\": \"control\",\n" + 
                "                        \"type\": \"SurveyQuestionOption\"\n" + 
                "                    },\n" + 
                "                    {\n" + 
                "                        \"label\": \"Prefer not to answer\",\n" + 
                "                        \"value\": \"no_answer\",\n" + 
                "                        \"type\": \"SurveyQuestionOption\"\n" + 
                "                    }\n" + 
                "                ],\n" + 
                "                \"allowOther\": false,\n" + 
                "                \"allowMultiple\": false,\n" + 
                "                \"type\": \"MultiValueConstraints\"\n" + 
                "            },\n" + 
                "            \"uiHint\": \"list\"\n" + 
                "        }\n" + 
                "    ],\n" + 
                "    \"type\": \"Survey\"\n" + 
                "}"));
        
        try {
            Validate.entityThrowingException(validator, config);    
        } catch(InvalidEntityException e) {
            System.out.println(e.getErrors());
        }
        
    }

}
