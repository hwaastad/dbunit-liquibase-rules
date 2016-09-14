/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.waastad.dbunit.liquibase.rules.rule;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.FileSystemResourceAccessor;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.waastad.dbunit.liquibase.rules.cdi.DataSet;
import org.waastad.dbunit.liquibase.rules.cdi.DbInstance;

/**
 *
 * @author helge
 */
public class LiquibaseEnvironment extends ExternalResource {

    private static final Logger LOG = Logger.getLogger(LiquibaseEnvironment.class.getName());

    private final ThreadLocal<Configuration> configurationThreadLocal = new ThreadLocal<>();

    public LiquibaseEnvironment() {
        configurationThreadLocal.set(new Configuration());
    }

    public LiquibaseEnvironment resourcesHolder(final Object test) {
        configurationThreadLocal.get().providerInstance(test);
        return this;
    }

    public LiquibaseEnvironment dataSource(final DataSource ds) {
        configurationThreadLocal.get().dataSource = ds;
        return this;
    }

    @Override
    protected void before() throws Throwable {
        if (!configurationThreadLocal.get().run) {
            return;
        }
        final DataSource dataSource = findDataSource();
        if (dataSource == null) {
            throw new IllegalStateException("DataSource is not yet initialized");
        }

        try {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(dataSource.getConnection()));
            final Liquibase liquibase = new Liquibase(configurationThreadLocal.get().scriptSource, new ClassLoaderResourceAccessor(getClass().getClassLoader()), database);
            if (configurationThreadLocal.get().cleanBefore) {
                liquibase.dropAll();
            }
            liquibase.update("");
        } catch (SQLException | LiquibaseException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    protected void after() {
        if (!configurationThreadLocal.get().run) {
            return;
        }
        final DataSource dataSource = findDataSource();
        if (dataSource == null) {
            throw new IllegalStateException("DataSource is not yet initialized");
        }
        try {
            if (configurationThreadLocal.get().cleanAfter) {
                Database database = DatabaseFactory.getInstance()
                        .findCorrectDatabaseImplementation(new JdbcConnection(dataSource.getConnection()));
                final Liquibase liquibase = new Liquibase(configurationThreadLocal.get().scriptSource, new FileSystemResourceAccessor(), database);

                liquibase.dropAll();
            }
        } catch (SQLException | LiquibaseException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public Statement apply(Statement base, Description description) {
        DataSet dataset = resolveDataset(description);
        configurationThreadLocal.get().run = (dataset == null) ? Boolean.FALSE : Boolean.TRUE;
        configurationThreadLocal.get().cleanAfter = (dataset != null) ? dataset.cleanAfter() : Boolean.FALSE;
        configurationThreadLocal.get().cleanBefore = (dataset != null) ? dataset.cleanBefore() : Boolean.FALSE;
        configurationThreadLocal.get().scriptSource = (dataset != null) ? dataset.value() : "";

        return super.apply(base, description); //To change body of generated methods, choose Tools | Templates.
    }

    private DataSet resolveDataset(Description description) {
        DataSet dataSet = description.getAnnotation(DataSet.class);
        if (dataSet == null) {
            dataSet = description.getTestClass().getAnnotation(DataSet.class);
        }
        return dataSet;
    }

    private DataSource findDataSource() {
        final Configuration configuration = configurationThreadLocal.get();
        if (configuration != null && configuration.dataSource != null) {
            return configuration.dataSource;
        }
        if (configuration != null && configuration.instance != null) {
            Class<?> c = configuration.instance.getClass();
            while (c != null && c != Object.class) {
                for (final Field f : c.getDeclaredFields()) {
                    if (f.getType() == DataSource.class && f.getAnnotation(DbInstance.class) != null) {
                        if (!f.isAccessible()) {
                            f.setAccessible(true);
                        }
                        try {
                            return configuration.dataSource = DataSource.class.cast(f.get(Modifier.isStatic(f.getModifiers()) ? null : configuration.instance));
                        } catch (final IllegalAccessException e) {
                            throw new IllegalStateException(e);
                        }
                    }
                }
                for (final Method m : c.getDeclaredMethods()) {
                    if (DataSource.class == m.getReturnType() && m.getAnnotation(DbInstance.class) != null && m.getParameterTypes().length == 0) {
                        if (!m.isAccessible()) {
                            m.setAccessible(true);
                        }
                        try {
                            return configuration.dataSource = DataSource.class.cast(m.invoke(Modifier.isStatic(m.getModifiers()) ? null : configuration.instance));
                        } catch (final InvocationTargetException e) {
                            throw new IllegalStateException(e.getCause());
                        } catch (IllegalAccessException e) {
                            throw new IllegalStateException(e);
                        }
                    }
                }
                c = c.getSuperclass();
            }
        }
        throw new IllegalStateException("No datasource available, provide either the tets instance with @DbInstance on a datasource field or directly the datasource to the rule.");
    }

    private static final class Configuration {

        private DataSource dataSource;
        private Object instance;
        private Boolean run;
        private Boolean cleanBefore;
        private Boolean cleanAfter;
        private String scriptSource;

        public void dataSource(final DataSource ds) {
            this.dataSource = ds;
        }

        public void providerInstance(final Object test) {
            this.instance = test;
        }

        public void runDb(final Boolean test) {
            this.run = test;
        }

        public void cleanBefore(final Boolean test) {
            this.cleanBefore = test;
        }

        public void cleanAfter(final Boolean test) {
            this.cleanAfter = test;
        }

        public void scriptSource(String source) {
            this.scriptSource = source;
        }
    }

}
