package org.sagebionetworks.bridge.models;

import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.USER_DATA_GROUPS;
import static org.sagebionetworks.bridge.models.ClientInfo.UNKNOWN_CLIENT;
import static org.sagebionetworks.bridge.models.CriteriaUtils.matchCriteria;
import static org.sagebionetworks.bridge.models.OperatingSystem.ANDROID;
import static org.sagebionetworks.bridge.models.OperatingSystem.IOS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.validation.Errors;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.validators.Validate;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

public class CriteriaUtilsTest {
    
    private static final String KEY = "key";
    private static final Set<String> EMPTY_SET = ImmutableSet.of();
    
    // All tests are against v4 of the app.
    private static ClientInfo IOS_SHORT_INFO = ClientInfo.fromUserAgentCache("Unknown Client/14");
    private static ClientInfo IOS_CLIENT_INFO = ClientInfo.fromUserAgentCache("app/4 (deviceName; iPhone OS/3.9) BridgeJavaSDK/12");
    private static ClientInfo ANDROID_CLIENT_INFO = ClientInfo.fromUserAgentCache("app/4 (deviceName; Android/3.9) BridgeJavaSDK/12");
    
    @Test
    public void matchesAgainstNothing() {
        CriteriaContext context = getContext().withClientInfo(IOS_CLIENT_INFO).build();
        
        Criteria criteria = getCriteria().build();
        
        assertTrue(matchCriteria(context, criteria));
    }
    
    @Test
    public void matchesAppRange() {
        CriteriaContext context = getContext().withClientInfo(IOS_CLIENT_INFO).build();
        
        // These should all match v4
        assertTrue(matchCriteria(context, getCriteria().minAppVersion(IOS, 1).build()));
        assertTrue(matchCriteria(context, getCriteria().maxAppVersion(IOS, 4).build()));
        assertTrue(matchCriteria(context, getCriteria().appVersion(IOS, 1, 4).build()));
    }
    
    @Test
    public void filtersAppRange() {
        CriteriaContext context = getContext().withClientInfo(IOS_CLIENT_INFO).build();
        
        // None of these match v4 of an app
        assertFalse(matchCriteria(context, getCriteria().maxAppVersion(IOS, 2).build()));
        assertFalse(matchCriteria(context, getCriteria().minAppVersion(IOS, 5).build()));
        assertFalse(matchCriteria(context, getCriteria().appVersion(IOS, 6, 11).build()));
    }
    
    @Test
    public void matchesAndroidAppRange() {
        CriteriaContext context = getContext().withClientInfo(ANDROID_CLIENT_INFO).build();
        
        // These all match because the os name matches and so matching is used
        assertTrue(matchCriteria(context, getCriteria().maxAppVersion(ANDROID, 4).build()));
        assertTrue(matchCriteria(context, getCriteria().minAppVersion(ANDROID, 1).build()));
        assertTrue(matchCriteria(context, getCriteria().appVersion(ANDROID, 1, 4).build()));
    }
    
    @Test
    public void filtersAppRangeWithAndroid() {
        CriteriaContext context = getContext().withClientInfo(ANDROID_CLIENT_INFO).build();
        
        // These do not match because the os name matches and so matching is applied
        assertFalse(matchCriteria(context, getCriteria().maxAppVersion(ANDROID, 2).build()));
        assertFalse(matchCriteria(context, getCriteria().minAppVersion(ANDROID, 5).build()));
        assertFalse(matchCriteria(context, getCriteria().appVersion(ANDROID, 6, 11).build()));
    }
    
    @Test
    public void doesNotFilterOutIosWithAndroidAppRange() {
        CriteriaContext context = getContext().withClientInfo(IOS_CLIENT_INFO).build();
        
        // But although these do not match the version, the client is different so no filtering occurs
        assertTrue(matchCriteria(context, getCriteria().maxAppVersion(ANDROID, 2).build()));
        assertTrue(matchCriteria(context, getCriteria().minAppVersion(ANDROID, 5).build()));
        assertTrue(matchCriteria(context, getCriteria().appVersion(ANDROID, 6, 11).build()));
    }
    
    @Test
    public void matchesAppRangeIfNoPlatformDeclared() {
        CriteriaContext context = new CriteriaContext.Builder()
            .withContext(getContext().withClientInfo(IOS_SHORT_INFO).build())
            .withClientInfo(ClientInfo.fromUserAgentCache("app/4")).build();
        
        // When the user agent doesn't include platform information, then filtering is not applied
        assertTrue(matchCriteria(context, getCriteria().maxAppVersion(IOS, 2).build()));
        assertTrue(matchCriteria(context, getCriteria().minAppVersion(IOS, 5).build()));
        assertTrue(matchCriteria(context, getCriteria().appVersion(IOS, 6, 11).build()));
    }
    
    @Test
    public void allOfGroupsMatch() {
        CriteriaContext context = getContext().withClientInfo(IOS_CLIENT_INFO).withUserDataGroups(USER_DATA_GROUPS)
                .build();
        
        assertTrue(matchCriteria(context, getCriteria().allOfGroups(ImmutableSet.of("group1")).build()));
        // Two groups are required, that still matches
        assertTrue(matchCriteria(context, getCriteria().allOfGroups(ImmutableSet.of("group1", "group2")).build()));
        // but this doesn't
        assertFalse(matchCriteria(context, getCriteria().allOfGroups(ImmutableSet.of("group1", "group3")).build()));
    }
    
    @Test
    public void noneOfGroupsMatch() {
        CriteriaContext context = getContext().withClientInfo(IOS_CLIENT_INFO).withUserDataGroups(USER_DATA_GROUPS)
                .build();
        // Here, any group at all prevents a match.
        assertFalse(matchCriteria(context, getCriteria().noneOfGroups(ImmutableSet.of("group3", "group1")).build()));
    }

    @Test
    public void noneOfGroupsDefinedButDontPreventMatch() {
        CriteriaContext context = getContext().withClientInfo(IOS_CLIENT_INFO).withUserDataGroups(USER_DATA_GROUPS)
                .build();
        assertTrue(matchCriteria(context, getCriteria().noneOfGroups(ImmutableSet.of("group3")).build()));
    }
    
    @Test
    public void allOfSubstudyIdsMatch() {
        CriteriaContext context = getContext().withClientInfo(IOS_CLIENT_INFO)
                .withUserSubstudyIds(ImmutableSet.of("substudyA", "substudyB")).build();
        
        assertTrue(matchCriteria(context, getCriteria().allOfSubstudyIds(ImmutableSet.of("substudyA")).build()));
        // Two groups are required, that still matches
        assertTrue(matchCriteria(context,
                getCriteria().allOfSubstudyIds(ImmutableSet.of("substudyA", "substudyB")).build()));
        // but this doesn't
        assertFalse(matchCriteria(context,
                getCriteria().allOfSubstudyIds(ImmutableSet.of("substudyA", "substudyc")).build()));
    }
    
    @Test
    public void noneOfSubstudyIdsMatch() {
        CriteriaContext context = getContext().withClientInfo(IOS_CLIENT_INFO)
                .withUserSubstudyIds(ImmutableSet.of("substudyA", "substudyB")).build();
        // Here, any group at all prevents a match.
        assertFalse(matchCriteria(context,
                getCriteria().noneOfSubstudyIds(ImmutableSet.of("substudyC", "substudyA")).build()));
    }

    @Test
    public void noneOfSubstudyIdsDefinedButDontPreventMatch() {
        CriteriaContext context = getContext().withClientInfo(IOS_CLIENT_INFO)
                .withUserSubstudyIds(ImmutableSet.of("substudyA", "substudyB")).build();
        
        assertTrue(matchCriteria(context,
                getCriteria().noneOfSubstudyIds(ImmutableSet.of("substudyC")).build()));
    }
    
    @Test
    public void matchingWithMinimalContextDoesNotCrash() {
        CriteriaContext context = new CriteriaContext.Builder().withStudyIdentifier(TEST_STUDY)
                .withClientInfo(UNKNOWN_CLIENT).build();
        
        assertTrue(matchCriteria(context, getCriteria().build()));
        assertFalse(matchCriteria(context, getCriteria().allOfSubstudyIds(ImmutableSet.of("group1")).build()));
    }
    
    @Test
    public void validateIosMinMaxSameVersionOK() {
        Criteria criteria = getCriteria().appVersion(IOS, 1, 1).build();
        
        Errors errors = Validate.getErrorsFor(criteria);
        CriteriaUtils.validate(criteria, EMPTY_SET, EMPTY_SET, errors);
        assertFalse(errors.hasErrors());
    }

    @Test
    public void validateIosCannotSetMaxUnderMinAppVersion() {
        Criteria criteria = getCriteria().appVersion(IOS, 2, 1).build();
        
        Errors errors = Validate.getErrorsFor(criteria);
        CriteriaUtils.validate(criteria, EMPTY_SET, EMPTY_SET, errors);
        assertEquals(errors.getFieldErrors("maxAppVersions.iphone_os").get(0).getCode(),
                "cannot be less than minAppVersions.iphone_os");
    }
    
    @Test
    public void validateIosCannotSetMinLessThanZero() {
        Criteria criteria = getCriteria().minAppVersion(IOS, -2).build();
        
        Errors errors = Validate.getErrorsFor(criteria);
        CriteriaUtils.validate(criteria, EMPTY_SET, EMPTY_SET, errors);
        assertEquals(errors.getFieldErrors("minAppVersions.iphone_os").get(0).getCode(), "cannot be negative");
    }
    
    // Try these again with a different os name. If two different values work, any value should work.
    
    @Test
    public void validateAndroidMinMaxSameVersionOK() {
        Criteria criteria = getCriteria().appVersion(ANDROID, 1, 1).build();
        
        Errors errors = Validate.getErrorsFor(criteria);
        CriteriaUtils.validate(criteria, EMPTY_SET, EMPTY_SET, errors);
        assertFalse(errors.hasErrors());
    }

    @Test
    public void validateAndroidCannotSetMaxUnderMinAppVersion() {
        Criteria criteria = getCriteria().appVersion(ANDROID, 2, 1).build();
        
        Errors errors = Validate.getErrorsFor(criteria);
        CriteriaUtils.validate(criteria, EMPTY_SET, EMPTY_SET, errors);
        assertEquals(errors.getFieldErrors("maxAppVersions.android").get(0).getCode(),
                "cannot be less than minAppVersions.android");
    }
    
    @Test
    public void validateAndroidCannotSetMinLessThanZero() {
        Criteria criteria = getCriteria().minAppVersion(ANDROID, -2).build();
        
        Errors errors = Validate.getErrorsFor(criteria);
        CriteriaUtils.validate(criteria, EMPTY_SET, EMPTY_SET, errors);
        assertEquals(errors.getFieldErrors("minAppVersions.android").get(0).getCode(), "cannot be negative");
    }
    
    @Test
    public void validateDataGroupSetsCannotBeNull() {
        // Here's an implementation that allows these fields to be null
        Criteria criteria = new Criteria() {
            private String key;
            private String language;
            private Map<String,Integer> minAppVersions = Maps.newHashMap();
            private Map<String,Integer> maxAppVersions = Maps.newHashMap();
            private Set<String> allOfGroups;
            private Set<String> noneOfGroups;
            private Set<String> allOfSubstudies;
            private Set<String> noneOfSubstudies;
            public void setKey(String key) { this.key = key; }
            public String getKey() { return key; }
            public void setLanguage(String language) { this.language = language; }
            public String getLanguage() { return language; }
            public void setMinAppVersion(String osName, Integer minAppVersion) { this.minAppVersions.put(osName, minAppVersion); }
            public Integer getMinAppVersion(String osName) { return minAppVersions.get(osName); }
            public void setMaxAppVersion(String osName, Integer maxAppVersion) { this.maxAppVersions.put(osName, maxAppVersion); }
            public Integer getMaxAppVersion(String osName) { return maxAppVersions.get(osName); }
            public void setAllOfGroups(Set<String> allOfGroups) { this.allOfGroups = allOfGroups; }
            public Set<String> getAllOfGroups() { return allOfGroups; }
            public void setNoneOfGroups(Set<String> noneOfGroups) { this.noneOfGroups = noneOfGroups; }
            public Set<String> getNoneOfGroups() { return noneOfGroups; }
            public void setAllOfSubstudyIds(Set<String> allOfSubstudies) { this.allOfSubstudies = allOfSubstudies; }
            public Set<String> getAllOfSubstudyIds() { return allOfSubstudies; }
            public void setNoneOfSubstudyIds(Set<String> noneOfSubstudies) { this.noneOfSubstudies = noneOfSubstudies; }
            public Set<String> getNoneOfSubstudyIds() { return noneOfSubstudies; }
            public Set<String> getAppVersionOperatingSystems() { return new ImmutableSet.Builder<String>()
                .addAll(minAppVersions.keySet()).addAll(maxAppVersions.keySet()).build(); }
        };
        
        Errors errors = Validate.getErrorsFor(criteria);
        CriteriaUtils.validate(criteria, EMPTY_SET, EMPTY_SET, errors);
        assertEquals(errors.getFieldErrors("allOfGroups").get(0).getCode(), "cannot be null");
        assertEquals(errors.getFieldErrors("noneOfGroups").get(0).getCode(), "cannot be null");
        assertEquals(errors.getFieldErrors("allOfSubstudyIds").get(0).getCode(), "cannot be null");
        assertEquals(errors.getFieldErrors("noneOfSubstudyIds").get(0).getCode(), "cannot be null");
    }
    
    @Test
    public void validateDataGroupCannotBeWrong() {
        Criteria criteria = getCriteria().allOfGroups(ImmutableSet.of("group1"))
                .noneOfGroups(ImmutableSet.of("group2")).build();
        
        Errors errors = Validate.getErrorsFor(criteria);
        CriteriaUtils.validate(criteria, ImmutableSet.of("group3"), EMPTY_SET, errors);
        assertEquals(errors.getFieldErrors("allOfGroups").get(0).getCode(), "'group1' is not in enumeration: group3");
        assertEquals(errors.getFieldErrors("noneOfGroups").get(0).getCode(), "'group2' is not in enumeration: group3");
    }
    
    @Test
    public void validateDataGroupNotBothRequiredAndProhibited() {
        Criteria criteria = getCriteria().allOfGroups(ImmutableSet.of("group1", "group2", "group3"))
                .noneOfGroups(ImmutableSet.of("group2", "group3")).build();
        
        Errors errors = Validate.getErrorsFor(criteria);
        CriteriaUtils.validate(criteria, ImmutableSet.of("group1","group2","group3","group4"), EMPTY_SET, errors);
        // It's a set so validate without describing the order of the groups in the error message
        assertTrue(errors.getFieldErrors("allOfGroups").get(0).getCode().contains("includes these excluded data groups: "));
        assertTrue(errors.getFieldErrors("allOfGroups").get(0).getCode().contains("group2"));
        assertTrue(errors.getFieldErrors("allOfGroups").get(0).getCode().contains("group3"));
    }
    
    @Test
    public void validateSubstudyIdCannotBeWrong() {
        Criteria criteria = getCriteria().allOfSubstudyIds(ImmutableSet.of("substudyA"))
                .noneOfSubstudyIds(ImmutableSet.of("substudyB")).build();
        
        Errors errors = Validate.getErrorsFor(criteria);
        CriteriaUtils.validate(criteria, EMPTY_SET, ImmutableSet.of("substudyC"), errors);
        assertEquals(errors.getFieldErrors("allOfSubstudyIds").get(0).getCode(), "'substudyA' is not in enumeration: substudyC");
        assertEquals(errors.getFieldErrors("noneOfSubstudyIds").get(0).getCode(), "'substudyB' is not in enumeration: substudyC");
    }
    
    @Test
    public void validateSubstudyIdNotBothRequiredAndProhibited() {
        Criteria criteria = getCriteria().build();
        criteria.setAllOfSubstudyIds(ImmutableSet.of("substudyA", "substudyB", "substudyC"));
        criteria.setNoneOfSubstudyIds(ImmutableSet.of("substudyB", "substudyC"));
        Errors errors = Validate.getErrorsFor(criteria);
        CriteriaUtils.validate(criteria, EMPTY_SET, ImmutableSet.of("substudyA", "substudyB", "substudyC"), errors);
        // It's a set so validate without describing the order of the groups in the error message
        assertTrue(errors.getFieldErrors("allOfSubstudyIds").get(0).getCode().contains("includes these excluded substudies: "));
        assertTrue(errors.getFieldErrors("allOfSubstudyIds").get(0).getCode().contains("substudyB"));
        assertTrue(errors.getFieldErrors("allOfSubstudyIds").get(0).getCode().contains("substudyC"));
    }
    
    @Test
    public void validateLanguage() {
        Criteria criteria = getCriteria().minAppVersion(IOS, -2).lang("ena").build();
        
        Errors errors = Validate.getErrorsFor(criteria);
        CriteriaUtils.validate(criteria, EMPTY_SET, EMPTY_SET, errors);
        assertTrue(errors.getFieldErrors("language").get(0).getCode().contains("is not a valid language code"));
        
        errors = Validate.getErrorsFor(criteria);
        criteria.setLanguage("en");
        CriteriaUtils.validate(criteria, EMPTY_SET, EMPTY_SET, errors);
        assertTrue(errors.getFieldErrors("language").isEmpty());
    }
    
    @Test
    public void matchesLanguage() {
        // If a language is declared, the user has to match it.
        Criteria criteria = getCriteria().minAppVersion(IOS, -2).lang("en").build();
        
        // Requires English, user declares English, it matches
        CriteriaContext context = getContext().withLanguages(ImmutableList.of("en")).build();
        assertTrue(matchCriteria(context, criteria));
        
        // Requires English, user declares Spanish, it does not match
        context = getContext().withLanguages(ImmutableList.of("es")).build();
        assertFalse(matchCriteria(context, criteria));
        
        // Doesn't require a language, so we do not care about the user's language to select this
        criteria.setLanguage(null);
        assertTrue(matchCriteria(context, criteria));
        
        // Requires English, but the user declares no language, this does NOT match.
        criteria.setLanguage("en");
        context = getContext().build();
        assertFalse(matchCriteria(context, criteria));
    }
    
    @Test
    public void matchesLanguageRegardlessOfCase() {
        Criteria criteria = getCriteria().minAppVersion(IOS, -2).lang("EN").build();
        
        CriteriaContext context = getContext().withLanguages(ImmutableList.of("en")).build();
        assertTrue(matchCriteria(context, criteria));
        
        criteria.setLanguage("en");
        
        context = getContext().withLanguages(ImmutableList.of("EN")).build();
        assertTrue(matchCriteria(context, criteria));
    }
    
    // We had a report that this was not working so I mocked this out... it filters correctly.
    @Test
    public void testThatMoleMapperConfigurationWorks() {
        ClientInfo info = ClientInfo.parseUserAgentString("MoleMapper/4 (iPhone 6S+; iPhone OS/9.3.4) BridgeSDK/7");
        
        Criteria oldCriteria = getCriteria().appVersion(IOS, 0, 3).appVersion(ANDROID, 0, 0).build();
        
        CriteriaContext context = getContext().withClientInfo(info).build();
        assertFalse(matchCriteria(context, oldCriteria));
        
        Criteria newCriteria = Criteria.create();
        newCriteria.setMinAppVersion("iPhone OS", 4);
        
        assertTrue(matchCriteria(context, newCriteria));
    }
    
    @Test
    public void selectByCriteriaRespectsLanguageOrder() {
        // If a language is declared, the user has to match it.
        AppConfig enAppConfig = AppConfig.create();
        Criteria enCriteria = getCriteria().lang("en").build();
        enAppConfig.setCriteria(enCriteria);
        
        AppConfig frAppConfig = AppConfig.create();
        Criteria frCriteria = getCriteria().lang("fr").build();
        frAppConfig.setCriteria(frCriteria);
        
        List<AppConfig> collection = ImmutableList.of(enAppConfig, frAppConfig);
        
        CriteriaContext context = new CriteriaContext.Builder().withStudyIdentifier(TEST_STUDY)
                .withLanguages(ImmutableList.of("de", "fr", "en")).build();
        
        // Although English is first, we correctly understand that the French app config is the 
        // one the user prefers (given the order of the languages in the context).
        AppConfig selected = CriteriaUtils.selectByCriteria(context, collection);
        assertSame(selected, frAppConfig);
    }
    
    @Test
    public void selectByCriteria() {
        AppConfig firstAppConfig = AppConfig.create();
        firstAppConfig.setCriteria(getCriteria().maxAppVersion(IOS, 5).build());
        
        AppConfig secondAppConfig = AppConfig.create();
        secondAppConfig.setCriteria(getCriteria().minAppVersion(IOS, 6).build());
        
        List<AppConfig> collection = ImmutableList.of(firstAppConfig, secondAppConfig);
        
        CriteriaContext context = getContext()
                .withClientInfo(ClientInfo.parseUserAgentString("AppName/8 (Device Name; iPhone OS) BridgeJavaSDK/3"))
                .build();
        
        AppConfig selected = CriteriaUtils.selectByCriteria(context, collection);
        assertSame(selected, secondAppConfig);
    }

    @Test
    public void selectByCriteriaSortsByLanguageOrder() {
        AppConfig enAppConfig = AppConfig.create();
        Criteria enCriteria = getCriteria().lang("en").build();
        enAppConfig.setCriteria(enCriteria);
        
        AppConfig frAppConfig = AppConfig.create();
        Criteria frCriteria = getCriteria().lang("fr").build();
        frAppConfig.setCriteria(frCriteria);

        AppConfig zhAppConfig = AppConfig.create();
        Criteria zhCriteria = getCriteria().lang("zh").build();
        zhAppConfig.setCriteria(zhCriteria);
        
        List<AppConfig> collection = ImmutableList.of(zhAppConfig, enAppConfig, frAppConfig);
        
        CriteriaContext context = new CriteriaContext.Builder().withStudyIdentifier(TEST_STUDY)
                .withLanguages(ImmutableList.of("de", "fr", "en")).build();

        AppConfig selected = CriteriaUtils.selectByCriteria(context, collection);
        assertEquals(selected.getCriteria().getLanguage(), "fr");
    }
    
    @Test
    public void filterByCriteriaSortsByLanguageOrder() {
        // If a language is declared, the user has to match it.
        AppConfig enAppConfig = AppConfig.create();
        Criteria enCriteria = getCriteria().lang("en").build();
        enAppConfig.setCriteria(enCriteria);
        
        AppConfig frAppConfig = AppConfig.create();
        Criteria frCriteria = getCriteria().lang("fr").build();
        frAppConfig.setCriteria(frCriteria);

        AppConfig zhAppConfig = AppConfig.create();
        Criteria zhCriteria = getCriteria().lang("zh").build();
        zhAppConfig.setCriteria(zhCriteria);
        
        List<AppConfig> collection = ImmutableList.of(zhAppConfig, enAppConfig, frAppConfig);
        
        // The user wants French more than English, so French should be first in the list.
        CriteriaContext context = new CriteriaContext.Builder().withStudyIdentifier(TEST_STUDY)
                .withLanguages(ImmutableList.of("de", "fr", "en")).build();
        
        // Although English is first, we correctly understand that the French app config is the 
        // one the user prefers (given the order of the languages in the context).
        List<AppConfig> selected = CriteriaUtils.filterByCriteria(context, collection);
        assertEquals(selected.size(), 2);
        assertSame(selected.get(0), frAppConfig);
        assertSame(selected.get(1), enAppConfig);
        
        // Let's do it again with a different language preference... the results should change
        context = new CriteriaContext.Builder().withStudyIdentifier(TEST_STUDY)
                .withLanguages(ImmutableList.of("en", "fr", "zh")).build();
        selected = CriteriaUtils.filterByCriteria(context, collection);
        assertEquals(selected.size(), 3);
        assertSame(selected.get(0), enAppConfig);
        assertSame(selected.get(1), frAppConfig);
        assertSame(selected.get(2), zhAppConfig);
    }    

    private CriteriaContext.Builder getContext() {
        return new CriteriaContext.Builder().withStudyIdentifier(TEST_STUDY);
    }
    
    private CritBuilder getCriteria() { 
        return new CritBuilder();
    }
    
    private static class CritBuilder {
        private Criteria criteria = Criteria.create();
        
        CritBuilder lang(String language) {
            criteria.setLanguage(language);
            return this;
        }
        CritBuilder allOfGroups(Set<String> allOfGroups) {
            criteria.setAllOfGroups(allOfGroups);
            return this;
        }
        CritBuilder noneOfGroups(Set<String> noneOfGroups) {
            criteria.setNoneOfGroups(noneOfGroups);
            return this;
        }
        CritBuilder allOfSubstudyIds(Set<String> substudyIds) {
            criteria.setAllOfSubstudyIds(substudyIds);
            return this;
        }
        CritBuilder noneOfSubstudyIds(Set<String> substudyIds) {
            criteria.setNoneOfSubstudyIds(substudyIds);
            return this;
        }
        CritBuilder minAppVersion(String osName, int minAppVersion) {
            criteria.setMinAppVersion(osName, minAppVersion);
            return this;
        }
        CritBuilder maxAppVersion(String osName, int maxAppVersion) {
            criteria.setMaxAppVersion(osName, maxAppVersion);
            return this;
        }
        CritBuilder appVersion(String osName, int minAppVersion, int maxAppVersion) {
            criteria.setMinAppVersion(osName, minAppVersion);
            criteria.setMaxAppVersion(osName, maxAppVersion);
            return this;
        }
        Criteria build() {
            criteria.setKey(KEY);
            return criteria;
        }
    }
}
