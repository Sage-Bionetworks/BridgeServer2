package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

public interface SubpopulationDao {

    /**
     * Create subpopulation. 
     */
    Subpopulation createSubpopulation(Subpopulation subpop);
    
    /**
     * If no subpoulation exists, a default subpopulation can be created. This will be called
     * as part of creating an app, or when an existing app is found to have no subpopulations.
     * Subpopulations in turn create a default consent document. 
     */
    Subpopulation createDefaultSubpopulation(String appId, String studyId);
    
    /**
     * Get all subpopulations defined for this app. It is possible to create a first default
     * subpopulation if none exists
     *
     * @param includeDeleted
     *      if true, return logically deleted subpopulations. If false, do not return them.
     */
    List<Subpopulation> getSubpopulations(String appId, boolean includeDeleted);
    
    /**
     * Get a specific subpopulation. This always returns the subpopulation whether it is logically deleted or not. 
     * @return subpopulation
     */
    Subpopulation getSubpopulation(String appId, SubpopulationGuid subpopGuid);
    
    /**
     * Update a subpopulation.
     */
    Subpopulation updateSubpopulation(Subpopulation subpop);

    /**
     * Logically delete a subpopulation. You cannot logically delete the default subpopulation for an app. 
     */
    void deleteSubpopulation(String appId, SubpopulationGuid subpopGuid);
    
    /**
     * Delete a subpopulation permanently. 
     */
    void deleteSubpopulationPermanently(String appId, SubpopulationGuid subpopGuid);
    
}
