/**
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
 */

package fr.gouv.vitam.ihmrecette.appserver;


import static com.jayway.restassured.RestAssured.given;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.ws.rs.core.Response.Status;

import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.model.administration.ContextModel;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;
import com.mongodb.BasicDBObject;

import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import fr.gouv.vitam.common.GlobalDataRest;
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.database.builder.query.Query;
import fr.gouv.vitam.common.database.builder.query.QueryHelper;
import fr.gouv.vitam.common.database.builder.request.exception.InvalidCreateOperationException;
import fr.gouv.vitam.common.database.server.DbRequestResult;
import fr.gouv.vitam.common.database.server.elasticsearch.ElasticsearchNode;
import fr.gouv.vitam.common.database.server.mongodb.VitamDocument;
import fr.gouv.vitam.common.exception.InvalidGuidOperationException;
import fr.gouv.vitam.common.exception.VitamApplicationServerException;
import fr.gouv.vitam.common.guid.GUID;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.guid.GUIDReader;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.junit.JunitHelper;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.server.application.configuration.DbConfigurationImpl;
import fr.gouv.vitam.common.server.application.configuration.MongoDbNode;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.functional.administration.common.Context;
import fr.gouv.vitam.functional.administration.common.exception.ReferentialException;
import fr.gouv.vitam.functional.administration.common.server.AdminManagementConfiguration;
import fr.gouv.vitam.functional.administration.common.server.ElasticsearchAccessAdminFactory;
import fr.gouv.vitam.functional.administration.common.server.FunctionalAdminCollections;
import fr.gouv.vitam.functional.administration.common.server.MongoDbAccessAdminFactory;
import fr.gouv.vitam.functional.administration.common.server.MongoDbAccessAdminImpl;
import fr.gouv.vitam.logbook.common.server.LogbookConfiguration;
import fr.gouv.vitam.logbook.common.server.database.collections.LogbookCollections;
import fr.gouv.vitam.logbook.common.server.database.collections.LogbookLifeCycleObjectGroup;
import fr.gouv.vitam.logbook.common.server.database.collections.LogbookLifeCycleUnit;
import fr.gouv.vitam.logbook.common.server.database.collections.LogbookMongoDbAccessFactory;
import fr.gouv.vitam.logbook.common.server.database.collections.LogbookMongoDbAccessImpl;
import fr.gouv.vitam.logbook.common.server.database.collections.LogbookOperation;
import fr.gouv.vitam.metadata.api.config.MetaDataConfiguration;
import fr.gouv.vitam.metadata.core.MongoDbAccessMetadataFactory;
import fr.gouv.vitam.metadata.core.database.collections.MetadataCollections;
import fr.gouv.vitam.metadata.core.database.collections.MetadataDocument;
import fr.gouv.vitam.metadata.core.database.collections.MongoDbAccessMetadataImpl;
import fr.gouv.vitam.metadata.core.database.collections.ObjectGroup;
import fr.gouv.vitam.metadata.core.database.collections.Unit;

public class WebApplicationResourceDeleteTest {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(WebApplicationResourceDeleteTest.class);

    private static final String CONTEXT_NAME = "Name";
    private static final String ADMIN_CONTEXT = "admin-context";
    private static final String SECURITY_PROFIL_NAME = "Name";
    private static final String SECURITY_PROFIL_NAME_TO_SAVE = "admin-security-profile";
    // Take it from conf file
    private static final String DEFAULT_WEB_APP_CONTEXT = "/ihm-recette";
    private static final String CREDENTIALS = "{\"token\": {\"principal\": \"myName\", \"credentials\": \"myName\"}}";
    private static final String CREDENTIALS_NO_VALID =
        "{\"token\": {\"principal\": \"myName\", \"credentials\": \"myName\"}}";
    private static final String IHM_RECETTE_CONF = "ihm-recette.conf";
    private static JunitHelper junitHelper;
    private static int serverPort;
    private static int databasePort;
    private static File adminConfigFile;

    static MongodExecutable mongodExecutable;
    static MongodProcess mongod;

    private static MongoDbAccessAdminImpl mongoDbAccessAdmin;
    private static LogbookMongoDbAccessImpl mongoDbAccessLogbook;
    private static MongoDbAccessMetadataImpl mongoDbAccessMetadata;

    private static IhmRecetteMain application;

    private static JunitHelper.ElasticsearchTestConfiguration config = null;
    private final static String CLUSTER_NAME = "vitam-cluster";
    private final static String HOST_NAME = "127.0.0.1";
    private static final Integer TENANT_ID = 0;
    static final int tenantId = 0;
    static final List<Integer> tenantList = Arrays.asList(0);

    @Rule
    public RunWithCustomExecutorRule runInThread =
        new RunWithCustomExecutorRule(VitamThreadPoolExecutor.getDefaultExecutor());

    @ClassRule
    public static TemporaryFolder tempFolder = new TemporaryFolder();

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {

        junitHelper = JunitHelper.getInstance();
        // ES
        try {
            config = JunitHelper.startElasticsearchForTest(tempFolder, CLUSTER_NAME);
        } catch (final VitamApplicationServerException e1) {
            assumeTrue(false);
        }

        databasePort = junitHelper.findAvailablePort();
        serverPort = junitHelper.findAvailablePort();

        final File adminConfig = PropertiesUtils.findFile("ihm-recette.conf");
        final WebApplicationConfig realAdminConfig =
            PropertiesUtils.readYaml(adminConfig, WebApplicationConfig.class);
        realAdminConfig.getMongoDbNodes().get(0).setDbPort(databasePort);
        realAdminConfig.setBaseUrl(DEFAULT_WEB_APP_CONTEXT);
        realAdminConfig.setAuthentication(false);
        realAdminConfig.setClusterName(CLUSTER_NAME);
        realAdminConfig.setTenants(tenantList);

        realAdminConfig.getElasticsearchNodes().get(0).setTcpPort(config.getTcpPort());
        adminConfigFile = File.createTempFile("test", IHM_RECETTE_CONF, adminConfig.getParentFile());
        PropertiesUtils.writeYaml(adminConfigFile, realAdminConfig);

        final MongodStarter starter = MongodStarter.getDefaultInstance();
        mongodExecutable = starter.prepare(new MongodConfigBuilder()
            .withLaunchArgument("--enableMajorityReadConcern")
            .version(Version.Main.PRODUCTION)
            .net(new Net(databasePort, Network.localhostIsIPv6()))
            .build());
        mongod = mongodExecutable.start();

        final List<MongoDbNode> mongoNodes = new ArrayList<>();
        mongoNodes.add(new MongoDbNode("localhost", databasePort));

        final List<ElasticsearchNode> esNodes = new ArrayList<>();
        esNodes.add(new ElasticsearchNode(HOST_NAME, config.getTcpPort()));

        RestAssured.port = serverPort;
        RestAssured.basePath = DEFAULT_WEB_APP_CONTEXT + "/v1/api";


        final DbConfigurationImpl adminConfiguration =
            new DbConfigurationImpl(realAdminConfig.getMongoDbNodes(), realAdminConfig.getMasterdataDbName(), false,
                realAdminConfig.getDbUserName(), realAdminConfig.getDbPassword());
        mongoDbAccessAdmin = MongoDbAccessAdminFactory.create(adminConfiguration);

        final LogbookConfiguration logbookConfiguration =
            new LogbookConfiguration(realAdminConfig.getMongoDbNodes(), realAdminConfig.getLogbookDbName(),
                realAdminConfig.getClusterName(), realAdminConfig.getElasticsearchNodes(), false,
                realAdminConfig.getDbUserName(), realAdminConfig.getDbPassword());
        logbookConfiguration.setTenants(realAdminConfig.getTenants());

        mongoDbAccessLogbook = LogbookMongoDbAccessFactory.create(logbookConfiguration);

        final MetaDataConfiguration metaDataConfiguration =
            new MetaDataConfiguration(realAdminConfig.getMongoDbNodes(), realAdminConfig.getMetadataDbName(),
                realAdminConfig.getClusterName(), realAdminConfig.getElasticsearchNodes(), false,
                realAdminConfig.getDbUserName(), realAdminConfig.getDbPassword());
        metaDataConfiguration.setTenants(tenantList);
        
        mongoDbAccessMetadata = MongoDbAccessMetadataFactory.create(metaDataConfiguration);
        ElasticsearchAccessAdminFactory.create(
            new AdminManagementConfiguration(mongoNodes, realAdminConfig.getMasterdataDbName(), CLUSTER_NAME, esNodes));

        try {
            application = new IhmRecetteMain(adminConfigFile.getAbsolutePath());
            application.start();
            JunitHelper.unsetJettyPortSystemProperty();
        } catch (final VitamApplicationServerException e) {

            throw new IllegalStateException(
                "Cannot start the Logbook Application Server", e);
        }
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        LOGGER.debug("Ending tests");
        try {
            application.stop();
        } catch (final VitamApplicationServerException e) {
            LOGGER.error(e);
        }
        mongoDbAccessAdmin.close();
        mongoDbAccessLogbook.close();
        mongoDbAccessMetadata.close();
        if (config != null) {
            JunitHelper.stopElasticsearchForTest(config);
        }
        mongod.stop();
        mongodExecutable.stop();
        junitHelper.releasePort(databasePort);
        junitHelper.releasePort(serverPort);
    }

    @Test
    public void givenNoSecureServerLoginUnauthorized() {
        given().contentType(ContentType.JSON).body(CREDENTIALS).expect().statusCode(Status.UNAUTHORIZED.getStatusCode())
            .when()
            .post("login");
        given().contentType(ContentType.JSON).body(CREDENTIALS_NO_VALID).expect()
            .statusCode(Status.UNAUTHORIZED.getStatusCode()).when()
            .post("login");
    }

    @Test
    public void testSuccessStatus() {
        given().expect().statusCode(Status.NO_CONTENT.getStatusCode()).when().get("status");
    }

    @Test
    @RunWithCustomExecutor
    public void testDeleteFormatOK() {
        try {
            final GUID idFormat = addData(FunctionalAdminCollections.FORMATS);
            assertTrue(existsData(FunctionalAdminCollections.FORMATS, idFormat.getId()));
            given().header(GlobalDataRest.X_TENANT_ID, TENANT_ID).expect().statusCode(Status.OK.getStatusCode()).when()
                .delete("delete/formats");
            assertFalse(existsData(FunctionalAdminCollections.FORMATS, idFormat.getId()));
        } catch (final Exception e) {
            LOGGER.error(e);
            fail("Exception using mongoDbAccess");
        }
    }

    @Test
    @RunWithCustomExecutor
    public void testDeleteRulesFileOK() {
        try {
            VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
            final GUID idRule = addData(FunctionalAdminCollections.RULES);
            assertTrue(existsData(FunctionalAdminCollections.RULES, idRule.getId()));
            given().header(GlobalDataRest.X_TENANT_ID, TENANT_ID).expect().statusCode(Status.OK.getStatusCode()).when()
                .delete("delete/rules");
            assertFalse(existsData(FunctionalAdminCollections.RULES, idRule.getId()));
        } catch (final Exception e) {
            fail("Exception using mongoDbAccess");
        }
    }

    @Test
    @RunWithCustomExecutor
    public void testAccessionRegisterOK() {
        try {
            VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
            final GUID idRegisterSummary = addData(FunctionalAdminCollections.ACCESSION_REGISTER_SUMMARY);
            final GUID idRegisterDetail = addData(FunctionalAdminCollections.ACCESSION_REGISTER_DETAIL);
            assertTrue(existsData(FunctionalAdminCollections.ACCESSION_REGISTER_SUMMARY, idRegisterSummary.getId()));
            assertTrue(existsData(FunctionalAdminCollections.ACCESSION_REGISTER_DETAIL, idRegisterDetail.getId()));
            given().header(GlobalDataRest.X_TENANT_ID, TENANT_ID).expect().statusCode(Status.OK.getStatusCode()).when()
                .delete("delete/accessionregisters");
            assertFalse(existsData(FunctionalAdminCollections.ACCESSION_REGISTER_SUMMARY, idRegisterSummary.getId()));
            assertFalse(existsData(FunctionalAdminCollections.ACCESSION_REGISTER_DETAIL, idRegisterDetail.getId()));
        } catch (final Exception e) {
            fail("Exception using mongoDbAccess");
        }
    }

    @Test
    public void testDeleteLogbookOperationOK() {
        try {
            final GUID idOperation = addData(LogbookCollections.OPERATION);
            assertTrue(existsData(LogbookCollections.OPERATION, idOperation.getId()));
            given().header(GlobalDataRest.X_TENANT_ID, TENANT_ID).expect().statusCode(Status.OK.getStatusCode()).when()
                .delete("delete/logbook/operation");
            assertFalse(existsData(LogbookCollections.OPERATION, idOperation.getId()));
        } catch (final Exception e) {
            LOGGER.error(e);
            fail("Exception using mongoDbAccess");
        }
    }

    @Test
    public void testDeleteLogbookLifecycleOGOK() {
        try {
            final GUID idLfcOg = addData(LogbookCollections.LIFECYCLE_OBJECTGROUP);
            assertTrue(existsData(LogbookCollections.LIFECYCLE_OBJECTGROUP, idLfcOg.getId()));
            given().header(GlobalDataRest.X_TENANT_ID, TENANT_ID).expect().statusCode(Status.OK.getStatusCode()).when()
                .delete("delete/logbook/lifecycle/objectgroup");
            assertFalse(existsData(LogbookCollections.LIFECYCLE_OBJECTGROUP, idLfcOg.getId()));
        } catch (final Exception e) {
            LOGGER.error(e);
            fail("Exception using mongoDbAccess");
        }
    }

    @Test
    public void testDeleteLogbookLifecycleUnitOK() {
        try {
            final GUID idLfcUnit = addData(LogbookCollections.LIFECYCLE_UNIT);
            assertTrue(existsData(LogbookCollections.LIFECYCLE_UNIT, idLfcUnit.getId()));
            given().header(GlobalDataRest.X_TENANT_ID, TENANT_ID).expect().statusCode(Status.OK.getStatusCode()).when()
                .delete("delete/logbook/lifecycle/unit");
            assertFalse(existsData(LogbookCollections.LIFECYCLE_UNIT, idLfcUnit.getId()));
        } catch (final Exception e) {
            LOGGER.error(e);
            fail("Exception using mongoDbAccess");
        }
    }

    @Test
    public void testDeleteMetadataOGOK() {
        try {
            final GUID idOg = addData(MetadataCollections.C_OBJECTGROUP);
            assertTrue(existsData(MetadataCollections.C_OBJECTGROUP, idOg.getId()));
            given().header(GlobalDataRest.X_TENANT_ID, TENANT_ID).expect().statusCode(Status.OK.getStatusCode()).when()
                .delete("delete/metadata/objectgroup");
            assertFalse(existsData(MetadataCollections.C_OBJECTGROUP, idOg.getId()));
        } catch (final Exception e) {
            LOGGER.error(e);
            fail("Exception using mongoDbAccess");
        }
    }

    @Test
    public void testDeleteMetadataUnitOK() {
        try {
            final GUID idUnit = addData(MetadataCollections.C_UNIT);
            assertTrue(existsData(MetadataCollections.C_UNIT, idUnit.getId()));
            given().header(GlobalDataRest.X_TENANT_ID, TENANT_ID).expect().statusCode(Status.OK.getStatusCode()).when()
                .delete("delete/metadata/unit");
            assertFalse(existsData(MetadataCollections.C_UNIT, idUnit.getId()));
        } catch (final Exception e) {
            LOGGER.error(e);
            fail("Exception using mongoDbAccess");
        }
    }

    @Test
    @RunWithCustomExecutor
    public void testDeleteMasterdataAccessOK() {
        try {
            VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);

            final GUID idUnit = addData(FunctionalAdminCollections.ACCESS_CONTRACT);
            assertTrue(existsData(FunctionalAdminCollections.ACCESS_CONTRACT, idUnit.getId()));
            given().header(GlobalDataRest.X_TENANT_ID, TENANT_ID).expect().statusCode(Status.OK.getStatusCode()).when()
                .delete("delete/masterdata/accessContract");
            assertFalse(existsData(FunctionalAdminCollections.ACCESS_CONTRACT, idUnit.getId()));
        } catch (final Exception e) {
            LOGGER.error(e);
            fail("Exception using mongoDbAccess");
        }
    }

    @Test
    @RunWithCustomExecutor
    public void testDeleteMasterdataIngestOK() {
        try {
            VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);

            final GUID idUnit = addData(FunctionalAdminCollections.INGEST_CONTRACT);
            assertTrue(existsData(FunctionalAdminCollections.INGEST_CONTRACT, idUnit.getId()));
            given().header(GlobalDataRest.X_TENANT_ID, TENANT_ID).expect().statusCode(Status.OK.getStatusCode()).when()
                .delete("delete/masterdata/ingestContract");
            assertFalse(existsData(FunctionalAdminCollections.INGEST_CONTRACT, idUnit.getId()));
        } catch (final Exception e) {
            LOGGER.error(e);
            fail("Exception using mongoDbAccess");
        }
    }

    @Test
    @RunWithCustomExecutor
    public void testDeleteTnrOk() {
        try {
            VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
            // insert and check data
            final GUID idFormat = addData(FunctionalAdminCollections.FORMATS);
            assertTrue(existsData(FunctionalAdminCollections.FORMATS, idFormat.getId()));
            final GUID idRule = addData(FunctionalAdminCollections.RULES);
            assertTrue(existsData(FunctionalAdminCollections.RULES, idRule.getId()));
            final GUID idAgency = addData(FunctionalAdminCollections.AGENCIES);
            assertTrue(existsData(FunctionalAdminCollections.AGENCIES, idAgency.getId()));
            final GUID idProfile = addData(FunctionalAdminCollections.PROFILE);
            assertTrue(existsData(FunctionalAdminCollections.PROFILE, idProfile.getId()));
            final GUID idAccessContract = addData(FunctionalAdminCollections.ACCESS_CONTRACT);
            assertTrue(existsData(FunctionalAdminCollections.ACCESS_CONTRACT, idAccessContract.getId()));
            final GUID idIngestContract = addData(FunctionalAdminCollections.INGEST_CONTRACT);
            assertTrue(existsData(FunctionalAdminCollections.INGEST_CONTRACT, idIngestContract.getId()));
            final GUID idRegisterSummary = addData(FunctionalAdminCollections.ACCESSION_REGISTER_SUMMARY);
            final GUID idRegisterDetail = addData(FunctionalAdminCollections.ACCESSION_REGISTER_DETAIL);
            assertTrue(existsData(FunctionalAdminCollections.ACCESSION_REGISTER_SUMMARY, idRegisterSummary.getId()));
            assertTrue(existsData(FunctionalAdminCollections.ACCESSION_REGISTER_DETAIL, idRegisterDetail.getId()));
            // delete all
            given().header(GlobalDataRest.X_TENANT_ID, TENANT_ID).expect().statusCode(Status.OK.getStatusCode()).when()
                .delete("delete/deleteTnr");
            // check deleted
            assertFalse(existsData(FunctionalAdminCollections.FORMATS, idFormat.getId()));
            assertFalse(existsData(FunctionalAdminCollections.AGENCIES, idAgency.getId()));
            assertFalse(existsData(FunctionalAdminCollections.PROFILE, idProfile.getId()));
            assertFalse(existsData(FunctionalAdminCollections.ACCESS_CONTRACT, idAccessContract.getId()));
            assertFalse(existsData(FunctionalAdminCollections.INGEST_CONTRACT, idIngestContract.getId()));
            assertFalse(existsData(FunctionalAdminCollections.RULES, idRule.getId()));
            assertFalse(existsData(FunctionalAdminCollections.ACCESSION_REGISTER_SUMMARY, idRegisterSummary.getId()));
            assertFalse(existsData(FunctionalAdminCollections.ACCESSION_REGISTER_DETAIL, idRegisterDetail.getId()));
        } catch (final ReferentialException e) {
            LOGGER.error(e);
            fail("Exception using mongoDbAccess");
        }
    }

    @Test
    @RunWithCustomExecutor
    public void testDeleteMasterdataProfileOK() {
        try {
            VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);

            final GUID idProfile = addData(FunctionalAdminCollections.PROFILE);
            assertTrue(existsData(FunctionalAdminCollections.PROFILE, idProfile.getId()));
            given().header(GlobalDataRest.X_TENANT_ID, TENANT_ID).expect().statusCode(Status.OK.getStatusCode()).when()
                .delete("delete/masterdata/profile");
            assertFalse(existsData(FunctionalAdminCollections.PROFILE, idProfile.getId()));
        } catch (final Exception e) {
            LOGGER.error(e);
            fail("Exception using mongoDbAccess");
        }
    }

    @Test
    @RunWithCustomExecutor
    public void testDeleteMasterdataAgencyOK() {
        try {
            VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);

            final GUID idAgency = addData(FunctionalAdminCollections.AGENCIES);
            assertTrue(existsData(FunctionalAdminCollections.AGENCIES, idAgency.getId()));
            given().header(GlobalDataRest.X_TENANT_ID, TENANT_ID).expect().statusCode(Status.OK.getStatusCode()).when()
                    .delete("delete/masterdata/agencies");
            assertFalse(existsData(FunctionalAdminCollections.AGENCIES, idAgency.getId()));
        } catch (final Exception e) {
            LOGGER.error(e);
            fail("Exception using mongoDbAccess");
        }
    }

    @Test
    @RunWithCustomExecutor
    public void testDeleteMasterdataContextOK() {
        try {
            VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);

            final GUID adminContext = addAdminContextData(FunctionalAdminCollections.CONTEXT);
            // Needs two contexts for testing purposes (admin context won't be deleted)
            final GUID idContext2 = addData(FunctionalAdminCollections.CONTEXT);
            assertTrue(existsData(FunctionalAdminCollections.CONTEXT, adminContext.getId()));
            assertTrue(existsData(FunctionalAdminCollections.CONTEXT, idContext2.getId()));
            given().header(GlobalDataRest.X_TENANT_ID, TENANT_ID).expect().statusCode(Status.OK.getStatusCode()).when()
                .delete("delete/masterdata/context");
            assertTrue(existsData(FunctionalAdminCollections.CONTEXT, adminContext.getId()));
            assertFalse(existsData(FunctionalAdminCollections.CONTEXT, idContext2.getId()));
        } catch (final Exception e) {
            LOGGER.error(e);
            fail("Exception using mongoDbAccess");
        }
    }

    @Test
    @RunWithCustomExecutor
    public void testDeleteMasterdataSecuryityProfilOK() {
        try {
            VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);

            final GUID adminSecurity = addAdminSecurityData(FunctionalAdminCollections.SECURITY_PROFILE);
            // Needs two contexts for testing purposes (admin context won't be deleted)
            final GUID idSecurity = addData(FunctionalAdminCollections.SECURITY_PROFILE);
            assertTrue(existsData(FunctionalAdminCollections.SECURITY_PROFILE, adminSecurity.getId()));
            assertTrue(existsData(FunctionalAdminCollections.SECURITY_PROFILE, idSecurity.getId()));
            given().header(GlobalDataRest.X_TENANT_ID, TENANT_ID).expect().statusCode(Status.OK.getStatusCode()).when()
                .delete("delete/masterdata/securityProfil");
            assertTrue(existsData(FunctionalAdminCollections.SECURITY_PROFILE, adminSecurity.getId()));
            assertFalse(existsData(FunctionalAdminCollections.SECURITY_PROFILE, idSecurity.getId()));
        } catch (final Exception e) {
            LOGGER.error(e);
            fail("Exception using mongoDbAccess");
        }
    }


    @Test
    @RunWithCustomExecutor
    public void testDeleteAllOk() throws InvalidCreateOperationException, InvalidGuidOperationException {
        try {
            VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
            // insert and check data
            final GUID idFormat = addData(FunctionalAdminCollections.FORMATS);
            assertTrue(existsData(FunctionalAdminCollections.FORMATS, idFormat.getId()));
            final GUID idRule = addData(FunctionalAdminCollections.RULES);
            assertTrue(existsData(FunctionalAdminCollections.RULES, idRule.getId()));
            final GUID idAgency = addData(FunctionalAdminCollections.AGENCIES);
            assertTrue(existsData(FunctionalAdminCollections.AGENCIES, idAgency.getId()));
            final GUID idProfile = addData(FunctionalAdminCollections.PROFILE);
            assertTrue(existsData(FunctionalAdminCollections.PROFILE, idProfile.getId()));
            final GUID idAccessContract = addData(FunctionalAdminCollections.ACCESS_CONTRACT);
            assertTrue(existsData(FunctionalAdminCollections.ACCESS_CONTRACT, idAccessContract.getId()));
            final GUID idIngestContract = addData(FunctionalAdminCollections.INGEST_CONTRACT);
            assertTrue(existsData(FunctionalAdminCollections.INGEST_CONTRACT, idIngestContract.getId()));
            final GUID idRegisterSummary = addData(FunctionalAdminCollections.ACCESSION_REGISTER_SUMMARY);
            final GUID idRegisterDetail = addData(FunctionalAdminCollections.ACCESSION_REGISTER_DETAIL);
            assertTrue(existsData(FunctionalAdminCollections.ACCESSION_REGISTER_SUMMARY, idRegisterSummary.getId()));
            assertTrue(existsData(FunctionalAdminCollections.ACCESSION_REGISTER_DETAIL, idRegisterDetail.getId()));
            final GUID adminContext = addAdminContextData(FunctionalAdminCollections.CONTEXT);
            final GUID idContext2 = addData(FunctionalAdminCollections.CONTEXT);
            assertTrue(existsData(FunctionalAdminCollections.CONTEXT, adminContext.getId()));
            assertTrue(existsData(FunctionalAdminCollections.CONTEXT, idContext2.getId()));
            // delete all
            given().header(GlobalDataRest.X_TENANT_ID, TENANT_ID).expect().statusCode(Status.OK.getStatusCode()).when()
                .delete("delete");
            // check not deleted
            assertTrue(existsData(FunctionalAdminCollections.FORMATS, idFormat.getId()));
            // check deleted
            assertFalse(existsData(FunctionalAdminCollections.PROFILE, idProfile.getId()));
            assertFalse(existsData(FunctionalAdminCollections.ACCESS_CONTRACT, idAccessContract.getId()));
            assertFalse(existsData(FunctionalAdminCollections.INGEST_CONTRACT, idIngestContract.getId()));
            assertFalse(existsData(FunctionalAdminCollections.RULES, idRule.getId()));
            assertFalse(existsData(FunctionalAdminCollections.AGENCIES, idAgency.getId()));
            assertFalse(existsData(FunctionalAdminCollections.ACCESSION_REGISTER_SUMMARY, idRegisterSummary.getId()));
            assertFalse(existsData(FunctionalAdminCollections.ACCESSION_REGISTER_DETAIL, idRegisterDetail.getId()));
            assertFalse(existsData(FunctionalAdminCollections.PROFILE, idProfile.getId()));
            assertFalse(existsData(FunctionalAdminCollections.CONTEXT, idContext2.getId()));
            //Admin context must still exist
            assertTrue(existsData(FunctionalAdminCollections.CONTEXT, adminContext.getId()));
        } catch (final ReferentialException | InvalidParseOperationException e) {
            LOGGER.error(e);
            fail("Exception using mongoDbAccess");
        }
    }

    public GUID addData(FunctionalAdminCollections collection)
        throws ReferentialException {
        final GUID guid = GUIDFactory.newGUID();
        final ObjectNode data1 = JsonHandler.createObjectNode().put("_id", guid.getId());
        mongoDbAccessAdmin.insertDocument(data1, collection).close();
        return guid;
    }

    @SuppressWarnings("rawtypes")
    public GUID addData(MetadataCollections collection) {
        final GUID guid = GUIDFactory.newGUID();
        final ObjectNode data1 = JsonHandler.createObjectNode().put("_id", guid.getId());
        data1.put(VitamDocument.TENANT_ID, TENANT_ID);
        MetadataDocument document;
        if (collection.equals(MetadataCollections.C_OBJECTGROUP)) {
            document = new ObjectGroup(data1);
        } else {
            document = new Unit(data1);
        }

        mongoDbAccessMetadata.getMongoDatabase().getCollection(collection.getClasz().getSimpleName())
            .insertOne(document);

        return guid;
    }

    @SuppressWarnings("rawtypes")
    public GUID addData(LogbookCollections collection) {
        final GUID guid = GUIDFactory.newGUID();
        final ObjectNode data1 = JsonHandler.createObjectNode().put("_id", guid.getId());
        data1.put(VitamDocument.TENANT_ID, TENANT_ID);
        VitamDocument document;
        if (collection.equals(LogbookCollections.OPERATION)) {
            document = new LogbookOperation(data1);
        } else if (collection.equals(LogbookCollections.LIFECYCLE_UNIT)) {
            document = new LogbookLifeCycleUnit(data1);
        } else {
            document = new LogbookLifeCycleObjectGroup(data1);
        }

        mongoDbAccessLogbook.getMongoDatabase().getCollection(collection.getClasz().getSimpleName())
            .insertOne(document);
        return guid;
    }

    public GUID addAdminContextData(FunctionalAdminCollections collection)
        throws ReferentialException, InvalidCreateOperationException, InvalidGuidOperationException,
        InvalidParseOperationException {
        final Query query = QueryHelper.or().add(QueryHelper.eq(CONTEXT_NAME, ADMIN_CONTEXT));
        JsonNode select = query.getCurrentObject();
        DbRequestResult result = mongoDbAccessAdmin.findDocuments(select, FunctionalAdminCollections.CONTEXT);
        GUID adminContext = null;
        if (result.getCount() > 0) {
            adminContext = GUIDReader.getGUID(result.getDocuments(Context.class, ContextModel.class).get(0).getId());
        } else {
            adminContext = GUIDFactory.newGUID();
            final ObjectNode data1 = JsonHandler.createObjectNode().put("_id", adminContext.getId());
            data1.put(CONTEXT_NAME, ADMIN_CONTEXT);
            mongoDbAccessAdmin.insertDocument(data1, collection).close();
        }
        return adminContext;
    }


    public GUID addAdminSecurityData(FunctionalAdminCollections collection)
        throws ReferentialException, InvalidCreateOperationException, InvalidGuidOperationException,
        InvalidParseOperationException {
        final Query query = QueryHelper.or().add(QueryHelper.eq(SECURITY_PROFIL_NAME, SECURITY_PROFIL_NAME_TO_SAVE));
        JsonNode select = query.getCurrentObject();
        DbRequestResult result = mongoDbAccessAdmin.findDocuments(select, FunctionalAdminCollections.SECURITY_PROFILE);
        GUID adminContext = null;
        if (result.getCount() > 0) {
            adminContext = GUIDReader.getGUID(result.getDocuments(Context.class, ContextModel.class).get(0).getId());
        } else {
            adminContext = GUIDFactory.newGUID();
            final ObjectNode data1 = JsonHandler.createObjectNode().put("_id", adminContext.getId());
            data1.put(SECURITY_PROFIL_NAME, SECURITY_PROFIL_NAME_TO_SAVE);
            mongoDbAccessAdmin.insertDocument(data1, collection).close();
        }
        return adminContext;
    }
    public boolean existsData(FunctionalAdminCollections collection, String id)
        throws ReferentialException {
        try {
            return mongoDbAccessAdmin.getDocumentById(id, collection) != null;
        } catch (final ReferentialException exc) {
            return false;
        }
    }

    public boolean existsData(LogbookCollections collection, String id) {
        final BasicDBObject bbo = new BasicDBObject("_id", id);
        mongoDbAccessLogbook.getMongoDatabase().listCollectionNames();
        return mongoDbAccessLogbook.getMongoDatabase().getCollection(collection.getClasz().getSimpleName()).find(bbo)
            .first() != null;

    }

    public boolean existsData(MetadataCollections collection, String id) {
        final BasicDBObject bbo = new BasicDBObject("_id", id);
        return mongoDbAccessMetadata.getMongoDatabase().getCollection(collection.getClasz().getSimpleName()).find(bbo)
            .first() != null;
    }
}
