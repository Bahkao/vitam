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
package fr.gouv.vitam.storage.offers.workspace.driver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import fr.gouv.vitam.common.junit.JunitHelper;
import fr.gouv.vitam.storage.driver.exception.StorageDriverException;

public class DriverImplTest extends JerseyTest {

    private static DriverImpl driver;
    protected static final String HOSTNAME = "localhost";
    private static final String DRIVER_NAME = "WorkspaceDriver";
    private static JunitHelper junitHelper;
    private static int port;

    protected ExpectedResults mock;

    interface ExpectedResults {
        Response get();
    }

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        driver = new DriverImpl();
        junitHelper = new JunitHelper();
        port = junitHelper.findAvailablePort();
    }

    @Override
    protected Application configure() {
        enable(TestProperties.DUMP_ENTITY);
        forceSet(TestProperties.CONTAINER_PORT, Integer.toString(port));
        mock = mock(ExpectedResults.class);
        final ResourceConfig resourceConfig = new ResourceConfig();
        resourceConfig.register(JacksonFeature.class);
        return resourceConfig.registerInstances(new MockResource(mock));
    }

    @Path("/offer/v1")
    public static class MockResource {
        private final ExpectedResults expectedResponse;

        public MockResource(ExpectedResults expectedResponse) {
            this.expectedResponse = expectedResponse;
        }

        @GET
        @Path("/status")
        @Produces(MediaType.APPLICATION_JSON)
        public Response getStatus() {
            return expectedResponse.get();
        }

    }

    @Test(expected = StorageDriverException.class)
    public void givenNullUrlThenRaiseAnException() throws Exception {
        driver.connect(null, null);
    }

    @Test(expected = StorageDriverException.class)
    public void givenCorrectUrlThenConnectResponseKO() throws Exception {
        when(mock.get()).thenReturn(Response.status(Status.INTERNAL_SERVER_ERROR).build());
        driver.connect("http://" + HOSTNAME + ":" + port, null);
    }

    @Test
    public void givenCorrectUrlThenConnectResponseOK() throws Exception {
        when(mock.get()).thenReturn(Response.status(Status.OK).build());
        ConnectionImpl connection = driver.connect("http://" + HOSTNAME + ":" + port, null);
        assertNotNull(connection);
    }

    @Test()
    public void getNameOK() throws Exception {
        assertEquals(DRIVER_NAME, driver.getName());
    }

    @Test()
    public void isStorageOfferAvailableOK() throws Exception {
        assertEquals(true, driver.isStorageOfferAvailable(null, null));
    }

    @Test()
    public void getMajorVersionOK() throws Exception {
        assertEquals(0, driver.getMajorVersion());
    }

    @Test()
    public void getMinorVersionOK() throws Exception {
        assertEquals(0, driver.getMinorVersion());
    }

}
