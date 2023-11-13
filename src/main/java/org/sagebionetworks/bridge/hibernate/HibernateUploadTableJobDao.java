package org.sagebionetworks.bridge.hibernate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.UploadTableJobDao;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.upload.UploadTableJob;

/** Hibernate implementation of UploadTableJobDao. */
@Component
public class HibernateUploadTableJobDao implements UploadTableJobDao {
    private HibernateHelper hibernateHelper;

    @Resource(name = "basicHibernateHelper")
    public final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }

    @Override
    public Optional<UploadTableJob> getUploadTableJob(String jobGuid) {
        UploadTableJob job = hibernateHelper.getById(HibernateUploadTableJob.class, jobGuid);
        return Optional.ofNullable(job);
    }

    @Override
    public PagedResourceList<UploadTableJob> listUploadTableJobsForStudy(String appId, String studyId, int start,
            int pageSize) {
        // Basic query.
        QueryBuilder builder = new QueryBuilder();
        builder.append("FROM HibernateUploadTableJob WHERE appId = :appId AND studyId = :studyId ORDER BY requestedOn DESC");
        builder.getParameters().put("appId", appId);
        builder.getParameters().put("studyId", studyId);

        // Get total.
        int total = hibernateHelper.queryCount("SELECT COUNT(DISTINCT jobGuid) " + builder.getQuery(),
                builder.getParameters());

        // Query.
        List<HibernateUploadTableJob> hibernateList = hibernateHelper.queryGet(builder.getQuery(),
                builder.getParameters(), start, pageSize, HibernateUploadTableJob.class);

        // Because of Java generic typing issues, we need to convert this to a non-Hibernate UploadTableJob.
        List<UploadTableJob> list = new ArrayList<>(hibernateList);
        return new PagedResourceList<>(list, total)
                .withRequestParam("start", start)
                .withRequestParam("pageSize", pageSize);
    }

    @Override
    public void saveUploadTableJob(UploadTableJob job) {
        hibernateHelper.saveOrUpdate(job);
    }
}
