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
package fr.gouv.vitam.metadata.core.database.collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;

import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.json.JsonHandler;

public class ObjectGroupTest {

    private final int IntTest = 12345;
    String groupGUID = GUIDFactory.newObjectGUID(IntTest).toString();
    private final String go = "{\"_id\":\"" + groupGUID +
        "\", \"_qualifiers\" :{\"Physique Master\" : {\"PhysiqueOId\" : \"abceff\", \"Description\" : \"Test\"}}, \"title\":\"title1\"}";

    @Test
    public void testGOInitialization() throws InvalidParseOperationException {
        final JsonNode jsonGO = JsonHandler.getFromString(go);

        final ObjectGroup go1 = new ObjectGroup();
        final ObjectGroup go2 = new ObjectGroup(go);
        final ObjectGroup go3 = new ObjectGroup(jsonGO);

        assertTrue(go1.isEmpty());
        assertFalse(go2.isEmpty());
        assertEquals("Document{{}}", go1.toStringDirect());
        assertNotNull(go3);
    }

    @Test
    public void testloadDocument() {
        final ObjectGroup group = new ObjectGroup();
        group.load(go);
        assertNotNull(group);
    }

    @Test
    public void givenObjectGroupWhenGetGuid() {
        final ObjectGroup group = new ObjectGroup(go);
        assertNotNull(group.newObjectGuid());
        assertEquals(2, ObjectGroup.getGUIDObjectTypeId());
    }

    @Test
    public void givenObjectGroupWhenGetFathersUnitIdThenReturnAList() {
        final ObjectGroup group = new ObjectGroup();
        assertNotNull(group.getFathersUnitIds(true));
        assertNotNull(group.getFathersUnitIds(false));
    }

    @Test
    public void givenObjectGroupWhenCleanStructureThenItemCleaned() {
        final ObjectGroup group = new ObjectGroup();
        group.cleanStructure(true);
    }

    @Test
    public void givenObjectGroupWhenGetCollection() {
        final ObjectGroup group = new ObjectGroup();
        group.getMetadataCollections();
    }

    @Test
    public void givenObjectGroupWhenIsNotImmediateParentThenReturnFalse() {
        final ObjectGroup group = new ObjectGroup(go);
        assertFalse(group.isImmediateParent(groupGUID));
    }
}
