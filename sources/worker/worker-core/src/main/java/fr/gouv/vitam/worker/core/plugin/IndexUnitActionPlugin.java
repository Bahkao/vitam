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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.database.builder.query.VitamFieldsHelper;
import fr.gouv.vitam.common.database.builder.request.multiple.InsertMultiQuery;
import fr.gouv.vitam.common.database.builder.request.multiple.RequestMultiple;
import fr.gouv.vitam.common.database.builder.request.multiple.UpdateMultiQuery;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.model.UnitType;
import fr.gouv.vitam.common.parameter.ParameterHelper;
import fr.gouv.vitam.metadata.api.exception.MetaDataException;
import fr.gouv.vitam.metadata.api.exception.MetaDataNotFoundException;
import fr.gouv.vitam.metadata.client.MetaDataClient;
import fr.gouv.vitam.metadata.client.MetaDataClientFactory;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.worker.common.HandlerIO;
import fr.gouv.vitam.worker.common.utils.IngestWorkflowConstants;
import fr.gouv.vitam.worker.common.utils.SedaConstants;
import fr.gouv.vitam.worker.core.handler.ActionHandler;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageNotFoundException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageServerException;

import javax.ws.rs.core.Response;
import java.io.File;
import java.io.InputStream;
import java.util.Iterator;

/**
 * IndexUnitAction Plugin
 */
public class IndexUnitActionPlugin extends ActionHandler {
    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(IndexUnitActionPlugin.class);
    private static final String HANDLER_PROCESS = "INDEXATION";

    private static final String ARCHIVE_UNIT = "ArchiveUnit";
    private static final String TAG_WORK = "_work";
    private static final String TAG_CONTENT = "Content";
    private static final String TAG_MANAGEMENT = "Management";
    private static final int SEDA_PARAMETERS_RANK = 1;


    private HandlerIO handlerIO;

    /**
     * Constructor with parameter SedaUtilsFactory
     */
    public IndexUnitActionPlugin() {
        // Empty
    }

    /**
     * @return HANDLER_ID
     */
    public static final String getId() {
        return HANDLER_PROCESS;
    }

    @Override
    public ItemStatus execute(WorkerParameters params, HandlerIO param) {
        checkMandatoryParameters(params);
        handlerIO = param;
        final ItemStatus itemStatus = new ItemStatus(HANDLER_PROCESS);

        try {
            indexArchiveUnit(params, itemStatus);
        } catch (final IllegalArgumentException e) {
            LOGGER.error(e);
            itemStatus.increment(StatusCode.KO);
        } catch (final ProcessingException e) {
            LOGGER.error(e);
            itemStatus.increment(StatusCode.FATAL);
        }

        return new ItemStatus(HANDLER_PROCESS).setItemsStatus(HANDLER_PROCESS, itemStatus);

    }

    /**
     * Index archive unit
     *
     * @param params work parameters
     * @param itemStatus item status
     * @throws ProcessingException when error in execution
     */
    private void indexArchiveUnit(WorkerParameters params, ItemStatus itemStatus) throws ProcessingException {
        ParameterHelper.checkNullOrEmptyParameters(params);

        final String containerId = params.getContainerName();
        final String objectName = params.getObjectName();
        RequestMultiple query = null;
        InputStream input;
        Response response = null;
        try (MetaDataClient metadataClient = MetaDataClientFactory.getInstance().getClient()) {
            response = handlerIO
                    .getInputStreamNoCachedFromWorkspace(IngestWorkflowConstants.ARCHIVE_UNIT_FOLDER + "/" + objectName);

            if (response != null) {
                input = (InputStream) response.getEntity();
                JsonNode archiveUnit = prepareArchiveUnitJson(input, containerId, objectName);
                final ObjectNode data = (ObjectNode) archiveUnit.get(ARCHIVE_UNIT);
                final JsonNode work = archiveUnit.get(TAG_WORK);
                Boolean existing = false;
                if (work != null && work.get("_existing") != null) {
                    existing = work.get("_existing").asBoolean();
                }

                if (existing) {
                    query = new UpdateMultiQuery();
                } else {
                    query = new InsertMultiQuery();
                }
                // Add _up to archive unit json object
                if (work != null && work.get("_up") != null) {
                    final ArrayNode parents = (ArrayNode) work.get("_up");
                    query.addRoots(parents);
                }
                if (!Boolean.TRUE.equals(existing)) {
                    // insert case
                    if (handlerIO.getInput() != null && !handlerIO.getInput().isEmpty()) {
                        String unitType = UnitType.getUnitTypeString((String) handlerIO.getInput(0));
                        data.put(VitamFieldsHelper.unitType(), unitType);
                    }
                    ObjectNode finalInsert = ((InsertMultiQuery) query).addData(data).getFinalInsert();
                    metadataClient.insertUnit(finalInsert);
                }
                itemStatus.increment(StatusCode.OK);
            } else {
                LOGGER.error("Archive unit not found");
                throw new ProcessingException("Archive unit not found");
            }

        } catch (final MetaDataNotFoundException e) {
            LOGGER.error("Unit references a non existing unit " + query.toString());
            throw new IllegalArgumentException(e);
        } catch (final MetaDataException | InvalidParseOperationException e) {
            LOGGER.error("Internal Server Error", e);
            throw new ProcessingException(e);
        } catch (ContentAddressableStorageNotFoundException | ContentAddressableStorageServerException e) {
            LOGGER.error("Workspace Server Error");
            throw new ProcessingException(e);
        } catch (IllegalArgumentException e) {
            LOGGER.error("Illegal Argument Exception for " + (query != null ? query.toString() : ""));
            throw e;
        } /* catch (InvalidCreateOperationException e) {
            e.printStackTrace();
        } */finally {
            handlerIO.consumeAnyEntityAndClose(response);
        }
    }

    /**
     * Convert xml archive unit to json node for insert/update.
     *
     * @param input xml archive unit
     * @param containerId container id
     * @param objectName unit file name
     * @return map of data
     * @throws InvalidParseOperationException exception while reading temporary json file
     * @throws ProcessingException exception while reading xml file
     */
    // FIXME do we need to create a new file or not ?
    private JsonNode prepareArchiveUnitJson(InputStream input, String containerId, String objectName)
            throws InvalidParseOperationException, ProcessingException {
        ParametersChecker.checkParameter("Input stream is a mandatory parameter", input);
        ParametersChecker.checkParameter("Container id is a mandatory parameter", containerId);
        ParametersChecker.checkParameter("ObjectName id is a mandatory parameter", objectName);

        JsonNode archiveUnit = JsonHandler.getFromInputStream(input);
        ObjectNode archiveUnitNode = (ObjectNode) archiveUnit.get(ARCHIVE_UNIT);

        // replace _id by #id
        archiveUnitNode.set("#id", archiveUnitNode.get("_id"));
        archiveUnitNode.remove("_id");

        // replace Management by _mgt
        ObjectNode managementNode = (ObjectNode) archiveUnitNode.get(TAG_MANAGEMENT);
        final JsonNode sedaParameters = JsonHandler.getFromFile((File) handlerIO.getInput(SEDA_PARAMETERS_RANK));
        if (sedaParameters.get(SedaConstants.TAG_ARCHIVE_TRANSFER)
                .get(SedaConstants.TAG_DATA_OBJECT_PACKAGE).get(SedaConstants.TAG_ORIGINATINGAGENCYIDENTIFIER) != null) {

            String prodService = sedaParameters.get(SedaConstants.TAG_ARCHIVE_TRANSFER)
                    .get(SedaConstants.TAG_DATA_OBJECT_PACKAGE).get(SedaConstants.TAG_ORIGINATINGAGENCYIDENTIFIER).asText();

            ArrayNode originatingAgencies = JsonHandler.createArrayNode();
            originatingAgencies.add(prodService);

            archiveUnitNode.set(VitamFieldsHelper.originatingAgencies(), originatingAgencies);
            archiveUnitNode.put(VitamFieldsHelper.originatingAgency(), prodService);
        }
        archiveUnitNode.set(SedaConstants.PREFIX_MGT, (JsonNode) managementNode);
        archiveUnitNode.remove(TAG_MANAGEMENT);

        // remove DataObjectReference
        // FIXME is it normal to have this TAG "DataObjectReference" after ExtractSeda since "_og" contains the guids
        archiveUnitNode.remove("DataObjectReference");


        // add #operations
        archiveUnitNode.putArray(VitamFieldsHelper.operations()).add(containerId);

        return archiveUnit;

    }

    @Override
    public void checkMandatoryIOParameter(HandlerIO handler) throws ProcessingException {
        // Handler without parameters input
    }
}
