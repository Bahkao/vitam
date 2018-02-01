/*******************************************************************************
 * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2019)
 *
 * contact.vitam@culture.gouv.fr
 *
 * This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 * high volumetry securely and efficiently.
 *
 * This software is governed by the CeCILL 2.1 license under French law and abiding by the rules of distribution of free
 * software. You can use, modify and/ or redistribute the software under the terms of the CeCILL 2.1 license as
 * circulated by CEA, CNRS and INRIA at the following URL "http://www.cecill.info".
 *
 * As a counterpart to the access to the source code and rights to copy, modify and redistribute granted by the license,
 * users are provided only with a limited warranty and the software's author, the holder of the economic rights, and the
 * successive licensors have only limited liability.
 *
 * In this respect, the user's attention is drawn to the risks associated with loading, using, modifying and/or
 * developing or reproducing the software by the user in light of its specific status of free software, that may mean
 * that it is complicated to manipulate, and that also therefore means that it is reserved for developers and
 * experienced professionals having in-depth computer knowledge. Users are therefore encouraged to load and test the
 * software's suitability as regards their requirements in conditions enabling the security of their systems and/or data
 * to be ensured and, more generally, to use and operate it in the same conditions as regards security.
 *
 * The fact that you are presently reading this means that you have had knowledge of the CeCILL 2.1 license and that you
 * accept its terms.
 *******************************************************************************/
package fr.gouv.vitam.logbook.rest;

import static com.jayway.restassured.RestAssured.given;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.ws.rs.core.Response.Status;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.jhades.JHades;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.fasterxml.jackson.databind.JsonNode;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;

import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import fr.gouv.vitam.common.GlobalDataRest;
import fr.gouv.vitam.common.LocalDateUtil;
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.ServerIdentity;
import fr.gouv.vitam.common.database.builder.query.QueryHelper;
import fr.gouv.vitam.common.database.builder.request.exception.InvalidCreateOperationException;
import fr.gouv.vitam.common.database.builder.request.single.Select;
import fr.gouv.vitam.common.database.server.elasticsearch.ElasticsearchNode;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamApplicationServerException;
import fr.gouv.vitam.common.guid.GUID;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.junit.JunitHelper;
import fr.gouv.vitam.common.junit.JunitHelper.ElasticsearchTestConfiguration;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.LifeCycleStatusCode;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.server.application.configuration.MongoDbNode;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.logbook.common.parameters.LogbookLifeCycleObjectGroupParameters;
import fr.gouv.vitam.logbook.common.parameters.LogbookLifeCycleUnitParameters;
import fr.gouv.vitam.logbook.common.parameters.LogbookParameterName;
import fr.gouv.vitam.logbook.common.parameters.LogbookParametersFactory;
import fr.gouv.vitam.logbook.common.parameters.LogbookTypeProcess;
import fr.gouv.vitam.logbook.common.server.LogbookConfiguration;
import fr.gouv.vitam.logbook.common.server.database.collections.LogbookLifeCycleMongoDbName;
import fr.gouv.vitam.logbook.common.server.exception.LogbookDatabaseException;
import fr.gouv.vitam.logbook.common.server.exception.LogbookNotFoundException;

/**
 *
 */
public class LogBookLifeCycleUnitTest {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(LogBookLifeCycleUnitTest.class);

    private static final String REST_URI = "/logbook/v1";

    private static final String JETTY_CONFIG = "jetty-config-test.xml";
    private static final String SERVER_HOST = "localhost";
    private static MongodExecutable mongodExecutable;
    private static MongodProcess mongod;

    // ES
    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();
    private final static String ES_CLUSTER_NAME = "vitam-cluster";
    private final static String ES_HOST_NAME = "localhost";
    private static ElasticsearchTestConfiguration config = null;

    private static final String LIFE_UNIT_ID_URI = "/operations/{id_op}/unitlifecycles/{id_lc}";
    private static final String LIFE_UNIT_URI = "/operations/{id_op}/unitlifecycles";
    private static final String LIFE_OG_ID_URI = "/operations/{id_op}/objectgrouplifecycles/{id_lc}";

    private static final String FAKE_UNIT_LF_ID = "1";
    private static final String FAKE_OBG_LF_ID = "1";
    private static final String SELECT_UNIT_BY_ID_URI = "/unitlifecycles/" + FAKE_UNIT_LF_ID;
    private static final String SELECT_OBG_BY_ID_URI = "/objectgrouplifecycles/" + FAKE_OBG_LF_ID;

    private static int databasePort;
    private static int serverPort;
    private static LogbookMain application;

    private static LogbookLifeCycleUnitParameters logbookLifeCyclesUnitParametersStart;
    private static LogbookLifeCycleUnitParameters logbookLifeCyclesUnitParametersBAD;
    private static LogbookLifeCycleUnitParameters logbookLifeCyclesUnitParametersUpdate;

    private static LogbookLifeCycleObjectGroupParameters LogbookLifeCycleObjectGroupParametersStart;

    private static JunitHelper junitHelper;

    private static final Integer TENANT_ID = 0;
    private static final List<Integer> tenantList = Arrays.asList(0);

    @Rule
    public RunWithCustomExecutorRule runInThread = new RunWithCustomExecutorRule(
        VitamThreadPoolExecutor.getDefaultExecutor());

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        // Identify overlapping in particular jsr311
        new JHades().overlappingJarsReport();


        junitHelper = JunitHelper.getInstance();
        databasePort = junitHelper.findAvailablePort();

        final MongodStarter starter = MongodStarter.getDefaultInstance();
        mongodExecutable = starter.prepare(new MongodConfigBuilder()
            .withLaunchArgument("--enableMajorityReadConcern")
            .version(Version.Main.PRODUCTION)
            .net(new Net(databasePort, Network.localhostIsIPv6()))
            .build());

        mongod = mongodExecutable.start();
        // ES
        try {
            config = JunitHelper.startElasticsearchForTest(temporaryFolder, ES_CLUSTER_NAME);
        } catch (final VitamApplicationServerException e1) {
            assumeTrue(false);
        }
        serverPort = junitHelper.findAvailablePort();

        // TODO P1 verifier la compatibilité avec les tests parallèles sur jenkins

        try {
            final LogbookConfiguration logbookConf = new LogbookConfiguration();
            final List<MongoDbNode> nodes = new ArrayList<>();
            nodes.add(new MongoDbNode(SERVER_HOST, databasePort));
            logbookConf.setDbName("vitam-test").setMongoDbNodes(nodes);
            final List<ElasticsearchNode> esNodes = new ArrayList<>();
            esNodes.add(new ElasticsearchNode(ES_HOST_NAME, config.getTcpPort()));
            logbookConf.setJettyConfig(JETTY_CONFIG);
            logbookConf.setP12LogbookFile("tsa.p12");
            logbookConf.setP12LogbookPassword("1234");
            logbookConf.setWorkspaceUrl("http://localhost:8001");
            logbookConf.setProcessingUrl("http://localhost:8002");
            logbookConf.setClusterName(ES_CLUSTER_NAME);
            logbookConf.setElasticsearchNodes(esNodes);
            logbookConf.setTenants(tenantList);

            File file = temporaryFolder.newFile();
            String configurationFile = file.getAbsolutePath();
            PropertiesUtils.writeYaml(file, logbookConf);


            application = new LogbookMain(configurationFile);
            application.start();

            RestAssured.port = serverPort;
            RestAssured.basePath = REST_URI;
            JunitHelper.unsetJettyPortSystemProperty();
        } catch (final VitamApplicationServerException e) {
            LOGGER.error(e);
            throw new IllegalStateException(
                "Cannot start the Logbook Application Server", e);
        }

        final GUID eip = GUIDFactory.newWriteLogbookGUID(0);
        final GUID iop = GUIDFactory.newWriteLogbookGUID(0);
        final GUID ioL = GUIDFactory.newUnitGUID(0);


        logbookLifeCyclesUnitParametersStart = LogbookParametersFactory.newLogbookLifeCycleUnitParameters();
        logbookLifeCyclesUnitParametersStart.setStatus(StatusCode.OK);
        logbookLifeCyclesUnitParametersStart.putParameterValue(LogbookParameterName.eventIdentifier,
            eip.toString());
        logbookLifeCyclesUnitParametersStart.putParameterValue(LogbookParameterName.eventIdentifierProcess,
            iop.toString());
        logbookLifeCyclesUnitParametersStart.putParameterValue(LogbookParameterName.objectIdentifier,
            ioL.toString());
        /**
         * Bad request
         */
        logbookLifeCyclesUnitParametersBAD = LogbookParametersFactory.newLogbookLifeCycleUnitParameters();

        logbookLifeCyclesUnitParametersBAD.putParameterValue(LogbookParameterName.eventIdentifier,
            eip.toString());
        logbookLifeCyclesUnitParametersBAD.putParameterValue(LogbookParameterName.eventIdentifierProcess,
            iop.toString());
        logbookLifeCyclesUnitParametersBAD.putParameterValue(LogbookParameterName.objectIdentifier,
            ioL.toString());
        /**
         * update
         *
         */
        final GUID eip2 = GUIDFactory.newWriteLogbookGUID(0);
        final GUID iop2 = GUIDFactory.newWriteLogbookGUID(0);
        final GUID ioL2 = GUIDFactory.newUnitGUID(0);
        logbookLifeCyclesUnitParametersUpdate = LogbookParametersFactory.newLogbookLifeCycleUnitParameters();
        logbookLifeCyclesUnitParametersUpdate.setStatus(StatusCode.OK);
        logbookLifeCyclesUnitParametersUpdate.putParameterValue(LogbookParameterName.eventIdentifier,
            eip2.toString());
        logbookLifeCyclesUnitParametersUpdate.putParameterValue(LogbookParameterName.eventIdentifierProcess,
            iop2.toString());
        logbookLifeCyclesUnitParametersUpdate.putParameterValue(LogbookParameterName.objectIdentifier,
            ioL2.toString());

        /**
         * Start ObjectGroup
         */

        LogbookLifeCycleObjectGroupParametersStart =
            LogbookParametersFactory.newLogbookLifeCycleObjectGroupParameters();

        LogbookLifeCycleObjectGroupParametersStart =
            LogbookParametersFactory.newLogbookLifeCycleObjectGroupParameters();
        LogbookLifeCycleObjectGroupParametersStart.setStatus(StatusCode.OK);
        LogbookLifeCycleObjectGroupParametersStart.putParameterValue(LogbookParameterName.eventIdentifier,
            eip.toString());
        LogbookLifeCycleObjectGroupParametersStart.putParameterValue(LogbookParameterName.eventIdentifierProcess,
            iop.toString());
        LogbookLifeCycleObjectGroupParametersStart.putParameterValue(LogbookParameterName.objectIdentifier,
            ioL.toString());
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        LOGGER.debug("Ending tests");
        try {
            if (application != null) {
                application.stop();
            }
        } catch (final VitamApplicationServerException e) {
            LOGGER.error(e);
        }
        if (config != null) {
            JunitHelper.stopElasticsearchForTest(config);
        }
        mongod.stop();
        mongodExecutable.stop();
        junitHelper.releasePort(databasePort);
        junitHelper.releasePort(serverPort);
    }

    @Test
    public final void given_lifeCycleUnit_when_create_update_test() throws InvalidCreateOperationException {
        // Creation OK

        logbookLifeCyclesUnitParametersStart.putParameterValue(LogbookParameterName.eventType, "event");
        logbookLifeCyclesUnitParametersStart.setTypeProcess(LogbookTypeProcess.INGEST);
        logbookLifeCyclesUnitParametersStart.putParameterValue(LogbookParameterName.outcomeDetail, "outcomeDetail");
        logbookLifeCyclesUnitParametersStart.putParameterValue(LogbookParameterName.outcomeDetailMessage,
            "outcomeDetailMessage");
        logbookLifeCyclesUnitParametersStart.putParameterValue(LogbookParameterName.eventDateTime,
            LocalDateUtil.now().toString());
        logbookLifeCyclesUnitParametersStart.putParameterValue(LogbookParameterName.agentIdentifier,
            ServerIdentity.getInstance().getJsonIdentity());
        given()
            .contentType(ContentType.JSON)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .body(logbookLifeCyclesUnitParametersStart.toString())
            .when()
            .post(LIFE_UNIT_ID_URI,
                logbookLifeCyclesUnitParametersStart.getParameterValue(LogbookParameterName.eventIdentifierProcess),
                logbookLifeCyclesUnitParametersStart.getParameterValue(LogbookParameterName.objectIdentifier))
            .then()
            .statusCode(Status.CREATED.getStatusCode());

        // already exsits
        given()
            .contentType(ContentType.JSON)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .body(logbookLifeCyclesUnitParametersStart.toString())
            .when()
            .post(LIFE_UNIT_ID_URI,
                logbookLifeCyclesUnitParametersStart.getParameterValue(LogbookParameterName.eventIdentifierProcess),
                logbookLifeCyclesUnitParametersStart.getParameterValue(LogbookParameterName.objectIdentifier))
            .then()
            .statusCode(Status.CONFLICT.getStatusCode());

        // incoherence parameters ; response bad_request
        given()
            .contentType(ContentType.JSON)
            .body(logbookLifeCyclesUnitParametersStart.toString())
            .when()
            .post(LIFE_UNIT_ID_URI,
                logbookLifeCyclesUnitParametersStart.getParameterValue(LogbookParameterName.eventIdentifierProcess),
                "bad_id")
            .then()
            .statusCode(Status.BAD_REQUEST.getStatusCode());


        // update ok
        logbookLifeCyclesUnitParametersStart.putParameterValue(LogbookParameterName.outcomeDetailMessage,
            "ModifiedoutcomeDetailMessage");
        logbookLifeCyclesUnitParametersStart.setStatus(StatusCode.OK);
        given()
            .contentType(ContentType.JSON)
            .body(logbookLifeCyclesUnitParametersStart.toString())
            .when()
            .put(LIFE_UNIT_ID_URI,
                logbookLifeCyclesUnitParametersStart.getParameterValue(LogbookParameterName.eventIdentifierProcess),
                logbookLifeCyclesUnitParametersStart.getParameterValue(LogbookParameterName.objectIdentifier))
            .then()
            .statusCode(Status.OK.getStatusCode());

        // Update illegal argument incoherence parameters ; response bad_request
        given()
            .contentType(ContentType.JSON)
            .body(logbookLifeCyclesUnitParametersStart.toString())
            .when()
            .put(LIFE_UNIT_ID_URI,
                logbookLifeCyclesUnitParametersStart.getParameterValue(LogbookParameterName.eventIdentifierProcess),
                "bad_id")
            .then()
            .statusCode(Status.BAD_REQUEST.getStatusCode());

        // Commit the created unit lifeCycle
        given()
            .header(GlobalDataRest.X_EVENT_STATUS, LifeCycleStatusCode.LIFE_CYCLE_COMMITTED)
            .contentType(ContentType.JSON)
            .when()
            .put(LIFE_UNIT_ID_URI,
                logbookLifeCyclesUnitParametersStart.getParameterValue(LogbookParameterName.eventIdentifierProcess),
                logbookLifeCyclesUnitParametersStart.getParameterValue(LogbookParameterName.objectIdentifier))
            .then()
            .statusCode(Status.OK.getStatusCode());

        // Test direct access
        Select select = new Select();
        select.setQuery(QueryHelper.eq(LogbookLifeCycleMongoDbName.objectIdentifier.getDbname(),
            logbookLifeCyclesUnitParametersStart.getParameterValue(LogbookParameterName.objectIdentifier)));

        given()
            .contentType(ContentType.JSON)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .header(GlobalDataRest.X_EVENT_STATUS, LifeCycleStatusCode.LIFE_CYCLE_COMMITTED.toString())
            .body(select.getFinalSelect())
            .when()
            .get("/unitlifecycles/" +
                logbookLifeCyclesUnitParametersStart.getParameterValue(LogbookParameterName.objectIdentifier))
            .then()
            .statusCode(Status.OK.getStatusCode());


        given()
            .contentType(ContentType.JSON)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .body(new Select().getFinalSelect())
            .when()
            .get(LIFE_UNIT_URI,
                logbookLifeCyclesUnitParametersStart
                    .getParameterValue(LogbookParameterName.eventIdentifierProcess))
            .then()
            .statusCode(Status.OK.getStatusCode());

    }

    @Test
    public final void given_lifeCycleUnitWithoutMandotoryParams_when_create_thenReturn_BAD_RESUEST() {
        final GUID guidTest = GUIDFactory.newWriteLogbookGUID(0);

        logbookLifeCyclesUnitParametersBAD.putParameterValue(LogbookParameterName.objectIdentifier,
            guidTest.toString());
        given()
            .contentType(ContentType.JSON)
            .body(logbookLifeCyclesUnitParametersBAD.toString())
            .when()
            .post(LIFE_UNIT_ID_URI,
                logbookLifeCyclesUnitParametersBAD.getParameterValue(LogbookParameterName.eventIdentifierProcess),
                logbookLifeCyclesUnitParametersBAD.getParameterValue(LogbookParameterName.objectIdentifier))
            .then()
            .statusCode(Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    public final void given_lifeCycleUnitWithoutMandotoryParams_when_Update_thenReturn_BAD_RESUEST() {
        final GUID guidTest = GUIDFactory.newWriteLogbookGUID(0);

        logbookLifeCyclesUnitParametersBAD.putParameterValue(LogbookParameterName.objectIdentifier,
            guidTest.toString());
        given()
            .contentType(ContentType.JSON)
            .body(logbookLifeCyclesUnitParametersBAD.toString())
            .when()
            .header(GlobalDataRest.X_EVENT_STATUS, LifeCycleStatusCode.LIFE_CYCLE_IN_PROCESS.toString())
            .put(LIFE_UNIT_ID_URI,
                logbookLifeCyclesUnitParametersBAD.getParameterValue(LogbookParameterName.eventIdentifierProcess),
                logbookLifeCyclesUnitParametersBAD.getParameterValue(LogbookParameterName.objectIdentifier))
            .then()
            .statusCode(Status.BAD_REQUEST.getStatusCode());
    }


    @Test
    public final void deleteUnit_PassTheRightArgument_ResponseOK() {
        // Delete OK
        logbookLifeCyclesUnitParametersStart.putParameterValue(LogbookParameterName.eventDateTime,
            LocalDateUtil.now().toString());
        logbookLifeCyclesUnitParametersStart.putParameterValue(LogbookParameterName.agentIdentifier,
            ServerIdentity.getInstance().getJsonIdentity());

        logbookLifeCyclesUnitParametersStart.putParameterValue(LogbookParameterName.eventType, "event");
        logbookLifeCyclesUnitParametersStart.setTypeProcess(LogbookTypeProcess.INGEST);
        logbookLifeCyclesUnitParametersStart.putParameterValue(LogbookParameterName.outcomeDetail, "outcomeDetail");
        logbookLifeCyclesUnitParametersStart.putParameterValue(LogbookParameterName.outcomeDetailMessage,
            "outcomeDetailMessage");
        logbookLifeCyclesUnitParametersStart.putParameterValue(LogbookParameterName.eventDateTime,
            LocalDateUtil.now().toString());
        logbookLifeCyclesUnitParametersStart.putParameterValue(LogbookParameterName.agentIdentifier,
            ServerIdentity.getInstance().getJsonIdentity());
        given()
            .contentType(ContentType.JSON)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .body(logbookLifeCyclesUnitParametersStart.toString())
            .when()
            .post(LIFE_UNIT_ID_URI,
                logbookLifeCyclesUnitParametersStart.getParameterValue(LogbookParameterName.eventIdentifierProcess),
                logbookLifeCyclesUnitParametersStart.getParameterValue(LogbookParameterName.objectIdentifier))
            .then()
            .statusCode(Status.CREATED.getStatusCode());
        given()
            .when()
            .delete(LIFE_UNIT_ID_URI,
                logbookLifeCyclesUnitParametersStart.getParameterValue(LogbookParameterName.eventIdentifierProcess),
                logbookLifeCyclesUnitParametersStart.getParameterValue(LogbookParameterName.objectIdentifier))
            .then()
            .statusCode(Status.OK.getStatusCode());
    }

    @Test
    public final void deleteObjectGroup_PassWrongAgument_thenNotFound() {
        // Delete KO
        LogbookLifeCycleObjectGroupParametersStart.putParameterValue(LogbookParameterName.eventDateTime,
            LocalDateUtil.now().toString());
        LogbookLifeCycleObjectGroupParametersStart.putParameterValue(LogbookParameterName.agentIdentifier,
            ServerIdentity.getInstance().getJsonIdentity());
        given()
            .contentType(ContentType.JSON)
            .when()
            .delete(LIFE_UNIT_ID_URI,
                LogbookLifeCycleObjectGroupParametersStart
                    .getParameterValue(LogbookParameterName.eventIdentifierProcess),
                "notExists")
            .then()
            .statusCode(Status.NOT_FOUND.getStatusCode());
    }

    @Test
    public final void given_lifeCycleUnit_when_update_notfound() {
        // update notFound
        logbookLifeCyclesUnitParametersUpdate.putParameterValue(LogbookParameterName.eventType, "event");
        logbookLifeCyclesUnitParametersUpdate.setTypeProcess(LogbookTypeProcess.INGEST);
        logbookLifeCyclesUnitParametersUpdate.putParameterValue(LogbookParameterName.outcomeDetail, "outcomeDetail");
        logbookLifeCyclesUnitParametersUpdate.putParameterValue(LogbookParameterName.outcomeDetailMessage,
            "outcomeDetailMessage");
        logbookLifeCyclesUnitParametersUpdate.putParameterValue(LogbookParameterName.eventDateTime,
            LocalDateUtil.now().toString());
        logbookLifeCyclesUnitParametersUpdate.putParameterValue(LogbookParameterName.agentIdentifier,
            ServerIdentity.getInstance().getJsonIdentity());


        given()
            .contentType(ContentType.JSON)
            .body(logbookLifeCyclesUnitParametersUpdate.toString())
            .header(GlobalDataRest.X_EVENT_STATUS, LifeCycleStatusCode.LIFE_CYCLE_IN_PROCESS.toString())
            .when()
            .put(LIFE_UNIT_ID_URI,
                logbookLifeCyclesUnitParametersUpdate.getParameterValue(LogbookParameterName.eventIdentifierProcess),
                logbookLifeCyclesUnitParametersUpdate.getParameterValue(LogbookParameterName.objectIdentifier))
            .then()
            .statusCode(Status.NOT_FOUND.getStatusCode());
    }

    @Test
    public final void deleteObjectGroup_PassTheRightArgument_ResponseOK() {
        // Delete OK
        LogbookLifeCycleObjectGroupParametersStart.putParameterValue(LogbookParameterName.eventDateTime,
            LocalDateUtil.now().toString());
        LogbookLifeCycleObjectGroupParametersStart.putParameterValue(LogbookParameterName.agentIdentifier,
            ServerIdentity.getInstance().getJsonIdentity());

        LogbookLifeCycleObjectGroupParametersStart.putParameterValue(LogbookParameterName.eventType, "event");
        LogbookLifeCycleObjectGroupParametersStart.setTypeProcess(LogbookTypeProcess.INGEST);
        LogbookLifeCycleObjectGroupParametersStart.putParameterValue(LogbookParameterName.outcomeDetail,
            "outcomeDetail");
        LogbookLifeCycleObjectGroupParametersStart.putParameterValue(LogbookParameterName.outcomeDetailMessage,
            "outcomeDetailMessage");
        LogbookLifeCycleObjectGroupParametersStart.putParameterValue(LogbookParameterName.eventDateTime,
            LocalDateUtil.now().toString());
        LogbookLifeCycleObjectGroupParametersStart.putParameterValue(LogbookParameterName.agentIdentifier,
            ServerIdentity.getInstance().getJsonIdentity());
        given()
            .contentType(ContentType.JSON)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .body(LogbookLifeCycleObjectGroupParametersStart.toString())
            .when()
            .post(LIFE_OG_ID_URI,
                LogbookLifeCycleObjectGroupParametersStart
                    .getParameterValue(LogbookParameterName.eventIdentifierProcess),
                LogbookLifeCycleObjectGroupParametersStart.getParameterValue(LogbookParameterName.objectIdentifier))
            .then()
            .statusCode(Status.CREATED.getStatusCode());

        given()
            .when()
            .delete(LIFE_OG_ID_URI,
                LogbookLifeCycleObjectGroupParametersStart
                    .getParameterValue(LogbookParameterName.eventIdentifierProcess),
                LogbookLifeCycleObjectGroupParametersStart.getParameterValue(LogbookParameterName.objectIdentifier))
            .then()
            .statusCode(Status.OK.getStatusCode());
    }

    @Test
    public final void given_lifeCycleObjectGroupWithoutMandotoryParams_when_delete_thenReturn_BAD_RESUEST() {
        // Delete OK

        given()
            .when()
            .delete(LIFE_OG_ID_URI, "id", "f12")
            .then()
            .statusCode(Status.NOT_FOUND.getStatusCode());
    }

    @Test
    public void testGetUnitLifeCycleByIdThenOkWhenLogbookNotFoundException()
        throws LogbookDatabaseException, LogbookNotFoundException, InvalidParseOperationException,
        InvalidCreateOperationException {

        Select select = new Select();
        select.setQuery(QueryHelper.eq(LogbookLifeCycleMongoDbName.objectIdentifier.getDbname(),
            FAKE_UNIT_LF_ID));
        JsonNode query = select.getFinalSelect();

        given().contentType(ContentType.JSON).body(query)
            .param("id_lc", FAKE_UNIT_LF_ID).expect().statusCode(Status.NOT_FOUND.getStatusCode())
            .when().get(SELECT_UNIT_BY_ID_URI);
    }

    @Test
    public void testGetObjectGroupLifeCycleByIdThenOkWhenLogbookNotFoundException()
        throws LogbookDatabaseException, LogbookNotFoundException {
        given().contentType(ContentType.JSON)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID).body(new Select().getFinalSelect())
            .param("id_lc", FAKE_OBG_LF_ID).expect().statusCode(Status.NOT_FOUND.getStatusCode()).when()
            .get(SELECT_OBG_BY_ID_URI);
    }

    @RunWithCustomExecutor
    @Test
    public final void given_lifeCycleUnit_bulk_raw_when_create_thenReturn_created()
        throws InvalidParseOperationException, FileNotFoundException {
        List<JsonNode> lfcGotList = new ArrayList<>();
        lfcGotList.add(JsonHandler.getFromInputStream(
            PropertiesUtils.getResourceAsStream("lfc_unit_raw_aeaqaaaaaaef6ys5absnuala7tya75iaaacq.json")));
        lfcGotList.add(JsonHandler.getFromInputStream(
            PropertiesUtils.getResourceAsStream("lfc_unit_raw_aeaqaaaaaageqltuabfg2ala73ny3zaaaacq.json")));

        given()
            .contentType(ContentType.JSON)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .body(lfcGotList)
            .when()
            .post("/unitlifecycles/bulk/raw")
            .then()
            .statusCode(Status.CREATED.getStatusCode());

        given()
            .contentType(ContentType.JSON)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .body(lfcGotList)
            .when()
            .post("/unitlifecycles/bulk/raw")
            .then()
            .statusCode(Status.CREATED.getStatusCode());

        lfcGotList.add(JsonHandler.getFromInputStream(
            PropertiesUtils.getResourceAsStream("lfc_unit_raw_aeaqaaaaaageqltuabfg2ala73ny3zaaaacq_diff.json")));
        given()
            .contentType(ContentType.JSON)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .body(lfcGotList)
            .when()
            .post("/unitlifecycles/bulk/raw")
            .then()
            .statusCode(Status.CREATED.getStatusCode());
    }

}
