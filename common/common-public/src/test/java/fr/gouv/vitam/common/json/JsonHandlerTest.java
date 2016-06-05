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
package fr.gouv.vitam.common.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileNotFoundException;

import org.junit.Assume;
import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;

import fr.gouv.vitam.common.ResourcesPublicUtilTest;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;

public class JsonHandlerTest {
    private static final VitamLogger LOGGER =
        VitamLoggerFactory.getInstance(JsonHandlerTest.class);

    @Test
    public final void testGetFactory() {
        assertNotNull(JsonHandler.getFactory());
        assertNotNull(JsonHandler.createObjectNode());
        assertNotNull(JsonHandler.createArrayNode());
    }

    private static class TestClass {
        String a;

        @JsonGetter("a")
        protected final String getA() {
            return a;
        }

        @JsonSetter("a")
        protected final TestClass setA(String a) {
            this.a = a;
            return this;
        }

        @Override
        public String toString() {
            return "TC: " + a;
        }
    }

    @Test
    public final void testJsonString() throws InvalidParseOperationException, FileNotFoundException {
        final JsonNode node = JsonHandler.getFromString("{ 'a' : 'val' }");
        final File file = ResourcesPublicUtilTest.getInstance().getJsonTestJsonFile();
        if (file == null) {
            LOGGER.error(ResourcesPublicUtilTest.CANNOT_FIND_RESOURCES_TEST_FILE);
        }
        Assume.assumeTrue(ResourcesPublicUtilTest.CANNOT_FIND_RESOURCES_TEST_FILE, file != null);

        final JsonNode node2 = JsonHandler.getFromFile(file);
        assertEquals(node.toString(), node2.toString());
        final TestClass tc = JsonHandler.getFromString(node.toString(), TestClass.class);
        assertEquals("val", tc.getA());
        final JsonNode node3 = JsonHandler.getFromBytes(node.toString().getBytes());
        assertEquals(node.toString(), node3.toString());
        final TestClass tc2 = JsonHandler.getFromFile(file, TestClass.class);
        assertEquals("val", tc2.getA());
        final JsonNode node4 = JsonHandler.toJsonNode(tc2);
        assertEquals(JsonHandler.prettyPrint(node4), JsonHandler.writeAsString(tc2));
        assertEquals(JsonHandler.prettyPrint(node4), JsonHandler.prettyPrint(tc2));
        assertEquals("a", JsonHandler.checkUnicity("check", node4).getKey());
        assertEquals("a", JsonHandler.checkLaxUnicity("check", node4).getKey());
        assertEquals(1, JsonHandler.getMapFromString(node4.toString()).size());
        assertEquals(1, JsonHandler.getMapStringFromString(node4.toString()).size());
    }

    @Test
    public final void testError() {
        try {
            JsonHandler.getFromBytes(null);
            fail(ResourcesPublicUtilTest.SHOULD_RAIZED_AN_EXCEPTION);
        } catch (final InvalidParseOperationException e) {// NOSONAR
            // Ignore
        }
        try {
            JsonHandler.getFromFile(null);
            fail(ResourcesPublicUtilTest.SHOULD_RAIZED_AN_EXCEPTION);
        } catch (final InvalidParseOperationException e) {// NOSONAR
            // Ignore
        }
        try {
            JsonHandler.getFromString(null);
            fail(ResourcesPublicUtilTest.SHOULD_RAIZED_AN_EXCEPTION);
        } catch (final InvalidParseOperationException e) {// NOSONAR
            // Ignore
        }
        try {
            JsonHandler.getFromString(null, TestClass.class);
            fail(ResourcesPublicUtilTest.SHOULD_RAIZED_AN_EXCEPTION);
        } catch (final InvalidParseOperationException e) {// NOSONAR
            // Ignore
        }
        try {
            JsonHandler.getFromString("{}", null);
            fail(ResourcesPublicUtilTest.SHOULD_RAIZED_AN_EXCEPTION);
        } catch (final InvalidParseOperationException e) {// NOSONAR
            // Ignore
        }
        try {
            JsonHandler.getFromFile(null, TestClass.class);
            fail(ResourcesPublicUtilTest.SHOULD_RAIZED_AN_EXCEPTION);
        } catch (final InvalidParseOperationException e) {// NOSONAR
            // Ignore
        }
        try {
            JsonHandler.getFromFile(new File(""), null);
            fail(ResourcesPublicUtilTest.SHOULD_RAIZED_AN_EXCEPTION);
        } catch (final InvalidParseOperationException e) {// NOSONAR
            // Ignore
        }
        try {
            JsonHandler.writeAsString(null);
            fail(ResourcesPublicUtilTest.SHOULD_RAIZED_AN_EXCEPTION);
        } catch (final InvalidParseOperationException e) {// NOSONAR
            // Ignore
        }
        try {
            JsonHandler.writeAsFile(null, new File(""));
            fail(ResourcesPublicUtilTest.SHOULD_RAIZED_AN_EXCEPTION);
        } catch (final InvalidParseOperationException e) {// NOSONAR
            // Ignore
        }
        try {
            JsonHandler.writeAsFile(new File(""), null);
            fail(ResourcesPublicUtilTest.SHOULD_RAIZED_AN_EXCEPTION);
        } catch (final InvalidParseOperationException e) {// NOSONAR
            // Ignore
        }
        try {
            JsonHandler.toJsonNode(null);
            fail(ResourcesPublicUtilTest.SHOULD_RAIZED_AN_EXCEPTION);
        } catch (final InvalidParseOperationException e) {// NOSONAR
            // Ignore
        }
        assertEquals("{}", JsonHandler.prettyPrint(null));
        try {
            JsonHandler.checkUnicity("", null);
            fail(ResourcesPublicUtilTest.SHOULD_RAIZED_AN_EXCEPTION);
        } catch (final InvalidParseOperationException e) {// NOSONAR
            // Ignore
        }
        try {
            JsonHandler.checkLaxUnicity("", null);
            fail(ResourcesPublicUtilTest.SHOULD_RAIZED_AN_EXCEPTION);
        } catch (final InvalidParseOperationException e) {// NOSONAR
            // Ignore
        }
        try {
            JsonHandler.checkUnicity("title",
                JsonHandler.getFromString("{ 'a' : 'val','b' : { 'c' : 'val2' } }"));
            fail(ResourcesPublicUtilTest.SHOULD_RAIZED_AN_EXCEPTION);
        } catch (final InvalidParseOperationException e) {// NOSONAR
            // Ignore
        }
        try {
            JsonHandler.checkLaxUnicity("title",
                JsonHandler.getFromString("{ 'a' : 'val','b' : { 'c' : 'val2' } }"));
            fail(ResourcesPublicUtilTest.SHOULD_RAIZED_AN_EXCEPTION);
        } catch (final InvalidParseOperationException e) {// NOSONAR
            // Ignore
        }
        try {
            JsonHandler.checkUnicity("title",
                JsonHandler.getFromString("{ }"));
            fail(ResourcesPublicUtilTest.SHOULD_RAIZED_AN_EXCEPTION);
        } catch (final InvalidParseOperationException e) {// NOSONAR
            // Ignore
        }
        try {
            JsonHandler.checkLaxUnicity("title",
                JsonHandler.getFromString("{  }"));
            fail(ResourcesPublicUtilTest.SHOULD_RAIZED_AN_EXCEPTION);
        } catch (final InvalidParseOperationException e) {// NOSONAR
            // Ignore
        }
        final JsonNode node = JsonHandler.createObjectNode().numberNode(2.0);
        try {
            JsonHandler.checkUnicity("title", node);
            fail(ResourcesPublicUtilTest.SHOULD_RAIZED_AN_EXCEPTION);
        } catch (final InvalidParseOperationException e) {// NOSONAR
            // Ignore
        }
        try {
            assertNotNull(JsonHandler.checkLaxUnicity("title", node));
        } catch (final InvalidParseOperationException e) {// NOSONAR
            fail(ResourcesPublicUtilTest.SHOULD_NOT_RAIZED_AN_EXCEPTION);
        }
        try {
            assertEquals(0, JsonHandler.getMapFromString(null).size());
        } catch (final InvalidParseOperationException e1) {// NOSONAR
            fail(ResourcesPublicUtilTest.SHOULD_NOT_RAIZED_AN_EXCEPTION);
        }
        try {
            assertEquals(0, JsonHandler.getMapStringFromString(null).size());
        } catch (final InvalidParseOperationException e1) {// NOSONAR
            fail(ResourcesPublicUtilTest.SHOULD_NOT_RAIZED_AN_EXCEPTION);
        }
        try {
            JsonHandler.getMapFromString("{");
            fail(ResourcesPublicUtilTest.SHOULD_RAIZED_AN_EXCEPTION);
        } catch (final InvalidParseOperationException e) {// NOSONAR
            // Ignore
        }
        try {
            JsonHandler.getMapStringFromString("{");
            fail(ResourcesPublicUtilTest.SHOULD_RAIZED_AN_EXCEPTION);
        } catch (final InvalidParseOperationException e) {// NOSONAR
            // Ignore
        }

    }
}
