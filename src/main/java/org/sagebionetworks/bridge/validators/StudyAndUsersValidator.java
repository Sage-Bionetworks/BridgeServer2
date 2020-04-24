package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;

import java.util.Collections;
import java.util.List;

import com.google.common.collect.ImmutableSet;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.studies.App;
import org.sagebionetworks.bridge.models.studies.StudyAndUsers;
import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.client.exceptions.SynapseNotFoundException;

@Component
public class StudyAndUsersValidator implements Validator {

    private SynapseClient synapseClient;

    @Autowired
    public final void setSynapseClient(SynapseClient synapseClient) {
        this.synapseClient = synapseClient;
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return StudyAndUsers.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        StudyAndUsers studyAndUsers = (StudyAndUsers)object;
        
        List<String> adminIds = studyAndUsers.getAdminIds();
        if (adminIds == null || adminIds.isEmpty()) {
            errors.rejectValue("adminIds", "are required");
        } else {
            for (int i=0; i < adminIds.size(); i++) {
                String adminId = adminIds.get(i);
                errors.pushNestedPath("adminIds["+i+"]");
                if (isBlank(adminId)) {
                    errors.rejectValue("", "cannot be blank or null");
                }
                try {
                    synapseClient.getUserProfile(adminId);
                } catch (SynapseNotFoundException e) {
                    errors.rejectValue("", "is invalid");
                } catch (SynapseException se) {
                    throw new RuntimeException(se);
                }
                errors.popNestedPath();
            }
        }
        
        List<StudyParticipant> users = studyAndUsers.getUsers();
        if (users == null || users.isEmpty()) {
            errors.rejectValue("users", "are required");
        } else {
            for (int i=0; i < users.size(); i++) {
                StudyParticipant user = users.get(i);
                errors.pushNestedPath("users["+i+"]");
                if (isBlank(user.getSynapseUserId())) {
                    errors.rejectValue("synapseUserId", "cannot be blank");
                } else {
                    try {
                        synapseClient.getUserProfile(user.getSynapseUserId());
                    } catch (SynapseNotFoundException e) {
                        errors.rejectValue("synapseUserId", "is invalid");
                    } catch (SynapseException se) {
                        throw new RuntimeException(se);
                    }
                }
                
                // validate roles for each user
                if (user.getRoles() == null || user.getRoles().isEmpty()) {
                    errors.rejectValue("roles", "should have at least one role");
                } else if (!Collections.disjoint(user.getRoles(), ImmutableSet.of(ADMIN, WORKER, SUPERADMIN))) {
                    errors.rejectValue("roles", "can only have roles developer and/or researcher");
                }
                
                errors.popNestedPath();
            }
        }
        
        App app = studyAndUsers.getStudy();
        if (app == null) {
            errors.rejectValue("study", "cannot be null");
        } else {
            errors.pushNestedPath("study");
            try {
                BridgeUtils.toSynapseFriendlyName(app.getName());    
            } catch(NullPointerException | IllegalArgumentException e) {
                errors.rejectValue("name", "is an invalid Synapse project name");
            }
            if (StringUtils.isBlank(app.getSponsorName())) {
                errors.rejectValue("sponsorName", "is required");
            }
            if (StringUtils.isBlank(app.getIdentifier())) {
                errors.rejectValue("identifier", "is required");
            }
            errors.popNestedPath();
        }
    }
}
