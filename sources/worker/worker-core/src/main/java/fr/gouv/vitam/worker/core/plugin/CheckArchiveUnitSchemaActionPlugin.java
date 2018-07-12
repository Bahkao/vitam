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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;

import fr.gouv.vitam.common.SedaConstants;
import fr.gouv.vitam.common.database.builder.request.single.Select;
import fr.gouv.vitam.common.database.server.mongodb.VitamDocument;
import fr.gouv.vitam.common.exception.ArchiveUnitOntologyValidationException;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.json.SchemaValidationStatus;
import fr.gouv.vitam.common.json.SchemaValidationStatus.SchemaValidationStatusEnum;
import fr.gouv.vitam.common.json.SchemaValidationUtils;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.IngestWorkflowConstants;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.model.administration.OntologyModel;
import fr.gouv.vitam.common.model.administration.OntologyType;
import fr.gouv.vitam.common.security.SanityChecker;
import fr.gouv.vitam.functional.administration.client.AdminManagementClientFactory;
import fr.gouv.vitam.processing.common.exception.ArchiveUnitContainSpecialCharactersException;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.worker.common.HandlerIO;
import fr.gouv.vitam.worker.core.handler.ActionHandler;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageNotFoundException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageServerException;

/**
 * CheckArchiveUnitSchema Plugin.<br>
 *
 */

public class CheckArchiveUnitSchemaActionPlugin extends ActionHandler {
    private static final String WORKSPACE_SERVER_ERROR = "Workspace Server Error";

    private static final String SCHEMA_ERROR = "Json validation couldn't be done";

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(CheckArchiveUnitSchemaActionPlugin.class);

    private static final String CHECK_UNIT_SCHEMA_TASK_ID = "CHECK_UNIT_SCHEMA";

    private static final int UNIT_OUT_RANK = 0;

    private static final int ONTOLOGY_IN_RANK = 0;
    private HandlerIO handlerIO;

    private static final String NOT_AU_JSON_VALID = "NOT_AU_JSON_VALID";
    private static final String NOT_JSON_FILE = "NOT_JSON_FILE";
    private static final String UNIT_SANITIZE = "UNIT_SANITIZE";

    private static final String ONTOLOGY_VALIDATION = "ONTOLOGY_VALIDATION";

    private static final String UNKNOWN_TECHNICAL_EXCEPTION = "Unknown technical exception";

    private boolean isUpdateJsonMandatory = false;
    private boolean asyncIO = false;

    private final AdminManagementClientFactory adminManagementClientFactory;

    /**
     * Empty constructor UnitsRulesComputePlugin
     *
     */
    public CheckArchiveUnitSchemaActionPlugin() {
        this(AdminManagementClientFactory.getInstance());
    }

    /**
     * Empty constructor CheckArchiveUnitSchemaActionPlugin
     * 
     * @param adminManagementClientFactory
     *
     */
    @VisibleForTesting
    public CheckArchiveUnitSchemaActionPlugin(AdminManagementClientFactory adminManagementClientFactory) {
        this.adminManagementClientFactory = adminManagementClientFactory;
    }

    @Override
    public ItemStatus execute(WorkerParameters params, HandlerIO handler) {
        handlerIO = handler;
        final ItemStatus itemStatus = new ItemStatus(CHECK_UNIT_SCHEMA_TASK_ID);
        SchemaValidationStatus schemaValidationStatus;
        try {
            schemaValidationStatus = checkAUJsonAgainstSchema(params, itemStatus);
            SchemaValidationStatusEnum status = schemaValidationStatus.getValidationStatus();
            switch (status) {
                case VALID:
                    itemStatus.increment(StatusCode.OK);
                    return new ItemStatus(CHECK_UNIT_SCHEMA_TASK_ID).setItemsStatus(CHECK_UNIT_SCHEMA_TASK_ID,
                        itemStatus);
                case NOT_AU_JSON_VALID:
                    itemStatus.setGlobalOutcomeDetailSubcode(CheckUnitSchemaStatus.INVALID_UNIT.name());
                    itemStatus.increment(StatusCode.KO);
                    itemStatus.setEvDetailData(schemaValidationStatus.getValidationMessage());
                    return new ItemStatus(itemStatus.getItemId()).setItemsStatus(itemStatus.getItemId(),
                        itemStatus);
                case EMPTY_REQUIRED_FIELD:
                case RULE_DATE_FORMAT:
                    itemStatus.setGlobalOutcomeDetailSubcode(status.name());
                    itemStatus.increment(StatusCode.KO);
                    itemStatus.setEvDetailData(schemaValidationStatus.getValidationMessage());
                    return new ItemStatus(itemStatus.getItemId()).setItemsStatus(itemStatus.getItemId(),
                        itemStatus);
                case NOT_JSON_FILE:
                    itemStatus.setGlobalOutcomeDetailSubcode(CheckUnitSchemaStatus.INVALID_UNIT.name());
                    itemStatus.increment(StatusCode.KO);
                    final ObjectNode object = JsonHandler.createObjectNode();
                    object.put(CHECK_UNIT_SCHEMA_TASK_ID, schemaValidationStatus.getValidationMessage());
                    itemStatus.setEvDetailData(JsonHandler.unprettyPrint(object));
                    return new ItemStatus(itemStatus.getItemId()).setItemsStatus(itemStatus.getItemId(),
                        itemStatus);
                case RULE_BAD_START_END_DATE:
                    itemStatus.setGlobalOutcomeDetailSubcode(CheckUnitSchemaStatus.CONSISTENCY.name());
                    itemStatus.increment(StatusCode.KO);

                    ItemStatus is = new ItemStatus(CheckUnitSchemaStatus.CONSISTENCY.name());
                    is.setEvDetailData(schemaValidationStatus.getValidationMessage());
                    is.increment(StatusCode.KO);

                    itemStatus.setSubTaskStatus(schemaValidationStatus.getObjectId(), is);
                    itemStatus.setItemId(CheckUnitSchemaStatus.CONSISTENCY.name());

                    return new ItemStatus(itemStatus.getItemId()).setItemsStatus(itemStatus.getItemId(),
                        itemStatus);
            }
        } catch (final ArchiveUnitContainSpecialCharactersException e) {
            itemStatus.setItemId(UNIT_SANITIZE);
            itemStatus.increment(StatusCode.KO);
            final ObjectNode object = JsonHandler.createObjectNode();
            object.put(SedaConstants.EV_DET_TECH_DATA, e.getMessage());
            itemStatus.setEvDetailData(JsonHandler.unprettyPrint(object));
            return new ItemStatus(CHECK_UNIT_SCHEMA_TASK_ID).setItemsStatus(CHECK_UNIT_SCHEMA_TASK_ID,
                itemStatus);
        } catch (final ArchiveUnitOntologyValidationException e) {
            itemStatus.setItemId(ONTOLOGY_VALIDATION);
            itemStatus.increment(StatusCode.KO);
            final ObjectNode object = JsonHandler.createObjectNode();
            object.put(SedaConstants.EV_DET_TECH_DATA, e.getMessage());
            itemStatus.setEvDetailData(JsonHandler.unprettyPrint(object));
            return new ItemStatus(CHECK_UNIT_SCHEMA_TASK_ID).setItemsStatus(CHECK_UNIT_SCHEMA_TASK_ID,
                itemStatus);
        } catch (final ProcessingException e) {
            LOGGER.error(e);
            itemStatus.increment(StatusCode.FATAL);
        }
        return new ItemStatus(CHECK_UNIT_SCHEMA_TASK_ID).setItemsStatus(itemStatus.getItemId(), itemStatus);
    }

    @Override
    public void checkMandatoryIOParameter(HandlerIO handler) throws ProcessingException {
        // Nothing to check
    }


    private SchemaValidationStatus checkAUJsonAgainstSchema(WorkerParameters params, ItemStatus itemStatus)
        throws ProcessingException, ArchiveUnitOntologyValidationException {
        final String objectName = params.getObjectName();

        try (InputStream archiveUnitToJson =
            handlerIO.getInputStreamFromWorkspace(IngestWorkflowConstants.ARCHIVE_UNIT_FOLDER +
                File.separator + objectName)) {
            SchemaValidationUtils validator = new SchemaValidationUtils();
            JsonNode archiveUnit = JsonHandler.getFromInputStream(archiveUnitToJson);

            // sanityChecker
            try {
                SanityChecker.checkJsonAll(archiveUnit);
            } catch (InvalidParseOperationException e) {
                itemStatus.setGlobalOutcomeDetailSubcode(CheckUnitSchemaStatus.INVALID_UNIT.toString());
                final String err = "Sanity Checker failed for Archive Unit: " + e.getMessage();
                LOGGER.error(err);
                throw new ArchiveUnitContainSpecialCharactersException(err);
            }
            handleExternalOntologies(archiveUnit, itemStatus, validator);

            handlerIO.addOuputResult(UNIT_OUT_RANK, archiveUnit, true, false);
            if (isUpdateJsonMandatory) {
                handlerIO.transferJsonToWorkspace(IngestWorkflowConstants.ARCHIVE_UNIT_FOLDER, objectName, archiveUnit,
                    false, asyncIO);
            }

            return validator.validateUnit(archiveUnit.get(SedaConstants.TAG_ARCHIVE_UNIT));
        } catch (final InvalidParseOperationException e) {
            LOGGER.error("File couldnt be converted into json", e);
            return new SchemaValidationStatus("File is not a valid json file",
                SchemaValidationStatusEnum.NOT_JSON_FILE);
        } catch (ContentAddressableStorageNotFoundException | ContentAddressableStorageServerException |
            IOException e) {
            LOGGER.error(WORKSPACE_SERVER_ERROR);
            throw new ProcessingException(e);
        } catch (com.github.fge.jsonschema.core.exceptions.ProcessingException e) {
            LOGGER.error(SCHEMA_ERROR);
            throw new ProcessingException(e);
        }
    }


    private void handleExternalOntologies(JsonNode archiveUnit, ItemStatus itemStatus, SchemaValidationUtils validator)
        throws ProcessingException, ArchiveUnitOntologyValidationException {
        Select selectOntologies = new Select();
        Map<String, OntologyModel> ontologyModelMap = new HashMap<String, OntologyModel>();
        JsonNode originalArchiveUnit = archiveUnit.deepCopy();
        try {
            final File ontologyFile = (File) handlerIO.getInput(ONTOLOGY_IN_RANK);
            InputStream ontologyFIS = null;
            if (ontologyFile != null) {
                ontologyFIS = new FileInputStream(ontologyFile);
            } else {
                return;
            }
            JsonNode ontologyJson = JsonHandler.getFromInputStream(ontologyFIS);
            if (ontologyJson.isArray()) {
                List<ObjectNode> ontologyModelList = JsonHandler.toArrayList((ArrayNode) ontologyJson);
                for (ObjectNode ontology : ontologyModelList) {
                    OntologyModel ontMode = new OntologyModel();
                    ontMode.setType(OntologyType.valueOf(ontology.get(OntologyModel.TAG_TYPE).asText()));
                    ontMode.setIdentifier(ontology.get(OntologyModel.TAG_IDENTIFIER).asText());
                    ontologyModelMap.put(ontMode.getIdentifier(), ontMode);
                }
                if (ontologyModelMap.size() > 0) {
                    List<String> errors = new ArrayList<String>();
                    // that means a transformation could be done so we need to process the full json
                    validator.loopAndReplaceInJson(archiveUnit, ontologyModelMap, errors);
                    if (!errors.isEmpty()) {
                        // archive unit could not be transformed, so the error would be thrown later by the schema
                        // validation verification
                        String error = "Archive unit contains fields declared in ontology with a wrong format : " +
                            String.join(",", errors.toString());
                        LOGGER.error(error);
                        throw new ArchiveUnitOntologyValidationException(error);
                    }
                }
            }
        } catch (InvalidParseOperationException e) {
            LOGGER.error(UNKNOWN_TECHNICAL_EXCEPTION, e);
            itemStatus.increment(StatusCode.FATAL);
            throw new ProcessingException(e);
        } catch (FileNotFoundException e) {
            LOGGER.error("Ontology File cannot be found ", e);
            return;
        }

        final String unitBeforeUpdate = JsonHandler.prettyPrint(originalArchiveUnit);
        final String unitAfterUpdate = JsonHandler.prettyPrint(archiveUnit);
        List<String> diff = VitamDocument.getUnifiedDiff(unitAfterUpdate, unitBeforeUpdate);
        if (diff.size() > 0) {
            isUpdateJsonMandatory = true;
        }

    }


    /**
     * Check unit schema status values
     */
    public enum CheckUnitSchemaStatus {
        /**
         * Improper unit
         */
        INVALID_UNIT,
        /**
         * Required field empty
         */
        EMPTY_REQUIRED_FIELD,
        /**
         * Rule's date in bad format
         */
        RULE_DATE_FORMAT,
        /**
         * StartDate is after EndDate
         */
        CONSISTENCY;
    }
}
