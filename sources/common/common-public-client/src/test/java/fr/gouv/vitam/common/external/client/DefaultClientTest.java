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
package fr.gouv.vitam.common.external.client;

import com.google.common.collect.Sets;
import fr.gouv.vitam.common.exception.VitamApplicationServerException;
import fr.gouv.vitam.common.exception.VitamClientException;
import fr.gouv.vitam.common.exception.VitamClientInternalException;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.server.application.junit.ResteasyTestApplication;
import fr.gouv.vitam.common.server.application.resources.ApplicationStatusResource;
import fr.gouv.vitam.common.serverv2.VitamServerTestRunner;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.GET;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.NotAcceptableException;
import javax.ws.rs.NotAllowedException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.NotSupportedException;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.ServiceUnavailableException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.Set;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

public class DefaultClientTest extends ResteasyTestApplication {
    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(DefaultClientTest.class);
    private static final String RESOURCE_PATH = "/vitam-test/v1";

    private final static ExpectedResults mock = mock(ExpectedResults.class);

    private static DefaultClient client;

    static TestVitamClientFactory
        factory = new TestVitamClientFactory<DefaultClient>(
        1, RESOURCE_PATH);
    public static VitamServerTestRunner
        vitamServerTestRunner = new VitamServerTestRunner(DefaultClientTest.class, factory);


    @BeforeClass
    public static void setUpBeforeClass() throws Throwable {
        vitamServerTestRunner.start();
        client = (DefaultClient) vitamServerTestRunner.getClient();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Throwable {
        vitamServerTestRunner.runAfter();
    }

    @Before
    public void before() {
        reset(mock);
    }

    @Override
    public Set<Object> getResources() {
        return Sets.newHashSet(new MockResource(mock));
    }

    // Define your Resource class if necessary
    @Path(RESOURCE_PATH)
    @javax.ws.rs.ApplicationPath("webresources")
    public static class MockResource extends ApplicationStatusResource {
        private final ExpectedResults mock;

        public MockResource(ExpectedResults mock) {
            this.mock = mock;
        }

        @GET
        @Path(STATUS_URL)
        @Produces(MediaType.APPLICATION_JSON)
        @Override
        public Response status() {
            return mock.get();
        }
    }

    // Now write your tests
    @Test
    public void statusExecutionWithouthBody() throws Exception {
        when(mock.get()).thenReturn(Response.status(Response.Status.OK).build());
        client.checkStatus();
    }

    @Test
    public void constructorWithGivenClient() throws VitamClientException {
        final Client mock = mock(Client.class);
        final TestVitamClientFactory<DefaultClient> testMockFactory =
            new TestVitamClientFactory<>(vitamServerTestRunner.getBusinessPort(), RESOURCE_PATH, mock);
        try (DefaultClient testClient = testMockFactory.getClient()) {
            assertEquals(mock, testClient.getHttpClient());
            assertEquals("http://localhost:" + vitamServerTestRunner.getBusinessPort() + client.getResourcePath(),
                testClient.getServiceUrl());
        }
    }

    @Test
    public void statusExecutionWithBody() throws Exception {
        when(mock.get()).thenReturn(
            Response.status(Response.Status.NO_CONTENT).build());
        client.checkStatus();
        assertTrue("no exception".length() > 0);
        assertTrue(client.getChunkedMode());
        assertTrue(client.getHttpClient() == client.getHttpClient(true));
        String map1 = factory.getDefaultConfigCient().toString();
        String map2 = factory.getDefaultConfigCient(true).toString();
        LOGGER.warn(map1);
        assertTrue(map1.equals(map2));
    }

    @Test
    public void statusExecutionThroughPerformRequest() throws Exception {
        when(mock.get()).thenReturn(
            Response.status(Response.Status.OK).entity("{\"pid\":\"1\",\"name\":\"name1\", \"role\":\"role1\"}")
                .build());
        final MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.add("X-Test", "testvalue");
        LOGGER.warn("Coinfig: " + client.clientFactory.getDefaultConfigCient());
        Response message =
            client.performRequest(HttpMethod.GET, BasicClient.STATUS_URL, headers, MediaType.APPLICATION_JSON_TYPE);
        assertEquals(Response.Status.OK.getStatusCode(), message.getStatus());
        when(mock.get()).thenReturn(
            Response.status(Response.Status.OK).entity("{\"pid\":\"1\",\"name\":\"name1\", \"role\":\"role1\"}")
                .build());
        message = client.performRequest(HttpMethod.GET, BasicClient.STATUS_URL, headers, null, null,
            MediaType.APPLICATION_JSON_TYPE);
        assertEquals(Response.Status.OK.getStatusCode(), message.getStatus());
        when(mock.get()).thenReturn(
            Response.status(Response.Status.OK).entity("{\"pid\":\"1\",\"name\":\"name1\", \"role\":\"role1\"}")
                .build());
        message = client.performRequest(HttpMethod.GET, BasicClient.STATUS_URL, headers, null, null,
            MediaType.APPLICATION_JSON_TYPE, true);
        assertEquals(Response.Status.OK.getStatusCode(), message.getStatus());
        when(mock.get()).thenReturn(
            Response.status(Response.Status.OK).entity("{\"pid\":\"1\",\"name\":\"name1\", \"role\":\"role1\"}")
                .build());
        message = client.performRequest(HttpMethod.GET, BasicClient.STATUS_URL, headers, null, null,
            MediaType.APPLICATION_JSON_TYPE, false);
        assertEquals(Response.Status.OK.getStatusCode(), message.getStatus());
        when(mock.get()).thenReturn(
            Response.status(Response.Status.OK).entity("{\"pid\":\"1\",\"name\":\"name1\", \"role\":\"role1\"}")
                .build());
        message = client.performRequest(HttpMethod.GET, BasicClient.STATUS_URL, headers, "{}",
            MediaType.APPLICATION_JSON_TYPE,
            MediaType.APPLICATION_JSON_TYPE, false);
        assertEquals(Response.Status.OK.getStatusCode(), message.getStatus());
    }

    @Test
    public void statusExecutionThroughPerformAsyncRequest() throws Exception {
        when(mock.get()).thenReturn(
            Response.status(Response.Status.OK).entity("{\"pid\":\"1\",\"name\":\"name1\", \"role\":\"role1\"}")
                .build());
        final MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.add("X-Test", "testvalue");
        Future<Response> future = client.performAsyncRequest(HttpMethod.GET, BasicClient.STATUS_URL, headers, null,
            null, MediaType.APPLICATION_JSON_TYPE);
        Response message = future.get();
        assertEquals(Response.Status.OK.getStatusCode(), message.getStatus());
        when(mock.get()).thenReturn(
            Response.status(Response.Status.OK).entity("{\"pid\":\"1\",\"name\":\"name1\", \"role\":\"role1\"}")
                .build());
        future = client.performAsyncRequest(HttpMethod.GET, BasicClient.STATUS_URL, headers, null, null,
            MediaType.APPLICATION_JSON_TYPE,
            new InvocationCallback<Response>() {

                @Override
                public void completed(Response response) {
                    // Completed
                }

                @Override
                public void failed(Throwable throwable) {
                    // Failed
                    LOGGER.error("Failed Status in Async Callback", throwable);
                }
            });
        message = future.get();
        assertEquals(Response.Status.OK.getStatusCode(), message.getStatus());
        when(mock.get()).thenReturn(
            Response.status(Response.Status.OK).entity("{\"pid\":\"1\",\"name\":\"name1\", \"role\":\"role1\"}")
                .build());
        headers.clear();
        headers.add("X-Test", "testvalue");
        future = client.performAsyncRequest(HttpMethod.GET, BasicClient.STATUS_URL, headers, "{}",
            MediaType.APPLICATION_JSON_TYPE, MediaType.APPLICATION_JSON_TYPE);
        message = future.get();
        assertEquals(Response.Status.OK.getStatusCode(), message.getStatus());
        when(mock.get()).thenReturn(
            Response.status(Response.Status.OK).entity("{\"pid\":\"1\",\"name\":\"name1\", \"role\":\"role1\"}")
                .build());
        future = client.performAsyncRequest(HttpMethod.GET, BasicClient.STATUS_URL, headers, "{}",
            MediaType.APPLICATION_JSON_TYPE,
            MediaType.APPLICATION_JSON_TYPE,
            new InvocationCallback<Response>() {

                @Override
                public void completed(Response response) {
                    // Completed
                }

                @Override
                public void failed(Throwable throwable) {
                    // Failed
                    LOGGER.error("Failed Status in Async Callback", throwable);
                }
            });
        message = future.get();
        assertEquals(Response.Status.OK.getStatusCode(), message.getStatus());
    }

    @Test(expected = VitamApplicationServerException.class)
    public void failsStatusExecution() throws Exception {
        when(mock.get()).thenReturn(Response.status(Response.Status.SERVICE_UNAVAILABLE).build());
        client.checkStatus();
    }

    @Test
    public void testVariousFails() throws Throwable {
        Response response;
        try {
            when(mock.get()).thenThrow(new ForbiddenException());
            response = client.performRequest(HttpMethod.GET, "/status", null, MediaType.APPLICATION_JSON_TYPE);
            assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
        } catch (final Exception e) {
            // Ignore
        }
        try {
            when(mock.get()).thenThrow(new NotAcceptableException());
            response = client.performRequest(HttpMethod.GET, "/status", null, MediaType.APPLICATION_JSON_TYPE);
            assertEquals(Response.Status.NOT_ACCEPTABLE.getStatusCode(), response.getStatus());
        } catch (final Exception e) {
            // Ignore
        }
        try {
            when(mock.get()).thenThrow(new NotAllowedException("POST"));
            response = client.performRequest(HttpMethod.GET, "/status", null, MediaType.APPLICATION_JSON_TYPE);
            assertEquals(Response.Status.METHOD_NOT_ALLOWED.getStatusCode(), response.getStatus());
        } catch (final Exception e) {
            // Ignore
        }
        try {
            when(mock.get()).thenThrow(new NotAuthorizedException(Response.status(Status.UNAUTHORIZED).build()));
            response = client.performRequest(HttpMethod.GET, "/status", null, MediaType.APPLICATION_JSON_TYPE);
            assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
        } catch (final Exception e) {
            // Ignore
        }
        try {
            when(mock.get()).thenThrow(new NotSupportedException());
            response = client.performRequest(HttpMethod.GET, "/status", null, MediaType.APPLICATION_JSON_TYPE);
            assertEquals(Response.Status.UNSUPPORTED_MEDIA_TYPE.getStatusCode(), response.getStatus());
        } catch (final Exception e) {
            // Ignore
        }
        try {
            when(mock.get()).thenThrow(new ServiceUnavailableException());
            response = client.performRequest(HttpMethod.GET, "/status", null, MediaType.APPLICATION_JSON_TYPE);
            assertEquals(Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), response.getStatus());
        } catch (final Exception e) {
            // Ignore
        }
        try {
            when(mock.get()).thenThrow(new NotFoundException());
            response =
                client.performRequest(HttpMethod.GET, "/statusNotFound", null, MediaType.APPLICATION_JSON_TYPE);
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        } catch (final Exception e) {
            // Ignore
        }
        // try to get the retry when unavailable host
        vitamServerTestRunner.stop();
        try {
            response =
                client.performRequest(HttpMethod.GET, "/status", null, MediaType.APPLICATION_JSON_TYPE);
            fail("Should generate an exception");
        } catch (final VitamClientInternalException e) {
            // Ignore
            LOGGER.info(e);
        }
        try {
            response = client.performRequest(HttpMethod.GET, BasicClient.STATUS_URL, null,
                "{\"pid\":\"1\",\"name\":\"name1\", \"role\":\"role1\"}",
                MediaType.APPLICATION_JSON_TYPE,
                MediaType.APPLICATION_JSON_TYPE, false);
            fail("Should generate an exception");
        } catch (final VitamClientInternalException e) {
            // Ignore
            LOGGER.info(e);
        }
        try {
            response = client.performRequest(HttpMethod.GET, BasicClient.STATUS_URL, null,
                "{\"pid\":\"1\",\"name\":\"name1\", \"role\":\"role1\"}",
                MediaType.APPLICATION_JSON_TYPE,
                MediaType.APPLICATION_JSON_TYPE, true);
            fail("Should generate an exception");
        } catch (final VitamClientInternalException e) {
            // Ignore
            LOGGER.info(e);
        }

        vitamServerTestRunner.start();
    }
}
