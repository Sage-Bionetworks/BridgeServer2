package org.sagebionetworks.bridge.hibernate;

import java.util.List;

import javax.annotation.Resource;

import org.hibernate.query.Query;
import org.sagebionetworks.bridge.models.studies.Demographic;
import org.springframework.stereotype.Component;

@Component
public class HibernateDemographicDao {
    private HibernateHelper hibernateHelper;

    @Resource(name = "mysqlHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }

    public List<Demographic> foo() {
        // return hibernateHelper.getById(Demographic.class, "id1");
        System.out.println(hibernateHelper);
        return hibernateHelper.execute((session) -> {
            Query<Demographic> query = session.createSQLQuery("select * from Demographics;");
            System.out.println(query);
            return query.list();
        });
    }
}
