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
package fr.gouv.vitam.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assume;
import org.junit.Test;

import fr.gouv.vitam.common.ServerIdentity.MAP_KEYNAME;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;

public class ServerIdentityTest {
    private static final VitamLogger LOGGER =
        VitamLoggerFactory.getInstance(ServerIdentityTest.class);

    private static boolean first = true;

    public static final void testGetInstance() {
        if (first) {
            final ServerIdentityInterface serverIdentity = ServerIdentity.getInstance();
            assertNotNull("Should not be null", serverIdentity);
            assertEquals("Role should be set to default", "UnknownRole", serverIdentity.getRole());
            assertNotEquals("Mac Address should not be 0", 0, serverIdentity.getPlatformId());
            assertFalse("Server Name should not be empty", serverIdentity.getName().isEmpty());
            first = false;
        }
    }

    @Test
    public final void testSetName() {
        testGetInstance();
        final ServerIdentity serverIdentity = ServerIdentity.getInstance();
        final String role = serverIdentity.getRole();
        final int pid = serverIdentity.getPlatformId();
        serverIdentity.setName("name1");
        assertEquals("Server Name test", "name1", serverIdentity.getName());
        assertEquals("Role still the same", role, serverIdentity.getRole());
        assertEquals("Pid still the same", pid, serverIdentity.getPlatformId());
    }

    @Test
    public final void testSetRole() {
        testGetInstance();
        final ServerIdentity serverIdentity = ServerIdentity.getInstance();
        final String name = serverIdentity.getName();
        final int pid = serverIdentity.getPlatformId();
        serverIdentity.setRole("name2");
        assertEquals("Server Role test", "name2", serverIdentity.getRole());
        assertEquals("Name still the same", name, serverIdentity.getName());
        assertEquals("Pid still the same", pid, serverIdentity.getPlatformId());
    }

    @Test
    public final void testSetPlatformId() {
        testGetInstance();
        final ServerIdentity serverIdentity = ServerIdentity.getInstance();
        final String name = serverIdentity.getName();
        final String role = serverIdentity.getRole();
        serverIdentity.setPlatformId(2);
        assertEquals("Platform Id test", 2, serverIdentity.getPlatformId());
        assertEquals("Name still the same", name, serverIdentity.getName());
        assertEquals("Role still the same", role, serverIdentity.getRole());
    }

    @Test
    public final void testSetFromMap() {
        testGetInstance();
        final ServerIdentity serverIdentity = ServerIdentity.getInstance();
        final Map<String, Object> map = new HashMap<>();
        map.put(MAP_KEYNAME.NAME.name(), "valuemap1");
        map.put(MAP_KEYNAME.ROLE.name(), "valuemap2");
        map.put(MAP_KEYNAME.PLATFORMID.name(), Long.valueOf(4));
        serverIdentity.setFromMap(map);
        assertEquals("Name still the same", "valuemap1", serverIdentity.getName());
        assertEquals("Role still the same", "valuemap2", serverIdentity.getRole());
        assertEquals("Pid still the same", 4, serverIdentity.getPlatformId());
        // by piece
        map.clear();
        map.put(MAP_KEYNAME.NAME.name(), "valuemap3");
        serverIdentity.setFromMap(map);
        assertEquals("Name still the same", "valuemap3", serverIdentity.getName());
        assertEquals("Role still the same", "valuemap2", serverIdentity.getRole());
        assertEquals("Pid still the same", 4, serverIdentity.getPlatformId());
        map.clear();
        map.put(MAP_KEYNAME.ROLE.name(), "valuemap4");
        serverIdentity.setFromMap(map);
        assertEquals("Name still the same", "valuemap3", serverIdentity.getName());
        assertEquals("Role still the same", "valuemap4", serverIdentity.getRole());
        assertEquals("Pid still the same", 4, serverIdentity.getPlatformId());
        map.clear();
        map.put(MAP_KEYNAME.PLATFORMID.name(), Long.valueOf(6));
        serverIdentity.setFromMap(map);
        assertEquals("Name still the same", "valuemap3", serverIdentity.getName());
        assertEquals("Role still the same", "valuemap4", serverIdentity.getRole());
        assertEquals("Pid still the same", 6, serverIdentity.getPlatformId());
        map.clear();
        map.put(MAP_KEYNAME.PLATFORMID.name(), "7");
        serverIdentity.setFromMap(map);
        assertEquals("Name still the same", "valuemap3", serverIdentity.getName());
        assertEquals("Role still the same", "valuemap4", serverIdentity.getRole());
        assertEquals("Pid still the same", 7, serverIdentity.getPlatformId());
        map.clear();
        map.put(MAP_KEYNAME.PLATFORMID.name(), "7a");
        serverIdentity.setFromMap(map);
        assertEquals("Name still the same", "valuemap3", serverIdentity.getName());
        assertEquals("Role still the same", "valuemap4", serverIdentity.getRole());
        assertEquals("Pid still the same", 7, serverIdentity.getPlatformId());
    }

    @Test
    public final void testSetFromPropertyFile() {
        testGetInstance();
        final ServerIdentity serverIdentity = ServerIdentity.getInstance();
        final File file = ResourcesPrivateUtilTest.getInstance().getServerIdentityPropertiesFile();
        if (file == null) {
            LOGGER.error(ResourcesPrivateUtilTest.CANNOT_FIND_RESOURCES_TEST_FILE);
        }
        Assume.assumeTrue(ResourcesPrivateUtilTest.CANNOT_FIND_RESOURCES_TEST_FILE, file != null);

        try {
            serverIdentity.setFromPropertyFile(file);
        } catch (final FileNotFoundException e) {
            LOGGER.error("Property file not found", e);
            fail("Should find the property file: " + e.getMessage());
        }
        assertEquals("Name still the same", "test1", serverIdentity.getName());
        assertEquals("Role still the same", "test2", serverIdentity.getRole());
        assertEquals("Pid still the same", 1000, serverIdentity.getPlatformId());
    }

}
