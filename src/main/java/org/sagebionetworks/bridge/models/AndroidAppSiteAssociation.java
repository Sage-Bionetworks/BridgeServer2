package org.sagebionetworks.bridge.models;

import org.sagebionetworks.bridge.models.studies.AndroidAppLink;

/**
 * A model of the JSON we produce to associate app links on Android with our server. The JSON 
 * that is returned is an array of these objects, one for every study that has defined app 
 * links.
 * 
 * @see https://developer.android.com/training/app-links/verify-site-associations.html
 */
public final class AndroidAppSiteAssociation {
    
    static final String RELATION = "delegate_permission/common.handle_all_urls";
    
    private final AndroidAppLink target;
    
    public AndroidAppSiteAssociation(AndroidAppLink target) {
        this.target = target;
    }
    public String[] getRelation() {
        return new String[] {RELATION};
    }
    public AndroidAppLink getTarget() {
        return target;
    }
}
