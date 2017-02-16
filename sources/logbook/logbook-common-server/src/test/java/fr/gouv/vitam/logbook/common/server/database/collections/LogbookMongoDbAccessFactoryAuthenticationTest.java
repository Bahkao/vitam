package fr.gouv.vitam.logbook.common.server.database.collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import fr.gouv.vitam.common.database.server.elasticsearch.ElasticsearchNode;
import fr.gouv.vitam.common.exception.VitamApplicationServerException;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.junit.JunitHelper;
import fr.gouv.vitam.common.junit.JunitHelper.ElasticsearchTestConfiguration;
import fr.gouv.vitam.common.server.application.configuration.MongoDbNode;
import fr.gouv.vitam.logbook.common.parameters.LogbookOperationParameters;
import fr.gouv.vitam.logbook.common.parameters.LogbookParameterName;
import fr.gouv.vitam.logbook.common.parameters.LogbookParametersFactory;
import fr.gouv.vitam.logbook.common.server.LogbookConfiguration;
import fr.gouv.vitam.logbook.common.server.exception.LogbookAlreadyExistsException;
import fr.gouv.vitam.logbook.common.server.exception.LogbookDatabaseException;
import ru.yandex.qatools.embed.service.MongoEmbeddedService;

public class LogbookMongoDbAccessFactoryAuthenticationTest {
    private static final String DATABASE_HOST = "localhost";
    static LogbookMongoDbAccessImpl mongoDbAccess;
    private static int port;
    private static JunitHelper junitHelper;
    private static MongoEmbeddedService mongo;
    private static final String databaseName = "db-logbook";
    private static final String user = "user-logbook";
    private static final String pwd = "user-logbook";

    // ES
    @ClassRule
    public static TemporaryFolder esTempFolder = new TemporaryFolder();
    private final static String ES_CLUSTER_NAME = "vitam-cluster";
    private final static String ES_HOST_NAME = "localhost";
    private static ElasticsearchTestConfiguration config = null;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        junitHelper = JunitHelper.getInstance();
        port = junitHelper.findAvailablePort();

        // ES
        try {
            config = JunitHelper.startElasticsearchForTest(esTempFolder, ES_CLUSTER_NAME);
        } catch (final VitamApplicationServerException e1) {
            assumeTrue(false);
        }

        // Starting the embedded services within temporary dir
        mongo = new MongoEmbeddedService(
            DATABASE_HOST + ":" + port, databaseName, user, pwd, "localreplica");
        mongo.start();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        if (config != null) {
            JunitHelper.stopElasticsearchForTest(config);
        }
        mongo.stop();
        junitHelper.releasePort(port);
    }

    @Test
    public void testCreateLogbook() throws LogbookDatabaseException, LogbookAlreadyExistsException {
        // mongo
        final List<MongoDbNode> nodes = new ArrayList<>();
        nodes.add(new MongoDbNode(DATABASE_HOST, port));
        // es
        final List<ElasticsearchNode> esNodes = new ArrayList<>();
        esNodes.add(new ElasticsearchNode(ES_HOST_NAME, config.getTcpPort()));

        new LogbookMongoDbAccessFactory();
        mongoDbAccess = LogbookMongoDbAccessFactory.create(
            new LogbookConfiguration(nodes, databaseName, ES_CLUSTER_NAME, esNodes, true, user, pwd));
        assertNotNull(mongoDbAccess);
        assertEquals("db-logbook", mongoDbAccess.getMongoDatabase().getName());
        final LogbookOperationParameters parameters = LogbookParametersFactory.newLogbookOperationParameters();
        for (final LogbookParameterName name : LogbookParameterName.values()) {
            parameters.putParameterValue(name,
                GUIDFactory.newEventGUID(0).getId());
        }
        mongoDbAccess.createLogbookOperation(parameters);
        mongoDbAccess.close();
    }

}
