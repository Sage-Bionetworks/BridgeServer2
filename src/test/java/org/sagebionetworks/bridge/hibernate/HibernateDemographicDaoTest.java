package org.sagebionetworks.bridge.hibernate;

import java.util.ArrayList;
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
import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicValue;
import org.testng.annotations.Test;

public class HibernateDemographicDaoTest {
    @Test
    public void foo() {
        // List<DemographicValue> values = new ArrayList<>();
        // Demographic d = new Demographic("id1", "api-study", "wZpd8tqNLNlWj2jF3pSOtYdv", "category1", true, values, "units1");
        
        // MetadataSources metadataSources = new MetadataSources();
        // metadataSources.addAnnotatedClass(Demographic.class);
        // metadataSources.addAnnotatedClass(DemographicValue.class);
        // SessionFactory factory = metadataSources.buildMetadata().buildSessionFactory();

        
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
        url += "?rewriteBatchedStatements=true&serverTimezone=UTC&requireSSL="+useSsl+"&useSSL="+useSsl+"&verifyServerCertificate="+useSsl;
        props.put("hibernate.connection.url", url);

        StandardServiceRegistry reg = new StandardServiceRegistryBuilder().applySettings(props).build();
        
        // For whatever reason, we need to list each Hibernate-enabled class individually.
        MetadataSources metadataSources = new MetadataSources(reg);
        metadataSources.addAnnotatedClass(Demographic.class);
        metadataSources.addAnnotatedClass(DemographicValue.class);
        SessionFactory factory = metadataSources.buildMetadata().buildSessionFactory();

        Session session = factory.openSession();
        Query<Demographic> query = session.createQuery("from Demographic", Demographic.class);
        // query.
        List<Demographic> results = query.list();
        System.out.println(results);
        System.out.println(query.list().get(0).toString());


        // System.out.println(new HibernateDemographicDao().foo());
    }
}
