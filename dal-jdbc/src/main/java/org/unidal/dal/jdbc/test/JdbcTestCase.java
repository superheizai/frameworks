package org.unidal.dal.jdbc.test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;

import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.LoggerManager;
import org.junit.Before;
import org.unidal.dal.jdbc.DalException;
import org.unidal.dal.jdbc.datasource.DataSourceManager;
import org.unidal.dal.jdbc.raw.RawDao;
import org.unidal.dal.jdbc.raw.RawDataObject;
import org.unidal.dal.jdbc.test.data.entity.DatabaseModel;
import org.unidal.dal.jdbc.test.function.StringFunction;
import org.unidal.helper.Files;
import org.unidal.helper.Reflects;
import org.unidal.helper.Reflects.MethodFilter;
import org.unidal.lookup.ComponentTestCase;

/**
 * <xmp>
 * 
 * <dependency>
 * <groupId>com.h2database</groupId>
 * <artifactId>h2</artifactId>
 * <version>1.4.186</version>
 * <scope>test</scope>
 * </dependency>
 * 
 * </xmp>
 */
public abstract class JdbcTestCase extends ComponentTestCase {
   private LoggerManager m_loggerManager;

   protected void createTables(String name) throws Exception {
      String resource = String.format("/META-INF/dal/jdbc/%s-codegen.xml", name);
      InputStream in = getClass().getResourceAsStream(resource);

      if (in == null) {
         throw new IllegalArgumentException(String.format("Resource(%s) not found!", resource));
      }

      TableMaker maker = lookup(TableMaker.class);

      maker.make(getDefaultDataSource(), in);
   }

   protected Logger getLogger() {
      return m_loggerManager.getLoggerForComponent("");
   }

   protected void defineFunctions(Class<?> functionClass) throws DalException {
      List<Method> methods = Reflects.forMethod().getMethods(functionClass, MethodFilter.PUBLIC_STATIC);

      for (Method method : methods) {
         if (method.getReturnType() == Void.TYPE) {
            getLogger().warn(String.format("Method(%s) return void, IGNORED!", method));
            continue;
         }

         String name = method.getName();
         String className = functionClass.getName();

         executeUpdate(String
               .format("CREATE ALIAS IF NOT EXISTS %s FOR \"%s.%s\"", name.toUpperCase(), className, name));
      }
   }

   protected void dumpTo(String dataXml, String... tables) throws DalException, IOException {
      if (tables.length > 0) {
         DatabaseDumper dumper = lookup(DatabaseDumper.class);
         File base = new File("src/test/resources");
         File file;

         if (dataXml.startsWith("/")) {
            file = new File(base, dataXml);
         } else {
            String packageName = getClass().getPackage().getName();

            file = new File(base, packageName.replace('.', '/') + "/" + dataXml);
         }

         DatabaseModel model = dumper.dump(getDefaultDataSource(), tables);

         Files.forIO().writeTo(file, model.toString());
      }
   }

   protected List<RawDataObject> executeQuery(String sql) throws DalException {
      RawDao dao = lookup(RawDao.class);

      return dao.executeQuery(getDefaultDataSource(), sql);
   }

   protected void executeUpdate(String sql) throws DalException {
      RawDao dao = lookup(RawDao.class);

      dao.executeUpdate(getDefaultDataSource(), sql);
   }

   protected abstract String getDefaultDataSource();

   protected void loadFrom(String dataXml) throws Exception {
      InputStream in = getClass().getResourceAsStream(dataXml);

      if (in == null) {
         throw new IllegalArgumentException(String.format("Resource(%s) not found!", dataXml));
      }

      TableLoader loader = lookup(TableLoader.class);

      loader.loadFrom(getDefaultDataSource(), in);
      release(loader);
   }

   @Before
   @Override
   public void setUp() throws Exception {
      System.setProperty("devMode", "true");

      super.setUp();

      m_loggerManager = lookup(LoggerManager.class);

      defineComponent(DataSourceManager.class, TestDataSourceManager.class);
      defineFunctions(StringFunction.class);
   }

   protected void showQuery(String sql) throws DalException {
      long start = System.currentTimeMillis();
      List<RawDataObject> rowset = executeQuery(sql);
      long end = System.currentTimeMillis();

      System.out.println(new QueryResultBuilder().build(rowset));
      System.out.println(String.format("%s rows in set (%.3f sec)", rowset.size(), (end - start) / 1000.0));
      System.out.println();
   }

   @Override
   public void tearDown() throws Exception {
      executeUpdate("SHUTDOWN");

      super.tearDown();
   }
}
