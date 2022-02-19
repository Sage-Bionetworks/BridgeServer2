package org.sagebionetworks.bridge.models;

import static java.util.Comparator.comparingLong;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
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
import org.mockito.Mockito;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

public class CriteriaUtilsTest extends Mockito {
    
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
    public void allOfStudyIdsMatch() {
        CriteriaContext context = getContext().withClientInfo(IOS_CLIENT_INFO)
                .withUserStudyIds(ImmutableSet.of("studyA", "studyB")).build();
        
        assertTrue(matchCriteria(context, getCriteria().allOfStudyIds(ImmutableSet.of("studyA")).build()));
        // Two groups are required, that still matches
        assertTrue(matchCriteria(context,
                getCriteria().allOfStudyIds(ImmutableSet.of("studyA", "studyB")).build()));
        // but this doesn't
        assertFalse(matchCriteria(context,
                getCriteria().allOfStudyIds(ImmutableSet.of("studyA", "studyc")).build()));
    }
    
    @Test
    public void noneOfStudyIdsMatch() {
        CriteriaContext context = getContext().withClientInfo(IOS_CLIENT_INFO)
                .withUserStudyIds(ImmutableSet.of("studyA", "studyB")).build();
        // Here, any group at all prevents a match.
        assertFalse(matchCriteria(context,
                getCriteria().noneOfStudyIds(ImmutableSet.of("studyC", "studyA")).build()));
    }

    @Test
    public void noneOfStudyIdsDefinedButDontPreventMatch() {
        CriteriaContext context = getContext().withClientInfo(IOS_CLIENT_INFO)
                .withUserStudyIds(ImmutableSet.of("studyA", "studyB")).build();
        
        assertTrue(matchCriteria(context,
                getCriteria().noneOfStudyIds(ImmutableSet.of("studyC")).build()));
    }
    
    @Test
    public void matchingWithMinimalContextDoesNotCrash() {
        CriteriaContext context = new CriteriaContext.Builder().withAppId(TEST_APP_ID)
                .withClientInfo(UNKNOWN_CLIENT).build();
        
        assertTrue(matchCriteria(context, getCriteria().build()));
        assertFalse(matchCriteria(context, getCriteria().allOfStudyIds(ImmutableSet.of("group1")).build()));
    }
    
    @Test
    public void validateIosMinMaxSameVersionOK() {
        Criteria criteria = getCriteria().appVersion(IOS, 1, 1).build();
        
        Errors errors = mock(Errors.class);
        CriteriaUtils.validate(criteria, EMPTY_SET, EMPTY_SET, errors);
        verifyNoMoreInteractions(errors);
    }

    @Test
    public void validateIosCannotSetMaxUnderMinAppVersion() {
        Criteria criteria = getCriteria().appVersion(IOS, 2, 1).build();
        
        Errors errors = mock(Errors.class);
        CriteriaUtils.validate(criteria, EMPTY_SET, EMPTY_SET, errors);
        
        verify(errors).pushNestedPath("maxAppVersions");
        verify(errors).rejectValue("iphone_os", "cannot be less than minAppVersions.iphone_os");
    }
    
    @Test
    public void validateIosCannotSetMinLessThanZero() {
        Criteria criteria = getCriteria().minAppVersion(IOS, -2).build();
        
        Errors errors = mock(Errors.class);
        CriteriaUtils.validate(criteria, EMPTY_SET, EMPTY_SET, errors);
        verify(errors).pushNestedPath("minAppVersions");
        verify(errors).rejectValue("iphone_os", "cannot be negative");
    }
    
    // Try these again with a different os name. If two different values work, any value should work.
    
    @Test
    public void validateAndroidMinMaxSameVersionOK() {
        Criteria criteria = getCriteria().appVersion(ANDROID, 1, 1).build();
        
        Errors errors = mock(Errors.class);
        CriteriaUtils.validate(criteria, EMPTY_SET, EMPTY_SET, errors);
        verifyNoMoreInteractions(errors);
    }

    @Test
    public void validateAndroidCannotSetMaxUnderMinAppVersion() {
        Criteria criteria = getCriteria().appVersion(ANDROID, 2, 1).build();
        
        Errors errors = mock(Errors.class);
        CriteriaUtils.validate(criteria, EMPTY_SET, EMPTY_SET, errors);
        verify(errors).pushNestedPath("maxAppVersions");
        verify(errors).rejectValue("android", "cannot be less than minAppVersions.android");
    }
    
    @Test
    public void validateAndroidCannotSetMinLessThanZero() {
        Criteria criteria = getCriteria().minAppVersion(ANDROID, -2).build();
        
        Errors errors = mock(Errors.class);
        CriteriaUtils.validate(criteria, EMPTY_SET, EMPTY_SET, errors);
        verify(errors).pushNestedPath("minAppVersions");
        verify(errors).rejectValue("android", "cannot be negative");
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
            private Set<String> allOfStudies;
            private Set<String> noneOfStudies;
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
            public void setAllOfStudyIds(Set<String> allOfStudies) { this.allOfStudies = allOfStudies; }
            public Set<String> getAllOfStudyIds() { return allOfStudies; }
            public void setNoneOfStudyIds(Set<String> noneOfStudies) { this.noneOfStudies = noneOfStudies; }
            public Set<String> getNoneOfStudyIds() { return noneOfStudies; }
            public Set<String> getAppVersionOperatingSystems() { return new ImmutableSet.Builder<String>()
                .addAll(minAppVersions.keySet()).addAll(maxAppVersions.keySet()).build(); }
        };
        
        Errors errors = mock(Errors.class);
        CriteriaUtils.validate(criteria, EMPTY_SET, EMPTY_SET, errors);
        verify(errors).rejectValue("allOfGroups", "cannot be null");
        verify(errors).rejectValue("noneOfGroups", "cannot be null");
        verify(errors).rejectValue("allOfStudyIds", "cannot be null");
        verify(errors).rejectValue("noneOfStudyIds", "cannot be null");
    }
    
    @Test
    public void validateDataGroupCannotBeWrong() {
        Criteria criteria = getCriteria().allOfGroups(ImmutableSet.of("group1"))
                .noneOfGroups(ImmutableSet.of("group2")).build();
        
        Errors errors = mock(Errors.class);
        CriteriaUtils.validate(criteria, ImmutableSet.of("group3"), EMPTY_SET, errors);
        verify(errors).rejectValue("allOfGroups", "'group1' is not in enumeration: group3");
        verify(errors).rejectValue("noneOfGroups", "'group2' is not in enumeration: group3");
    }
    
    @Test
    public void validateDataGroupNotBothRequiredAndProhibited() {
        Criteria criteria = getCriteria().allOfGroups(ImmutableSet.of("group1", "group2", "group3"))
                .noneOfGroups(ImmutableSet.of("group2", "group3")).build();
        
        Errors errors = mock(Errors.class);
        CriteriaUtils.validate(criteria, ImmutableSet.of("group1","group2","group3","group4"), EMPTY_SET, errors);
        // It's a set so validate without describing the order of the groups in the error message
        verify(errors).rejectValue("allOfGroups", "includes these excluded data groups: group2, group3");
    }
    
    @Test
    public void validateStudyIdCannotBeWrong() {
        Criteria criteria = getCriteria().allOfStudyIds(ImmutableSet.of("studyA"))
                .noneOfStudyIds(ImmutableSet.of("studyB")).build();
        
        Errors errors = mock(Errors.class);
        CriteriaUtils.validate(criteria, EMPTY_SET, ImmutableSet.of("studyC"), errors);
        verify(errors).rejectValue("allOfStudyIds", "'studyA' is not in enumeration: studyC");
        verify(errors).rejectValue("noneOfStudyIds", "'studyB' is not in enumeration: studyC");
    }
    
    @Test
    public void validateStudyIdNotBothRequiredAndProhibited() {
        Criteria criteria = getCriteria().build();
        criteria.setAllOfStudyIds(ImmutableSet.of("studyA", "studyB", "studyC"));
        criteria.setNoneOfStudyIds(ImmutableSet.of("studyB", "studyC"));

        Errors errors = mock(Errors.class);
        CriteriaUtils.validate(criteria, EMPTY_SET, ImmutableSet.of("studyA", "studyB", "studyC"), errors);
        // It's a set so validate without describing the order of the groups in the error message
        verify(errors).rejectValue("allOfStudyIds", "includes these excluded studies: studyB, studyC");
    }
    
    @Test
    public void validateLanguage() {
        Criteria criteria = getCriteria().minAppVersion(IOS, 2).lang("ena").build();
        
        Errors errors = mock(Errors.class);
        CriteriaUtils.validate(criteria, EMPTY_SET, EMPTY_SET, errors);
        verify(errors).rejectValue("language", "is not a valid language code");    
        
        errors = mock(Errors.class);
        criteria.setLanguage("en");
        CriteriaUtils.validate(criteria, EMPTY_SET, EMPTY_SET, errors);
        verify(errors, never()).rejectValue(any(), any());
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
        CriteriaContext context = new CriteriaContext.Builder().withAppId(TEST_APP_ID)
                .withLanguages(ImmutableList.of("de", "fr", "en")).build();
        
        // Although English is first, we correctly understand that the French app config is the 
        // one the user prefers (given the order of the languages in the context).
        List<AppConfig> selected = CriteriaUtils.filterByCriteria(context, collection, null);
        assertEquals(selected.size(), 2);
        assertSame(selected.get(0), frAppConfig);
        assertSame(selected.get(1), enAppConfig);
        
        // Let's do it again with a different language preference... the results should change
        context = new CriteriaContext.Builder().withAppId(TEST_APP_ID)
                .withLanguages(ImmutableList.of("en", "fr", "zh")).build();
        selected = CriteriaUtils.filterByCriteria(context, collection, null);
        assertEquals(selected.size(), 3);
        assertSame(selected.get(0), enAppConfig);
        assertSame(selected.get(1), frAppConfig);
        assertSame(selected.get(2), zhAppConfig);
    }
    
    @Test
    public void filterByCriteriaDoesNotChangeSortOrderWithoutLanguages() { 
        AppConfig appConfig1 = AppConfig.create();
        Criteria criteria1 = getCriteria().allOfGroups(ImmutableSet.of("group1")).build();
        appConfig1.setCriteria(criteria1);
        
        AppConfig appConfig2 = AppConfig.create();
        Criteria criteria2 = getCriteria().allOfGroups(ImmutableSet.of("group2")).build();
        appConfig2.setCriteria(criteria2);

        AppConfig appConfig3 = AppConfig.create();
        Criteria criteria3 = getCriteria().allOfGroups(ImmutableSet.of("group1")).build();
        appConfig3.setCriteria(criteria3);
        
        List<AppConfig> collection = ImmutableList.of(appConfig1, appConfig2, appConfig3);

        // User has languages, but criteria don't match against them. The results returned do not 
        // change their sort order.
        CriteriaContext context = new CriteriaContext.Builder().withAppId(TEST_APP_ID)
                .withLanguages(ImmutableList.of("en", "fr")).withUserDataGroups(USER_DATA_GROUPS).build();
        
        List<AppConfig> selected = CriteriaUtils.filterByCriteria(context, collection, null);
        assertEquals(selected.size(), 3);
        assertSame(selected.get(0), appConfig1);
        assertSame(selected.get(1), appConfig2);
        assertSame(selected.get(2), appConfig3);
    }
    
    @Test
    public void filterByCriteriaSortedWithAdditionalComparator() {
        AppConfig appConfig1 = AppConfig.create();
        Criteria criteria1 = getCriteria().lang("de").allOfGroups(ImmutableSet.of("group1")).build();
        appConfig1.setCriteria(criteria1);
        appConfig1.setCreatedOn(TIMESTAMP.minusHours(1).getMillis());
        
        AppConfig appConfig2 = AppConfig.create();
        Criteria criteria2 = getCriteria().lang("de").allOfGroups(ImmutableSet.of("group2")).build();
        appConfig2.setCriteria(criteria2);
        appConfig2.setCreatedOn(TIMESTAMP.minusHours(2).getMillis());

        AppConfig appConfig3 = AppConfig.create();
        Criteria criteria3 = getCriteria().lang("en").allOfGroups(ImmutableSet.of("group1")).build();
        appConfig3.setCriteria(criteria3);
        appConfig3.setCreatedOn(TIMESTAMP.minusHours(3).getMillis());
        
        List<AppConfig> collection = ImmutableList.of(appConfig1, appConfig2, appConfig3);
        
        CriteriaContext context = new CriteriaContext.Builder().withAppId(TEST_APP_ID)
                .withLanguages(ImmutableList.of("de", "en")).withUserDataGroups(USER_DATA_GROUPS).build();
        
        // All of these match, but they are returned in 
        List<AppConfig> selected = CriteriaUtils.filterByCriteria(context, collection, comparingLong(AppConfig::getCreatedOn));
        assertSame(selected.get(0), appConfig2);
        assertSame(selected.get(1), appConfig1);
        assertSame(selected.get(2), appConfig3);
    }

    private CriteriaContext.Builder getContext() {
        return new CriteriaContext.Builder().withAppId(TEST_APP_ID);
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
        CritBuilder allOfStudyIds(Set<String> studyIds) {
            criteria.setAllOfStudyIds(studyIds);
            return this;
        }
        CritBuilder noneOfStudyIds(Set<String> studyIds) {
            criteria.setNoneOfStudyIds(studyIds);
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
