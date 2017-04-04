package com.mulesoft.mule.cassandradb.automation.testrunners;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.experimental.categories.Categories;
import org.junit.runner.RunWith;
import org.junit.runners.Suite.SuiteClasses;
import org.mule.tools.devkit.ctf.mockup.ConnectorTestContext;
import org.mule.tools.devkit.ctf.platform.PlatformManager;

import com.mulesoft.mule.cassandradb.CassandraDBConnector;
import com.mulesoft.mule.cassandradb.automation.functional.InsertTestCases;
import com.mulesoft.mule.cassandradb.automation.functional.QueryMetadataIT;
import com.mulesoft.mule.cassandradb.automation.functional.SelectTestCases;

@RunWith(Categories.class)
@SuiteClasses({
    InsertTestCases.class,
    SelectTestCases.class,
    QueryMetadataIT.class
})
public class FunctionalTestSuite {

      @BeforeClass
      public static void initializeSuite(){
          ConnectorTestContext.initialize(CassandraDBConnector.class);
      }
      @AfterClass
      public static void shutdownSuite() throws Exception{
          ConnectorTestContext<CassandraDBConnector> context = ConnectorTestContext.getInstance();
          PlatformManager platform =  context.getPlatformManager();
          platform.shutdown();
      }

}