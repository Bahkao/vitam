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
package fr.gouv.vitam.worker.core.plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.database.builder.request.exception.InvalidCreateOperationException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.metadata.client.MetaDataClient;
import fr.gouv.vitam.metadata.client.MetaDataClientFactory;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.storage.engine.common.model.StorageCollectionType;
import fr.gouv.vitam.storage.engine.common.model.request.ObjectDescription;
import fr.gouv.vitam.storage.engine.common.model.response.StoredInfoResult;
import fr.gouv.vitam.worker.common.HandlerIO;
import fr.gouv.vitam.worker.common.utils.IngestWorkflowConstants;
import fr.gouv.vitam.worker.common.utils.SedaConstants;

/**
 * StoreObjectGroupAction Plugin.<br>
 *
 */
public class StoreObjectGroupActionPlugin extends StoreObjectActionHandler {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(StoreObjectGroupActionPlugin.class);

    private static final String STORING_OBJECT_TASK_ID = "OBJECT_STORAGE_SUB_TASK";
    private static final String SIP = "SIP/";
    private HandlerIO handlerIO;

    /**
     * Constructor
     */
    public StoreObjectGroupActionPlugin() {}


    @Override
    public ItemStatus execute(WorkerParameters params, HandlerIO actionDefinition) {
        checkMandatoryParameters(params);
        handlerIO = actionDefinition;
        final ItemStatus itemStatus = new ItemStatus(STORING_OBJECT_TASK_ID);
        try (MetaDataClient metaDataClient = MetaDataClientFactory.getInstance().getClient()) {
            checkMandatoryIOParameter(actionDefinition);

            // get list of object group's objects
            final MapOfObjects mapOfObjects = getMapOfObjectsIdsAndUris(params);
            // get list of object uris
            LOGGER.debug("Pre OG: {}", JsonHandler.prettyPrint(mapOfObjects.jsonOG));
            for (final Map.Entry<String, String> objectGuid : mapOfObjects.binaryObjectsToStore.entrySet()) {
                // Execute action on the object

                // store binary data object
                final ObjectDescription description = new ObjectDescription();
                // set container name and object URI
                description.setWorkspaceContainerGUID(params.getContainerName())
                    .setWorkspaceObjectURI(SIP + objectGuid.getValue());
                // set type and object name
                description.setType(StorageCollectionType.OBJECTS).setObjectName(objectGuid.getKey());

                StoredInfoResult result = storeObject(description, itemStatus);

                if (result != null) {
                    try {
                        storeStorageInfo((ObjectNode) mapOfObjects.objectJsonMap.get(objectGuid.getKey()), result, false);
                    } catch (InvalidCreateOperationException e) {
                        LOGGER.error(e);
                    }
                }
                LOGGER.debug("Final OBJ: {}", mapOfObjects.objectJsonMap.get(objectGuid.getKey()));

                // subtask
                itemStatus.setSubTaskStatus(objectGuid.getKey(), itemStatus);

            }
            // store OG to workspace
            ((ObjectNode) mapOfObjects.jsonOG).remove(SedaConstants.PREFIX_WORK);
            LOGGER.debug("Pre Final OG: {}", JsonHandler.prettyPrint(mapOfObjects.jsonOG));
            try {
                handlerIO.transferJsonToWorkspace(StorageCollectionType.OBJECTGROUPS.getCollectionName(),
                    params.getObjectName(),
                    mapOfObjects.jsonOG, false);
            } catch (ProcessingException e) {
                LOGGER.error(params.getObjectName(), e);
                throw e;
            }

        } catch (final ProcessingException e) {
            LOGGER.error(e);
            itemStatus.increment(StatusCode.FATAL);
        }

        if (StatusCode.UNKNOWN.equals(itemStatus.getGlobalStatus())) {
            itemStatus.increment(StatusCode.OK);
        }
        return new ItemStatus(STORING_OBJECT_TASK_ID).setItemsStatus(STORING_OBJECT_TASK_ID, itemStatus);
    }

    private static class MapOfObjects {
        private JsonNode jsonOG;
        private Map<String, JsonNode> objectJsonMap;
        private Map<String, String> binaryObjectsToStore;
    }

    /**
     * Get the list of objects linked to the current object group
     *
     * @param params worker parameters
     * @return the list of object guid and corresponding Json
     * @throws ProcessingException throws when error occurs while retrieving the object group file from workspace
     */
    private MapOfObjects getMapOfObjectsIdsAndUris(WorkerParameters params) throws ProcessingException {
        final MapOfObjects mapOfObjects = new MapOfObjects();
        mapOfObjects.binaryObjectsToStore = new HashMap<>();
        mapOfObjects.objectJsonMap = new HashMap<>();
        final String containerId = params.getContainerName();
        final String objectName = params.getObjectName();
        ParametersChecker.checkParameter("Container id is a mandatory parameter", containerId);
        ParametersChecker.checkParameter("ObjectName id is a mandatory parameter", objectName);
        // Get objectGroup objects ids
        mapOfObjects.jsonOG = handlerIO.getJsonFromWorkspace(
            IngestWorkflowConstants.OBJECT_GROUP_FOLDER + "/" + objectName);
        // Filter on objectGroup objects ids to retrieve only binary objects
        // informations linked to the ObjectGroup
        final JsonNode original = mapOfObjects.jsonOG.get(SedaConstants.PREFIX_QUALIFIERS);
        final JsonNode work = mapOfObjects.jsonOG.get(SedaConstants.PREFIX_WORK);
        final JsonNode qualifiers = work.get(SedaConstants.PREFIX_QUALIFIERS);
        if (qualifiers == null) {
            return mapOfObjects;
        }

        final List<JsonNode> originalVersions = original.findValues(SedaConstants.TAG_VERSIONS);
        final List<JsonNode> versions = qualifiers.findValues(SedaConstants.TAG_VERSIONS);
        if (versions == null || versions.isEmpty()) {
            return mapOfObjects;
        }
        for (final JsonNode version : versions) {
            for (final JsonNode binaryObject : version) {
                if (binaryObject.get(SedaConstants.TAG_PHYSICAL_ID) == null) {
                    String id = binaryObject.get(SedaConstants.PREFIX_ID).asText();
                    mapOfObjects.binaryObjectsToStore.put(id,
                        binaryObject.get(SedaConstants.TAG_URI).asText());
                    for (final JsonNode version2 : originalVersions) {
                        for (final JsonNode binaryObject2 : version) {
                            if (binaryObject.get(SedaConstants.TAG_PHYSICAL_ID) == null
                                && binaryObject.get(SedaConstants.PREFIX_ID).asText().equals(id)) {
                                mapOfObjects.objectJsonMap.put(id, binaryObject2);
                            }
                        }
                    }
                }
            }
        }
        return mapOfObjects;
    }

    @Override
    public void checkMandatoryIOParameter(HandlerIO handler) throws ProcessingException {
        // TODO P0 Add objectGroup.json add input and check it
    }

}
