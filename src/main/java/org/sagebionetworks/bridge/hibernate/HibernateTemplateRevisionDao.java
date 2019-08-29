package org.sagebionetworks.bridge.hibernate;

import static com.amazonaws.services.s3.model.ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.Charset.defaultCharset;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;
import static org.sagebionetworks.bridge.models.ResourceList.TOTAL;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import javax.annotation.Resource;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.google.common.collect.ImmutableMap;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.TemplateRevisionDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.templates.TemplateRevision;
import org.sagebionetworks.bridge.models.templates.TemplateRevisionId;
import org.sagebionetworks.bridge.s3.S3Helper;

@Component
public class HibernateTemplateRevisionDao implements TemplateRevisionDao {
    static final String TEMPLATE_GUID_PARAM_NAME = "templateGuid";
    static final String SELECT_TEMPLATE = "SELECT revision ";
    static final String SELECT_COUNT = "SELECT count(*) ";
    static final String GET_ALL = "FROM HibernateTemplateRevision AS revision " + 
            "WHERE templateGuid = :templateGuid ORDER BY createdOn DESC";

    private S3Helper s3Helper;
    private String publicationsBucket;
    private HibernateHelper hibernateHelper;
    
    @Autowired
    final void setS3Helper(S3Helper s3Helper) {
        this.s3Helper = s3Helper;
    }
    @Autowired
    final void setBridgeConfig(BridgeConfig bridgeConfig) {
        this.publicationsBucket = bridgeConfig.getHostnameWithPostfix("docs");
    }
    @Resource(name = "basicHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }
    
    @Override
    public PagedResourceList<? extends TemplateRevision> getTemplateRevisions(String templateGuid, Integer offset, Integer pageSize) {
        checkNotNull(templateGuid);
        
        ImmutableMap<String,Object> params = ImmutableMap.of(TEMPLATE_GUID_PARAM_NAME, templateGuid);
        
        int total = hibernateHelper.queryCount(SELECT_COUNT + GET_ALL, params);
        
        List<? extends HibernateTemplateRevision> results = hibernateHelper.queryGet(
                SELECT_TEMPLATE + GET_ALL, params, offset, pageSize, HibernateTemplateRevision.class);
        
        return new PagedResourceList<>(results, total)
                .withRequestParam(PAGE_SIZE, pageSize)
                .withRequestParam(OFFSET_BY, offset)
                .withRequestParam(TOTAL, total);
    }

    @Override
    public Optional<TemplateRevision> getTemplateRevision(String templateGuid, DateTime createdOn) {
        checkNotNull(templateGuid);
        checkNotNull(createdOn);
        
        TemplateRevisionId revisionId = new TemplateRevisionId(templateGuid, createdOn);
        HibernateTemplateRevision revision = hibernateHelper.getById(HibernateTemplateRevision.class, revisionId);
        if (revision == null) {
            return Optional.empty();
        }
        // Load document content
        try {
            String storagePath = getStoragePath(templateGuid, createdOn);
            String documentContent = s3Helper.readS3FileAsString(publicationsBucket, storagePath);
            revision.setDocumentContent(documentContent);
        } catch(IOException ioe) {
            throw new BridgeServiceException("Error loading template revision document", ioe);
        }
        return Optional.of(revision);
    }

    @Override
    public void createTemplateRevision(TemplateRevision revision) {
        checkNotNull(revision);
        
        try {
            String storagePath = getStoragePath(revision.getTemplateGuid(), revision.getCreatedOn());
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setSSEAlgorithm(AES_256_SERVER_SIDE_ENCRYPTION);
            s3Helper.writeBytesToS3(publicationsBucket, storagePath, revision.getDocumentContent().getBytes(defaultCharset()), metadata);
        } catch(IOException ioe) {
            throw new BridgeServiceException("Error persisting template revision document", ioe);
        }
        hibernateHelper.create(revision, null);
    }

    private String getStoragePath(String templateGuid, DateTime createdOn) {
        return templateGuid + "." + createdOn.getMillis();   
    }
}
