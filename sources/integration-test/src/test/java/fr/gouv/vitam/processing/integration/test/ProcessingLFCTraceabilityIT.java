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
package fr.gouv.vitam.processing.integration.test;


import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;
import com.mongodb.client.MongoDatabase;
import fr.gouv.vitam.common.CommonMediaType;
import fr.gouv.vitam.common.DataLoader;
import fr.gouv.vitam.common.LocalDateUtil;
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.VitamRuleRunner;
import fr.gouv.vitam.common.VitamServerRunner;
import fr.gouv.vitam.common.client.VitamClientFactory;
import fr.gouv.vitam.common.client.VitamClientFactoryInterface;
import fr.gouv.vitam.common.database.builder.query.Query;
import fr.gouv.vitam.common.database.builder.query.QueryHelper;
import fr.gouv.vitam.common.database.builder.request.single.Select;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.format.identification.FormatIdentifierFactory;
import fr.gouv.vitam.common.guid.GUID;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.guid.GUIDReader;
import fr.gouv.vitam.common.i18n.VitamLogbookMessages;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.SysErrLogger;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.ProcessAction;
import fr.gouv.vitam.common.model.ProcessState;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.functional.administration.rest.AdminManagementMain;
import fr.gouv.vitam.logbook.common.exception.LogbookClientAlreadyExistsException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientBadRequestException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientServerException;
import fr.gouv.vitam.logbook.common.model.TraceabilityEvent;
import fr.gouv.vitam.logbook.common.model.TraceabilityType;
import fr.gouv.vitam.logbook.common.parameters.Contexts;
import fr.gouv.vitam.logbook.common.parameters.LogbookLifeCycleObjectGroupParameters;
import fr.gouv.vitam.logbook.common.parameters.LogbookLifeCycleUnitParameters;
import fr.gouv.vitam.logbook.common.parameters.LogbookOperationParameters;
import fr.gouv.vitam.logbook.common.parameters.LogbookParametersFactory;
import fr.gouv.vitam.logbook.common.parameters.LogbookTypeProcess;
import fr.gouv.vitam.logbook.common.server.database.collections.LogbookCollections;
import fr.gouv.vitam.logbook.common.server.database.collections.LogbookDocument;
import fr.gouv.vitam.logbook.lifecycles.client.LogbookLifeCyclesClient;
import fr.gouv.vitam.logbook.lifecycles.client.LogbookLifeCyclesClientFactory;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClient;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClientFactory;
import fr.gouv.vitam.logbook.rest.LogbookMain;
import fr.gouv.vitam.metadata.core.database.collections.MetadataCollections;
import fr.gouv.vitam.metadata.rest.MetadataMain;
import fr.gouv.vitam.processing.common.ProcessingEntry;
import fr.gouv.vitam.processing.common.model.ProcessWorkflow;
import fr.gouv.vitam.processing.common.parameter.WorkerParameterName;
import fr.gouv.vitam.processing.engine.core.monitoring.ProcessMonitoringImpl;
import fr.gouv.vitam.processing.management.client.ProcessingManagementClient;
import fr.gouv.vitam.processing.management.client.ProcessingManagementClientFactory;
import fr.gouv.vitam.processing.management.rest.ProcessManagementMain;
import fr.gouv.vitam.storage.engine.client.StorageClientFactory;
import fr.gouv.vitam.storage.engine.server.rest.StorageMain;
import fr.gouv.vitam.storage.offers.common.rest.DefaultOfferMain;
import fr.gouv.vitam.worker.server.rest.WorkerMain;
import fr.gouv.vitam.workspace.client.WorkspaceClient;
import fr.gouv.vitam.workspace.client.WorkspaceClientFactory;
import fr.gouv.vitam.workspace.rest.WorkspaceMain;
import io.restassured.RestAssured;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.core.Response.Status;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

import static io.restassured.RestAssured.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Processing integration test
 */
public class ProcessingLFCTraceabilityIT extends VitamRuleRunner {

    @ClassRule
    public static VitamServerRunner runner =
        new VitamServerRunner(ProcessingLFCTraceabilityIT.class, mongoRule.getMongoDatabase().getName(),
            elasticsearchRule.getClusterName(),
            Sets.newHashSet(
                MetadataMain.class,
                WorkerMain.class,
                AdminManagementMain.class,
                LogbookMain.class,
                WorkspaceMain.class,
                ProcessManagementMain.class,
                StorageMain.class,
                DefaultOfferMain.class
            ));

    private static final long SLEEP_TIME = 20l;
    private static final long NB_TRY = 18000;
    private static final int MAX_ENTRIES = 100000;
    private static final Integer TENANT_ID = 0;


    private static final String SIP_FOLDER = "SIP";
    private static final String METADATA_PATH = "/metadata/v1";
    private static final String PROCESSING_PATH = "/processing/v1";
    private static final String WORKER_PATH = "/worker/v1";
    private static final String WORKSPACE_PATH = "/workspace/v1";
    private static final String LOGBOOK_PATH = "/logbook/v1";


    private static String CONFIG_SIEGFRIED_PATH = "";


    private WorkspaceClient workspaceClient;
    private ProcessingManagementClient processingClient;
    private static ProcessMonitoringImpl processMonitoring;

    private static String WORFKLOW_NAME = "PROCESS_SIP_UNITARY";

    private static String SIP_COMPLEX_RULES = "integration-processing/3_UNITS_2_GOTS.zip";

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        CONFIG_SIEGFRIED_PATH =
            PropertiesUtils.getResourcePath("integration-processing/format-identifiers.conf").toString();
        FormatIdentifierFactory.getInstance().changeConfigurationFile(CONFIG_SIEGFRIED_PATH);

        new DataLoader("integration-processing").prepareData();

        processMonitoring = ProcessMonitoringImpl.getInstance();

        checkServerStatus();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        StorageClientFactory storageClientFactory = StorageClientFactory.getInstance();
        storageClientFactory.setVitamClientType(VitamClientFactoryInterface.VitamClientType.PRODUCTION);

        runAfter();
        VitamClientFactory.resetConnections();
    }

    @Before
    public void before() {
        workspaceClient = WorkspaceClientFactory.getInstance().getClient();
        processingClient = ProcessingManagementClientFactory.getInstance().getClient();
    }

    @After
    public void afterTest() throws Exception {
        MongoDatabase db = mongoRule.getMongoDatabase();
        db.getCollection("Unit").drop();
        db.getCollection("ObjectGroup").drop();
        db.getCollection("LogbookOperation").drop();
        db.getCollection("LogbookLifeCycleUnit").drop();
        db.getCollection("LogbookLifeCycleObjectGroup").drop();

        for (LogbookCollections collection : LogbookCollections.values()) {
            if (collection.getEsClient() != null) {
                collection.getEsClient().deleteIndex(collection, TENANT_ID);
                collection.getEsClient().addIndex(collection, TENANT_ID);
            }
        }
        for (MetadataCollections collection : MetadataCollections.values()) {
            if (collection.getEsClient() != null) {
                collection.getEsClient().deleteIndex(collection, TENANT_ID);
                collection.getEsClient().addIndex(collection, TENANT_ID);
            }
        }
    }

    private static void checkServerStatus() {
        RestAssured.port = runner.PORT_SERVICE_PROCESSING;
        RestAssured.basePath = PROCESSING_PATH;

        get("/status").then().statusCode(Status.NO_CONTENT.getStatusCode());

        RestAssured.port = runner.PORT_SERVICE_WORKSPACE;
        RestAssured.basePath = WORKSPACE_PATH;
        get("/status").then().statusCode(Status.NO_CONTENT.getStatusCode());

        RestAssured.port = runner.PORT_SERVICE_METADATA;
        RestAssured.basePath = METADATA_PATH;
        get("/status").then().statusCode(Status.NO_CONTENT.getStatusCode());

        RestAssured.port = runner.PORT_SERVICE_WORKER;
        RestAssured.basePath = WORKER_PATH;
        get("/status").then().statusCode(Status.NO_CONTENT.getStatusCode());

        RestAssured.port = runner.PORT_SERVICE_LOGBOOK;
        RestAssured.basePath = LOGBOOK_PATH;
        get("/status").then().statusCode(Status.NO_CONTENT.getStatusCode());
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflowUnitLfcTraceability_shouldGetWarningWhenDbIsEmpty() throws Exception {

        // Given (empty db)
        ProcessingIT.prepareVitamSession();
        // When
        String traceabilityOperation = launchLogbookLFC(0, Contexts.UNIT_LFC_TRACEABILITY);

        // Then
        assertCompletedWithStatus(traceabilityOperation, StatusCode.WARNING);

        TraceabilityEvent traceabilityEvent = getTraceabilityEvent(traceabilityOperation);
        assertThat(traceabilityEvent).isNull();
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflowUnitLfcTraceability_shouldGetWarnOnFirstTraceabilityWithFreshData()
        throws Exception {

        // Given
        launchIngest();

        // When
        String traceabilityOperation = launchLogbookLFC(300, Contexts.UNIT_LFC_TRACEABILITY);

        // Then
        assertCompletedWithStatus(traceabilityOperation, StatusCode.WARNING);

        TraceabilityEvent traceabilityEvent = getTraceabilityEvent(traceabilityOperation);
        assertThat(traceabilityEvent).isNull();
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflowUnitLfcTraceability_shouldGetOkOnFirstTraceabilityWithOldData() throws Exception {

        // Given
        int temporizationDelayInSeconds = 2;
        launchIngest();
        Thread.sleep(temporizationDelayInSeconds * 1000);

        // When
        LocalDateTime beforeTraceability = LocalDateUtil.now();
        String containerName = launchLogbookLFC(temporizationDelayInSeconds, Contexts.UNIT_LFC_TRACEABILITY);
        LocalDateTime afterTraceability = LocalDateUtil.now();

        // Then
        assertCompletedWithStatus(containerName, StatusCode.OK);

        TraceabilityEvent traceabilityEvent = getTraceabilityEvent(containerName);

        assertThat(traceabilityEvent.getLogType()).isEqualTo(TraceabilityType.UNIT_LIFECYCLE);
        assertThat(traceabilityEvent.getMaxEntriesReached()).isFalse();
        assertThat(traceabilityEvent.getStartDate()).isEqualTo("1970-01-01T00:00:00.000");
        assertThatDateIsBetween(traceabilityEvent.getEndDate(),
            beforeTraceability.minusSeconds(temporizationDelayInSeconds),
            afterTraceability.minusSeconds(temporizationDelayInSeconds));
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflowUnitLfcTraceability_shouldGetWarnOnNextTraceabilityWithFreshData() throws Exception {

        // Given / When

        // First ingest + traceability
        launchIngest();
        String traceabilityOperation1 = launchLogbookLFC(0, Contexts.UNIT_LFC_TRACEABILITY);


        // Second ingest + traceability
        launchIngest();
        String traceabilityOperation2 = launchLogbookLFC(300, Contexts.UNIT_LFC_TRACEABILITY);

        // Then

        assertCompletedWithStatus(traceabilityOperation1, StatusCode.OK);
        assertCompletedWithStatus(traceabilityOperation2, StatusCode.WARNING);

        TraceabilityEvent traceabilityEvent2 = getTraceabilityEvent(traceabilityOperation2);
        assertThat(traceabilityEvent2).isNull();
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflowUnitLfcTraceability_shouldGetOkOnNextTraceabilityWithOldData() throws Exception {

        // Given / When

        // First ingest + traceability
        int temporizationDelayInSeconds = 2;
        launchIngest();
        Thread.sleep(temporizationDelayInSeconds * 1000);

        LocalDateTime beforeTraceability1 = LocalDateUtil.now();
        String traceabilityOperation1 = launchLogbookLFC(temporizationDelayInSeconds, Contexts.UNIT_LFC_TRACEABILITY);
        LocalDateTime afterTraceability1 = LocalDateUtil.now();

        // Second ingest + traceability
        launchIngest();
        Thread.sleep(temporizationDelayInSeconds * 1000);

        LocalDateTime beforeTraceability2 = LocalDateUtil.now();
        String traceabilityOperation2 = launchLogbookLFC(temporizationDelayInSeconds, Contexts.UNIT_LFC_TRACEABILITY);
        LocalDateTime afterTraceability2 = LocalDateUtil.now();

        // Then

        assertCompletedWithStatus(traceabilityOperation1, StatusCode.OK);
        assertCompletedWithStatus(traceabilityOperation2, StatusCode.OK);

        TraceabilityEvent traceabilityEvent1 = getTraceabilityEvent(traceabilityOperation1);
        TraceabilityEvent traceabilityEvent2 = getTraceabilityEvent(traceabilityOperation2);

        assertThat(traceabilityEvent1.getStartDate()).isEqualTo("1970-01-01T00:00:00.000");
        assertThatDateIsBetween(traceabilityEvent1.getEndDate(),
            beforeTraceability1.minusSeconds(temporizationDelayInSeconds),
            afterTraceability1.minusSeconds(temporizationDelayInSeconds));

        assertThat(traceabilityEvent2.getStartDate()).isEqualTo(traceabilityEvent1.getEndDate());
        assertThatDateIsBetween(traceabilityEvent2.getEndDate(),
            beforeTraceability2.minusSeconds(temporizationDelayInSeconds),
            afterTraceability2.minusSeconds(temporizationDelayInSeconds));
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflowUnitLfcTraceability_shouldGetWarnOnFreshGotUpdate() throws Exception {

        // Given / When

        // First ingest + traceability
        launchIngest();
        String traceabilityOperation1 = launchLogbookLFC(0, Contexts.UNIT_LFC_TRACEABILITY);

        // Update Got + traceability
        changeOneGotLFC();
        String traceabilityOperation2 = launchLogbookLFC(300, Contexts.UNIT_LFC_TRACEABILITY);

        // Then

        assertCompletedWithStatus(traceabilityOperation1, StatusCode.OK);
        assertCompletedWithStatus(traceabilityOperation2, StatusCode.WARNING);

        TraceabilityEvent traceabilityEvent2 = getTraceabilityEvent(traceabilityOperation2);
        assertThat(traceabilityEvent2).isNull();
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflowUnitLfcTraceability_shouldGetOkOnOldGotUpdate() throws Exception {

        // Given / When

        // First ingest + traceability
        int temporizationDelayInSeconds = 2;
        launchIngest();
        Thread.sleep(temporizationDelayInSeconds * 1000);

        LocalDateTime beforeTraceability1 = LocalDateUtil.now();
        String traceabilityOperation1 = launchLogbookLFC(temporizationDelayInSeconds, Contexts.UNIT_LFC_TRACEABILITY);
        LocalDateTime afterTraceability1 = LocalDateUtil.now();


        // Update Got + traceability
        changeOneUnitLFC();
        Thread.sleep(temporizationDelayInSeconds * 1000);

        LocalDateTime beforeTraceability2 = LocalDateUtil.now();
        String traceabilityOperation2 = launchLogbookLFC(temporizationDelayInSeconds, Contexts.UNIT_LFC_TRACEABILITY);
        LocalDateTime afterTraceability2 = LocalDateUtil.now();

        // Then

        assertCompletedWithStatus(traceabilityOperation1, StatusCode.OK);
        assertCompletedWithStatus(traceabilityOperation2, StatusCode.OK);

        TraceabilityEvent traceabilityEvent1 = getTraceabilityEvent(traceabilityOperation1);
        TraceabilityEvent traceabilityEvent2 = getTraceabilityEvent(traceabilityOperation2);

        assertThat(traceabilityEvent1.getStartDate()).isEqualTo("1970-01-01T00:00:00.000");
        assertThatDateIsBetween(traceabilityEvent1.getEndDate(),
            beforeTraceability1.minusSeconds(temporizationDelayInSeconds),
            afterTraceability1.minusSeconds(temporizationDelayInSeconds));

        assertThat(traceabilityEvent2.getStartDate()).isEqualTo(traceabilityEvent1.getEndDate());
        assertThatDateIsBetween(traceabilityEvent2.getEndDate(),
            beforeTraceability2.minusSeconds(temporizationDelayInSeconds),
            afterTraceability2.minusSeconds(temporizationDelayInSeconds));
    }



    @RunWithCustomExecutor
    @Test
    public void testWorkflowObjectGroupLfcTraceability_shouldGetWarningWhenDbIsEmpty() throws Exception {

        ProcessingIT.prepareVitamSession();
        // Given (empty db)

        // When
        String traceabilityOperation = launchLogbookLFC(0, Contexts.OBJECTGROUP_LFC_TRACEABILITY);

        // Then
        assertCompletedWithStatus(traceabilityOperation, StatusCode.WARNING);

        TraceabilityEvent traceabilityEvent = getTraceabilityEvent(traceabilityOperation);
        assertThat(traceabilityEvent).isNull();
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflowObjectGroupLfcTraceability_shouldGetWarnOnFirstTraceabilityWithFreshData()
        throws Exception {

        // Given
        launchIngest();

        // When
        String traceabilityOperation = launchLogbookLFC(300, Contexts.OBJECTGROUP_LFC_TRACEABILITY);

        // Then
        assertCompletedWithStatus(traceabilityOperation, StatusCode.WARNING);

        TraceabilityEvent traceabilityEvent = getTraceabilityEvent(traceabilityOperation);
        assertThat(traceabilityEvent).isNull();
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflowObjectGroupLfcTraceability_shouldGetOkOnFirstTraceabilityWithOldData() throws Exception {

        // Given
        int temporizationDelayInSeconds = 2;
        launchIngest();
        Thread.sleep(temporizationDelayInSeconds * 1000);

        // When
        LocalDateTime beforeTraceability = LocalDateUtil.now();
        String containerName = launchLogbookLFC(temporizationDelayInSeconds, Contexts.OBJECTGROUP_LFC_TRACEABILITY);
        LocalDateTime afterTraceability = LocalDateUtil.now();

        // Then
        assertCompletedWithStatus(containerName, StatusCode.OK);

        TraceabilityEvent traceabilityEvent = getTraceabilityEvent(containerName);

        assertThat(traceabilityEvent.getLogType()).isEqualTo(TraceabilityType.OBJECTGROUP_LIFECYCLE);
        assertThat(traceabilityEvent.getMaxEntriesReached()).isFalse();
        assertThat(traceabilityEvent.getStartDate()).isEqualTo("1970-01-01T00:00:00.000");
        assertThatDateIsBetween(traceabilityEvent.getEndDate(),
            beforeTraceability.minusSeconds(temporizationDelayInSeconds),
            afterTraceability.minusSeconds(temporizationDelayInSeconds));
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflowObjectGroupLfcTraceability_shouldGetWarnOnNextTraceabilityWithFreshData() throws Exception {

        // Given / When

        // First ingest + traceability
        launchIngest();
        String traceabilityOperation1 = launchLogbookLFC(0, Contexts.OBJECTGROUP_LFC_TRACEABILITY);


        // Second ingest + traceability
        launchIngest();
        String traceabilityOperation2 = launchLogbookLFC(300, Contexts.OBJECTGROUP_LFC_TRACEABILITY);

        // Then

        assertCompletedWithStatus(traceabilityOperation1, StatusCode.OK);
        assertCompletedWithStatus(traceabilityOperation2, StatusCode.WARNING);

        TraceabilityEvent traceabilityEvent2 = getTraceabilityEvent(traceabilityOperation2);
        assertThat(traceabilityEvent2).isNull();
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflowObjectGroupLfcTraceability_shouldGetOkOnNextTraceabilityWithOldData() throws Exception {

        // Given / When

        // First ingest + traceability
        int temporizationDelayInSeconds = 2;
        launchIngest();
        Thread.sleep(temporizationDelayInSeconds * 1000);

        LocalDateTime beforeTraceability1 = LocalDateUtil.now();
        String traceabilityOperation1 =
            launchLogbookLFC(temporizationDelayInSeconds, Contexts.OBJECTGROUP_LFC_TRACEABILITY);
        LocalDateTime afterTraceability1 = LocalDateUtil.now();

        // Second ingest + traceability
        launchIngest();
        Thread.sleep(temporizationDelayInSeconds * 1000);

        LocalDateTime beforeTraceability2 = LocalDateUtil.now();
        String traceabilityOperation2 =
            launchLogbookLFC(temporizationDelayInSeconds, Contexts.OBJECTGROUP_LFC_TRACEABILITY);
        LocalDateTime afterTraceability2 = LocalDateUtil.now();

        // Then

        assertCompletedWithStatus(traceabilityOperation1, StatusCode.OK);
        assertCompletedWithStatus(traceabilityOperation2, StatusCode.OK);

        TraceabilityEvent traceabilityEvent1 = getTraceabilityEvent(traceabilityOperation1);
        TraceabilityEvent traceabilityEvent2 = getTraceabilityEvent(traceabilityOperation2);

        assertThat(traceabilityEvent1.getStartDate()).isEqualTo("1970-01-01T00:00:00.000");
        assertThatDateIsBetween(traceabilityEvent1.getEndDate(),
            beforeTraceability1.minusSeconds(temporizationDelayInSeconds),
            afterTraceability1.minusSeconds(temporizationDelayInSeconds));

        assertThat(traceabilityEvent2.getStartDate()).isEqualTo(traceabilityEvent1.getEndDate());
        assertThatDateIsBetween(traceabilityEvent2.getEndDate(),
            beforeTraceability2.minusSeconds(temporizationDelayInSeconds),
            afterTraceability2.minusSeconds(temporizationDelayInSeconds));
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflowObjectGroupLfcTraceability_shouldGetWarnOnFreshGotUpdate() throws Exception {

        // Given / When

        // First ingest + traceability
        launchIngest();
        String traceabilityOperation1 = launchLogbookLFC(0, Contexts.OBJECTGROUP_LFC_TRACEABILITY);

        // Update Got + traceability
        changeOneGotLFC();
        String traceabilityOperation2 = launchLogbookLFC(300, Contexts.OBJECTGROUP_LFC_TRACEABILITY);

        // Then

        assertCompletedWithStatus(traceabilityOperation1, StatusCode.OK);
        assertCompletedWithStatus(traceabilityOperation2, StatusCode.WARNING);

        TraceabilityEvent traceabilityEvent2 = getTraceabilityEvent(traceabilityOperation2);
        assertThat(traceabilityEvent2).isNull();
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflowObjectGroupLfcTraceability_shouldGetOkOnOldGotUpdate() throws Exception {

        // Given / When

        // First ingest + traceability
        int temporizationDelayInSeconds = 2;
        launchIngest();
        Thread.sleep(temporizationDelayInSeconds * 1000);

        LocalDateTime beforeTraceability1 = LocalDateUtil.now();
        String traceabilityOperation1 =
            launchLogbookLFC(temporizationDelayInSeconds, Contexts.OBJECTGROUP_LFC_TRACEABILITY);
        LocalDateTime afterTraceability1 = LocalDateUtil.now();


        // Update Got + traceability
        changeOneGotLFC();
        Thread.sleep(temporizationDelayInSeconds * 1000);

        LocalDateTime beforeTraceability2 = LocalDateUtil.now();
        String traceabilityOperation2 =
            launchLogbookLFC(temporizationDelayInSeconds, Contexts.OBJECTGROUP_LFC_TRACEABILITY);
        LocalDateTime afterTraceability2 = LocalDateUtil.now();

        // Then

        assertCompletedWithStatus(traceabilityOperation1, StatusCode.OK);
        assertCompletedWithStatus(traceabilityOperation2, StatusCode.OK);

        TraceabilityEvent traceabilityEvent1 = getTraceabilityEvent(traceabilityOperation1);
        TraceabilityEvent traceabilityEvent2 = getTraceabilityEvent(traceabilityOperation2);

        assertThat(traceabilityEvent1.getStartDate()).isEqualTo("1970-01-01T00:00:00.000");
        assertThatDateIsBetween(traceabilityEvent1.getEndDate(),
            beforeTraceability1.minusSeconds(temporizationDelayInSeconds),
            afterTraceability1.minusSeconds(temporizationDelayInSeconds));

        assertThat(traceabilityEvent2.getStartDate()).isEqualTo(traceabilityEvent1.getEndDate());
        assertThatDateIsBetween(traceabilityEvent2.getEndDate(),
            beforeTraceability2.minusSeconds(temporizationDelayInSeconds),
            afterTraceability2.minusSeconds(temporizationDelayInSeconds));
    }

    private void wait(String operationId) {
        int nbTry = 0;
        while (!processingClient.isOperationCompleted(operationId)) {
            try {
                Thread.sleep(SLEEP_TIME);
            } catch (InterruptedException e) {
                SysErrLogger.FAKE_LOGGER.ignoreLog(e);
            }
            if (nbTry == NB_TRY)
                break;
            nbTry++;
        }
    }

    private void createLogbookOperation(GUID operationId, GUID objectId)
        throws LogbookClientBadRequestException, LogbookClientAlreadyExistsException, LogbookClientServerException {
        createLogbookOperation(operationId, objectId, null);
    }

    private void createLogbookOperation(GUID operationId, GUID objectId, String type)
        throws LogbookClientBadRequestException, LogbookClientAlreadyExistsException, LogbookClientServerException {

        final LogbookOperationsClient logbookClient = LogbookOperationsClientFactory.getInstance().getClient();
        if (type == null) {
            type = "Process_SIP_unitary";
        }
        final LogbookOperationParameters initParameters = LogbookParametersFactory.newLogbookOperationParameters(
            operationId, type, objectId,
            "Process_SIP_unitary".equals(type) ? LogbookTypeProcess.INGEST : LogbookTypeProcess.TRACEABILITY,
            StatusCode.STARTED,
            operationId != null ? operationId.toString() : "outcomeDetailMessage",
            operationId);

        logbookClient.create(initParameters);
    }

    private void launchIngest() throws Exception {
        ProcessingIT.prepareVitamSession();
        final GUID operationGuid2 = GUIDFactory.newOperationLogbookGUID(TENANT_ID);
        VitamThreadUtils.getVitamSession().setRequestId(operationGuid2);
        final GUID objectGuid2 = GUIDFactory.newManifestGUID(TENANT_ID);
        final String containerName2 = objectGuid2.getId();
        createLogbookOperation(operationGuid2, objectGuid2);

        final InputStream zipInputStreamSipObject =
            PropertiesUtils.getResourceAsStream(SIP_COMPLEX_RULES);
        workspaceClient.createContainer(containerName2);
        workspaceClient.uncompressObject(containerName2, SIP_FOLDER, CommonMediaType.ZIP,
            zipInputStreamSipObject);

        // call processing
        processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName2, WORFKLOW_NAME);
        final RequestResponse<JsonNode> ret2 =
            processingClient.executeOperationProcess(containerName2, WORFKLOW_NAME,
                Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());
        assertNotNull(ret2);
        assertEquals(Status.ACCEPTED.getStatusCode(), ret2.getStatus());
        wait(containerName2);
        assertCompletedWithStatus(containerName2, StatusCode.OK);
    }

    private String launchLogbookLFC(int temporizationDelayInSeconds, Contexts traceabilityContext)
        throws Exception {
        final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(TENANT_ID);
        VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
        final GUID objectGuid = GUIDFactory.newManifestGUID(TENANT_ID);
        final String containerName = objectGuid.getId();

        createLogbookOperation(operationGuid, objectGuid, traceabilityContext.getEventType());

        workspaceClient.createContainer(containerName);

        // lets call traceability for lifecycles
        ProcessingEntry processingEntry = new ProcessingEntry(containerName, traceabilityContext.getEventType());
        processingEntry.getExtraParams().put(
            WorkerParameterName.lifecycleTraceabilityTemporizationDelayInSeconds.name(),
            Integer.toString(temporizationDelayInSeconds));
        processingEntry.getExtraParams().put(
            WorkerParameterName.lifecycleTraceabilityMaxEntries.name(), Integer.toString(MAX_ENTRIES));

        processingClient.initVitamProcess(traceabilityContext.name(), processingEntry);
        RequestResponse<ItemStatus> ret =
            processingClient.updateOperationActionProcess(ProcessAction.RESUME.getValue(), containerName);
        assertNotNull(ret);

        assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

        wait(containerName);

        return containerName;
    }

    private void changeOneGotLFC() throws Exception {
        try (LogbookLifeCyclesClient logbookLifeCyclesClient = LogbookLifeCyclesClientFactory.getInstance()
            .getClient()) {
            // search for got lfc
            final Query parentQuery = QueryHelper.gte("evDateTime", LocalDateTime.MIN.toString());
            final Select select = new Select();
            select.setQuery(parentQuery);
            select.addOrderByAscFilter("evDateTime");
            RequestResponseOK requestResponseOK = RequestResponseOK.getFromJsonNode(
                logbookLifeCyclesClient.selectObjectGroupLifeCycle(select.getFinalSelect()));
            List<JsonNode> foundObjectGroupLifecycles = requestResponseOK.getResults();
            assertTrue(foundObjectGroupLifecycles != null && foundObjectGroupLifecycles.size() > 0);

            // get one got lfc
            String oneGotLfc = foundObjectGroupLifecycles.get(0).get(LogbookDocument.ID).asText();

            // update got lfc
            final GUID updateLfcGuidStart = GUIDFactory.newOperationLogbookGUID(TENANT_ID);
            LogbookLifeCycleObjectGroupParameters logbookLifeGotUpdateParameters =
                LogbookParametersFactory.newLogbookLifeCycleObjectGroupParameters(updateLfcGuidStart,
                    VitamLogbookMessages.getEventTypeLfc("AUDIT_CHECK_OBJECT"),
                    updateLfcGuidStart,
                    LogbookTypeProcess.AUDIT, StatusCode.KO,
                    VitamLogbookMessages.getOutcomeDetailLfc("AUDIT_CHECK_OBJECT", StatusCode.KO),
                    VitamLogbookMessages.getCodeLfc("AUDIT_CHECK_OBJECT", StatusCode.KO),
                    GUIDReader.getGUID(oneGotLfc));

            logbookLifeCyclesClient.update(logbookLifeGotUpdateParameters);
            logbookLifeCyclesClient.commit(logbookLifeGotUpdateParameters);
        }
    }

    private void changeOneUnitLFC() throws Exception {
        try (LogbookLifeCyclesClient logbookLifeCyclesClient = LogbookLifeCyclesClientFactory.getInstance()
            .getClient()) {
            // search for got lfc
            final Query parentQuery = QueryHelper.gte("evDateTime", LocalDateTime.MIN.toString());
            final Select select = new Select();
            select.setQuery(parentQuery);
            select.addOrderByAscFilter("evDateTime");
            RequestResponseOK requestResponseOK = RequestResponseOK.getFromJsonNode(
                logbookLifeCyclesClient.selectUnitLifeCycle(select.getFinalSelect()));
            List<JsonNode> foundUnitLifecycles = requestResponseOK.getResults();
            assertTrue(foundUnitLifecycles != null && foundUnitLifecycles.size() > 0);

            // get one got lfc
            String oneUnitLfc = foundUnitLifecycles.get(0).get(LogbookDocument.ID).asText();

            // update got lfc
            final GUID updateLfcGuidStart = GUIDFactory.newOperationLogbookGUID(TENANT_ID);
            LogbookLifeCycleUnitParameters logbookLifeUnitUpdateParameters =
                LogbookParametersFactory.newLogbookLifeCycleUnitParameters(updateLfcGuidStart,
                    VitamLogbookMessages.getEventTypeLfc("UNIT_UPDATE"),
                    updateLfcGuidStart,
                    LogbookTypeProcess.UPDATE, StatusCode.OK,
                    VitamLogbookMessages.getOutcomeDetailLfc("UNIT_UPDATE", StatusCode.OK),
                    VitamLogbookMessages.getCodeLfc("UNIT_UPDATE", StatusCode.OK),
                    GUIDReader.getGUID(oneUnitLfc));

            logbookLifeCyclesClient.update(logbookLifeUnitUpdateParameters);
            logbookLifeCyclesClient.commit(logbookLifeUnitUpdateParameters);
        }
    }

    private void assertCompletedWithStatus(String containerName, StatusCode expected) {
        ProcessWorkflow processWorkflow =
            processMonitoring.findOneProcessWorkflow(containerName, TENANT_ID);
        assertNotNull(processWorkflow);
        assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
        assertEquals(expected, processWorkflow.getStatus());
    }

    private TraceabilityEvent getTraceabilityEvent(String containerName)
        throws LogbookClientException, InvalidParseOperationException {
        final LogbookOperationsClient logbookClient = LogbookOperationsClientFactory.getInstance().getClient();
        Select select = new Select();
        JsonNode response = logbookClient.selectOperationById(containerName);
        JsonNode jsonNode = response.get("$results").get(0);
        JsonNode evDetData = jsonNode.get("evDetData");
        if (evDetData == null || evDetData.isNull())
            return null;
        return JsonHandler.getFromString(evDetData.textValue(), TraceabilityEvent.class);
    }

    private void assertThatDateIsBetween(String mongoDate, LocalDateTime expectedMin, LocalDateTime expectedMax) {
        assertThatDateIsAfterOrEqualTo(mongoDate, expectedMin);
        assertThatDateIsBeforeOrEqualTo(mongoDate, expectedMax);
    }

    private void assertThatDateIsBeforeOrEqualTo(String mongoDate, LocalDateTime expectedMax) {
        LocalDateTime dateTime = LocalDateUtil.parseMongoFormattedDate(mongoDate);
        assertThat(dateTime).isBeforeOrEqualTo(expectedMax);
    }

    private void assertThatDateIsAfterOrEqualTo(String mongoDate, LocalDateTime expectedMin) {
        LocalDateTime dateTime = LocalDateUtil.parseMongoFormattedDate(mongoDate);
        assertThat(dateTime).isAfterOrEqualTo(expectedMin);
    }
}
