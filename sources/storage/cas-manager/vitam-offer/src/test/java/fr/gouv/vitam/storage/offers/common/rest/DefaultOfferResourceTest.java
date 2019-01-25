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
package fr.gouv.vitam.storage.offers.common.rest;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.io.Files;
import com.mongodb.client.FindIterable;
import com.mongodb.client.model.Filters;
import fr.gouv.vitam.common.GlobalDataRest;
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.client.VitamClientFactory;
import fr.gouv.vitam.common.database.collections.VitamCollection;
import fr.gouv.vitam.common.digest.Digest;
import fr.gouv.vitam.common.digest.DigestType;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamApplicationServerException;
import fr.gouv.vitam.common.exception.VitamException;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.junit.FakeInputStream;
import fr.gouv.vitam.common.junit.JunitHelper;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.mongo.MongoRule;
import fr.gouv.vitam.common.server.application.configuration.MongoDbNode;
import fr.gouv.vitam.common.storage.StorageConfiguration;
import fr.gouv.vitam.common.storage.cas.container.api.ContentAddressableStorageAbstract;
import fr.gouv.vitam.storage.engine.common.model.DataCategory;
import fr.gouv.vitam.storage.engine.common.model.OfferLog;
import fr.gouv.vitam.storage.engine.common.model.Order;
import fr.gouv.vitam.storage.engine.common.model.request.OfferLogRequest;
import fr.gouv.vitam.storage.offers.common.database.OfferCollections;
import io.restassured.RestAssured;
import io.restassured.response.ResponseBody;
import org.apache.commons.io.FileUtils;
import org.bson.Document;
import org.hamcrest.Matchers;
import org.jhades.JHades;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * DefaultOfferResource Test
 */
public class DefaultOfferResourceTest {

    private static final String WORKSPACE_OFFER_CONF = "storage-default-offer.conf";
    private static final String DATABASE_NAME = "Vitam";

    private static final String REST_URI = "/offer/v1";
    private static int serverPort;
    private static JunitHelper junitHelper;
    private static final String OBJECTS_URI = "/objects";
    private static final String OBJECT_TYPE_URI = "/{type}";
    private static final String OBJECT_ID_URI = "/{id}";
    private static final String STATUS_URI = "/status";
    private static final String LOG_URI = "/logs";
    private static final String UNIT_CODE = "UNIT";
    private static final String OBJECT_CODE = "OBJECT";
    private static final String METADATA = "/metadatas";

    private static final String DEFAULT_STORAGE_CONF = "default-storage.conf";
    private static final String ARCHIVE_FILE_TXT = "archivefile.txt";
    private static final String ARCHIVE_FILE_V2_TXT = "archivefile_v2.txt";
    private static final String PREFIX = GUIDFactory.newGUID().getId();

    private static final ObjectMapper OBJECT_MAPPER;
    private static DefaultOfferMain application;

    static {
        OBJECT_MAPPER = new ObjectMapper(new JsonFactory());
        OBJECT_MAPPER.disable(SerializationFeature.INDENT_OUTPUT);
    }

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(DefaultOfferResourceTest.class);

    @ClassRule
    public static MongoRule mongoRule =
        new MongoRule(VitamCollection.getMongoClientOptions(), DATABASE_NAME);

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        for (OfferCollections o : OfferCollections.values()) {
            o.setPrefix(PREFIX);
            mongoRule.addCollectionToBePurged(o.getName());
        }

        // Identify overlapping in particular jsr311
        new JHades().overlappingJarsReport();

        final File workspaceOffer = PropertiesUtils.findFile(WORKSPACE_OFFER_CONF);
        final OfferConfiguration realWorkspaceOffer =
            PropertiesUtils.readYaml(workspaceOffer, OfferConfiguration.class);
        List<MongoDbNode> mongoDbNodes = realWorkspaceOffer.getMongoDbNodes();
        mongoDbNodes.get(0).setDbPort(MongoRule.getDataBasePort());
        realWorkspaceOffer.setMongoDbNodes(mongoDbNodes);
        File newWorkspaceOfferConf = File.createTempFile("test", WORKSPACE_OFFER_CONF, workspaceOffer.getParentFile());
        PropertiesUtils.writeYaml(newWorkspaceOfferConf, realWorkspaceOffer);

        try {
            junitHelper = JunitHelper.getInstance();
            serverPort = junitHelper.findAvailablePort();

            RestAssured.port = serverPort;
            RestAssured.basePath = REST_URI;

            application = new DefaultOfferMain(newWorkspaceOfferConf.getAbsolutePath());
            application.start();
            ContentAddressableStorageAbstract.disableContainerCaching();
        } catch (final VitamApplicationServerException e) {
            LOGGER.error(e);
            throw new IllegalStateException("Cannot start the Wokspace Offer Application Server", e);
        }
    }

    @AfterClass
    public static void tearDownAfterClass() {
        mongoRule.handleAfterClass();

        LOGGER.debug("Ending tests");
        try {
            application.stop();
        } catch (final VitamApplicationServerException e) {
            LOGGER.error(e);
        }
        junitHelper.releasePort(serverPort);
        VitamClientFactory.resetConnections();
    }

    @Before
    public void initCollections() throws VitamException {
        try {
            // restart server to reinit collection sequence
            application.stop();
            application.start();
        } catch (VitamApplicationServerException e) {
            throw new VitamException("could not restart server");
        }
    }


    @After
    public void deleteExistingFiles() throws Exception {
        final StorageConfiguration conf = PropertiesUtils.readYaml(PropertiesUtils.findFile(DEFAULT_STORAGE_CONF),
            StorageConfiguration.class);
        // delete directories recursively
        FileUtils.deleteDirectory((new File(conf.getStoragePath() + "/1_unit")));
        FileUtils.deleteDirectory((new File(conf.getStoragePath() + "/2_unit")));
        FileUtils.deleteDirectory((new File(conf.getStoragePath() + "/0_object")));
        FileUtils.deleteDirectory((new File(conf.getStoragePath() + "/1_object")));
        FileUtils.deleteDirectory((new File(conf.getStoragePath() + "/2_object")));

        mongoRule.handleAfter();
    }

    @Test
    public void getCapacityTestBadRequest() {
        given().head(OBJECTS_URI + "/" + UNIT_CODE).then().statusCode(Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    public void getCapacityTestOk() {
        // test
        given().header(GlobalDataRest.X_TENANT_ID, 1).when().head(OBJECTS_URI + "/" + UNIT_CODE).then().statusCode(200);
    }

    @Test
    public void getCapacityTestNoContainers() {
        // test
        given().header(GlobalDataRest.X_TENANT_ID, 1)
            .when().head(OBJECTS_URI + "/" + UNIT_CODE).then().statusCode(Status.OK.getStatusCode());
    }

    @Test
    public void getObjectTestPreconditionFailed() {
        // no tenant id
        given().get(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1").then().statusCode(412);

    }

    @Test
    public void getObjectTestNotFound() {
        // not found
        given().header(GlobalDataRest.X_TENANT_ID, "1").contentType(MediaType.APPLICATION_JSON).then()
            .statusCode(Status.NOT_FOUND.getStatusCode()).when()
            .get(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1");
    }

    @Test
    public void getObjectTestOK() throws Exception {

        try (FileInputStream in = new FileInputStream(PropertiesUtils.findFile(ARCHIVE_FILE_TXT))) {
            assertNotNull(in);
            with().header(GlobalDataRest.X_TENANT_ID, "1")
                .header(GlobalDataRest.VITAM_CONTENT_LENGTH, "8766")
                .header(GlobalDataRest.X_DIGEST_ALGORITHM, DigestType.SHA512.getName())
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(in).when()
                .put(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1");
        }

        checkOfferDatabaseExistingDocument("1_object", "id1");

        // found
        given().header(GlobalDataRest.X_TENANT_ID, "1").contentType(MediaType.APPLICATION_JSON).then()
            .statusCode(Status.OK.getStatusCode()).when()
            .get(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1");
    }

    @Test
    public void getObjectWithdot() throws Exception {

        try (FileInputStream in = new FileInputStream(PropertiesUtils.findFile(ARCHIVE_FILE_TXT))) {
            assertNotNull(in);
            with().header(GlobalDataRest.X_TENANT_ID, "1")
                .header(GlobalDataRest.VITAM_CONTENT_LENGTH, "8766")
                .header(GlobalDataRest.X_DIGEST_ALGORITHM, DigestType.SHA512.getName())
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(in).when()
                .put(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1.xml");
        }

        checkOfferDatabaseExistingDocument("1_object", "id1.xml");

        // found
        given().header(GlobalDataRest.X_TENANT_ID, "1").contentType(MediaType.APPLICATION_JSON).then()
            .statusCode(Status.OK.getStatusCode()).when()
            .get(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1.xml");
    }

    @Test
    public void putObjectTest() throws Exception {
        checkOfferDatabaseEmptiness();

        // no tenant id
        given().contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(GlobalDataRest.VITAM_CONTENT_LENGTH, 0)
            .header(GlobalDataRest.X_DIGEST_ALGORITHM, DigestType.SHA512.getName())
            .contentType(MediaType.APPLICATION_OCTET_STREAM).body(new ByteArrayInputStream(new byte[0])).when()
            .put(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, UNIT_CODE, "id1").then().statusCode(400);

        // no size
        given().contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(GlobalDataRest.X_TENANT_ID, "1")
            .header(GlobalDataRest.X_DIGEST_ALGORITHM, DigestType.SHA512.getName())
            .contentType(MediaType.APPLICATION_OCTET_STREAM).body(new ByteArrayInputStream(new byte[0])).when()
            .put(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, UNIT_CODE, "id1").then().statusCode(400);

        // no digest type
        given().contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(GlobalDataRest.X_TENANT_ID, "1")
            .header(GlobalDataRest.VITAM_CONTENT_LENGTH, 0)
            .contentType(MediaType.APPLICATION_OCTET_STREAM).body(new ByteArrayInputStream(new byte[0])).when()
            .put(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, UNIT_CODE, "id1").then().statusCode(400);

        // ok
        try (FileInputStream in = new FileInputStream(PropertiesUtils.findFile(ARCHIVE_FILE_TXT))) {
            given().header(GlobalDataRest.X_TENANT_ID, "2")
                .header(GlobalDataRest.VITAM_CONTENT_LENGTH, "8766")
                .header(GlobalDataRest.X_DIGEST_ALGORITHM, DigestType.SHA512.getName())
                .contentType(MediaType.APPLICATION_OCTET_STREAM).body(in).when()
                .put(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, UNIT_CODE, "id1").then().statusCode(201);
        }

        // check
        final StorageConfiguration conf = PropertiesUtils.readYaml(PropertiesUtils.findFile(DEFAULT_STORAGE_CONF),
            StorageConfiguration.class);
        final File container = new File(conf.getStoragePath() + "/2_unit");
        assertTrue(container.exists());
        assertTrue(container.isDirectory());
        final File object = new File(container.getAbsolutePath(), "/id1");
        assertTrue(object.exists());
        assertFalse(object.isDirectory());

        checkOfferDatabaseExistingDocument("2_unit", "id1");

        assertTrue(com.google.common.io.Files.equal(PropertiesUtils.findFile(ARCHIVE_FILE_TXT), object));
    }

    @Test
    public void putObjectOverrideExistingInRewritableContainer() throws Exception {
        checkOfferDatabaseEmptiness();

        // Given
        File file1 = PropertiesUtils.findFile(ARCHIVE_FILE_TXT);
        File file2 = PropertiesUtils.findFile(ARCHIVE_FILE_V2_TXT);

        // When
        putObject(DataCategory.UNIT, "myfile", file1, Status.CREATED);
        putObject(DataCategory.UNIT, "myfile", file2, Status.CREATED);

        // Then
        checkWrittenFile(file2, "2_unit", "myfile");
        checkOfferDatabaseExistingDocument("2_unit", "myfile", 2);
    }

    @Test
    public void putObjectOverrideExistingWithSameContentInNonRewritableContainer() throws Exception {
        checkOfferDatabaseEmptiness();

        // Given
        File file = PropertiesUtils.findFile(ARCHIVE_FILE_TXT);

        // When
        putObject(DataCategory.OBJECT, "myfile", file, Status.CREATED);
        putObject(DataCategory.OBJECT, "myfile", file, Status.CREATED);

        // Then
        checkWrittenFile(file, "2_object", "myfile");
        checkOfferDatabaseExistingDocument("2_object", "myfile", 1);
    }

    @Test
    public void putObjectOverrideExistingWitNewContentInNonRewritableContainer() throws Exception {
        checkOfferDatabaseEmptiness();

        // Given
        File file1 = PropertiesUtils.findFile(ARCHIVE_FILE_TXT);
        File file2 = PropertiesUtils.findFile(ARCHIVE_FILE_V2_TXT);

        // When
        putObject(DataCategory.OBJECT, "myfile", file1, Status.CREATED);
        putObject(DataCategory.OBJECT, "myfile", file2, Status.CONFLICT);

        // Then
        checkWrittenFile(file1, "2_object", "myfile");
        checkOfferDatabaseExistingDocument("2_object", "myfile", 1);
    }

    private void checkWrittenFile(File file, String container, String objectId) throws IOException {
        // check
        final StorageConfiguration conf = PropertiesUtils.readYaml(PropertiesUtils.findFile(DEFAULT_STORAGE_CONF),
            StorageConfiguration.class);
        final File object = new File(conf.getStoragePath() + "/" + container + "/" + objectId);
        assertThat(Files.equal(file, object)).isTrue();
    }

    private void putObject(DataCategory dataCategory, String id, File file, Status expectedStatus)
        throws IOException, InvalidParseOperationException {

        io.restassured.response.Response response;
        try (FileInputStream in = new FileInputStream(file)) {
            response = given().header(GlobalDataRest.X_TENANT_ID, "2")
                .header(GlobalDataRest.VITAM_CONTENT_LENGTH, file.length())
                .header(GlobalDataRest.X_DIGEST_ALGORITHM, DigestType.SHA512.getName())
                .contentType(MediaType.APPLICATION_OCTET_STREAM).body(in).when()
                .put(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, dataCategory.name(), id)
                .andReturn();
        }

        assertThat(response.statusCode()).isEqualTo(expectedStatus.getStatusCode());

        if (expectedStatus == Status.CREATED) {
            JsonNode content = JsonHandler.getFromInputStream(response.body().asInputStream());
            assertThat(content.get("size").longValue()).isEqualTo(file.length());
            Digest digest = new Digest(DigestType.SHA512);
            digest.update(file);
            assertThat(content.get("digest").textValue()).isEqualTo(digest.digestHex());

        }
    }

    @Test
    public void headObjectTest() throws Exception {
        // no tenant id
        given().head(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, UNIT_CODE, "id1").then().statusCode(400);

        // no object
        given().header(GlobalDataRest.X_TENANT_ID, 2).and()
            .head(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, UNIT_CODE, "id1")
            .then().statusCode(404);

        // object
        final StorageConfiguration conf = PropertiesUtils.readYaml(PropertiesUtils.findFile(DEFAULT_STORAGE_CONF),
            StorageConfiguration.class);
        final File container = new File(conf.getStoragePath() + "/1_unit");

        container.mkdir();
        final File object = new File(container.getAbsolutePath(), "/id1");
        object.createNewFile();
        given().header(GlobalDataRest.X_TENANT_ID, 1).and()
            .head(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, UNIT_CODE, "id1")
            .then().statusCode(204);
    }

    @Test
    public void deleteObjectTestNotExisting() {
        // no object found -> 404
        given().header(GlobalDataRest.X_TENANT_ID, 0).header(GlobalDataRest.X_DIGEST, "digest")
            .header(GlobalDataRest.X_DIGEST_ALGORITHM, VitamConfiguration.getDefaultDigestType().getName())
            .delete(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1").then().statusCode(404);
    }

    @Test
    public void deleteObjectTestBadRequests() {
        // bad requests (missing headers) -> 400
        given().header(GlobalDataRest.X_DIGEST, "digest")
            .header(GlobalDataRest.X_DIGEST_ALGORITHM, VitamConfiguration.getDefaultDigestType().getName())
            .delete(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1").then().statusCode(400);

    }

    @Test
    public void deleteObjectTest() throws Exception {

        try (FileInputStream in = new FileInputStream(PropertiesUtils.findFile(ARCHIVE_FILE_TXT))) {
            assertNotNull(in);
            with().header(GlobalDataRest.X_TENANT_ID, "1")
                .header(GlobalDataRest.VITAM_CONTENT_LENGTH, "8766")
                .header(GlobalDataRest.X_DIGEST_ALGORITHM, DigestType.SHA512.getName())
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(in).when()
                .put(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1");
        }

        final File testFile = PropertiesUtils.findFile(ARCHIVE_FILE_TXT);
        Digest digest = Digest.digest(testFile, VitamConfiguration.getDefaultDigestType());

        // object is found, creation worked
        given().header(GlobalDataRest.X_TENANT_ID, "1").contentType(MediaType.APPLICATION_JSON).then()
            .statusCode(Status.OK.getStatusCode()).when()
            .get(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1");

        // object is found, delete has failed, for sure
        given().header(GlobalDataRest.X_TENANT_ID, "1").contentType(MediaType.APPLICATION_JSON).then()
            .statusCode(Status.OK.getStatusCode()).when()
            .get(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1");


        // object is found, delete has failed, for sure
        given().header(GlobalDataRest.X_TENANT_ID, "1").contentType(MediaType.APPLICATION_JSON).then()
            .statusCode(Status.OK.getStatusCode()).when()
            .get(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1");

        String responseAsJson = "{\"id\":\"" + "id1" + "\",\"status\":\"" + Response.Status.OK.toString() + "\"}";
        // good combo digest algorithm + digest -> object found and deleted
        given().header(GlobalDataRest.X_TENANT_ID, "1").header(GlobalDataRest.X_DIGEST, digest.toString())
            .header(GlobalDataRest.X_DIGEST_ALGORITHM, VitamConfiguration.getDefaultDigestType().getName())
            .delete(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1").then().statusCode(200)
            .body(Matchers.equalTo(responseAsJson));

        // lets check that we cant find the object again, meaning we re sure
        // that the object has been deleted
        given().header(GlobalDataRest.X_TENANT_ID, "1").contentType(MediaType.APPLICATION_JSON).then()
            .statusCode(Status.NOT_FOUND.getStatusCode()).when()
            .get(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1");

    }

    @Test
    public void statusTest() {
        given().get(STATUS_URI).then().statusCode(Status.NO_CONTENT.getStatusCode());
    }

    @Test
    public void checkObjectExistenceTestNotExisting() {
        // no object -> 404
        given()
            .header(GlobalDataRest.X_TENANT_ID, 0)
            .head(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1").then().statusCode(404);
    }

    @Test
    public void checkObjectExistenceTestBadRequest() {
        // Missing tenant
        given()
            .head(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1").then().statusCode(400);
    }

    @Test
    public void checkObjectTest() throws Exception {
        checkOfferDatabaseEmptiness();

        try (FileInputStream in = new FileInputStream(PropertiesUtils.findFile(ARCHIVE_FILE_TXT))) {
            assertNotNull(in);
            with().header(GlobalDataRest.X_TENANT_ID, "1")
                .header(GlobalDataRest.VITAM_CONTENT_LENGTH, "8766")
                .header(GlobalDataRest.X_DIGEST_ALGORITHM, DigestType.SHA512.getName())
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(in).when()
                .put(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1");
        }

        checkOfferDatabaseExistingDocument("1_object", "id1");

        given()
            .header(GlobalDataRest.X_TENANT_ID, "1")
            .head(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1").then().statusCode(204);

    }

    @Test
    public void getObjectMetadataOK() throws IOException {

        try (FileInputStream in = new FileInputStream(PropertiesUtils.findFile(ARCHIVE_FILE_TXT))) {
            assertNotNull(in);
            with().header(GlobalDataRest.X_TENANT_ID, "1")
                .header(GlobalDataRest.VITAM_CONTENT_LENGTH, "8766")
                .header(GlobalDataRest.X_DIGEST_ALGORITHM, DigestType.SHA512.getName())
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(in).when()
                .put(OBJECTS_URI + "/" + DataCategory.UNIT.name() + OBJECT_ID_URI, "id1");
        }

        checkOfferDatabaseExistingDocument("1_unit", "id1");

        // test
        given()
            .header(GlobalDataRest.X_TENANT_ID, 1)
            .header(GlobalDataRest.X_OFFER_NO_CACHE, "false")
            .when().get(OBJECTS_URI + "/" + UNIT_CODE + "/" + "id1" + METADATA)
            .then()
            .statusCode(200);
    }

    @Test
    public void getObjectMetadataKO() {
        // test
        given().header(GlobalDataRest.X_TENANT_ID, 1).when().get(OBJECTS_URI + "/" + UNIT_CODE + "/" + "" + METADATA)
            .then()
            .statusCode(404);
    }

    @Test
    public void listObjectsTest() {
        given().when().get(OBJECTS_URI + "/" + DataCategory.OBJECT.name()).then().statusCode(400);

        given().header(GlobalDataRest.X_TENANT_ID, "1").when().get(OBJECTS_URI + "/" + DataCategory.OBJECT.name())
            .then()
            .statusCode(204);

        for (int i = 0; i < 10; i++) {

            try (FakeInputStream fin = new FakeInputStream(50)) {
                given().header(GlobalDataRest.X_TENANT_ID, "1")
                    .header(GlobalDataRest.VITAM_CONTENT_LENGTH, "50")
                    .header(GlobalDataRest.X_DIGEST_ALGORITHM, DigestType.SHA512.getName())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM).content(fin).when()
                    .put(OBJECTS_URI + "/" + DataCategory.OBJECT.name() + OBJECT_ID_URI, "id" + i);
            }

            checkOfferDatabaseExistingDocument("1_object", "id" + i);
        }

        given().header(GlobalDataRest.X_CURSOR, true).header(GlobalDataRest.X_TENANT_ID, "1").when()
            .get(OBJECTS_URI + "/" + DataCategory.OBJECT.name()).then()
            .header(GlobalDataRest.X_CURSOR_ID, Matchers.notNullValue()).statusCode(200);

        // TODO: more ?
    }

    @Test
    public void getOfferLogTestBadRequest() {
        final OfferLogRequest getOfferLog = new OfferLogRequest();

        given().contentType(MediaType.APPLICATION_JSON).content(getOfferLog).when()
            .get(OBJECTS_URI + "/" + DataCategory.OBJECT.name() + LOG_URI).then().statusCode(400);

        given().header(GlobalDataRest.X_TENANT_ID, "1").contentType(MediaType.APPLICATION_JSON).when()
            .get(OBJECTS_URI + "/" + DataCategory.OBJECT.name() + LOG_URI).then().statusCode(400);

    }

    @Test
    public void getOfferLogTestOk() throws InvalidParseOperationException {

        for (int i = 0; i < 10; i++) {

            try (FakeInputStream fin = new FakeInputStream(50)) {
                assertNotNull(fin);
                given().header(GlobalDataRest.X_TENANT_ID, "1")
                    .header(GlobalDataRest.VITAM_CONTENT_LENGTH, 50)
                    .header(GlobalDataRest.X_DIGEST_ALGORITHM, DigestType.SHA512.getName())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM).content(fin).when()
                    .put(OBJECTS_URI + "/" + DataCategory.OBJECT.name() + OBJECT_ID_URI, "id" + i);
            }

            checkOfferDatabaseExistingDocument("1_object", "id" + i);
        }

        final OfferLogRequest getOfferLogNoResult = new OfferLogRequest(50L, 10, Order.ASC);
        ResponseBody responseBody1 = given().header(GlobalDataRest.X_TENANT_ID, "1")
            .contentType(MediaType.APPLICATION_JSON).content(getOfferLogNoResult).when()
            .get(OBJECTS_URI + "/" + DataCategory.OBJECT.name() + LOG_URI).getBody();
        final RequestResponseOK<OfferLog> response1 =
            JsonHandler.getFromInputStream(responseBody1.asInputStream(), RequestResponseOK.class, OfferLog.class);
        assertThat(response1.getStatus()).isEqualTo(200);
        assertThat(response1.getResults().size()).isEqualTo(0);

        OfferLogRequest getOfferLogWithOffsetWithLimit = new OfferLogRequest(7L, 10, Order.ASC);
        ResponseBody responseBody2 = given().header(GlobalDataRest.X_TENANT_ID, "1")
            .contentType(MediaType.APPLICATION_JSON).content(getOfferLogWithOffsetWithLimit).when()
            .get(OBJECTS_URI + "/" + DataCategory.OBJECT.name() + LOG_URI).getBody();
        final RequestResponseOK<OfferLog> response2 =
            JsonHandler.getFromInputStream(responseBody2.asInputStream(), RequestResponseOK.class, OfferLog.class);
        assertThat(response2.getStatus()).isEqualTo(200);
        assertThat(response2.getResults().size()).isEqualTo(4);

        OfferLogRequest getOfferLogNoOffsetWithLimit = new OfferLogRequest(0L, 10, Order.ASC);
        ResponseBody responseBody3 = given().header(GlobalDataRest.X_TENANT_ID, "1")
            .contentType(MediaType.APPLICATION_JSON).content(getOfferLogNoOffsetWithLimit).when()
            .get(OBJECTS_URI + "/" + DataCategory.OBJECT.name() + LOG_URI).getBody();
        final RequestResponseOK<OfferLog> response3 =
            JsonHandler.getFromInputStream(responseBody3.asInputStream(), RequestResponseOK.class, OfferLog.class);
        assertThat(response3.getStatus()).isEqualTo(200);
        assertThat(response3.getResults().size()).isEqualTo(10);

        OfferLogRequest getOfferLogOffsetLimitDesc = new OfferLogRequest(5L, 3, Order.DESC);
        getOfferLogNoOffsetWithLimit.setLimit(10);
        ResponseBody responseBody4 = given().header(GlobalDataRest.X_TENANT_ID, "1")
            .contentType(MediaType.APPLICATION_JSON).content(getOfferLogOffsetLimitDesc).when()
            .get(OBJECTS_URI + "/" + DataCategory.OBJECT.name() + LOG_URI).getBody();
        final RequestResponseOK<OfferLog> response4 =
            JsonHandler.getFromInputStream(responseBody4.asInputStream(), RequestResponseOK.class, OfferLog.class);
        assertThat(response4.getStatus()).isEqualTo(200);
        assertThat(response4.getResults().size()).isEqualTo(3);

    }

    private void checkOfferDatabaseEmptiness() {
        FindIterable<Document> results = mongoRule.getMongoClient().getDatabase(DATABASE_NAME)
            .getCollection(OfferCollections.OFFER_LOG.getName()).find();
        assertThat(results).hasSize(0);
    }

    private void checkOfferDatabaseExistingDocument(String container, String filename) {
        checkOfferDatabaseExistingDocument(container, filename, 1);
    }

    private void checkOfferDatabaseExistingDocument(String container, String filename, int count) {
        FindIterable<Document> results = mongoRule.getMongoClient().getDatabase(DATABASE_NAME)
            .getCollection(OfferCollections.OFFER_LOG.getName())
            .find(Filters.and(Filters.eq("Container", container), Filters.eq("FileName", filename)));

        assertThat(results).hasSize(count);
    }
}
