package org.sagebionetworks.bridge.models.schedules2.timelines;

import static com.google.common.base.Charsets.UTF_8;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_APP_ID;
import static org.sagebionetworks.bridge.models.appconfig.ConfigResolver.INSTANCE;

import java.util.List;
import java.util.Objects;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.models.Label;
import org.sagebionetworks.bridge.models.assessments.ColorScheme;
import org.sagebionetworks.bridge.models.assessments.ImageResource;
import org.sagebionetworks.bridge.models.schedules2.AssessmentReference;

public final class AssessmentInfo {
    
    private static final HashFunction HASHER = Hashing.concatenating(
            Hashing.murmur3_32(), Hashing.murmur3_32());
    
    private final String key;
    private final String guid;
    private final String appId;
    private final String identifier;
    private final Integer revision;
    private final String label;
    private final Integer minutesToComplete;
    private final ColorScheme colorScheme;
    private final ImageResource imageResource;

    public static AssessmentInfo create(AssessmentReference ref) {
        List<String> languages = RequestContext.get().getCallerLanguages();
        
        Label label = BridgeUtils.selectByLang(ref.getLabels(), 
                languages, new Label("", ref.getTitle()));
        
        // This generates a hash that designed to avoid collisions, unlike e.g. 
        // hashCode() which doesn’t (not its purpose).
        Hasher hc = HASHER.newHasher();
        hashValue(hc, ref.getGuid());
        hashValue(hc, ref.getAppId());
        hashValue(hc, ref.getIdentifier());
        hashValue(hc, label.getValue());
        hashValue(hc, ref.getRevision());
        hashValue(hc, ref.getMinutesToComplete());
        if (ref.getColorScheme() != null) {
            ColorScheme cs = ref.getColorScheme();
            hashValue(hc, cs.getBackground());
            hashValue(hc, cs.getForeground());
            hashValue(hc, cs.getActivated());
            hashValue(hc, cs.getInactivated());
        }
        if (ref.getImageResource() != null) {
            ImageResource imageResource = ref.getImageResource();
            hashValue(hc, imageResource.getName());
            hashValue(hc, imageResource.getModule());
            if (imageResource.getLabel() != null) {
                hashValue(hc, imageResource.getLabel().getLang());
                hashValue(hc, imageResource.getLabel().getValue());
            }
        }
        String hash = hc.hash().toString();
        return new AssessmentInfo(hash, ref.getGuid(), ref.getAppId(), ref.getIdentifier(),
                ref.getRevision(), label.getValue(), ref.getMinutesToComplete(), ref.getColorScheme(),
                ref.getImageResource());
    }
    private static void hashValue(Hasher hc, String value) {
        if (value != null) {
            hc.putString(value, UTF_8);
        }
    }
    private static void hashValue(Hasher hc, Integer value) {
        if (value != null) {
            hc.putInt(value);
        }
    }
    
    private AssessmentInfo(String key, String guid, String appId, String identifier,
            Integer revision, String label, Integer minutesToComplete, ColorScheme colorScheme,
            ImageResource imageResource) {
        this.key = key;
        this.guid = guid;
        this.appId = appId;
        this.identifier = identifier;
        this.revision = revision;
        this.label = label;
        this.colorScheme = colorScheme;
        this.minutesToComplete = minutesToComplete;
        this.imageResource = imageResource;
    }
    
    public String getKey() {
        return key;
    }
    
    public String getGuid() {
        return guid;
    }

    public String getAppId() {
        return appId;
    }

    public String getIdentifier() {
        return identifier;
    }
    
    public Integer getRevision() {
        return revision;
    }

    public String getLabel() {
        return label;
    }

    public Integer getMinutesToComplete() {
        return minutesToComplete;
    }

    public ColorScheme getColorScheme() {
        return colorScheme;
    }
    
    public String getConfigUrl() {
        if (guid == null) {
            return null;
        }
        String path = SHARED_APP_ID.equals(appId) ? "/v1/sharedassessments/" : "/v1/assessments/";
        return INSTANCE.url("ws", path + guid + "/config");
    }

    public ImageResource getImageResource() {
        return imageResource;
    }

    @Override
    public int hashCode() {
        return Objects.hash(appId, colorScheme, guid, identifier, revision, label, minutesToComplete, imageResource);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        AssessmentInfo other = (AssessmentInfo) obj;
        return Objects.equals(appId, other.appId) &&
                Objects.equals(colorScheme, other.colorScheme) &&
                Objects.equals(guid, other.guid) &&
                Objects.equals(identifier, other.identifier) &&
                Objects.equals(revision, other.revision) &&
                Objects.equals(label, other.label) &&
                Objects.equals(minutesToComplete, other.minutesToComplete) &&
                Objects.equals(imageResource, other.imageResource);
    }
}
