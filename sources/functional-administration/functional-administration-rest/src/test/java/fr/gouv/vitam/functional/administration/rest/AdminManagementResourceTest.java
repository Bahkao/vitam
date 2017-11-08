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
package fr.gouv.vitam.functional.administration.rest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.jayway.restassured.RestAssured.get;
import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.RestAssured.with;
import static fr.gouv.vitam.common.database.builder.query.QueryHelper.eq;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.Response.Status;

import org.jhades.JHades;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import com.google.common.collect.Sets;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.Response;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import fr.gouv.vitam.common.GlobalDataRest;
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.SystemPropertyUtil;
import fr.gouv.vitam.common.database.builder.request.exception.InvalidCreateOperationException;
import fr.gouv.vitam.common.database.builder.request.single.Select;
import fr.gouv.vitam.common.database.server.elasticsearch.ElasticsearchNode;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamApplicationServerException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.junit.JunitHelper;
import fr.gouv.vitam.common.junit.JunitHelper.ElasticsearchTestConfiguration;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.administration.AccessContractModel;
import fr.gouv.vitam.common.model.administration.ContractStatus;
import fr.gouv.vitam.common.server.application.configuration.DbConfigurationImpl;
import fr.gouv.vitam.common.server.application.configuration.MongoDbNode;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.functional.administration.common.AccessionRegisterDetail;
import fr.gouv.vitam.functional.administration.common.server.AdminManagementConfiguration;
import fr.gouv.vitam.functional.administration.common.server.ElasticsearchAccessFunctionalAdmin;
import fr.gouv.vitam.functional.administration.common.server.FunctionalAdminCollections;
import fr.gouv.vitam.functional.administration.common.server.MongoDbAccessAdminFactory;
import fr.gouv.vitam.functional.administration.common.server.MongoDbAccessAdminImpl;
import fr.gouv.vitam.functional.administration.common.server.MongoDbAccessReferential;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClientFactory;
import fr.gouv.vitam.storage.engine.client.StorageClient;
import fr.gouv.vitam.storage.engine.client.StorageClientFactory;
import fr.gouv.vitam.workspace.client.WorkspaceClient;
import fr.gouv.vitam.workspace.client.WorkspaceClientFactory;


public class AdminManagementResourceTest {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(AdminManagementResourceTest.class);
    private static final String ADMIN_MANAGEMENT_CONF = "functional-administration-test.conf";
    private static final String RESULTS = "$results";

    private static final String RESOURCE_URI = "/adminmanagement/v1";
    private static final String STATUS_URI = "/status";
    private static final String CHECK_FORMAT_URI = "/format/check";
    private static final String IMPORT_FORMAT_URI = "/format/import";

    private static final String GET_BYID_FORMAT_URI = "/format";
    private static final String FORMAT_ID_URI = "/{id_format}";

    private static final String GET_DOCUMENT_FORMAT_URI = "/format/document";

    private static final String CHECK_RULES_URI = "/rules/check";
    private static final String IMPORT_RULES_URI = "/rules/import";

    private static final String GET_BYID_RULES_URI = "/rules";
    private static final String RULES_ID_URI = "/{id_rule}";

    private static final String GET_DOCUMENT_RULES_URI = "/rules/document";

    private static final String CREATE_FUND_REGISTER_URI = "/accession-register";
    private static final String FILE_TEST_OK = "jeu_donnees_OK_regles_CSV.csv";

    private static final int TENANT_ID = 0;
    private static final String ERROR_REPORT_CONTENT = "error_report_content.json";

    static MongodExecutable mongodExecutable;
    static MongodProcess mongod;
    static MongoDbAccessReferential mongoDbAccess;
    static String DATABASE_NAME = "vitam-test";
    private static String DATABASE_HOST = "localhost";

    private static MongoDbAccessAdminImpl dbImpl;

    private InputStream stream;
    private static JunitHelper junitHelper = JunitHelper.getInstance();
    private static int serverPort;
    private static int databasePort;
    private static File adminConfigFile;

    private static AdminManagementMain application;

    private static int workspacePort = junitHelper.findAvailablePort();
    @Rule
    public RunWithCustomExecutorRule runInThread =
        new RunWithCustomExecutorRule(VitamThreadPoolExecutor.getDefaultExecutor());

    @ClassRule
    public static WireMockClassRule wireMockRule = new WireMockClassRule(workspacePort);

    @Rule
    public WireMockClassRule instanceRule = wireMockRule;

    private static ElasticsearchTestConfiguration configEs = null;

    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final static String CLUSTER_NAME = "vitam-cluster";
    private static ElasticsearchAccessFunctionalAdmin esClient;
    private final static String originatingAgency = "OriginatingAgency";
    private InputStream streamErrorReport;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        new JHades().overlappingJarsReport();

        databasePort = junitHelper.findAvailablePort();

        // ES
        try {
            configEs = JunitHelper.startElasticsearchForTest(temporaryFolder, CLUSTER_NAME);
        } catch (final VitamApplicationServerException e1) {
            assumeTrue(false);
        }

        File tempFolder = temporaryFolder.newFolder();
        System.setProperty("vitam.tmp.folder", tempFolder.getAbsolutePath());

        SystemPropertyUtil.refresh();

        final List<ElasticsearchNode> nodesEs = new ArrayList<>();
        nodesEs.add(new ElasticsearchNode("localhost", configEs.getTcpPort()));
        esClient = new ElasticsearchAccessFunctionalAdmin(CLUSTER_NAME, nodesEs);
        LogbookOperationsClientFactory.changeMode(null);

        final File adminConfig = PropertiesUtils.findFile(ADMIN_MANAGEMENT_CONF);
        final AdminManagementConfiguration realAdminConfig =
            PropertiesUtils.readYaml(adminConfig, AdminManagementConfiguration.class);
        realAdminConfig.getMongoDbNodes().get(0).setDbPort(databasePort);
        realAdminConfig.setElasticsearchNodes(nodesEs);
        realAdminConfig.setClusterName(CLUSTER_NAME);
        realAdminConfig.setWorkspaceUrl("http://localhost:" + workspacePort);

        adminConfigFile = File.createTempFile("test", ADMIN_MANAGEMENT_CONF, adminConfig.getParentFile());
        PropertiesUtils.writeYaml(adminConfigFile, realAdminConfig);

        final MongodStarter starter = MongodStarter.getDefaultInstance();
        mongodExecutable = starter.prepare(new MongodConfigBuilder()
            .withLaunchArgument("--enableMajorityReadConcern")
            .version(Version.Main.PRODUCTION)
            .net(new Net(databasePort, Network.localhostIsIPv6()))
            .build());
        mongod = mongodExecutable.start();

        final List<MongoDbNode> nodes = new ArrayList<>();
        nodes.add(new MongoDbNode(DATABASE_HOST, databasePort));
        mongoDbAccess = MongoDbAccessAdminFactory.create(new DbConfigurationImpl(nodes, "vitam-test"));

        serverPort = junitHelper.findAvailablePort();

        RestAssured.port = serverPort;
        RestAssured.basePath = RESOURCE_URI;

        
        dbImpl = MongoDbAccessAdminFactory.create(new DbConfigurationImpl(nodes, DATABASE_NAME));

        try {

            application = new AdminManagementMain(adminConfigFile.getAbsolutePath());
            application.start();
            JunitHelper.unsetJettyPortSystemProperty();

        } catch (final VitamApplicationServerException e) {
            LOGGER.error(e);
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

        mongod.stop();
        mongodExecutable.stop();
        junitHelper.releasePort(databasePort);
        junitHelper.releasePort(serverPort);
    }

    @Before
    public void setUp() throws Exception {
        instanceRule.stubFor(WireMock.post(urlMatching("/workspace/v1/containers/(.*)"))
            .willReturn(
                aResponse().withStatus(201).withHeader(GlobalDataRest.X_TENANT_ID, Integer.toString(TENANT_ID))));
        instanceRule.stubFor(WireMock.delete(urlMatching("/workspace/v1/containers/(.*)"))
            .willReturn(
                aResponse().withStatus(204).withHeader(GlobalDataRest.X_TENANT_ID, Integer.toString(TENANT_ID))));
    }

    @After
    public void tearDown() throws Exception {
        mongoDbAccess.deleteCollection(FunctionalAdminCollections.FORMATS).close();
        mongoDbAccess.deleteCollection(FunctionalAdminCollections.RULES).close();
    }

    @Test
    @RunWithCustomExecutor
    public final void testGetStatus() {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        get(STATUS_URI).then().statusCode(Status.NO_CONTENT.getStatusCode());
    }

    @Test
    @RunWithCustomExecutor
    public void givenAWellFormedXMLInputstreamCheckThenReturnOK() throws FileNotFoundException {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        stream = PropertiesUtils.getResourceAsStream("FF-vitam.xml");
        given().contentType(ContentType.BINARY).body(stream)
            .when().post(CHECK_FORMAT_URI)
            .then().statusCode(Status.OK.getStatusCode());
    }

    @Test
    @RunWithCustomExecutor
    public void givenANotWellFormedXMLInputstreamCheckThenReturnKO() throws FileNotFoundException {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        stream = PropertiesUtils.getResourceAsStream("FF-vitam-format-KO.xml");
        given().contentType(ContentType.BINARY).body(stream)
            .when().post(CHECK_FORMAT_URI)
            .then().statusCode(Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    @RunWithCustomExecutor
    public void insertAPronomFile() throws FileNotFoundException {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        stream = PropertiesUtils.getResourceAsStream("FF-vitam.xml");
        given().contentType(ContentType.BINARY).body(stream).header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .header(GlobalDataRest.X_FILENAME, "FF-vitam.xml")
            .when().post(IMPORT_FORMAT_URI)
            .then().statusCode(Status.CREATED.getStatusCode());

        stream = PropertiesUtils.getResourceAsStream("FF-vitam-format-KO.xml");
        given().contentType(ContentType.BINARY).body(stream).header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .header(GlobalDataRest.X_FILENAME, "FF-vitam-format-KO.xml")
            .when().post(IMPORT_FORMAT_URI)
            .then().statusCode(Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    @RunWithCustomExecutor
    public void createAccessionRegister() throws Exception {

        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        stream = PropertiesUtils.getResourceAsStream("accession-register.json");
        final AccessionRegisterDetail register = JsonHandler.getFromInputStream(stream, AccessionRegisterDetail.class);
        given().contentType(ContentType.JSON).body(register)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .when().post(CREATE_FUND_REGISTER_URI)
            .then().statusCode(Status.CREATED.getStatusCode());
        register.setTotalObjects(null);

        given().contentType(ContentType.JSON).body(register)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .when().post(CREATE_FUND_REGISTER_URI)
            .then().statusCode(Status.PRECONDITION_FAILED.getStatusCode());
    }

    @Test
    @RunWithCustomExecutor
    public void findAccessionRegisterDetail() throws Exception {

        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        String contractId = "contractId";

        AccessContractModel contractModel = new AccessContractModel();
        contractModel.setOriginatingAgencies(Sets.newHashSet(originatingAgency));
        contractModel.setName(contractId);
        contractModel.setStatus(ContractStatus.ACTIVE.name());

        mongoDbAccess.insertDocument(JsonHandler.toJsonNode(contractModel), FunctionalAdminCollections.ACCESS_CONTRACT)
            .close();

        stream = PropertiesUtils.getResourceAsStream("accession-register.json");
        final AccessionRegisterDetail register = JsonHandler.getFromInputStream(stream, AccessionRegisterDetail.class);
        given().contentType(ContentType.JSON).body(register)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .when().post(CREATE_FUND_REGISTER_URI)
            .then().statusCode(Status.CREATED.getStatusCode());
        register.setTotalObjects(null);

        Select select = new Select();

        given().contentType(ContentType.JSON).body(select.getFinalSelect())
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .header(GlobalDataRest.X_ACCESS_CONTRAT_ID, contractId)
            .when().post("accession-register/detail/" + originatingAgency)
            .then()
            .body("$results.size()", equalTo(1))
            .statusCode(Status.OK.getStatusCode());
    }

    @Test
    @RunWithCustomExecutor
    public void getFileFormatByID() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        stream = PropertiesUtils.getResourceAsStream("FF-vitam.xml");
        final Select select = new Select();
        select.setQuery(eq("PUID", "x-fmt/2"));
        with()
            .contentType(ContentType.BINARY).body(stream)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .header(GlobalDataRest.X_FILENAME, "FF-vitam.xml")
            .when().post(IMPORT_FORMAT_URI)
            .then().statusCode(Status.CREATED.getStatusCode());

        final String document =
            given()
                .contentType(ContentType.JSON)
                .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
                .body(select.getFinalSelect())
                .when().post(GET_DOCUMENT_FORMAT_URI).getBody().asString();
        final JsonNode jsonDocument = JsonHandler.getFromString(document).get(RESULTS);


        given()
            .contentType(ContentType.JSON)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .body(jsonDocument)
            .pathParam("id_format", jsonDocument.get(0).get("PUID").asText())
            .when().get(GET_BYID_FORMAT_URI + FORMAT_ID_URI)
            .then().statusCode(Status.OK.getStatusCode());
    }

    @Test
    @RunWithCustomExecutor
    public void givenFileFormatByIDWhenNotFoundThenThrowReferentialException()
        throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        stream = PropertiesUtils.getResourceAsStream("FF-vitam.xml");
        final Select select = new Select();
        select.setQuery(eq("PUID", "x-fmt/2"));
        with()
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .contentType(ContentType.BINARY).body(stream)
            .header(GlobalDataRest.X_FILENAME, "FF-vitam.xml")
            .when().post(IMPORT_FORMAT_URI)
            .then().statusCode(Status.CREATED.getStatusCode());

        final String document =
            given()
                .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
                .contentType(ContentType.JSON)
                .body(select.getFinalSelect())
                .when().post(GET_DOCUMENT_FORMAT_URI).getBody().asString();
        final JsonNode jsonDocument = JsonHandler.getFromString(document);

        given()
            .contentType(ContentType.JSON)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .body(jsonDocument)
            .pathParam("id_format", "fake_identifier")
            .when().get(GET_BYID_FORMAT_URI + FORMAT_ID_URI)
            .then().statusCode(Status.NOT_FOUND.getStatusCode());
    }


    @Test
    @RunWithCustomExecutor
    public void findFormat() throws Exception {
        stream = PropertiesUtils.getResourceAsStream("FF-vitam.xml");
        final Select select = new Select();
        select.setQuery(eq("PUID", "x-fmt/2"));
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        with()
            .contentType(ContentType.BINARY).body(stream).header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .header(GlobalDataRest.X_FILENAME, "FF-vitam.xml")
            .when().post(IMPORT_FORMAT_URI)
            .then().statusCode(Status.CREATED.getStatusCode());

        given()
            .contentType(ContentType.JSON)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .body(select.getFinalSelect())
            .when().post(GET_DOCUMENT_FORMAT_URI)
            .then().statusCode(Status.OK.getStatusCode());
    }


    @Test
    @RunWithCustomExecutor
    public void givenFindDocumentWhenNotFoundThenReturnZeroResult()
        throws IOException, InvalidParseOperationException, InvalidCreateOperationException {

        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        stream = PropertiesUtils.getResourceAsStream("FF-vitam.xml");
        final Select select = new Select();
        select.setQuery(eq("fakeName", "fakeValue"));

        with()
            .contentType(ContentType.BINARY).body(stream)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .header(GlobalDataRest.X_FILENAME, "FF-vitam.xml")
            .when().post(IMPORT_FORMAT_URI)
            .then().statusCode(Status.CREATED.getStatusCode());

        given()
            .contentType(ContentType.JSON)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .body(select.getFinalSelect())
            .when().post(GET_DOCUMENT_FORMAT_URI)
            .then().statusCode(Status.OK.getStatusCode());
    }

    @Test
    @RunWithCustomExecutor
    public void givenAWellFormedCSVInputstreamCheckThenReturnOK() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        stream = PropertiesUtils.getResourceAsStream(FILE_TEST_OK);
        given().contentType(ContentType.BINARY).body(stream)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .when().post(CHECK_RULES_URI)
            .then().statusCode(Status.OK.getStatusCode());
    }

    @Test
    @RunWithCustomExecutor
    public void givenANotWellFormedCSVInputstreamCheckThenReturnKO() throws IOException, InvalidParseOperationException {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        streamErrorReport = PropertiesUtils.getResourceAsStream(ERROR_REPORT_CONTENT);
        stream = PropertiesUtils.getResourceAsStream("jeu_donnees_KO_regles_CSV_DuplicatedReference.csv");
        Response rr = given().contentType(ContentType.BINARY).body(stream)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .when().post(CHECK_RULES_URI);
        rr.then().statusCode(Status.BAD_REQUEST.getStatusCode());
        JsonNode responseInputStream = JsonHandler.getFromInputStream(rr.asInputStream());        
        ArrayNode responseArrayNode = (ArrayNode) responseInputStream.get("error").get("line 3");
        JsonNode expectedInputStream = JsonHandler.getFromInputStream(streamErrorReport);
        ArrayNode expectedArrayNode = (ArrayNode) expectedInputStream.get("error").get("line 3");
        assertEquals(responseArrayNode.get(0), expectedArrayNode.get(0));
    }

    @Test
    @RunWithCustomExecutor
    public void givenADecadeMeasureCSVInputstreamCheckThenReturnKO() throws FileNotFoundException {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        stream = PropertiesUtils.getResourceAsStream("jeu_donnees_KO_regles_CSV_Decade_Measure.csv");
        given().contentType(ContentType.BINARY).body(stream)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .when().post(CHECK_RULES_URI)
            .then().statusCode(Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    @RunWithCustomExecutor
    public void givenAnANarchyRuleTypeCSVInputstreamCheckThenReturnKO() throws FileNotFoundException {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        stream = PropertiesUtils.getResourceAsStream("jeu_donnees_KO_regles_CSV_AnarchyRule.csv");
        given().contentType(ContentType.BINARY).body(stream)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .when().post(CHECK_RULES_URI)
            .then().statusCode(Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    @RunWithCustomExecutor
    public void givenWrongDurationTypeCSVInputstreamCheckThenReturnKO() throws FileNotFoundException {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        stream = PropertiesUtils.getResourceAsStream("jeu_donnees_KO_regles_CSV_90000_YEAR.csv");
        given().contentType(ContentType.BINARY).body(stream)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .when().post(CHECK_RULES_URI)
            .then().statusCode(Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    @RunWithCustomExecutor
    public void givenDuplicatedReferenceCSVInputstreamCheckThenReturnKO() throws FileNotFoundException {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        stream = PropertiesUtils.getResourceAsStream("jeu_donnees_KO_regles_CSV_DuplicatedReference.csv");
        given().contentType(ContentType.BINARY).body(stream)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .when().post(CHECK_RULES_URI)
            .then().statusCode(Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    @RunWithCustomExecutor
    public void givenNegativeDurationCSVInputstreamCheckThenReturnKO() throws FileNotFoundException {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        stream = PropertiesUtils.getResourceAsStream("jeu_donnees_KO_regles_CSV_Negative_Duration.csv");
        given().contentType(ContentType.BINARY).body(stream)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .when().post(CHECK_RULES_URI)
            .then().statusCode(Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    @RunWithCustomExecutor
    public void givenReferenceWithWrongCommaCSVInputstreamCheckThenReturnKO() throws FileNotFoundException {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        stream = PropertiesUtils.getResourceAsStream("jeu_donnees_KO_regles_CSV_ReferenceWithWrongComma.csv");
        given().contentType(ContentType.BINARY).body(stream)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .when().post(CHECK_RULES_URI)
            .then().statusCode(Status.BAD_REQUEST.getStatusCode());
    }


    @Test
    @RunWithCustomExecutor
    public void givenUnknownDurationCSVInputstreamCheckThenReturnKO() throws FileNotFoundException {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        stream = PropertiesUtils.getResourceAsStream("jeu_donnees_KO_regles_CSV_UNKNOWN_Duration.csv");
        given().contentType(ContentType.BINARY).body(stream)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .when().post(CHECK_RULES_URI)
            .then().statusCode(Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    @RunWithCustomExecutor
    public void given15000JoursCSVInputstreamCheckThenReturnOK() throws FileNotFoundException {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        stream = PropertiesUtils.getResourceAsStream("jeu_donnees_OK_regles_CSV_15000Jours.csv");
        given().contentType(ContentType.BINARY).body(stream)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .when().post(CHECK_RULES_URI)
            .then().statusCode(Status.OK.getStatusCode());
    }

    @Test
    @RunWithCustomExecutor
    public void givenUnlimitedDurationCSVInputstreamCheckThenReturnOK() throws FileNotFoundException {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        stream = PropertiesUtils.getResourceAsStream("jeu_donnees_OK_regles_CSV_unLimiTEd.csv");
        given().contentType(ContentType.BINARY).body(stream)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .when().post(CHECK_RULES_URI)
            .then().statusCode(Status.OK.getStatusCode());
    }

    @Test
    @RunWithCustomExecutor
    public void given600000DAYCSVInputstreamCheckThenReturnKO() throws FileNotFoundException {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        stream = PropertiesUtils.getResourceAsStream("jeu_donnees_KO_regles_600000_DAY.csv");
        given().contentType(ContentType.BINARY).body(stream)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .when().post(CHECK_RULES_URI)
            .then().statusCode(Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    @RunWithCustomExecutor
    public void given90000YEARCSVInputstreamCheckThenReturnKO() throws FileNotFoundException {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        stream = PropertiesUtils.getResourceAsStream("jeu_donnees_KO_regles_CSV_90000_YEAR.csv");
        given().contentType(ContentType.BINARY).body(stream)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .when().post(CHECK_RULES_URI)
            .then().statusCode(Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    @RunWithCustomExecutor
    public void insertRulesFile() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        stream = PropertiesUtils.getResourceAsStream(FILE_TEST_OK);
        given().contentType(ContentType.BINARY).body(stream).header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .header(GlobalDataRest.X_FILENAME, FILE_TEST_OK)
            .when().post(IMPORT_RULES_URI)
            .then().statusCode(Status.CREATED.getStatusCode());

        stream = PropertiesUtils.getResourceAsStream(FILE_TEST_OK);
        given().contentType(ContentType.BINARY).body(stream).header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .when().post(IMPORT_RULES_URI)
            .then().statusCode(Status.CREATED.getStatusCode());
    }

    @Test
    @RunWithCustomExecutor
    public void insertRulesForDifferentTenantsSuccess() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        stream = PropertiesUtils.getResourceAsStream(FILE_TEST_OK);
        given().contentType(ContentType.BINARY).body(stream).header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .header(GlobalDataRest.X_FILENAME, FILE_TEST_OK)
            .when().post(IMPORT_RULES_URI)
            .then().statusCode(Status.CREATED.getStatusCode());

        mongoDbAccess.deleteCollection(FunctionalAdminCollections.RULES).close();

        VitamThreadUtils.getVitamSession().setTenantId(1);
        stream = PropertiesUtils.getResourceAsStream(FILE_TEST_OK);
        given().contentType(ContentType.BINARY).body(stream).header(GlobalDataRest.X_TENANT_ID, 1)
            .header(GlobalDataRest.X_FILENAME, FILE_TEST_OK)
            .when().post(IMPORT_RULES_URI)
            .then().statusCode(Status.CREATED.getStatusCode());
    }

    @Test
    @RunWithCustomExecutor
    public void getRuleByID() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        stream = PropertiesUtils.getResourceAsStream(FILE_TEST_OK);
        final Select select = new Select();
        select.setQuery(eq("RuleId", "APP-00001"));
        with()
            .contentType(ContentType.BINARY).body(stream)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .header(GlobalDataRest.X_FILENAME, FILE_TEST_OK)
            .when().post(IMPORT_RULES_URI)
            .then().statusCode(Status.CREATED.getStatusCode());

        final String document =
            given()
                .contentType(ContentType.JSON)
                .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
                .body(select.getFinalSelect())
                .when().post(GET_DOCUMENT_RULES_URI).getBody().asString();
        final JsonNode jsonDocument = JsonHandler.getFromString(document).get(RESULTS);

        given()
            .contentType(ContentType.JSON)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .body(jsonDocument)
            .pathParam("id_rule", jsonDocument.get(0).get("RuleId").asText())
            .when().get(GET_BYID_RULES_URI + RULES_ID_URI)
            .then().statusCode(Status.OK.getStatusCode());
    }

    @Test
    @RunWithCustomExecutor
    public void givenFakeRuleByIDTheReturnNotFound()
        throws Exception {
        stream = PropertiesUtils.getResourceAsStream(FILE_TEST_OK);
        final Select select = new Select();
        select.setQuery(eq("RuleId", "APP-00001"));
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        with()
            .contentType(ContentType.BINARY).body(stream)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .header(GlobalDataRest.X_FILENAME, FILE_TEST_OK)
            .when().post(IMPORT_RULES_URI)
            .then().statusCode(Status.CREATED.getStatusCode());

        final String document =
            given()
                .contentType(ContentType.JSON)
                .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
                .body(select.getFinalSelect())
                .when().post(GET_DOCUMENT_RULES_URI).getBody().asString();
        final JsonNode jsonDocument = JsonHandler.getFromString(document);

        given()
            .contentType(ContentType.JSON)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .body(jsonDocument)
            .when().get(GET_BYID_RULES_URI + "/fake_identifier")
            .then().statusCode(Status.NOT_FOUND.getStatusCode());
    }


    @Test
    @RunWithCustomExecutor
    public void getDocumentRulesFile() throws InvalidCreateOperationException, FileNotFoundException {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        stream = PropertiesUtils.getResourceAsStream(FILE_TEST_OK);
        final Select select = new Select();
        select.setQuery(eq("RuleId", "APP-00001"));
        with()
            .contentType(ContentType.BINARY).body(stream)
            .header(GlobalDataRest.X_TENANT_ID, 0)
            .header(GlobalDataRest.X_FILENAME, FILE_TEST_OK)
            .when().post(IMPORT_RULES_URI)
            .then().statusCode(Status.CREATED.getStatusCode());

        given()
            .contentType(ContentType.JSON)
            .header(GlobalDataRest.X_TENANT_ID, 0)
            .body(select.getFinalSelect())
            .when().post(GET_DOCUMENT_RULES_URI)
            .then().statusCode(Status.OK.getStatusCode());
    }

    @Test
    @RunWithCustomExecutor
    public void testImportRulesForTenant0_ThenSearchForTenant1ReturnNotFound()
        throws InvalidCreateOperationException, FileNotFoundException {
        stream = PropertiesUtils.getResourceAsStream(FILE_TEST_OK);
        final Select select = new Select();
        select.setQuery(eq("RuleId", "APP-00001"));
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        with()
            .contentType(ContentType.BINARY).body(stream)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .header(GlobalDataRest.X_FILENAME, FILE_TEST_OK)
            .when().post(IMPORT_RULES_URI)
            .then().statusCode(Status.CREATED.getStatusCode());

        given()
            .contentType(ContentType.JSON)
            .header(GlobalDataRest.X_TENANT_ID, 1)
            .body(select.getFinalSelect())
            .when().post(GET_DOCUMENT_RULES_URI)
            .then().statusCode(Status.OK.getStatusCode());
    }

    @Test
    @RunWithCustomExecutor
    public void givenFindDocumentRulesFileWhenNotFoundThenReturnNotFound()
        throws IOException, InvalidParseOperationException, InvalidCreateOperationException {

        stream = PropertiesUtils.getResourceAsStream(FILE_TEST_OK);
        final Select select = new Select();
        select.setQuery(eq("fakeName", "fakeValue"));

        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        with()
            .contentType(ContentType.BINARY).body(stream)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .header(GlobalDataRest.X_FILENAME, FILE_TEST_OK)
            .when().post(IMPORT_RULES_URI)
            .then().statusCode(Status.CREATED.getStatusCode());

        given()
            .contentType(ContentType.JSON)
            .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
            .body(select.getFinalSelect())
            .when().post(GET_DOCUMENT_RULES_URI)
            .then().statusCode(Status.OK.getStatusCode());
    }
}
