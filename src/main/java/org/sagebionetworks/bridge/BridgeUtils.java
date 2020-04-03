package org.sagebionetworks.bridge;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Integer.parseInt;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.BridgeConstants.CALLER_NOT_MEMBER_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.CKEDITOR_WHITELIST;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.util.BridgeCollectors.toImmutableSet;
import static org.springframework.util.StringUtils.commaDelimitedListToSet;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Document.OutputSettings.Syntax;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities.EscapeMode;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Whitelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.Tuple;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;
import org.sagebionetworks.bridge.models.templates.TemplateType;

import org.springframework.core.annotation.AnnotationUtils;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.fasterxml.jackson.databind.JsonNode;
import com.amazonaws.util.Throwables;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonMappingException.Reference;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class BridgeUtils {
    private static final Logger LOG = LoggerFactory.getLogger(BridgeUtils.class);

    public static class SubstudyAssociations {
        private final Set<String> substudyIdsVisibleToCaller;
        private final Map<String, String> externalIdsVisibleToCaller;
        SubstudyAssociations(Set<String> substudyIdsVisibleToCaller, Map<String, String> externalIdsVisibleToCaller) {
            this.substudyIdsVisibleToCaller = substudyIdsVisibleToCaller;
            this.externalIdsVisibleToCaller = externalIdsVisibleToCaller;
        }
        public Set<String> getSubstudyIdsVisibleToCaller() {
            return substudyIdsVisibleToCaller;
        }
        public Map<String, String> getExternalIdsVisibleToCaller() {
            return externalIdsVisibleToCaller;
        }
    }

    public static final Joiner AND_JOINER = Joiner.on(" AND ");
    public static final Joiner COMMA_SPACE_JOINER = Joiner.on(", ");
    public static final Joiner COMMA_JOINER = Joiner.on(",");
    public static final Joiner SEMICOLON_SPACE_JOINER = Joiner.on("; ");
    public static final Joiner SPACE_JOINER = Joiner.on(" ");
    private static final int ONE_HOUR = 60*60;
    private static final int ONE_DAY = 60*60*24;
    private static final int ONE_MINUTE = 60;
    
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final SubstudyAssociations NO_ASSOCIATIONS = new SubstudyAssociations(ImmutableSet.of(),
            ImmutableMap.of());

    // ThreadLocals are weird. They are basically a container that allows us to hold "global variables" for each
    // thread. This can be used, for example, to provide the request ID to any class without having to plumb a
    // "request context" object into every method of every class.
    private static final ThreadLocal<RequestContext> REQUEST_CONTEXT_THREAD_LOCAL = ThreadLocal.withInitial(() -> null);

    public static Map<String,String> mapSubstudyMemberships(Account account) {
        ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<>();
        for (AccountSubstudy acctSubstudy : account.getAccountSubstudies()) {
            String value = (acctSubstudy.getExternalId() == null) ? 
                    "<none>" : acctSubstudy.getExternalId();
            builder.put(acctSubstudy.getSubstudyId(), value);
        }
        return builder.build();
    }
    
    public static Tuple<String> parseAutoEventValue(String automaticEventValue) {
        int lastIndex = automaticEventValue.lastIndexOf(":P");
        if (lastIndex == -1) {
            // This will certainly not pass validation
            return new Tuple<>(null, automaticEventValue); 
        }
        return new Tuple<>(automaticEventValue.substring(0, lastIndex), automaticEventValue.substring(lastIndex+1));
    }
    
    public static boolean isExternalIdAccount(StudyParticipant participant) {
        // We do not look at externalIds because it is a read-only reflection of the substudies
        // a user is associated to. It cannot be submitted by the caller.
        return (StringUtils.isNotBlank(participant.getExternalId()) && 
                StringUtils.isBlank(participant.getEmail()) && 
                participant.getPhone() == null);
    }

    public static Set<String> collectExternalIds(Account account) {
        return account.getAccountSubstudies().stream()
                .map(AccountSubstudy::getExternalId)
                .filter(Objects::nonNull)
                .collect(toImmutableSet());
    }
    
    public static Set<String> collectSubstudyIds(Account account) {
        return account.getAccountSubstudies().stream()
                .map(AccountSubstudy::getSubstudyId)
                .collect(toImmutableSet());
    }
    
    /** Gets the request context for the current thread. See also RequestInterceptor. */
    public static RequestContext getRequestContext() {
        RequestContext context = REQUEST_CONTEXT_THREAD_LOCAL.get();
        if (context == null) {
            return RequestContext.NULL_INSTANCE; 
        }
        return context;
    }

    /** @see #getRequestContext */
    public static void setRequestContext(RequestContext context) {
        REQUEST_CONTEXT_THREAD_LOCAL.set(context);
    }
    
    public static Account filterForSubstudy(Account account) {
        if (account != null) {
            RequestContext context = getRequestContext();
            Set<String> callerSubstudies = context.getCallerSubstudies();
            if (BridgeUtils.isEmpty(callerSubstudies)) {
                return account;
            }
            Set<AccountSubstudy> matched = account.getAccountSubstudies().stream()
                    .filter(as -> callerSubstudies.isEmpty() || callerSubstudies.contains(as.getSubstudyId()))
                    .collect(toSet());
            
            if (!matched.isEmpty()) {
                // Hibernate managed objects use a collection implementation that tracks changes,
                // and shouldn't be set with a Java library collection. Here it is okay because 
                // we're filtering an object to return through the API, and it won't be persisted.
                account.setAccountSubstudies(matched);
                return account;
            }
        }
        return null;
    }
    
    /**
     * Callers only see the accountSubstudy records they themselves are assigned to, unless they have no
     * substudy memberships (then they are global and see everything).
     */
    public static SubstudyAssociations substudyAssociationsVisibleToCaller(Collection<? extends AccountSubstudy> accountSubstudies) {
        if (accountSubstudies == null || accountSubstudies.isEmpty()) {
            return NO_ASSOCIATIONS;
        }
        ImmutableSet.Builder<String> substudyIds = new ImmutableSet.Builder<>();
        ImmutableMap.Builder<String,String> externalIds = new ImmutableMap.Builder<>();
        Set<String> callerSubstudies = getRequestContext().getCallerSubstudies();
        for (AccountSubstudy acctSubstudy : accountSubstudies) {
            if (callerSubstudies.isEmpty() || callerSubstudies.contains(acctSubstudy.getSubstudyId())) {
                substudyIds.add(acctSubstudy.getSubstudyId());
                if (acctSubstudy.getExternalId() != null) {
                    externalIds.put(acctSubstudy.getSubstudyId(), acctSubstudy.getExternalId());
                }
            }
        }
        return new SubstudyAssociations(substudyIds.build(), externalIds.build()); 
    }
    
    public static ExternalIdentifier filterForSubstudy(ExternalIdentifier externalId) {
        if (externalId != null) {
            RequestContext context = getRequestContext();
            Set<String> callerSubstudies = context.getCallerSubstudies();
            if (BridgeUtils.isEmpty(callerSubstudies)) {
                return externalId;
            }
            if (callerSubstudies.contains(externalId.getSubstudyId())) {
                return externalId;
            }
        }
        return null;
    }
    
    /**
     * Convert expiration measures in seconds to an English language explanation of
     * the expiration time. This is not intended to cover odd cases--our expirations 
     * are in minutes, hours, or possibly days. 
     */
    public static String secondsToPeriodString(int seconds) {
        if (seconds >= (ONE_DAY*2) && seconds % ONE_DAY == 0) {
            return Integer.toString(seconds/ONE_DAY) + " days";
        } else if (seconds >= ONE_DAY && seconds % ONE_DAY == 0) {
            return Integer.toString(seconds/ONE_DAY) + " day";
        } else if (seconds >= (ONE_HOUR*2) && seconds % ONE_HOUR == 0) {
            return Integer.toString(seconds/ONE_HOUR) + " hours";
        } else if (seconds >= ONE_HOUR && seconds % ONE_HOUR == 0) {
            return Integer.toString(seconds/ONE_HOUR) + " hour";
        } else if (seconds >= (ONE_MINUTE*2) && seconds % ONE_MINUTE == 0) {
            return Integer.toString(seconds/ONE_MINUTE) + " minutes";
        } else if (seconds >= ONE_MINUTE && seconds % ONE_MINUTE == 0) {
            return Integer.toString(seconds/ONE_MINUTE) + " minute";
        }
        return Integer.toString(seconds) + " seconds";
    }
    
    public static AccountId parseAccountId(String studyId, String identifier) {
        checkNotNull(studyId);
        checkNotNull(identifier);
        
        String id = identifier.toLowerCase();
        if (id.startsWith("externalid:")) {
            return AccountId.forExternalId(studyId, identifier.substring(11));
        } else if (id.startsWith("healthcode:")) {
            return AccountId.forHealthCode(studyId, identifier.substring(11));
        } else if (id.startsWith("synapseuserid:")) {
            return AccountId.forSynapseUserId(studyId, identifier.substring(14));
        } else if (id.startsWith("syn:")) {
            return AccountId.forSynapseUserId(studyId, identifier.substring(4));
        }
        return AccountId.forId(studyId, identifier);
    }
    
    /**
     * Create a variable map for the <code>resolveTemplate</code> method that includes common values from 
     * a study that used in most of our templates. The map is mutable. Variables include:
     * <ul>
     *  <li>studyName = study.getName()</li>
     *  <li>studyShortName = study.getShortName()</li>
     *  <li>studyId = study.getIdentifier()</li>
     *  <li>sponsorName = study.getSponsorName()</li>
     *  <li>supportEmail = study.getSupportEmail()</li>
     *  <li>technicalEmail = study.getTechnicalEmail()</li>
     *  <li>consentEmail = study.getConsentNotificationEmail()</li>
     * </ul>
     */
    public static Map<String,String> studyTemplateVariables(Study study, Function<String,String> escaper) {
        Map<String,String> map = Maps.newHashMap();
        map.put("studyName", study.getName());
        map.put("studyShortName", study.getShortName());
        map.put("studyId", study.getIdentifier());
        map.put("sponsorName", study.getSponsorName());
        map.put("supportEmail", 
                Iterables.getFirst(commaListToOrderedSet(study.getSupportEmail()), ""));
        map.put("technicalEmail", 
                Iterables.getFirst(commaListToOrderedSet(study.getTechnicalEmail()), ""));
        if (study.getConsentNotificationEmail() != null) {
            map.put("consentEmail", 
                    Iterables.getFirst(commaListToOrderedSet(study.getConsentNotificationEmail()), ""));
        }
        map.put("host", BridgeConfigFactory.getConfig().getHostnameWithPostfix("ws"));
        if (escaper != null) {
            for (Map.Entry<String,String> entry : map.entrySet()) {
                map.put(entry.getKey(), escaper.apply(entry.getValue()));
            }
        }
        return map;
    }
    
    public static Map<String,String> studyTemplateVariables(Study study) {
        return studyTemplateVariables(study, null);
    }
    
    /**
     * A simple means of providing template variables in template strings, in the format <code>${variableName}</code>.
     * This value will be replaced with the value of the variable name. The variable name/value pairs are passed to the
     * method as a map. Variables that are not found in the map will be left in the string as is.
     *
     * @see https://sagebionetworks.jira.com/wiki/display/BRIDGE/EmailTemplate
     * 
     * @param template
     * @param values
     * @return
     */
    public static String resolveTemplate(String template, Map<String,String> values) {
        checkNotNull(template);
        checkNotNull(values);
        
        for (Map.Entry<String,String> entry : values.entrySet()) {
            if (entry.getValue() != null) {
                String var = "${"+entry.getKey()+"}";
                template = template.replace(var, entry.getValue());
            }
        }
        return template;
    }
    
    public static String generateGuid() {
        // Increases size from 16 to 18 bytes over UUID.randomUUID() while 
        // still being shorter than the prior implementation. 
        byte[] buffer = new byte[18];
        SECURE_RANDOM.nextBytes(buffer);
            return ENCODER.encodeToString(buffer);
    }
    
    /** Generate a random 16-byte salt, using a {@link SecureRandom}. */
    public static byte[] generateSalt() {
        byte[] salt = new byte[16];
        SECURE_RANDOM.nextBytes(salt);
        return salt;
    }

    /**
     * Searches for a @BridgeTypeName annotation on this or any parent class in the class hierarchy, returning 
     * that value as the type name. If none exists, defaults to the simple class name. 
     * @param clazz
     * @return
     */
    public static String getTypeName(Class<?> clazz) {
        BridgeTypeName att = AnnotationUtils.findAnnotation(clazz,BridgeTypeName.class);
        if (att != null) {
            return att.value();
        }
        return clazz.getSimpleName();
    }
    
    /**
     * All batch methods in Dynamo return a list of failures rather than 
     * throwing an exception. We should have an exception specifically for 
     * these so the caller gets a list of items back, but for now, convert 
     * to a generic exception;
     * @param failures
     */
    public static void ifFailuresThrowException(List<FailedBatch> failures) {
        if (!failures.isEmpty()) {
            List<String> messages = Lists.newArrayList();
            for (FailedBatch failure : failures) {
                String message = failure.getException().getMessage();
                messages.add(message);
                String ids = Joiner.on("; ").join(failure.getUnprocessedItems().keySet());
                messages.add(ids);
            }
            throw new BridgeServiceException(Joiner.on(", ").join(messages));
        }
    }
    
    public static boolean isEmpty(Collection<?> coll) {
        return (coll == null || coll.isEmpty());
    }
    
    public static <S,T> Map<S,T> asMap(List<T> list, Function<T,S> function) {
        Map<S,T> map = Maps.newHashMap();
        if (list != null && function != null) {
            for (T item : list) {
                map.put(function.apply(item), item);
            }
        }
        return map;
    }
    
    public static Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch(NumberFormatException e) {
            throw new RuntimeException("'" + value + "' is not a valid integer");
        }
    }

    public static Set<String> commaListToOrderedSet(String commaList) {
        if (commaList != null) {
            // This implementation must return a LinkedHashSet. This is a set
            // with ordered keys, in the order they were in the string, as some
            // set serializations depend on the order of the keys (languages).
            return commaDelimitedListToSet(commaList).stream()
                    .map(string -> string.trim())
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        // Cannot make this immutable without losing the concrete type we rely 
        // upon to ensure they keys are in the order they are inserted.
        return new LinkedHashSet<String>();
    }
    
    public static String setToCommaList(Set<String> set) {
        if (set != null) {
            // User LinkedHashSet because some supplied sets will have ordered keys 
            // and we want to preserve that order while processing the set. 
            Set<String> result = set.stream()
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return (result.isEmpty()) ? null : COMMA_JOINER.join(result);
        }
        return null;
    }
    
    /**
     * Wraps a set in an immutable set, or returns an empty immutable set if null.
     * @param set
     * @return
     */
    public @Nonnull static <T> ImmutableSet<T> nullSafeImmutableSet(Set<T> set) {
        return (set == null) ? ImmutableSet.of() : ImmutableSet.copyOf(set.stream()
                .filter(element -> element != null).collect(Collectors.toSet()));
    }
    
    public @Nonnull static <T> ImmutableList<T> nullSafeImmutableList(List<T> list) {
        return (list == null) ? ImmutableList.of() : ImmutableList.copyOf(list.stream()
                .filter(element -> element != null).collect(Collectors.toList()));
    }
    
    public @Nonnull static <S,T> ImmutableMap<S,T> nullSafeImmutableMap(Map<S,T> map) {
        ImmutableMap.Builder<S, T> builder = new ImmutableMap.Builder<>();
        if (map != null) {
            for (Map.Entry<S, T> entry : map.entrySet()) {
                if (entry.getValue() != null) {
                    builder.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return builder.build();
    }
    
    /**
     * Converts a string to an error key friendly string, e.g. "iPhone OS" is converted to "iphone_os".
     * 
     * @throws IllegalArgumentException
     *             if the string cannot be converted to an error key.
     */
    public static String textToErrorKey(String text) {
        if (StringUtils.isBlank(text)) {
            throw new IllegalArgumentException("String is not translatable to an error key: " + text);
        }
        return text.toLowerCase().replaceAll(" ", "_").replaceAll("[^a-zA-Z0-9_-]", "");    
    }
    
    /**
     * Parse the string as an integer value, or return the defaultValue if it is null. 
     * If the value is provided but not a parseable integer, thrown a BadRequestException.
     */
    public static int getIntOrDefault(String value, int defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }
        try {
            return parseInt(value);
        } catch(NumberFormatException e) {
            throw new BadRequestException(value + " is not an integer");
        }
    }

    /**
     * Parse the string as a long value, or return the defaultValue if it is null. 
     * If the value is provided but not a parseable long, thrown a BadRequestException.
     */
    public static Long getLongOrDefault(String value, Long defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }
        try {
            return parseLong(value);
        } catch(RuntimeException e) {
            throw new BadRequestException(value + " is not a long");
        }
    }
    
    /**
     * Parse the string as a DateTime value, or return the defaultValue if it is null. 
     * If the value is provided but not a parseable DateTime, thrown a BadRequestException.
     */
    public static DateTime getDateTimeOrDefault(String value, DateTime defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }
        try {
            return DateTime.parse(value);
        } catch(Exception e) {
            throw new BadRequestException(value + " is not a DateTime value");
        }
    }
    
    public static LocalDate getLocalDateOrDefault(String value, LocalDate defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        } else {
            try {
                return DateUtils.parseCalendarDate(value);
            } catch (RuntimeException ex) {
                throw new BadRequestException(value + " is not a LocalDate value");
            }
        }
    }
    
    /**
     * Creates a new copy of the map, removing any entries that have a null value (particularly easy to do this in
     * JSON).
     */
    public static <K,V> Map<K,V> withoutNullEntries(Map<K,V> map) {
        checkNotNull(map);
        return map.entrySet().stream().filter(e -> e.getValue() != null).collect(Collectors.toMap(Map.Entry::getKey,
                Map.Entry::getValue));
    }

    /** Helper method which puts something to a map, or removes it from the map if the value is null. */
    public static <K,V> void putOrRemove(Map<K,V> map, K key, V value) {
        checkNotNull(map);
        checkNotNull(key);
        if (value != null) {
            map.put(key, value);
        } else {
            map.remove(key);
        }
    }
    
    public static String encodeURIComponent(String component) {
        String encoded = null;
        if (component != null) {
            try {
                encoded = URLEncoder.encode(component, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                // UTF-8 is always supported, so this should never happen. 
                throw new BridgeServiceException(e.getMessage());
            }
        }
        return encoded;
    }
    
    public static String passwordPolicyDescription(PasswordPolicy policy) {
        StringBuilder sb = new StringBuilder();
        sb.append("Password must be ").append(policy.getMinLength()).append(" or more characters");
        if (policy.isLowerCaseRequired() || policy.isNumericRequired() || policy.isSymbolRequired() || policy.isUpperCaseRequired()) {
            sb.append(", and must contain at least ");
            List<String> phrases = new ArrayList<>();
            if (policy.isLowerCaseRequired()) {
                phrases.add("one lower-case letter");
            }
            if (policy.isUpperCaseRequired()) {
                phrases.add("one upper-case letter");
            }
            if (policy.isNumericRequired()) {
                phrases.add("one number");
            }
            if (policy.isSymbolRequired()) {
                // !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~
                phrases.add("one symbolic character (non-alphanumerics like #$%&@)");
            }
            for (int i=0; i < phrases.size(); i++) {
                if (i == phrases.size()-1) {
                    sb.append(", and ");
                } else if (i > 0) {
                    sb.append(", ");
                }
                sb.append(phrases.get(i));
            }
        }
        sb.append(".");
        return sb.toString();
    }
    
    public static String extractPasswordFromURI(URI uri) {
        boolean hasPassword = (uri.getUserInfo() != null && uri.getUserInfo().contains(":"));
        
        return (hasPassword) ? uri.getUserInfo().split(":")[1] : null;
    }
    
    public static String createReferentGuidIndex(ActivityType type, String guid, String localDateTime) {
        checkNotNull(type);
        checkNotNull(guid);
        checkNotNull(localDateTime);
        return String.format("%s:%s:%s", guid , type.name().toLowerCase(), localDateTime);
    }
    
    public static String createReferentGuidIndex(Activity activity, LocalDateTime localDateTime) {
        checkNotNull(activity);
        checkNotNull(localDateTime);
        
        ActivityType type = activity.getActivityType();
        String timestamp = localDateTime.toString();
        
        switch(type) {
        case COMPOUND:
            return createReferentGuidIndex(type, activity.getCompoundActivity().getTaskIdentifier(), timestamp);
        case SURVEY:
            return createReferentGuidIndex(type, activity.getSurvey().getGuid(), timestamp);
        case TASK:
            return createReferentGuidIndex(type, activity.getTask().getIdentifier(), timestamp);
        }
        throw new BridgeServiceException("Invalid activityType specified");    
    }
    
    public static String toSynapseFriendlyName(String input) {
        checkNotNull(input);
        
        String value = input.replaceAll("[^a-zA-Z0-9\\.\\-_\\s]", " ").replaceAll("\\s+", " ").trim();
        checkArgument(StringUtils.isNotBlank(value));
        
        return value; 
    }
    
    public static String templateTypeToLabel(TemplateType type) {
        List<String> words = Arrays.asList(type.name().toLowerCase().split("_"));
        List<String> capitalized = words.stream().map(StringUtils::capitalize).collect(toList());
        if (capitalized.get(0).equals("Sms")) {
            capitalized.remove(0);
            capitalized.add("Default (SMS)");
        }
        if (capitalized.get(0).equals("Email")) {
            capitalized.remove(0);
            capitalized.add("Default (Email)");
        }
        return Joiner.on(" ").join(capitalized);
    } 
    
    public static String sanitizeHTML(String documentContent) {
        return sanitizeHTML(CKEDITOR_WHITELIST, documentContent);
    }
    
    public static String sanitizeHTML(Whitelist whitelist, String documentContent) {
        checkNotNull(whitelist);
        
        if (isBlank(documentContent)) {
            return documentContent;
        }
        // the prior version of this still pretty printed the output... this uglier use of JSoup's
        // APIs does not pretty print the output.
        Document dirty = Jsoup.parseBodyFragment(documentContent);
        Cleaner cleaner = new Cleaner(whitelist);
        Document clean = cleaner.clean(dirty);
        // All variants of the sanitizer remove this, so put it back. It's used in the consent document.
        // "brimg" is not a valid attribute, it marks our one template image.
        for (Element el : clean.select("img[brimg]")) {
            el.attr("src", "cid:consentSignature");
        }
        clean.outputSettings().escapeMode(EscapeMode.xhtml)
            .syntax(Syntax.xml).indentAmount(0).prettyPrint(false).charset("UTF-8");
        return clean.body().html();
    }
    
    public static boolean isInRole(Set<Roles> callerRoles, Roles requiredRole) {
        return (callerRoles != null && requiredRole != null && 
                (callerRoles.contains(SUPERADMIN) || callerRoles.contains(requiredRole)));
    }
    
    public static boolean isInRole(Set<Roles> callerRoles, Set<Roles> requiredRoles) {
        return callerRoles != null && requiredRoles != null && 
                requiredRoles.stream().anyMatch(role -> isInRole(callerRoles, role));
    }
    
    public static <T extends Enum<T>> T getEnumOrDefault(String value, Class<T> enumType, T defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(enumType, value.toUpperCase());
        } catch(IllegalArgumentException e) {
            Object[] enums = enumType.getEnumConstants();
            throw new BadRequestException(value + " is not a valid " + enumType.getSimpleName() + " (use: "
                    + COMMA_SPACE_JOINER.join(enums).toLowerCase() + ")");
        }
    }
    
    /**
     * If the caller has no organizational membership, then they can set any organization (however they 
     * must set one, unlike the implementation of substudy relationships to user accounts). In this 
     * case we check the org ID to ensure it's valid. If the caller has organizational memberships, 
     * then the caller must be a member of the organization being cited. At that point we do not need 
     * to validate the org ID since it was validated when it was set as an organizational relationship
     * on the account. 
     */
    public static void checkOwnership(String appId, String ownerId) {
        if (isBlank(ownerId)) {
            throw new UnauthorizedException(CALLER_NOT_MEMBER_ERROR);
        }
        Set<String> callerSubstudies = BridgeUtils.getRequestContext().getCallerSubstudies();
        if (callerSubstudies.isEmpty() || callerSubstudies.contains(ownerId)) {
            return;
        }
        throw new UnauthorizedException(CALLER_NOT_MEMBER_ERROR);
    }
    
    public static void checkSharedOwnership(String callerAppId, String guid, String ownerId) {
        if (ownerId == null) {
            LOG.error("Owner ID is null, guid=" + guid);
            throw new UnauthorizedException(CALLER_NOT_MEMBER_ERROR);
        }
        String[] parts = ownerId.split(":", 2);
        // This happens in tests, we expect it to never happens in production. So log if it does.
        if (parts.length != 2) {
            LOG.error("Could not parse shared assessment ownerID, guid=" + guid + ", ownerId=" + ownerId);
            throw new UnauthorizedException(CALLER_NOT_MEMBER_ERROR);
        }
        String originAppId = parts[0];
        String originOrgId = parts[1];
        Set<String> callerSubstudies = BridgeUtils.getRequestContext().getCallerSubstudies();
        boolean scopedUser = !callerSubstudies.isEmpty();
        boolean orgMember = callerSubstudies.contains(originOrgId);
        
        if (!callerAppId.equals(originAppId) || (scopedUser && !orgMember)) {
            throw new UnauthorizedException(CALLER_NOT_MEMBER_ERROR);
        }
    }
    
    public static Integer getIntegerOrDefault(String value, Integer defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }
        try {
            return parseInt(value);
        } catch(NumberFormatException e) {
            throw new BadRequestException(value + " is not an integer");
        }
    }
    
    /**
     * Walk a JSON node tree and call a BiConsumer with each object node or array 
     * element in the tree. This implements the visitor pattern over a JsonNode 
     * tree. The consumer is passed the path of the current node, and the node itself. 
     * The path can be used for error reporting, logging, etc.
     */
    public static void walk(JsonNode node, BiConsumer<String, JsonNode> consumer) {
        walk(node, "", consumer);
    }
    
    private static void walk(JsonNode node, String fieldPath, BiConsumer<String, JsonNode> consumer) {
        if (node.isObject()) {
            consumer.accept(fieldPath, node);
            for (Iterator<Map.Entry<String, JsonNode>> i = node.fields(); i.hasNext(); ) {
                Map.Entry<String, JsonNode> entry = i.next();
                walk(entry.getValue(), appendPath(fieldPath, entry.getKey()), consumer);
            }
        } else if (node.isArray()) {
            int i = 0;
            for (Iterator<JsonNode> iter = node.elements(); iter.hasNext(); ) {
                JsonNode element = iter.next();
                walk(element, fieldPath + "["+i+"]", consumer);
                i++;
            }
        }
    }
    
    private static String appendPath(String existingPath, String newElement) {
        return (existingPath.length() == 0) ? newElement : (existingPath + "." + newElement);
    }
    
    public static InvalidEntityException convertParsingError(Throwable throwable) {
        if (Throwables.getRootCause(throwable) instanceof InvalidEntityException) {
            return (InvalidEntityException)Throwables.getRootCause(throwable);
        } else if (throwable instanceof JsonMappingException) {
            JsonMappingException jme = (JsonMappingException)throwable;
            List<String> fields = jme.getPath().stream().map(Reference::getFieldName).collect(toList());
            String msg = "Error parsing JSON in request body, fields: " + COMMA_SPACE_JOINER.skipNulls().join(fields);
            return new InvalidEntityException(msg);
        }
        return new InvalidEntityException("Error parsing JSON in request body: " + throwable.getMessage());
    }
}