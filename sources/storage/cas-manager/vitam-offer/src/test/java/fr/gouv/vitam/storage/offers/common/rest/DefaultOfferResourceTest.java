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

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.RestAssured.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.io.FileUtils;
import org.bson.Document;
import org.hamcrest.Matchers;
import org.jhades.JHades;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.response.ResponseBody;
import com.mongodb.client.FindIterable;
import com.mongodb.client.model.Filters;

import fr.gouv.vitam.common.GlobalDataRest;
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.database.collections.VitamCollection;
import fr.gouv.vitam.common.digest.Digest;
import fr.gouv.vitam.common.digest.DigestType;
import fr.gouv.vitam.common.exception.VitamApplicationServerException;
import fr.gouv.vitam.common.exception.VitamException;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.junit.FakeInputStream;
import fr.gouv.vitam.common.junit.JunitHelper;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.mongo.MongoRule;
import fr.gouv.vitam.common.server.application.configuration.MongoDbNode;
import fr.gouv.vitam.common.storage.StorageConfiguration;
import fr.gouv.vitam.storage.engine.common.StorageConstants;
import fr.gouv.vitam.storage.engine.common.model.DataCategory;
import fr.gouv.vitam.storage.engine.common.model.ObjectInit;
import fr.gouv.vitam.storage.engine.common.model.OfferLog;
import fr.gouv.vitam.storage.engine.common.model.Order;
import fr.gouv.vitam.storage.engine.common.model.request.OfferLogRequest;
import fr.gouv.vitam.storage.offers.common.database.OfferLogDatabaseService;
import fr.gouv.vitam.storage.offers.common.database.OfferSequenceDatabaseService;

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

    private static final ObjectMapper OBJECT_MAPPER;
    private static DefaultOfferMain application;

    static {

        OBJECT_MAPPER = new ObjectMapper(new JsonFactory());
        OBJECT_MAPPER.disable(SerializationFeature.INDENT_OUTPUT);
    }

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(DefaultOfferResourceTest.class);

    @Rule
    public MongoRule mongoRule =
        new MongoRule(VitamCollection.getMongoClientOptions(), DATABASE_NAME,
            OfferLogDatabaseService.OFFER_LOG_COLLECTION_NAME, OfferSequenceDatabaseService.OFFER_SEQUENCE_COLLECTION);

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
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
        } catch (final VitamApplicationServerException e) {
            LOGGER.error(e);
            throw new IllegalStateException("Cannot start the Wokspace Offer Application Server", e);
        }
    }

    @AfterClass
    public static void tearDownAfterClass() {
        LOGGER.debug("Ending tests");
        try {
            application.stop();
        } catch (final VitamApplicationServerException e) {
            LOGGER.error(e);
        }
        junitHelper.releasePort(serverPort);
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
        // for skipped test (putObjectChunkTest)
        // FileUtils.deleteDirectory((new File(conf.getStoragePath() + "/1")));
    }

    @Test
    public void getCapacityTestBadRequest() {
        given().head(OBJECTS_URI + "/" + UNIT_CODE).then().statusCode(Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    public void getCapacityTestOk() {
        // create tenant
        final ObjectInit objectInit = new ObjectInit();
        objectInit.setType(DataCategory.UNIT);
        given().header(GlobalDataRest.X_TENANT_ID, 1).header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_INIT)
            .contentType(MediaType.APPLICATION_JSON).content(objectInit).when()
            .post(OBJECTS_URI + "/" + UNIT_CODE + "/" + "id1").then().statusCode(201);
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

        final ObjectInit objectInit = new ObjectInit();
        objectInit.setType(DataCategory.OBJECT);
        with().header(GlobalDataRest.X_TENANT_ID, "1").header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_INIT)
            .contentType(MediaType.APPLICATION_JSON).content(objectInit).when()
            .post(OBJECTS_URI + "/" + OBJECT_CODE + "/" + "id1");

        try (FileInputStream in = new FileInputStream(PropertiesUtils.findFile(ARCHIVE_FILE_TXT))) {
            assertNotNull(in);
            with().header(GlobalDataRest.X_TENANT_ID, "1")
                .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_END)
                .header(GlobalDataRest.VITAM_CONTENT_LENGTH, "8766")
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

        final ObjectInit objectInit = new ObjectInit();
        objectInit.setType(DataCategory.OBJECT);
        with().header(GlobalDataRest.X_TENANT_ID, "1").header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_INIT)
            .contentType(MediaType.APPLICATION_JSON).content(objectInit).when()
            .post(OBJECTS_URI + "/" + OBJECT_CODE + "/" + "id1.xml");

        try (FileInputStream in = new FileInputStream(PropertiesUtils.findFile(ARCHIVE_FILE_TXT))) {
            assertNotNull(in);
            with().header(GlobalDataRest.X_TENANT_ID, "1")
                .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_END)
                .header(GlobalDataRest.VITAM_CONTENT_LENGTH, "8766")
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
    public void getObjectChunkTestOK() throws Exception {

        final ObjectInit objectInit = new ObjectInit();
        objectInit.setType(DataCategory.UNIT);
        with().header(GlobalDataRest.X_TENANT_ID, "1").header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_INIT)
            .contentType(MediaType.APPLICATION_JSON).content(objectInit).when()
            .post(OBJECTS_URI + "/" + UNIT_CODE + "/" + "id1");

        try (FileInputStream in = new FileInputStream(PropertiesUtils.findFile(ARCHIVE_FILE_TXT))) {
            assertNotNull(in);
            final FileChannel fc = in.getChannel();
            final ByteBuffer bb = ByteBuffer.allocate(1024);

            byte[] bytes;
            int read = fc.read(bb);
            while (read >= 0) {
                bb.flip();
                if (fc.position() == fc.size()) {
                    bytes = new byte[read];
                    bb.get(bytes, 0, read);
                    try (InputStream inChunk = new ByteArrayInputStream(bytes)) {
                        assertNotNull(inChunk);
                        with().header(GlobalDataRest.X_TENANT_ID, "1")
                                .header(GlobalDataRest.VITAM_CONTENT_LENGTH, read)
                                .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_END)
                            .contentType(MediaType.APPLICATION_OCTET_STREAM).content(inChunk).when()
                            .put(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, UNIT_CODE, "id1");
                    }
                } else {
                    bytes = bb.array();
                    try (InputStream inChunk = new ByteArrayInputStream(bytes)) {
                        // assertNotNull(inChunk);
                        with().header(GlobalDataRest.X_TENANT_ID, "1")
                            .header(GlobalDataRest.VITAM_CONTENT_LENGTH, read)
                            .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_WRITE)
                            .contentType(MediaType.APPLICATION_OCTET_STREAM).content(inChunk).when()
                            .put(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, UNIT_CODE, "id1");
                    }
                }
                bb.clear();
                read = fc.read(bb);
            }
        }

        checkOfferDatabaseExistingDocument("1_unit", "id1");

        // found
        given().header(GlobalDataRest.X_TENANT_ID, "1").contentType(MediaType.APPLICATION_JSON).then()
            .statusCode(Status.OK.getStatusCode()).when()
            .get(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, UNIT_CODE, "id1");
    }

    @Test
    public void postObjectsTest() throws Exception {
        final String guid = GUIDFactory.newGUID().toString();
        // no tenant id
        given().contentType(MediaType.APPLICATION_JSON).when().post(OBJECTS_URI + "/" + UNIT_CODE + "/" + guid).then()
            .statusCode(400);

        // no command
        given().header(GlobalDataRest.X_TENANT_ID, "2").contentType(MediaType.APPLICATION_JSON).when()
            .post(OBJECTS_URI + "/" + UNIT_CODE + "/" + guid).then().statusCode(400);

        // no ObjectInit, command != INIT
        given().header(GlobalDataRest.X_TENANT_ID, "2").header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_END)
            .contentType(MediaType.APPLICATION_JSON).when().post(OBJECTS_URI + "/" + UNIT_CODE + "/" + guid).then()
            .statusCode(400);

        // no ObjectInit
        given().header(GlobalDataRest.X_TENANT_ID, "2").header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_INIT)
            .contentType(MediaType.APPLICATION_JSON).when().post(OBJECTS_URI + "/" + UNIT_CODE + "/" + guid).then()
            .statusCode(400);

        final ObjectInit objectInit = new ObjectInit();
        objectInit.setType(DataCategory.UNIT);
        assertNotNull(objectInit);

        given().header(GlobalDataRest.X_TENANT_ID, "2").header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_INIT)
            .contentType(MediaType.APPLICATION_JSON).content(objectInit).when()
            .post(OBJECTS_URI + "/" + UNIT_CODE + "/" + guid).then().statusCode(201);

        final StorageConfiguration conf = PropertiesUtils.readYaml(PropertiesUtils.findFile(DEFAULT_STORAGE_CONF),
            StorageConfiguration.class);
        final File container = new File(conf.getStoragePath() + "/2_unit");
        assertTrue(container.exists());
        assertTrue(container.isDirectory());
    }

    @Test
    public void putObjectTest() throws Exception {
        checkOfferDatabaseEmptiness();

        // no tenant id
        given().contentType(MediaType.APPLICATION_OCTET_STREAM).when()
            .put(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, UNIT_CODE, "id1").then().statusCode(400);

        // No command
        given().header(GlobalDataRest.X_TENANT_ID, "2").contentType(MediaType.APPLICATION_OCTET_STREAM).when()
            .put(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, UNIT_CODE, "id1").then().statusCode(400);

        // Bad command
        given().header(GlobalDataRest.X_TENANT_ID, "2").header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_INIT)
            .contentType(MediaType.APPLICATION_OCTET_STREAM).when()
            .put(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, UNIT_CODE, "id1").then().statusCode(400);

        // No INIT
        try (FileInputStream in = new FileInputStream(PropertiesUtils.findFile(ARCHIVE_FILE_TXT))) {
            assertNotNull(in);
            // try only with one chunk
            final byte[] bytes = new byte[1024];
            int read = in.read(bytes);
            try (InputStream inChunk = new ByteArrayInputStream(bytes)) {
                assertNotNull(inChunk);
                // TODO: review this when chunk really implemented in VITAM storage engine (theoretically bad request)
                given().header(GlobalDataRest.X_TENANT_ID, "1")
                    .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_WRITE)
                    .header(GlobalDataRest.VITAM_CONTENT_LENGTH, 0)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM).content(inChunk).when()
                    .put(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, UNIT_CODE, "id1").then().statusCode(201);
            }
        }

        final ObjectInit objectInit = new ObjectInit();
        objectInit.setType(DataCategory.UNIT);
        given().header(GlobalDataRest.X_TENANT_ID, "2").header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_INIT)
            .contentType(MediaType.APPLICATION_JSON).content(objectInit).when()
            .post(OBJECTS_URI + "/" + UNIT_CODE + "/" + "id1").then().statusCode(201);
        try (FileInputStream in = new FileInputStream(PropertiesUtils.findFile(ARCHIVE_FILE_TXT))) {
            assertNotNull(in);
            given().header(GlobalDataRest.X_TENANT_ID, "2")
                .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_END)
                .header(GlobalDataRest.VITAM_CONTENT_LENGTH, "8766")
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(in).when()
                .put(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, UNIT_CODE, "id1").then().statusCode(201);
        }
        // check
        final StorageConfiguration conf = PropertiesUtils.readYaml(PropertiesUtils.findFile(DEFAULT_STORAGE_CONF),
            StorageConfiguration.class);
        final File container = new File(conf.getStoragePath() + "/2_unit");
        assertNotNull(container);
        assertTrue(container.exists());
        assertTrue(container.isDirectory());
        final File object = new File(container.getAbsolutePath(), "/id1");
        assertNotNull(object);
        assertTrue(object.exists());
        assertFalse(object.isDirectory());

        checkOfferDatabaseExistingDocument("2_unit", "id1");

        assertTrue(com.google.common.io.Files.equal(PropertiesUtils.findFile(ARCHIVE_FILE_TXT), object));
    }

    // TODO activate when chunk mode is done in {@see DefaultOfferService}
    // method createObject
    @Test
    @Ignore
    public void putObjectChunkTest() throws Exception {
        // no tenant id
        given().contentType(MediaType.APPLICATION_OCTET_STREAM).when().put(OBJECTS_URI + OBJECT_ID_URI, "id1").then()
            .statusCode(400);

        // No command
        given().header(GlobalDataRest.X_TENANT_ID, "2").contentType(MediaType.APPLICATION_OCTET_STREAM).when()
            .put(OBJECTS_URI + OBJECT_ID_URI, "id1").then().statusCode(400);

        // Bad command
        given().header(GlobalDataRest.X_TENANT_ID, "2").header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_INIT)
            .contentType(MediaType.APPLICATION_OCTET_STREAM).when().put(OBJECTS_URI + OBJECT_ID_URI, "id1").then()
            .statusCode(400);

        // No INIT
        try (FileInputStream in = new FileInputStream(PropertiesUtils.findFile(ARCHIVE_FILE_TXT))) {
            assertNotNull(in);
            // try only with one chunk
            final byte[] bytes = new byte[1024];
            in.read(bytes);
            try (InputStream inChunk = new ByteArrayInputStream(bytes)) {
                assertNotNull(inChunk);
                given().header(GlobalDataRest.X_TENANT_ID, "2")
                    .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_WRITE)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM).content(inChunk).when()
                    .put(OBJECTS_URI + OBJECT_ID_URI, "id1").then().statusCode(500);
            }
        }

        final ObjectInit objectInit = new ObjectInit();
        objectInit.setType(DataCategory.OBJECT);
        given().header(GlobalDataRest.X_TENANT_ID, "2").header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_INIT)
            .contentType(MediaType.APPLICATION_JSON).content(objectInit).when()
            .post(OBJECTS_URI + "/" + OBJECT_CODE + "/" + "id1").then().statusCode(201);
        try (FileInputStream in = new FileInputStream(PropertiesUtils.findFile(ARCHIVE_FILE_TXT))) {
            assertNotNull(in);
            final FileChannel fc = in.getChannel();
            final ByteBuffer bb = ByteBuffer.allocate(1024);

            byte[] bytes;
            int read = fc.read(bb);
            while (read >= 0) {
                bb.flip();
                if (fc.position() == fc.size()) {
                    bytes = new byte[read];
                    bb.get(bytes, 0, read);
                    try (InputStream inChunk = new ByteArrayInputStream(bytes)) {
                        assertNotNull(inChunk);
                        given().header(GlobalDataRest.X_TENANT_ID, "2")
                            .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_END)
                            .contentType(MediaType.APPLICATION_OCTET_STREAM).content(inChunk).when()
                            .put(OBJECTS_URI + OBJECT_ID_URI, "id1").then().statusCode(201);
                    }
                } else {
                    bytes = bb.array();
                    try (InputStream inChunk = new ByteArrayInputStream(bytes)) {
                        assertNotNull(inChunk);
                        given().header(GlobalDataRest.X_TENANT_ID, "2")
                            .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_WRITE)
                            .contentType(MediaType.APPLICATION_OCTET_STREAM).content(inChunk).when()
                            .put(OBJECTS_URI + OBJECT_ID_URI, "id1").then().statusCode(201);
                    }
                }
                bb.clear();
                read = fc.read(bb);
            }
        }
        // check
        final StorageConfiguration conf = PropertiesUtils.readYaml(PropertiesUtils.findFile(DEFAULT_STORAGE_CONF),
            StorageConfiguration.class);
        final File container = new File(conf.getStoragePath() + "/1" + this);
        assertNotNull(container);
        assertTrue(container.exists());
        assertTrue(container.isDirectory());
        final File folder = new File(container.getAbsolutePath(), "/" + DataCategory.OBJECT.getFolder());
        assertNotNull(folder);
        assertTrue(folder.exists());
        assertTrue(folder.isDirectory());
        final File object = new File(folder.getAbsolutePath(), "id1");
        assertNotNull(object);
        assertTrue(object.exists());
        assertFalse(object.isDirectory());

        assertTrue(com.google.common.io.Files.equal(PropertiesUtils.findFile(ARCHIVE_FILE_TXT), object));
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

        given().header(GlobalDataRest.X_TENANT_ID, 0).header(GlobalDataRest.X_DIGEST, "digest")
            .delete(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1").then().statusCode(400);

        given().header(GlobalDataRest.X_TENANT_ID, 0)
            .header(GlobalDataRest.X_DIGEST_ALGORITHM, VitamConfiguration.getDefaultDigestType().getName())
            .delete(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1").then().statusCode(400);

        // unknwonw digest algorithm -> 500
        given().header(GlobalDataRest.X_TENANT_ID, 0).header(GlobalDataRest.X_DIGEST, "digest")
            .header(GlobalDataRest.X_DIGEST_ALGORITHM, "wrongTypeShouldTriggerAnInternalServerError")
            .delete(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1").then().statusCode(500);
    }

    @Test
    public void deleteObjectTest() throws Exception {
        // init object
        final ObjectInit objectInit = new ObjectInit();
        objectInit.setType(DataCategory.OBJECT);
        with().header(GlobalDataRest.X_TENANT_ID, "1").header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_INIT)
            .contentType(MediaType.APPLICATION_JSON).content(objectInit).when()
            .post(OBJECTS_URI + "/" + OBJECT_CODE + "/" + "id1");

        try (FileInputStream in = new FileInputStream(PropertiesUtils.findFile(ARCHIVE_FILE_TXT))) {
            assertNotNull(in);
            with().header(GlobalDataRest.X_TENANT_ID, "1")
                .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_END)
                    .header(GlobalDataRest.VITAM_CONTENT_LENGTH, "8766")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM).content(in).when()
                .put(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1");
        }

        final File testFile = PropertiesUtils.findFile(ARCHIVE_FILE_TXT);
        Digest digest = Digest.digest(testFile, VitamConfiguration.getDefaultDigestType());

        // object is found, creation worked
        given().header(GlobalDataRest.X_TENANT_ID, "1").contentType(MediaType.APPLICATION_JSON).then()
            .statusCode(Status.OK.getStatusCode()).when()
            .get(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1");

        // wrong digest -> no object found -> 404
        given().header(GlobalDataRest.X_TENANT_ID, "1").header(GlobalDataRest.X_DIGEST, "fakeDigest")
            .header(GlobalDataRest.X_DIGEST_ALGORITHM, VitamConfiguration.getDefaultDigestType().getName())
            .delete(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1").then().statusCode(404);

        // object is found, delete has failed, for sure
        given().header(GlobalDataRest.X_TENANT_ID, "1").contentType(MediaType.APPLICATION_JSON).then()
            .statusCode(Status.OK.getStatusCode()).when()
            .get(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1");

        // wrong digest algorithm -> no object found -> 404
        given().header(GlobalDataRest.X_TENANT_ID, "1").header(GlobalDataRest.X_DIGEST, digest.toString())
            .header(GlobalDataRest.X_DIGEST_ALGORITHM, VitamConfiguration.getSecurityDigestType().getName())
            .delete(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1").then().statusCode(404);

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
    public void checkObjectTestNotExisting() {
        // no object -> 500
        given().header(GlobalDataRest.X_TENANT_ID, 0).header(GlobalDataRest.X_DIGEST, "digest")
            .header(GlobalDataRest.X_DIGEST_ALGORITHM, "digestType")
            .head(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1").then().statusCode(500);
    }

    @Test
    public void checkObjectTestBadRequests() {
        given().header(GlobalDataRest.X_DIGEST, "digest").header(GlobalDataRest.X_DIGEST_ALGORITHM, "digestType")
            .head(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1").then().statusCode(400);

        given().header(GlobalDataRest.X_TENANT_ID, 0).header(GlobalDataRest.X_DIGEST, "digest")
            .head(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1").then().statusCode(500);

        given().header(GlobalDataRest.X_TENANT_ID, 0).header(GlobalDataRest.X_DIGEST_ALGORITHM, "digestType")
            .head(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1").then().statusCode(500);
    }

    @Test
    public void checkObjectTest() throws Exception {
        checkOfferDatabaseEmptiness();

        // init object
        final ObjectInit objectInit = new ObjectInit();
        Digest digest;
        objectInit.setType(DataCategory.OBJECT);
        with().header(GlobalDataRest.X_TENANT_ID, "1").header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_INIT)
            .contentType(MediaType.APPLICATION_JSON).content(objectInit).when()
            .post(OBJECTS_URI + "/" + OBJECT_CODE + "/" + "id1");

        try (FileInputStream in = new FileInputStream(PropertiesUtils.findFile(ARCHIVE_FILE_TXT))) {
            assertNotNull(in);
            with().header(GlobalDataRest.X_TENANT_ID, "1")
                .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_END)
                .header(GlobalDataRest.VITAM_CONTENT_LENGTH, "8766")
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(in).when()
                .put(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1");
        }

        checkOfferDatabaseExistingDocument("1_object", "id1");

        final File testFile = PropertiesUtils.findFile(ARCHIVE_FILE_TXT);
        digest = Digest.digest(testFile, VitamConfiguration.getDefaultDigestType());

        given().header(GlobalDataRest.X_TENANT_ID, "1").header(GlobalDataRest.X_DIGEST, "fakeDigest")
            .header(GlobalDataRest.X_DIGEST_ALGORITHM, DigestType.SHA512.getName())
            //.header("Content-Length", "8766")
            .head(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1").then().statusCode(409);

        given().header(GlobalDataRest.X_TENANT_ID, "1").header(GlobalDataRest.X_DIGEST, digest.toString())
            .header(GlobalDataRest.X_DIGEST_ALGORITHM, VitamConfiguration.getDefaultDigestType().getName())
            //.header("Content-Length", "8766")
            .head(OBJECTS_URI + OBJECT_TYPE_URI + OBJECT_ID_URI, OBJECT_CODE, "id1").then().statusCode(200);

    }

    @Test
    public void countObjectsTestKOBadRequest() {
        // test
        given().contentType(MediaType.APPLICATION_JSON).when()
            .get("/count" + OBJECTS_URI + "/" + DataCategory.UNIT.name()).then()
            .statusCode(400);
    }

    @Test
    public void countObjectsTestKONotFound() {
        // test
        given().header(GlobalDataRest.X_TENANT_ID, "0").contentType(MediaType.APPLICATION_JSON).when()
            .get("/count" + OBJECTS_URI + "/" + DataCategory.UNIT.name()).then().statusCode(404);
    }

    @Test
    public void countObjectsTestOK() throws IOException {

        checkOfferDatabaseEmptiness();

        final ObjectInit objectInit = new ObjectInit();
        objectInit.setType(DataCategory.UNIT);
        with().header(GlobalDataRest.X_TENANT_ID, "1").header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_INIT)
            .contentType(MediaType.APPLICATION_JSON).content(objectInit).when()
            .post(OBJECTS_URI + "/" + DataCategory.UNIT.name() + "/id1");

        try (FileInputStream in = new FileInputStream(PropertiesUtils.findFile(ARCHIVE_FILE_TXT))) {
            assertNotNull(in);
            with().header(GlobalDataRest.X_TENANT_ID, "1")
                .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_END)
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(in).when()
                .put(OBJECTS_URI + "/" + DataCategory.UNIT.name() + OBJECT_ID_URI, "id1");
        }

        checkOfferDatabaseExistingDocument("1_unit", "id1");

        // test
        given().header(GlobalDataRest.X_TENANT_ID, "1").contentType(MediaType.APPLICATION_JSON).when()
            .get("/count" + OBJECTS_URI + "/" + DataCategory.UNIT.name()).then().statusCode(200);
    }

    @Test
    public void getObjectMetadataOK() throws IOException {

        final ObjectInit objectInit = new ObjectInit();
        objectInit.setType(DataCategory.UNIT);
        with().header(GlobalDataRest.X_TENANT_ID, "1").header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_INIT)
            .contentType(MediaType.APPLICATION_JSON).content(objectInit).when()
            .post(OBJECTS_URI + "/" + DataCategory.UNIT.name() + "/id1");

        try (FileInputStream in = new FileInputStream(PropertiesUtils.findFile(ARCHIVE_FILE_TXT))) {
            assertNotNull(in);
            with().header(GlobalDataRest.X_TENANT_ID, "1")
                .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_END)
                .header(GlobalDataRest.VITAM_CONTENT_LENGTH, "8766")
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(in).when()
                .put(OBJECTS_URI + "/" + DataCategory.UNIT.name() + OBJECT_ID_URI, "id1");
        }

        checkOfferDatabaseExistingDocument("1_unit", "id1");

        // test
        given().header(GlobalDataRest.X_TENANT_ID, 1).when().get(OBJECTS_URI + "/" + UNIT_CODE + "/" + "id1" + METADATA)
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

        final ObjectInit objectInit = new ObjectInit();
        objectInit.setType(DataCategory.OBJECT);

        for (int i = 0; i < 10; i++) {
            given().header(GlobalDataRest.X_TENANT_ID, "1")
                .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_INIT)
                .contentType(MediaType.APPLICATION_JSON).content(objectInit).when()
                .post(OBJECTS_URI + "/" + DataCategory.OBJECT.name() + "/id" + i);

            try (FakeInputStream fin = new FakeInputStream(50)) {
                assertNotNull(fin);
                given().header(GlobalDataRest.X_TENANT_ID, "1")
                    .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_END)
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
    public void getOfferLogTestOk() {
        final ObjectInit objectInit = new ObjectInit();
        objectInit.setType(DataCategory.OBJECT);

        for (int i = 0; i < 10; i++) {
            given().header(GlobalDataRest.X_TENANT_ID, "1")
                .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_INIT)
                .contentType(MediaType.APPLICATION_JSON).content(objectInit).when()
                .post(OBJECTS_URI + "/" + DataCategory.OBJECT.name() + "/id" + i);

            try (FakeInputStream fin = new FakeInputStream(50)) {
                assertNotNull(fin);
                given().header(GlobalDataRest.X_TENANT_ID, "1")
                    .header(GlobalDataRest.X_COMMAND, StorageConstants.COMMAND_END)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM).content(fin).when()
                    .put(OBJECTS_URI + "/" + DataCategory.OBJECT.name() + OBJECT_ID_URI, "id" + i);
            }

            checkOfferDatabaseExistingDocument("1_object", "id" + i);
        }

        final OfferLogRequest getOfferLogNoResult = new OfferLogRequest(50L, 10, Order.ASC);
        ResponseBody responseBody1 = given().header(GlobalDataRest.X_TENANT_ID, "1")
            .contentType(MediaType.APPLICATION_JSON).content(getOfferLogNoResult).when()
            .get(OBJECTS_URI + "/" + DataCategory.OBJECT.name() + LOG_URI).getBody();
        final RequestResponseOK<OfferLog> response1 = responseBody1.as(RequestResponseOK.class);
        assertThat(response1.getStatus()).isEqualTo(200);
        assertThat(response1.getResults().size()).isEqualTo(0);

        OfferLogRequest getOfferLogWithOffsetWithLimit = new OfferLogRequest(7L, 10, Order.ASC);
        ResponseBody responseBody2 = given().header(GlobalDataRest.X_TENANT_ID, "1")
            .contentType(MediaType.APPLICATION_JSON).content(getOfferLogWithOffsetWithLimit).when()
            .get(OBJECTS_URI + "/" + DataCategory.OBJECT.name() + LOG_URI).getBody();
        final RequestResponseOK<OfferLog> response2 = responseBody2.as(RequestResponseOK.class);
        assertThat(response2.getStatus()).isEqualTo(200);
        assertThat(response2.getResults().size()).isEqualTo(4);

        OfferLogRequest getOfferLogNoOffsetWithLimit = new OfferLogRequest(0L, 10, Order.ASC);
        ResponseBody responseBody3 = given().header(GlobalDataRest.X_TENANT_ID, "1")
            .contentType(MediaType.APPLICATION_JSON).content(getOfferLogNoOffsetWithLimit).when()
            .get(OBJECTS_URI + "/" + DataCategory.OBJECT.name() + LOG_URI).getBody();
        final RequestResponseOK<OfferLog> response3 = responseBody3.as(RequestResponseOK.class);
        assertThat(response3.getStatus()).isEqualTo(200);
        assertThat(response3.getResults().size()).isEqualTo(10);
        
        OfferLogRequest getOfferLogOffsetLimitDesc = new OfferLogRequest(5L, 3, Order.DESC);
        getOfferLogNoOffsetWithLimit.setLimit(10);
        ResponseBody responseBody4 = given().header(GlobalDataRest.X_TENANT_ID, "1")
            .contentType(MediaType.APPLICATION_JSON).content(getOfferLogOffsetLimitDesc).when()
            .get(OBJECTS_URI + "/" + DataCategory.OBJECT.name() + LOG_URI).getBody();
        final RequestResponseOK<OfferLog> response4 = responseBody4.as(RequestResponseOK.class);
        assertThat(response4.getStatus()).isEqualTo(200);
        assertThat(response4.getResults().size()).isEqualTo(3);

    }

    private void checkOfferDatabaseEmptiness() {
        FindIterable<Document> results = mongoRule.getMongoClient().getDatabase(DATABASE_NAME)
            .getCollection(OfferLogDatabaseService.OFFER_LOG_COLLECTION_NAME).find();
        assertThat(results).hasSize(0);
    }

    private void checkOfferDatabaseExistingDocument(String container, String filename) {
        FindIterable<Document> results = mongoRule.getMongoClient().getDatabase(DATABASE_NAME)
            .getCollection(OfferLogDatabaseService.OFFER_LOG_COLLECTION_NAME)
            .find(Filters.and(Filters.eq("Container", container), Filters.eq("FileName", filename)));

        assertThat(results).hasSize(1);
    }

}
