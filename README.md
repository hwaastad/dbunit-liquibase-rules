Junit rule for liquibase container testing

Usage:

    @Default
    @Classes(cdi = true)
    @ContainerProperties({
        @ContainerProperties.Property(name = "TestDS", value = "new://Resource?type=DataSource"),
        @ContainerProperties.Property(name = "TestDS.LogSql", value = "true")
    })
    public class MyTest {
        @DbInstance
        @Resource(name = "TestDS")
        private DataSource ds;   
    
        @Rule
        public TestRule theRule = RuleChain.outerRule(new ApplicationComposerRule(this)).around(new LiquibaseEnvironment().resourcesHolder(this));
        
        @Test
        @DataSet("changelog.xml")
        public void test_00_DoStuff() throws Exception {
    
        }
    }

changelog.xml ligger da under src/test/resources