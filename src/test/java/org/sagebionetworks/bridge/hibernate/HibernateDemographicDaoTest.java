package org.sagebionetworks.bridge.hibernate;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.query.Query;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicUser;
import org.sagebionetworks.bridge.models.studies.DemographicValue;
import org.sagebionetworks.bridge.services.DemographicService;
import org.sagebionetworks.bridge.models.studies.DemographicValue;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class HibernateDemographicDaoTest {
    @Test
    public void foo() throws JsonProcessingException, IOException {
        // List<DemographicValue> values = new ArrayList<>();
        // Demographic d = new Demographic("id1", "api-study",
        // "wZpd8tqNLNlWj2jF3pSOtYdv", "category1", true, values, "units1");

        // MetadataSources metadataSources = new MetadataSources();
        // metadataSources.addAnnotatedClass(Demographic.class);
        // metadataSources.addAnnotatedClass(DemographicValue.class);
        // SessionFactory factory =
        // metadataSources.buildMetadata().buildSessionFactory();

        // Hibernate configs
        Properties props = new Properties();
        props.put("hibernate.connection.characterEncoding", "UTF-8");
        props.put("hibernate.connection.CharSet", "UTF-8");
        props.put("hibernate.connection.useUnicode", true);
        props.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");

        // c3p0 connection pool properties
        props.put("hibernate.c3p0.min_size", 5);
        props.put("hibernate.c3p0.max_size", 20);
        props.put("hibernate.c3p0.timeout", 300);
        props.put("hibernate.c3p0.idle_test_period", 300);

        // Connection properties come from Bridge configs
        BridgeConfig config = BridgeConfigFactory.getConfig();
        props.put("hibernate.connection.password", config.get("hibernate.connection.password"));
        props.put("hibernate.connection.username", config.get("hibernate.connection.username"));
        String url = config.get("hibernate.connection.url");
        // Append SSL props to URL
        boolean useSsl = Boolean.valueOf(config.get("hibernate.connection.useSSL"));
        url += "?rewriteBatchedStatements=true&serverTimezone=UTC&requireSSL=" + useSsl + "&useSSL=" + useSsl
                + "&verifyServerCertificate=" + useSsl;
        props.put("hibernate.connection.url", url);

        StandardServiceRegistry reg = new StandardServiceRegistryBuilder().applySettings(props).build();

        // For whatever reason, we need to list each Hibernate-enabled class
        // individually.
        MetadataSources metadataSources = new MetadataSources(reg);
        metadataSources.addAnnotatedClass(Demographic.class);
        metadataSources.addAnnotatedClass(DemographicValue.class);
        metadataSources.addAnnotatedClass(DemographicUser.class);
        SessionFactory factory = metadataSources.buildMetadata().buildSessionFactory();

        HibernateHelper helper = new HibernateHelper(factory, new OrganizationPersistenceExceptionConverter());
        HibernateDemographicDao h = new HibernateDemographicDao();
        h.setHibernateHelper(helper);
        DemographicService ds = new DemographicService();
        ds.setDemographicDao(h);

        System.out.println("creating user");
        DemographicUser du = new DemographicUser(null, "api", "api-study", "cw1gLb-hiOMb6kfCrmhUqJhX",
                new HashMap<>());
        du.getDemographics().put("testcategory1",
                new Demographic(null, du, "testcategory1", false, new ArrayList<>(), "testunits"));
        du.getDemographics().get("testcategory1").getValues().add(new DemographicValue("testvalue1"));
        ds.saveDemographicUser(du);
        System.out.println(h.getDemographicUserId("api", "api-study", "cw1gLb-hiOMb6kfCrmhUqJhX"));
        System.out.println(ds.getDemographicUser("api", "api-study", "cw1gLb-hiOMb6kfCrmhUqJhX"));

        System.out.println("creating demographic");
        DemographicUser du2 = ds.getDemographicUser("api", "api-study", "cw1gLb-hiOMb6kfCrmhUqJhX");
        du2.getDemographics().put("testcategory2",
                new Demographic(null, du2, "testcategory2", true, new ArrayList<>(), null));
        du2.getDemographics().get("testcategory2").getValues().add(new DemographicValue("testvalue2"));
        du2 = ds.saveDemographicUser(du2);
        ResourceList<DemographicUser> demographicUsers = h.getDemographicUsers("api", "api-study", 0, 10);
        System.out.println(demographicUsers.getItems());
        System.out.println("json");
        System.out.println(new ObjectMapper().writeValueAsString(demographicUsers));

        System.out.println("deleting demographic");
        ds.deleteDemographic("api", "api-study", "cw1gLb-hiOMb6kfCrmhUqJhX", du2.getDemographics().get("testcategory2").getId());
        System.out.println(ds.getDemographicUser("api", "api-study", "cw1gLb-hiOMb6kfCrmhUqJhX"));

        System.out.println("deleting user");
        ds.deleteDemographicUser("api", "api-study", "cw1gLb-hiOMb6kfCrmhUqJhX");
        System.out.println(ds.getDemographicUsers("api", "api-study", 0, 10).getItems());

        System.out.println(new ObjectMapper().readValue(new File("/Users/imajeed/Desktop/bridgenotes/demographic_results.json"), DemographicUser.class));

        /*
         * Session session = factory.openSession();
         * Query<Demographic> query = session.createQuery("from Demographic",
         * Demographic.class);
         * List<Demographic> results = query.list();
         * System.out.println(results);
         */

        /*
         * Session session = factory.openSession();
         * Query<DemographicUser> query = session.createQuery("from DemographicUser",
         * DemographicUser.class);
         * List<DemographicUser> results = query.list();
         * for (DemographicUser d : results) {
         * System.out.println(d);
         * }
         */
    }
}
