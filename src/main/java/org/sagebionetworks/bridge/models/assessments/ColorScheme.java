package org.sagebionetworks.bridge.models.assessments;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Colors to differentiate this assessment in a UI. The colors include:
 * <ul>
 *   <li>background - background color</li>
 *   <li>foreground - text, icon, border colors</li>
 *   <li>activated - enabled, active, hover, focus, completed</li>
 *   <li>inactivated - disabled, inactive, incomplete</li>
 * </ul>
 */
public class ColorScheme {
    private final String background;
    private final String foreground;
    private final String activated;
    private final String inactivated;
    
    @JsonCreator
    public ColorScheme(@JsonProperty("background") String background, @JsonProperty("foreground") String foreground,
            @JsonProperty("activated") String activated, @JsonProperty("inactivated") String inactivated) {
        this.background = background;
        this.foreground = foreground;
        this.activated = activated;
        this.inactivated = inactivated;
    }
    public String getBackground() {
        return background;
    }
    public String getForeground() {
        return foreground;
    }
    public String getActivated() {
        return activated;
    }
    public String getInactivated() {
        return inactivated;
    }
    @Override
    public int hashCode() {
        return Objects.hash(activated, background, foreground, inactivated);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        ColorScheme other = (ColorScheme) obj;
        return Objects.equals(activated, other.activated) &&
                Objects.equals(background, other.background) &&
                Objects.equals(foreground, other.foreground) &&
                Objects.equals(inactivated, other.inactivated);
    }
}
