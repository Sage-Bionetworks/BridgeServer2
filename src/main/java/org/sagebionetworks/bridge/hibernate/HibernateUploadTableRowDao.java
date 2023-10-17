package org.sagebionetworks.bridge.hibernate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.dao.UploadTableRowDao;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.upload.UploadTableRow;
import org.sagebionetworks.bridge.upload.UploadTableRowQuery;

/** Hibernate implementation of UploadTableRowDao. */
@Component
public class HibernateUploadTableRowDao implements UploadTableRowDao {
    private HibernateHelper hibernateHelper;

    @Resource(name = "basicHibernateHelper")
    public final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }

    @Override
    public void deleteUploadTableRow(String appId, String studyId, String recordId) {
        HibernateUploadTableRowId id = new HibernateUploadTableRowId(appId, studyId, recordId);
        hibernateHelper.deleteById(HibernateUploadTableRow.class, id);
    }

    @Override
    public Optional<UploadTableRow> getUploadTableRow(String appId, String studyId, String recordId) {
        HibernateUploadTableRowId id = new HibernateUploadTableRowId(appId, studyId, recordId);
        UploadTableRow row = hibernateHelper.getById(HibernateUploadTableRow.class, id);
        return Optional.ofNullable(row);
    }

    @Override
    public PagedResourceList<UploadTableRow> queryUploadTableRows(UploadTableRowQuery query) {
        QueryBuilder builder = new QueryBuilder();
        builder.append("FROM HibernateUploadTableRow");

        // appId and studyId are always required. (This is validated in the service.)
        builder.append("WHERE appId = :appId AND studyId = :studyId");
        builder.getParameters().put("appId", query.getAppId());
        builder.getParameters().put("studyId", query.getStudyId());

        // Filter by assessment.
        if (query.getAssessmentGuid() != null) {
            builder.append("AND assessmentGuid = :assessmentGuid");
            builder.getParameters().put("assessmentGuid", query.getAssessmentGuid());
        }

        // Filter by date range.
        if (query.getStartTime() != null) {
            builder.append("AND createdOn >= :startDate");
            builder.getParameters().put("startDate", query.getStartTime());
        }
        if (query.getEndTime() != null) {
            builder.append("AND createdOn < :endDate");
            builder.getParameters().put("endDate", query.getEndTime());
        }

        // Include test data?
        if (!query.getIncludeTestData()) {
            builder.append("AND testData = 0");
        }

        // Get total.
        int total = hibernateHelper.queryCount("SELECT COUNT(DISTINCT recordId) " + builder.getQuery(),
                builder.getParameters());

        // Start and count.
        Integer start = query.getStart();
        if (start == null) {
            start = 0;
        }
        Integer pageSize = query.getPageSize();
        if (pageSize == null) {
            pageSize = BridgeConstants.API_DEFAULT_PAGE_SIZE;
        }

        // Query.
        List<HibernateUploadTableRow> hibernateList = hibernateHelper.queryGet(builder.getQuery(),
                builder.getParameters(), start, pageSize, HibernateUploadTableRow.class);

        // Because of Java generic typing issues, we need to convert this to a non-Hibernate UploadTableRow.
        List<UploadTableRow> list = new ArrayList<>(hibernateList);
        return new PagedResourceList<>(list, total)
                .withRequestParam("assessmentGuid", query.getAssessmentGuid())
                .withRequestParam("startTime", query.getStartTime())
                .withRequestParam("endTime", query.getEndTime())
                .withRequestParam("includeTestData", query.getIncludeTestData())
                .withRequestParam("start", start)
                .withRequestParam("pageSize", pageSize);
    }

    @Override
    public void saveUploadTableRow(UploadTableRow row) {
        hibernateHelper.saveOrUpdate(row);
    }
}
