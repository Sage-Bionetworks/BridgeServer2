package org.sagebionetworks.bridge.hibernate;

import java.util.List;
import java.util.Optional;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.dao.DemographicDao;
import org.sagebionetworks.bridge.hibernate.QueryBuilder.WhereClauseBuilder;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.SearchTermPredicate;
import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicUser;
import org.springframework.stereotype.Component;

/**
 * Hibernate dao for demographic-related operations.
 */
@Component
public class HibernateDemographicDao implements DemographicDao {
    private HibernateHelper hibernateHelper;

    @Resource(name = "mysqlHibernateHelper")
    public final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }

    /**
     * Saves/overwrites a DemographicUser.
     * 
     * @param demographicUser The DemographicUser to save/overwrite.
     * @param appId           The appId of the app in which to save the
     *                        DemographicUser.
     * @param studyId         The studyId of the study in which to save the
     *                        DemographicUser. Can be null if the demographics are
     *                        app-level.
     * @param userId          The userId of the user to associate the
     *                        DemographicUser with.
     * @return the saved DemographicUser.
     */
    @Override
    public DemographicUser saveDemographicUser(DemographicUser demographicUser, String appId, String studyId,
            String userId) {
        Optional<DemographicUser> existingDemographicUser = getDemographicUser(appId, studyId, userId);
        if (existingDemographicUser.isPresent()) {
            // perform both deletion and saving in single transaction
            return hibernateHelper.executeWithExceptionHandling(null, (session) -> {
                session.delete(existingDemographicUser.get());
                session.flush();
                session.saveOrUpdate(demographicUser);
                return demographicUser;
            });
        } else {
            return hibernateHelper.saveOrUpdate(demographicUser);
        }
    }

    /**
     * Deletes a Demographic.
     * 
     * @param demographicId The id of the Demographic to delete.
     */
    @Override
    public void deleteDemographic(String demographicId) {
        hibernateHelper.deleteById(Demographic.class, demographicId);
    }

    /**
     * Deletes a DemographicUser.
     * 
     * @param demographicUserId The id of the DemographicUser to delete.
     */
    @Override
    public void deleteDemographicUser(String demographicUserId) {
        hibernateHelper.deleteById(DemographicUser.class, demographicUserId);
    }

    /**
     * Fetches a Demographic.
     * 
     * @param demographicId The id of the Demographic to fetch.
     * @return the fetched Demographic, or empty if it was not found.
     */
    @Override
    public Optional<String> getDemographicUserId(String appId, String studyId, String userId) {
        QueryBuilder builder = new QueryBuilder();
        builder.append("SELECT du.id FROM DemographicUser du");
        WhereClauseBuilder where = builder.startWhere(SearchTermPredicate.AND);
        where.append("du.appId = :appId", "appId", appId);
        if (studyId == null) {
            where.append("du.studyId IS NULL");
        } else {
            where.append("du.studyId = :studyId", "studyId", studyId);
        }
        where.append("du.userId = :userId", "userId", userId);
        Optional<String> existingDemographicUserId = hibernateHelper.queryGetOne(builder.getQuery(),
                builder.getParameters(), String.class);
        return existingDemographicUserId;
    }

    /**
     * Fetches the id for a DemographicUser.
     * 
     * @param appId   The appId of the app which contains the DemographicUser whose
     *                id will be fetched.
     * @param studyId The studyId of the study which contains the DemographicUser
     *                whose id will be fetched. Can be null if the demographics are
     *                app-level.
     * @param userId  The userId of the user who is associated with the
     *                DemographicUser whose id will be fetched.
     * @return the id for a DemographicUser, or empty if it was not found.
     */
    @Override
    public Optional<Demographic> getDemographic(String demographicId) {
        QueryBuilder builder = new QueryBuilder();
        builder.append("FROM Demographic d");
        WhereClauseBuilder where = builder.startWhere(SearchTermPredicate.AND);
        where.append("d.id = :demographicId", "demographicId", demographicId);
        Optional<Demographic> existingDemographic = hibernateHelper.queryGetOne(builder.getQuery(),
                builder.getParameters(), Demographic.class);
        return existingDemographic;
    }

    /**
     * Fetches a DemographicUser.
     * 
     * @param appId   The appId of the app which contains the DemographicUser to
     *                fetch.
     * @param studyId The studyId of the study which contains the DemographicUser to
     *                fetch. Can be null if the demographics are app-level.
     * @param userId  The userId of the user who is associated with the
     *                DemographicUser to be fetched.
     * @return a DemographicUser, or empty if it was not found.
     */
    @Override
    public Optional<DemographicUser> getDemographicUser(String appId, String studyId, String userId) {
        QueryBuilder builder = new QueryBuilder();
        builder.append("FROM DemographicUser du");
        WhereClauseBuilder where = builder.startWhere(SearchTermPredicate.AND);
        where.append("du.appId = :appId", "appId", appId);
        if (studyId == null) {
            where.append("du.studyId IS NULL");
        } else {
            where.append("du.studyId = :studyId", "studyId", studyId);
        }
        where.append("du.userId = :userId", "userId", userId);
        Optional<DemographicUser> existingDemographicUser = hibernateHelper.queryGetOne(builder.getQuery(),
                builder.getParameters(), DemographicUser.class);
        return existingDemographicUser;
    }

    /**
     * Fetches all app-level DemographicUsers for an app or all study-level
     * DemographicUsers for a study.
     * 
     * @param appId    The appId of the app which contains the DemographicUsers to
     *                 fetch.
     * @param studyId  The studyId of the study which contains the DemographicUsers
     *                 to fetch. Can be null if the demographics are app-level.
     * @param offsetBy The offset at which the returned list of DemographicUsers
     *                 should begin.
     * @param pageSize The maximum number of entries in the returned list of
     *                 DemographicUsers.
     * @return a paged list of fetched DemographicUsers.
     */
    @Override
    public PagedResourceList<DemographicUser> getDemographicUsers(String appId, String studyId, int offsetBy,
            int pageSize) {
        QueryBuilder builder = new QueryBuilder();
        builder.append("FROM DemographicUser du");
        WhereClauseBuilder where = builder.startWhere(SearchTermPredicate.AND);
        where.append("du.appId = :appId", "appId", appId);
        if (studyId == null) {
            where.append("du.studyId IS NULL");
        } else {
            where.append("du.studyId = :studyId", "studyId", studyId);
        }
        int count = hibernateHelper.queryCount("SELECT COUNT(*) " + builder.getQuery(), builder.getParameters());
        List<DemographicUser> existingDemographicUsers = hibernateHelper.queryGet(builder.getQuery(),
                builder.getParameters(), offsetBy, pageSize, DemographicUser.class);
        return new PagedResourceList<>(existingDemographicUsers, count, true)
                .withRequestParam("offsetBy", offsetBy)
                .withRequestParam("pageSize", pageSize);
    }
}
