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

import static com.jayway.restassured.RestAssured.get;
import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.RestAssured.with;
import static fr.gouv.vitam.common.database.builder.query.QueryHelper.eq;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.ws.rs.core.Response.Status;

import org.jhades.JHades;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;

import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.SystemPropertyUtil;
import fr.gouv.vitam.common.database.builder.request.exception.InvalidCreateOperationException;
import fr.gouv.vitam.common.database.builder.request.single.Select;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamApplicationServerException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.junit.JunitHelper;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.server.VitamServer;
import fr.gouv.vitam.common.server.application.configuration.DbConfigurationImpl;
import fr.gouv.vitam.functional.administration.common.server.MongoDbAccessAdminFactory;
import fr.gouv.vitam.functional.administration.common.server.MongoDbAccessReferential;

public class AdminManagementResourceTest {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(AdminManagementResourceTest.class);
    private static final String ADMIN_MANAGEMENT_CONF = "functional-administration-test.conf";


    private static final String RESOURCE_URI = "/adminmanagement/v1";
    private static final String STATUS_URI = "/status";
    private static final String CHECK_FORMAT_URI = "/format/check";
    private static final String IMPORT_FORMAT_URI = "/format/import";
    private static final String DELETE_FORMAT_URI = "/format/delete";

    private static final String GET_BYID_FORMAT_URI = "/format";
    private static final String FORMAT_ID_URI = "/{id_format}";

    private static final String GET_DOCUMENT_FORMAT_URI = "/format/document";

    private static final String CHECK_RULES_URI = "/rules/check";
    private static final String IMPORT_RULES_URI = "/rules/import";
    private static final String DELETE_RULES_URI = "/rules/delete";

    private static final String GET_BYID_RULES_URI = "/rules";
    private static final String RULES_ID_URI = "/{id_rule}";

    private static final String GET_DOCUMENT_RULES_URI = "/rules/document";

    static MongodExecutable mongodExecutable;
    static MongodProcess mongod;
    static MongoDbAccessReferential mongoDbAccess;
    static String DATABASE_NAME = "vitam-test";
    private static String DATABASE_HOST = "localhost";

    Select select = new Select();

    private static VitamServer vitamServer;
    private InputStream stream;
    private AdminManagementApplication adminApplication;
    private static JunitHelper junitHelper;
    private static int serverPort;
    private static int databasePort;
    private static AdminManagementConfiguration adminManegement;
    private static File functionalAdmin;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {

        new JHades().overlappingJarsReport();

        junitHelper = new JunitHelper();
        databasePort = junitHelper.findAvailablePort();

        functionalAdmin = PropertiesUtils.findFile(ADMIN_MANAGEMENT_CONF);
        final AdminManagementConfiguration realfunctionalAdmin =
            PropertiesUtils.readYaml(functionalAdmin, AdminManagementConfiguration.class);
        realfunctionalAdmin.setDbPort(databasePort);
        try (FileOutputStream outputStream = new FileOutputStream(functionalAdmin)) {
            final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            mapper.writeValue(outputStream, realfunctionalAdmin);
        }

        final MongodStarter starter = MongodStarter.getDefaultInstance();
        mongodExecutable = starter.prepare(new MongodConfigBuilder()
            .version(Version.Main.PRODUCTION)
            .net(new Net(databasePort, Network.localhostIsIPv6()))
            .build());
        mongod = mongodExecutable.start();

        mongoDbAccess =
            MongoDbAccessAdminFactory.create(new DbConfigurationImpl(DATABASE_HOST, databasePort, "vitam-test"));

        serverPort = junitHelper.findAvailablePort();

        // TODO lors de l'activation des tests parallèles est ce que cela fonctionne encore? #jettyConfig
        SystemPropertyUtil.set(VitamServer.PARAMETER_JETTY_SERVER_PORT, Integer.toString(serverPort));

        try {
            AdminManagementApplication.startApplication(new String[] {functionalAdmin.getAbsolutePath()});
        } catch (final VitamApplicationServerException e) {
            LOGGER.error(e);
            throw new IllegalStateException(
                "Cannot start the Admin Management Server", e);
        }

        RestAssured.port = serverPort;
        RestAssured.basePath = RESOURCE_URI;

    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        LOGGER.debug("Ending tests");
        try {
            AdminManagementApplication.stop();
        } catch (final VitamApplicationServerException e) {
            LOGGER.error(e);
        }

        mongod.stop();
        mongodExecutable.stop();
        junitHelper.releasePort(databasePort);
        junitHelper.releasePort(serverPort);
    }

    @After
    public void tearDown() {
        with()
            .when().delete(DELETE_FORMAT_URI)
            .then().statusCode(Status.OK.getStatusCode());
    }

    @Test
    public final void testGetStatus() {
        get(STATUS_URI).then().statusCode(Status.NO_CONTENT.getStatusCode());
    }

    @Test
    public void givenAWellFormedXMLInputstreamCheckThenReturnOK() {
        stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("FF-vitam.xml");
        given().contentType(ContentType.BINARY).body(stream)
            .when().post(CHECK_FORMAT_URI)
            .then().statusCode(Status.OK.getStatusCode());
    }

    @Test
    public void givenANotWellFormedXMLInputstreamCheckThenReturnKO() {
        stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("FF-vitam-format-KO.xml");
        given().contentType(ContentType.BINARY).body(stream)
            .when().post(CHECK_FORMAT_URI)
            .then().statusCode(Status.PRECONDITION_FAILED.getStatusCode());
    }

    @Test
    public void insertAPronomFile() {
        stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("FF-vitam.xml");
        given().contentType(ContentType.BINARY).body(stream)
            .when().post(IMPORT_FORMAT_URI)
            .then().statusCode(Status.OK.getStatusCode());

        stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("FF-vitam-format-KO.xml");
        given().contentType(ContentType.BINARY).body(stream)
            .when().post(IMPORT_FORMAT_URI)
            .then().statusCode(Status.PRECONDITION_FAILED.getStatusCode());
    }

    @Test
    public void deletePronom() {
        given()
            .when().delete(DELETE_FORMAT_URI)
            .then().statusCode(Status.OK.getStatusCode());
    }

    @Test
    public void getFileFormatByID() throws InvalidCreateOperationException, InvalidParseOperationException {
        stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("FF-vitam.xml");
        Select select = new Select();
        select.setQuery(eq("PUID", "x-fmt/2"));
        with()
            .contentType(ContentType.BINARY).body(stream)
            .when().post(IMPORT_FORMAT_URI)
            .then().statusCode(Status.OK.getStatusCode());

        String document =
            given()
                .contentType(ContentType.JSON)
                .body(select.getFinalSelect())
                .when().post(GET_DOCUMENT_FORMAT_URI).getBody().asString();
        JsonNode jsonDocument = JsonHandler.getFromString(document);


        given()
            .contentType(ContentType.JSON)
            .body(jsonDocument)
            .pathParam("id_format", jsonDocument.get(0).get("_id").asText())
            .when().post(GET_BYID_FORMAT_URI + FORMAT_ID_URI)
            .then().statusCode(Status.OK.getStatusCode());
    }

    @Test
    public void givenFileFormatByIDWhenNotFoundThenThrowReferentialException()
        throws InvalidCreateOperationException, InvalidParseOperationException {
        stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("FF-vitam.xml");
        Select select = new Select();
        select.setQuery(eq("PUID", "x-fmt/2"));
        with()
            .contentType(ContentType.BINARY).body(stream)
            .when().post(IMPORT_FORMAT_URI)
            .then().statusCode(Status.OK.getStatusCode());

        String document =
            given()
                .contentType(ContentType.JSON)
                .body(select.getFinalSelect())
                .when().post(GET_DOCUMENT_FORMAT_URI).getBody().asString();
        JsonNode jsonDocument = JsonHandler.getFromString(document);

        given()
            .contentType(ContentType.JSON)
            .body(jsonDocument)
            .pathParam("id_format", "fake_identifier")
            .when().post(GET_BYID_FORMAT_URI + FORMAT_ID_URI)
            .then().statusCode(Status.NOT_FOUND.getStatusCode());
    }


    @Test
    public void getDocument() throws InvalidCreateOperationException {
        stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("FF-vitam.xml");
        Select select = new Select();
        select.setQuery(eq("PUID", "x-fmt/2"));
        with()
            .contentType(ContentType.BINARY).body(stream)
            .when().post(IMPORT_FORMAT_URI)
            .then().statusCode(Status.OK.getStatusCode());

        given()
            .contentType(ContentType.JSON)
            .body(select.getFinalSelect())
            .when().post(GET_DOCUMENT_FORMAT_URI)
            .then().statusCode(Status.OK.getStatusCode());
    }


    @Test
    public void givenFindDocumentWhenNotFoundThenThrowReferentialException()
        throws IOException, InvalidParseOperationException, InvalidCreateOperationException {

        stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("FF-vitam.xml");
        Select select = new Select();
        select.setQuery(eq("fakeName", "fakeValue"));

        with()
            .contentType(ContentType.BINARY).body(stream)
            .when().post(IMPORT_FORMAT_URI)
            .then().statusCode(Status.OK.getStatusCode());

        given()
            .contentType(ContentType.JSON)
            .body(select.getFinalSelect())
            .when().post(GET_DOCUMENT_FORMAT_URI)
            .then().statusCode(Status.NOT_FOUND.getStatusCode());
    }

    /************************** rules Management ***************************************************/
    @Test
    public void givenAWellFormedCSVInputstreamCheckThenReturnOK() {
        stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("jeu_donnees_OK_regles_CSV.csv");
        given().contentType(ContentType.BINARY).body(stream)
            .when().post(CHECK_RULES_URI)
            .then().statusCode(Status.OK.getStatusCode());
    }

    @Test
    public void givenANotWellFormedCSVInputstreamCheckThenReturnKO() {
        stream = Thread.currentThread().getContextClassLoader()
            .getResourceAsStream("jeu_donnees_KO_regles_CSV_Parameters.csv");
        given().contentType(ContentType.BINARY).body(stream)
            .when().post(CHECK_RULES_URI)
            .then().statusCode(Status.PRECONDITION_FAILED.getStatusCode());
    }

    @Test
    public void insertRulesFile() {
        stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("jeu_donnees_OK_regles_CSV.csv");
        given().contentType(ContentType.BINARY).body(stream)
            .when().post(IMPORT_RULES_URI)
            .then().statusCode(Status.OK.getStatusCode());

        stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("jeu_donnees_OK_regles_CSV.csv");
        given().contentType(ContentType.BINARY).body(stream)
            .when().post(IMPORT_RULES_URI)
            .then().statusCode(Status.OK.getStatusCode());
    }

    @Test
    public void deleteRulesFile() {
        given()
            .when().delete(DELETE_RULES_URI)
            .then().statusCode(Status.OK.getStatusCode());
    }

    @Test
    public void getRuleByID() throws InvalidCreateOperationException, InvalidParseOperationException {
        stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("jeu_donnees_OK_regles_CSV.csv");
        Select select = new Select();
        select.setQuery(eq("RuleId", "APP-00001"));
        with()
            .contentType(ContentType.BINARY).body(stream)
            .when().post(IMPORT_RULES_URI)
            .then().statusCode(Status.OK.getStatusCode());

        String document =
            given()
                .contentType(ContentType.JSON)
                .body(select.getFinalSelect())
                .when().post(GET_DOCUMENT_RULES_URI).getBody().asString();
        JsonNode jsonDocument = JsonHandler.getFromString(document);


        given()
            .contentType(ContentType.JSON)
            .body(jsonDocument)
            .pathParam("id_rule", jsonDocument.get(0).get("RuleId").asText())
            .when().post(GET_BYID_RULES_URI + RULES_ID_URI)
            .then().statusCode(Status.NOT_FOUND.getStatusCode());
    }

    @Test
    public void givenFakeRuleByIDTheReturnNotFound()
        throws InvalidCreateOperationException, InvalidParseOperationException {
        stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("jeu_donnees_OK_regles_CSV.csv");
        Select select = new Select();
        select.setQuery(eq("RuleId", "APP-00001"));
        with()
            .contentType(ContentType.BINARY).body(stream)
            .when().post(IMPORT_RULES_URI)
            .then().statusCode(Status.OK.getStatusCode());

        String document =
            given()
                .contentType(ContentType.JSON)
                .body(select.getFinalSelect())
                .when().post(GET_DOCUMENT_RULES_URI).getBody().asString();
        JsonNode jsonDocument = JsonHandler.getFromString(document);

        given()
            .contentType(ContentType.JSON)
            .body(jsonDocument)
            .pathParam("id_rule", "fake_identifier")
            .when().post(GET_BYID_RULES_URI + RULES_ID_URI)
            .then().statusCode(Status.NOT_FOUND.getStatusCode());
    }


    @Test
    public void getDocumentRulesFile() throws InvalidCreateOperationException {
        stream = Thread.currentThread().getContextClassLoader()
            .getResourceAsStream("jeu_donnees_OK_regles_CSV.csv");
        Select select = new Select();
        select.setQuery(eq("RuleId", "APP-00001"));
        with()
            .contentType(ContentType.BINARY).body(stream)
            .when().post(IMPORT_RULES_URI)
            .then().statusCode(Status.OK.getStatusCode());

        given()
            .contentType(ContentType.JSON)
            .body(select.getFinalSelect())
            .when().post(GET_DOCUMENT_RULES_URI)
            .then().statusCode(Status.OK.getStatusCode());
    }


    @Test
    public void givenFindDocumentRulesFileWhenNotFoundThenReturnNotFound()
        throws IOException, InvalidParseOperationException, InvalidCreateOperationException {

        stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("jeu_donnees_OK_regles_CSV.csv");
        Select select = new Select();
        select.setQuery(eq("fakeName", "fakeValue"));

        with()
            .contentType(ContentType.BINARY).body(stream)
            .when().post(IMPORT_RULES_URI)
            .then().statusCode(Status.OK.getStatusCode());

        given()
            .contentType(ContentType.JSON)
            .body(select.getFinalSelect())
            .when().post(GET_DOCUMENT_RULES_URI)
            .then().statusCode(Status.NOT_FOUND.getStatusCode());
    }

}
