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
package fr.gouv.vitam.workspace.rest;


import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.Lists;
import fr.gouv.vitam.common.CommonMediaType;
import fr.gouv.vitam.common.GlobalDataRest;
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.client.VitamClientFactory;
import fr.gouv.vitam.common.digest.Digest;
import fr.gouv.vitam.common.digest.DigestType;
import fr.gouv.vitam.common.exception.VitamApplicationServerException;
import fr.gouv.vitam.common.junit.JunitHelper;
import fr.gouv.vitam.common.storage.StorageConfiguration;
import fr.gouv.vitam.common.stream.MultiplexedStreamReader;
import fr.gouv.vitam.workspace.api.model.TimeToLive;
import fr.gouv.vitam.workspace.common.CompressInformation;
import fr.gouv.vitam.workspace.common.Entry;
import io.restassured.RestAssured;
import io.restassured.config.EncoderConfig;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.get;
import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.with;
import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 */
public class WorkspaceResourceTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private static WorkspaceMain workspaceMain;

    private static final String RESOURCE_URI = "/workspace/v1";

    private static final String CONTAINER_NAME = "myContainer";
    private static final String FOLDER_NAME = "myFolder";
    private static final String FOLDER_SIP = "SIP";
    private static final String OBJECT_NAME = "myObject";
    private static final String FAKE_FOLDER_NAME = "fakeFolderName";
    public static final String X_DIGEST_ALGORITHM = "X-digest-algorithm";
    public static final String ALGO = "MD5";
    public static final String X_DIGEST = "X-digest";
    private static JunitHelper junitHelper;
    private static int port;
    private static final ObjectMapper OBJECT_MAPPER;
    static final int tenantId = 0;
    static final List tenantList = Lists.newArrayList(tenantId);

    static {
        OBJECT_MAPPER = new ObjectMapper(new JsonFactory());
        OBJECT_MAPPER.disable(SerializationFeature.INDENT_OUTPUT);
    }


    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        junitHelper = JunitHelper.getInstance();
    }

    @AfterClass
    public static void shutdownAfterClass() throws VitamApplicationServerException {
        junitHelper.releasePort(port);
        VitamClientFactory.resetConnections();
    }

    @Before
    public void setup() throws Exception {
        final StorageConfiguration configuration = new StorageConfiguration();
        final File tempDir = tempFolder.newFolder();
        configuration.setStoragePath(tempDir.getCanonicalPath());
        configuration.setJettyConfig("jetty-config-test.xml");
        VitamConfiguration.setTenants(tenantList);
        port = junitHelper.findAvailablePort();
        // TODO P1 verifier la compatibilité avec les tests parallèles sur jenkins

        File configurationFile = tempFolder.newFile();

        PropertiesUtils.writeYaml(configurationFile, configuration);

        //FIXME: should we start and stop workspace for each test ?!!
        workspaceMain = new WorkspaceMain(configurationFile.getAbsolutePath());
        workspaceMain.start();
        RestAssured.port = port;
        RestAssured.basePath = RESOURCE_URI;
    }

    @After
    public void tearDown() throws Exception {
        workspaceMain.stop();
    }

    // Status
    @Test
    public void givenStartedServerWhenGetStatusThenReturnStatusNoContent() {
        get("/status").then().statusCode(Status.NO_CONTENT.getStatusCode());
    }

    // Container
    @Test
    public void givenContainerAlreadyExistsWhenCreateContainerThenReturnCreated() {

        with().then()
            .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME);

        given().expect()
            .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME);
    }

    @Test
    public void givenContainerNotFoundWhenDeleteContainerThenReturnNotFound() {

        with().then()
            .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME);

        with().contentType(ContentType.JSON).header(GlobalDataRest.X_RECURSIVE, true).then()
            .statusCode(Status.NO_CONTENT.getStatusCode()).when().delete("/containers/" + CONTAINER_NAME);

        given().contentType(ContentType.JSON).header(GlobalDataRest.X_RECURSIVE, true).then()
            .statusCode(Status.NOT_FOUND.getStatusCode()).when().delete("/containers/" + CONTAINER_NAME);

    }

    @Test
    public void givenContainerNotFoundWhenGetStatusThenReturnNotFound() {
        given().then().statusCode(Status.NOT_FOUND.getStatusCode()).when().head("/containers/" + CONTAINER_NAME);
    }

    @Test
    public void givenContainerAlreadyExistsWhenCheckContainerThenReturnOk() {
        with().then()
            .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME);

        given().then().statusCode(Status.OK.getStatusCode()).when().head("/containers/" + CONTAINER_NAME);
    }

    @Test
    public void givenContainerNotFoundWhenCreateContainerThenReturnCreated() {

        given().then()
            .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME);

    }

    // Folder (Directory)
    @Test
    public void givenContainerNotFoundWhenCreateFolderThenReturnNotFound() {

        given().contentType(ContentType.JSON).then()
            .statusCode(Status.NOT_FOUND.getStatusCode()).when()
            .post("/containers/" + CONTAINER_NAME + "/folders/" + FOLDER_NAME);

    }

    @Test
    public void givenFolderAlreadyExistsWhenCreateFolderThenReturnCreated() {

        with().then()
            .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME);

        with().contentType(ContentType.JSON).then()
            .statusCode(Status.CREATED.getStatusCode()).when()
            .post("/containers/" + CONTAINER_NAME + "/folders/" + FOLDER_NAME);

        given().contentType(ContentType.JSON).then()
            .statusCode(Status.CREATED.getStatusCode()).when()
            .post("/containers/" + CONTAINER_NAME + "/folders/" + FOLDER_NAME);

    }

    @Test
    public void givenFolderNotFoundWhenDeleteFolderThenReturnNotFound() {

        with().then()
            .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME);

        given().contentType(ContentType.JSON).then()
            .statusCode(Status.NOT_FOUND.getStatusCode()).when()
            .delete("/containers" + CONTAINER_NAME + "/folders/" + FOLDER_NAME);

    }

    @Test
    public void givenContainerNotFoundWhenDeleteFolderThenReturnNotFound() {
        given().contentType(ContentType.JSON).then()
            .statusCode(Status.NOT_FOUND.getStatusCode()).when()
            .delete("/containers" + CONTAINER_NAME + "/folders/" + FOLDER_NAME);
    }

    @Test
    public void givenFolderAlreadyExistsWhenDeleteFolderThenReturnNotContent() {
        with().then()
            .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME);

        with().contentType(ContentType.JSON).then()
            .statusCode(Status.CREATED.getStatusCode()).when()
            .post("/containers/" + CONTAINER_NAME + "/folders/" + FOLDER_NAME);

        given().contentType(ContentType.JSON).then()
            .statusCode(Status.NO_CONTENT.getStatusCode()).when()
            .delete("/containers/" + CONTAINER_NAME + "/folders/" + FOLDER_NAME);
    }

    @Test
    public void givenFolderNotFoundWhenGetFolderThenReturnNotFound() {
        given().then().statusCode(Status.NOT_FOUND.getStatusCode()).when()
            .head("/containers" + CONTAINER_NAME + "/folders/" + FOLDER_NAME);
    }

    @Test
    public void givenFolderAlreadyExistsWhenCheckFolderThenReturnOk() {
        with().then()
            .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME);

        with().contentType(ContentType.JSON).then()
            .statusCode(Status.CREATED.getStatusCode()).when()
            .post("/containers/" + CONTAINER_NAME + "/folders/" + FOLDER_NAME);

        given().then().statusCode(Status.OK.getStatusCode()).when()
            .head("/containers/" + CONTAINER_NAME + "/folders/" + FOLDER_NAME);

    }

    // Object
    @Test
    public void givenContainerNotFoundWhenPutObjectThenReturnNotFound() throws IOException {
        try (InputStream stream = PropertiesUtils.getResourceAsStream("file1.pdf")) {
            given()
                .contentType(ContentType.BINARY).body(stream)
                .when().post("/containers/" + CONTAINER_NAME + "/objects/" + OBJECT_NAME)
                .then().statusCode(Status.NOT_FOUND.getStatusCode());
        }

    }

    @Test
    public void givenContainerAlreadyExistsWhenPutObjectThenReturnCreated() throws IOException {
        try (InputStream stream = PropertiesUtils.getResourceAsStream("file1.pdf")) {

            with().then()
                .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME);

            given().contentType(ContentType.BINARY).body(stream)
                .when().post("/containers/" + CONTAINER_NAME + "/objects/" + OBJECT_NAME)
                .then().statusCode(Status.CREATED.getStatusCode());
        }

    }

    @Test
    public void givenObjectAlreadyExistsWhenDeleteObjectThenReturnNotContent() throws IOException {
        try (InputStream stream = PropertiesUtils.getResourceAsStream("file1.pdf")) {

            with().then()
                .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME);

            with()
                .contentType(ContentType.BINARY).body(stream)
                .when().post("/containers/" + CONTAINER_NAME + "/objects/" + OBJECT_NAME)
                .then().statusCode(Status.CREATED.getStatusCode());

            given().then().statusCode(Status.NO_CONTENT.getStatusCode()).when()
                .delete("/containers/" + CONTAINER_NAME + "/objects/" + OBJECT_NAME);
        }
    }

    @Test
    public void givenObjectNotFoundWhenGetStatusThenReturnNotFound() {
        given().then().statusCode(Status.NOT_FOUND.getStatusCode()).when()
            .head("/containers/" + CONTAINER_NAME + "/objects/" + OBJECT_NAME);
    }

    @Test
    public void givenObjectNotFoundWhenComputeDigestThenReturnNotFound() {
        given().header(X_DIGEST_ALGORITHM, ALGO)
            .when()
            .head("/containers/" + CONTAINER_NAME + "/objects/" + OBJECT_NAME)
            .then().statusCode(Status.NOT_FOUND.getStatusCode());
    }

    @Test
    public void givenObjectNotFoundWhenDeleteObjectThenReturnNotFound() {
        given().then().statusCode(Status.NOT_FOUND.getStatusCode()).when()
            .delete("/containers/" + CONTAINER_NAME + "/objects/" + OBJECT_NAME);
    }

    @Test
    public void givenObjectAlreadyExistsWhenCheckObjectThenReturnOk() throws IOException {
        try (InputStream stream = PropertiesUtils.getResourceAsStream("file1.pdf")) {

            with().then()
                .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME);

            with()
                .contentType(ContentType.BINARY).body(stream)
                .when().post("/containers/" + CONTAINER_NAME + "/objects/" + OBJECT_NAME)
                .then().statusCode(Status.CREATED.getStatusCode());

            given().then().statusCode(Status.OK.getStatusCode()).when()
                .head("/containers/" + CONTAINER_NAME + "/objects/" + OBJECT_NAME);
        }
    }

    @Test
    public void givenObjectAlreadyExistsWhenComputeDigestThenReturnOk() throws IOException {
        try (InputStream stream = PropertiesUtils.getResourceAsStream("file1.pdf")) {

            with().then()
                .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME);

            with()
                .contentType(ContentType.BINARY).body(stream)
                .when().post("/containers/" + CONTAINER_NAME + "/objects/" + OBJECT_NAME)
                .then().statusCode(Status.CREATED.getStatusCode());

        }
        try (InputStream stream = PropertiesUtils.getResourceAsStream("file1.pdf")) {
            final Digest digest = new Digest(DigestType.fromValue(ALGO));
            digest.update(stream);

            given().header(X_DIGEST_ALGORITHM, ALGO)
                .when()
                .head("/containers/" + CONTAINER_NAME + "/objects/" + OBJECT_NAME)
                .then().statusCode(Status.OK.getStatusCode()).header(X_DIGEST, digest.toString());
        }
    }


    // get object
    @Test
    public void givenObjectNotFoundWhenGetObjectThenReturnNotFound() {
        given().contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_OCTET_STREAM).then()
            .statusCode(Status.NOT_FOUND.getStatusCode()).when()
            .get("/containers/" + CONTAINER_NAME + "/objects/" + OBJECT_NAME);
    }

    @Test
    public void givenObjectAlreadyExistsWhenGetObjectThenReturnOk() throws IOException {

        // Given
        try (InputStream stream = PropertiesUtils.getResourceAsStream("file1.pdf")) {

            with().then()
                .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME);

            with()
                .contentType(ContentType.BINARY).body(stream)
                .when().post("/containers/" + CONTAINER_NAME + "/objects/" + OBJECT_NAME)
                .then().statusCode(Status.CREATED.getStatusCode());
        }

        // When
        InputStream inputStream =
            given().contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_OCTET_STREAM).then()
                .statusCode(Status.OK.getStatusCode()).when()
                .get("/containers/" + CONTAINER_NAME + "/objects/" + OBJECT_NAME)
                .asInputStream();

        // Then
        try (InputStream expectedInputStream = PropertiesUtils.getResourceAsStream("file1.pdf")) {
            assertThat(inputStream).hasSameContentAs(expectedInputStream);
        }
    }

    @Test
    public void givenObjectAlreadyExistsWhenGetObjectWithZeroOffsetAndChunkSizeThenReturnOk() throws IOException {

        // Given
        try (InputStream stream = PropertiesUtils.getResourceAsStream("file1.pdf")) {

            with().then()
                .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME);

            with()
                .contentType(ContentType.BINARY).body(stream)
                .when().post("/containers/" + CONTAINER_NAME + "/objects/" + OBJECT_NAME)
                .then().statusCode(Status.CREATED.getStatusCode());
        }

        // When
        InputStream inputStream =
            given().contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_OCTET_STREAM)
                .header(GlobalDataRest.X_CHUNK_OFFSET, 0L)
                .header(GlobalDataRest.X_CHUNK_MAX_SIZE, 1_000_000_000L)
                .then()
                .statusCode(Status.OK.getStatusCode()).when()
                .get("/containers/" + CONTAINER_NAME + "/objects/" + OBJECT_NAME)
                .asInputStream();

        // Then
        try (InputStream expectedInputStream = PropertiesUtils.getResourceAsStream("file1.pdf")) {
            assertThat(inputStream).hasSameContentAs(expectedInputStream);
        }
    }

    @Test
    public void givenObjectAlreadyExistsWhenGetObjectWithOffsetAndChunkSizeThenReturnOk() throws IOException {

        // Given
        try (InputStream stream = PropertiesUtils.getResourceAsStream("file1.pdf")) {

            with().then()
                .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME);

            with()
                .contentType(ContentType.BINARY).body(stream)
                .when().post("/containers/" + CONTAINER_NAME + "/objects/" + OBJECT_NAME)
                .then().statusCode(Status.CREATED.getStatusCode());
        }

        // When
        InputStream inputStream =
            given().contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_OCTET_STREAM)
                .header(GlobalDataRest.X_CHUNK_OFFSET, 100L)
                .header(GlobalDataRest.X_CHUNK_MAX_SIZE, 200L)
                .then()
                .statusCode(Status.OK.getStatusCode()).when()
                .get("/containers/" + CONTAINER_NAME + "/objects/" + OBJECT_NAME)
                .asInputStream();

        // Then
        try (InputStream expectedInputStream = PropertiesUtils.getResourceAsStream("file1.pdf")) {

            IOUtils.readFully(expectedInputStream, 100);
            InputStream expectedInputStream2 = new BoundedInputStream(expectedInputStream, 200L);

            assertThat(inputStream).hasSameContentAs(expectedInputStream2);
        }
    }

    @Test
    public void givenObjectAlreadyExistsWhenGetObjectWithOffsetAndNoSizeThenReturnOk() throws IOException {

        // Given
        try (InputStream stream = PropertiesUtils.getResourceAsStream("file1.pdf")) {

            with().then()
                .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME);

            with()
                .contentType(ContentType.BINARY).body(stream)
                .when().post("/containers/" + CONTAINER_NAME + "/objects/" + OBJECT_NAME)
                .then().statusCode(Status.CREATED.getStatusCode());
        }

        // When
        InputStream inputStream =
            given().contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_OCTET_STREAM)
                .header(GlobalDataRest.X_CHUNK_OFFSET, 100L)
                .then()
                .statusCode(Status.OK.getStatusCode()).when()
                .get("/containers/" + CONTAINER_NAME + "/objects/" + OBJECT_NAME)
                .asInputStream();

        // Then
        try (InputStream expectedInputStream = PropertiesUtils.getResourceAsStream("file1.pdf")) {

            IOUtils.readFully(expectedInputStream, 100);

            assertThat(inputStream).hasSameContentAs(expectedInputStream);
        }
    }

    // get object information
    @Test
    public void givenObjectNotFoundWhenGetObjectInformationThenReturnNotFound() {
        given().contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).then()
            .statusCode(Status.NOT_FOUND.getStatusCode()).when()
            .get("/containers/" + CONTAINER_NAME + "/objects/" + OBJECT_NAME);
    }


    @Test
    public void givenObjectAlreadyExistsWhenGetObjectInformationThenReturnOk() throws IOException {
        try (InputStream stream = PropertiesUtils.getResourceAsStream("file1.pdf")) {
            with().then()
                .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME);

            with()
                .contentType(ContentType.BINARY).body(stream)
                .when().post("/containers/" + CONTAINER_NAME + "/objects/" + OBJECT_NAME)
                .then().statusCode(Status.CREATED.getStatusCode());

            given().then().statusCode(Status.OK.getStatusCode()).when()
                .get("/containers/" + CONTAINER_NAME + "/objects/" + OBJECT_NAME);
        }
    }

    // unzip
    @Test
    public void givenZipImputWhenUnzipThenReturnOK() throws IOException {
        try (InputStream stream = PropertiesUtils.getResourceAsStream("sip.zip")) {
            with().then()
                .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME);
            given().contentType(CommonMediaType.ZIP).body(stream)
                .then().statusCode(Status.CREATED.getStatusCode()).when()
                .put("/containers/" + CONTAINER_NAME + "/folders/" + FOLDER_SIP);
        }
    }

    @Test
    public void givenContainerNotFoundWhenUnzippingObjectThenReturnNotFound() throws IOException {
        try (InputStream stream = PropertiesUtils.getResourceAsStream("sip.zip")) {

            final byte[] bytes = IOUtils.toByteArray(stream); // need for the test !
            given()
                .contentType(CommonMediaType.ZIP)
                .config(RestAssured.config().encoderConfig(
                    EncoderConfig.encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                .body(stream).when()
                .put("/containers/" + CONTAINER_NAME + "/folders/" + FOLDER_SIP)
                .then()
                .statusCode(Status.NOT_FOUND.getStatusCode());
        }
    }

    @Test
    public void givenFolderAlreadyExistsWhenUnzippingObjectThenReturnConflict() throws IOException {
        try (InputStream stream = PropertiesUtils.getResourceAsStream("sip.zip")) {
            with().then()
                .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME);

            with().contentType(ContentType.JSON).then()
                .statusCode(Status.CREATED.getStatusCode()).when()
                .post("/containers/" + CONTAINER_NAME + "/folders/" + FOLDER_SIP);

            final byte[] bytes = IOUtils.toByteArray(stream); // need for the test !
            given()
                .contentType(CommonMediaType.ZIP)
                .config(RestAssured.config().encoderConfig(
                    EncoderConfig.encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                .content(stream).when()
                .put("/containers/" + CONTAINER_NAME + "/folders/" + FOLDER_SIP)
                .then()
                .statusCode(Status.CONFLICT.getStatusCode());
        }
    }

    @Test
    public void givenNonZipWhenUnzipThenReturnKO() throws IOException {
        try (InputStream stream =
            PropertiesUtils.getResourceAsStream("SIP_mauvais_format.pdf")) {

            with().then()
                .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME);

            given().contentType(CommonMediaType.ZIP).body(stream)
                .when()
                .put("/containers/" + CONTAINER_NAME + "/folders/" + FOLDER_SIP)
                .then().body(Matchers.equalTo("myContainer"))
                .statusCode(Status.INTERNAL_SERVER_ERROR.getStatusCode());

        }
    }

    // uriList
    @Test
    public void givenEmptyFolderWhenfindingThenReturnNoContent() throws IOException {
        with().then()
            .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME);

        given().contentType(ContentType.JSON).body(new Entry(FOLDER_NAME)).then()
            .statusCode(Status.NO_CONTENT.getStatusCode()).when()
            .get("/containers/" + CONTAINER_NAME + "/folders/" + FOLDER_NAME);
    }

    @Test
    public void givenNotEmptyFolderWhenfindingThenReturnOk() throws IOException {
        try (InputStream stream = PropertiesUtils.getResourceAsStream("file1.pdf")) {

            with().then()
                .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME);

            with()
                .contentType(ContentType.BINARY).body(stream)
                .when().post("/containers/" + CONTAINER_NAME + "/objects/" + FOLDER_NAME + "/" + OBJECT_NAME)
                .then().statusCode(Status.CREATED.getStatusCode());

            given().contentType(ContentType.JSON).body(new Entry(FOLDER_NAME)).then()
                .statusCode(Status.OK.getStatusCode()).when()
                .get("/containers/" + CONTAINER_NAME + "/folders/" + FOLDER_NAME);
        }
    }


    @Test
    public void givenContainerNameFolderNameNotExistWhenfindingThenReturn_NoContent() {

        with().then()
            .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME);

        given().contentType(ContentType.JSON).body(new Entry(FOLDER_NAME)).then()
            .statusCode(Status.NO_CONTENT.getStatusCode()).when()
            .get("/containers/" + CONTAINER_NAME + "/folders/" + FAKE_FOLDER_NAME);
    }

    @Test
    public void givenContainerNotFoundWhenCountThenReturnNotFound() {
        given().then().statusCode(Status.NOT_FOUND.getStatusCode()).when()
            .get("/containers/" + CONTAINER_NAME + "/count");
    }

    @Test
    public void givenContainerExistsWhenCountThenReturnOk() {
        with().then().statusCode(Status.CREATED.getStatusCode()).when()
            .post("/containers/" + CONTAINER_NAME);
        given().then().statusCode(Status.OK.getStatusCode()).when().get("/containers/" + CONTAINER_NAME + "/count");
    }

    @Test
    public void should_compress_workspace() throws Exception {
        try (InputStream stream = PropertiesUtils.getResourceAsStream("file1.pdf")) {

            String outputFile = "file.zip";

            with().then()
                .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME);

            with()
                .contentType(ContentType.BINARY).body(stream)
                .when().post("/containers/" + CONTAINER_NAME + "/objects/" + FOLDER_NAME + "/" + OBJECT_NAME)
                .then().statusCode(Status.CREATED.getStatusCode());

            String outputContainer = "outputContainer";

            with().then()
                .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + outputContainer);

            given().contentType(ContentType.JSON)
                .body(new CompressInformation(Collections.singletonList(FOLDER_NAME + "/" + OBJECT_NAME),
                    outputFile, outputContainer)).then()
                .statusCode(Status.CREATED.getStatusCode()).when()
                .post("/containers/" + CONTAINER_NAME);

            given().contentType(ContentType.JSON)
                .body(new CompressInformation(Collections.singletonList(FOLDER_NAME + "/" + OBJECT_NAME),
                    outputFile, outputContainer)).then()
                .statusCode(Status.CREATED.getStatusCode()).when()
                .post("/containers/" + CONTAINER_NAME);

            given().then()
                .statusCode(Status.OK.getStatusCode()).when()
                .get("/containers/" + outputContainer + "/objects/" + outputFile);
        }
    }

    @Test
    public void should_get_bulk_objects() throws Exception {

        byte[] file1 = "some-file".getBytes();
        byte[] file2 = "another-file".getBytes();

        String fileName1 = "file1.txt";
        String fileName2 = "toto/file2.txt";

        with().then()
            .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME);

        with()
            .contentType(ContentType.BINARY).body(new ByteArrayInputStream(file1))
            .when().post("/containers/" + CONTAINER_NAME + "/objects/" + fileName1)
            .then().statusCode(Status.CREATED.getStatusCode());

        with()
            .contentType(ContentType.BINARY).body(new ByteArrayInputStream(file2))
            .when().post("/containers/" + CONTAINER_NAME + "/objects/" + fileName2)
            .then().statusCode(Status.CREATED.getStatusCode());

        Response response = given().contentType(ContentType.JSON)
            .body(Arrays.asList(fileName1, fileName2)).then()
            .statusCode(Status.OK.getStatusCode()).when()
            .get("/containers/" + CONTAINER_NAME + "/objects")
            .andReturn();

        try (MultiplexedStreamReader reader = new MultiplexedStreamReader(response.asInputStream())) {
            assertThat(reader.readNextEntry().get()).hasSameContentAs(new ByteArrayInputStream(file1));
            assertThat(reader.readNextEntry().get()).hasSameContentAs(new ByteArrayInputStream(file2));
            assertThat(reader.readNextEntry()).isEmpty();
        }
    }

    @Test
    public void should_return_404_when_get_bulk_objects_with_unknown_file() throws Exception {

        byte[] file1 = "some-file".getBytes();

        String fileName1 = "file1.txt";
        String fileName2 = "toto/file2.txt";

        with().then()
            .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME);

        with()
            .contentType(ContentType.BINARY).body(new ByteArrayInputStream(file1))
            .when().post("/containers/" + CONTAINER_NAME + "/objects/" + fileName1)
            .then().statusCode(Status.CREATED.getStatusCode());

        given().contentType(ContentType.JSON)
            .body(Arrays.asList(fileName1, fileName2)).then()
            .statusCode(Status.NOT_FOUND.getStatusCode()).when()
            .get("/containers/" + CONTAINER_NAME + "/objects");
    }

    @Test
    public void should_purge_old_files() throws Exception {

        // Given 2 old files
        with().then()
            .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME);

        String file1 = "file1.zip";
        String file2 = "sub-folder/file2.zip";

        with()
            .contentType(ContentType.BINARY).body(IOUtils.toInputStream("test 1", StandardCharsets.UTF_8))
            .when().post("/containers/" + CONTAINER_NAME + "/objects/" + file1)
            .then().statusCode(Status.CREATED.getStatusCode());

        with()
            .contentType(ContentType.BINARY).body(IOUtils.toInputStream("test 2", StandardCharsets.UTF_8))
            .when().post("/containers/" + CONTAINER_NAME + "/objects/" + file2)
            .then().statusCode(Status.CREATED.getStatusCode());

        // When
        given().contentType(ContentType.JSON)
            .body(new TimeToLive(1, ChronoUnit.MINUTES)).then()
            .statusCode(Status.NO_CONTENT.getStatusCode()).when()
            .delete("/containers/" + CONTAINER_NAME + "/old_files");

        // Then
        given().then().statusCode(Status.OK.getStatusCode()).when().get("/containers/" + CONTAINER_NAME + "/objects/" + file1);
        given().then().statusCode(Status.OK.getStatusCode()).when().get("/containers/" + CONTAINER_NAME + "/objects/" + file2);
    }

    @Test
    public void should_not_purge_new_files() throws Exception {

        // Given 2 new files
        with().then()
            .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME);

        String file1 = "file1.zip";
        String file2 = "sub-folder/file2.zip";

        with()
            .contentType(ContentType.BINARY).body(IOUtils.toInputStream("test 1", StandardCharsets.UTF_8))
            .when().post("/containers/" + CONTAINER_NAME + "/objects/" + file1)
            .then().statusCode(Status.CREATED.getStatusCode());

        with()
            .contentType(ContentType.BINARY).body(IOUtils.toInputStream("test 2", StandardCharsets.UTF_8))
            .when().post("/containers/" + CONTAINER_NAME + "/objects/" + file2)
            .then().statusCode(Status.CREATED.getStatusCode());

        TimeUnit.SECONDS.sleep(2);

        // When
        given().contentType(ContentType.JSON)
            .body(new TimeToLive(1, ChronoUnit.SECONDS)).then()
            .statusCode(Status.NO_CONTENT.getStatusCode()).when()
            .delete("/containers/" + CONTAINER_NAME + "/old_files");

        // Then
        given().then().statusCode(Status.NOT_FOUND.getStatusCode()).when().get("/containers/" + CONTAINER_NAME + "/objects/" + file1);
        given().then().statusCode(Status.NOT_FOUND.getStatusCode()).when().get("/containers/" + CONTAINER_NAME + "/objects/" + file2);
    }
}
