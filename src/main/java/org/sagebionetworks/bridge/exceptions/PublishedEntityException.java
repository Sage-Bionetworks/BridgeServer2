package org.sagebionetworks.bridge.exceptions;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.BridgeEntity;

@SuppressWarnings("serial")
@NoStackTraceException
public class PublishedEntityException extends BridgeServiceException {
    
    public PublishedEntityException(BridgeEntity entity) {
        super("A " + BridgeUtils.getTypeName(entity.getClass()).toLowerCase() + 
                " cannot be updated after publication.", 400);
    }
}
