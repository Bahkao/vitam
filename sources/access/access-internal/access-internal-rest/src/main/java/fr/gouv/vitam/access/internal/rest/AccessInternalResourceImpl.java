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
package fr.gouv.vitam.access.internal.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import fr.gouv.culture.archivesdefrance.seda.v2.IdentifierType;
import fr.gouv.culture.archivesdefrance.seda.v2.LevelType;
import fr.gouv.vitam.access.internal.api.AccessInternalModule;
import fr.gouv.vitam.access.internal.api.AccessInternalResource;
import fr.gouv.vitam.access.internal.common.exception.AccessInternalExecutionException;
import fr.gouv.vitam.access.internal.common.exception.AccessInternalRuleExecutionException;
import fr.gouv.vitam.access.internal.common.model.AccessInternalConfiguration;
import fr.gouv.vitam.access.internal.core.AccessInternalModuleImpl;
import fr.gouv.vitam.access.internal.core.ObjectGroupDipServiceImpl;
import fr.gouv.vitam.access.internal.core.OntologyUtils;
import fr.gouv.vitam.common.GlobalDataRest;
import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.database.builder.request.exception.InvalidCreateOperationException;
import fr.gouv.vitam.common.database.model.DataType;
import fr.gouv.vitam.common.database.parser.query.helper.CheckSpecifiedFieldHelper;
import fr.gouv.vitam.common.database.parser.request.multiple.RequestParserHelper;
import fr.gouv.vitam.common.database.parser.request.multiple.RequestParserMultiple;
import fr.gouv.vitam.common.database.parser.request.multiple.SelectParserMultiple;
import fr.gouv.vitam.common.database.parser.request.multiple.UpdateParserMultiple;
import fr.gouv.vitam.common.database.utils.AccessContractRestrictionHelper;
import fr.gouv.vitam.common.error.VitamCode;
import fr.gouv.vitam.common.error.VitamCodeHelper;
import fr.gouv.vitam.common.error.VitamError;
import fr.gouv.vitam.common.exception.BadRequestException;
import fr.gouv.vitam.common.exception.InternalServerException;
import fr.gouv.vitam.common.exception.InvalidGuidOperationException;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.UpdatePermissionException;
import fr.gouv.vitam.common.exception.VitamClientException;
import fr.gouv.vitam.common.exception.VitamException;
import fr.gouv.vitam.common.guid.GUIDReader;
import fr.gouv.vitam.common.i18n.VitamLogbookMessages;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.mapping.dip.ArchiveUnitMapper;
import fr.gouv.vitam.common.mapping.dip.DipService;
import fr.gouv.vitam.common.mapping.dip.ObjectGroupMapper;
import fr.gouv.vitam.common.mapping.dip.UnitDipServiceImpl;
import fr.gouv.vitam.common.mapping.serializer.IdentifierTypeDeserializer;
import fr.gouv.vitam.common.mapping.serializer.LevelTypeDeserializer;
import fr.gouv.vitam.common.mapping.serializer.TextByLangDeserializer;
import fr.gouv.vitam.common.model.PreservationRequest;
import fr.gouv.vitam.common.model.ProcessAction;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.RequestResponseError;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.VitamSession;
import fr.gouv.vitam.common.model.administration.AccessContractModel;
import fr.gouv.vitam.common.model.administration.ActivationStatus;
import fr.gouv.vitam.common.model.dip.DipExportRequest;
import fr.gouv.vitam.common.model.elimination.EliminationRequestBody;
import fr.gouv.vitam.common.model.massupdate.MassUpdateUnitRuleRequest;
import fr.gouv.vitam.common.model.massupdate.RuleActions;
import fr.gouv.vitam.common.model.unit.TextByLang;
import fr.gouv.vitam.common.parameter.ParameterHelper;
import fr.gouv.vitam.common.security.SanityChecker;
import fr.gouv.vitam.common.server.application.HttpHeaderHelper;
import fr.gouv.vitam.common.server.application.VitamHttpHeader;
import fr.gouv.vitam.common.server.application.resources.ApplicationStatusResource;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.functional.administration.common.exception.AdminManagementClientServerException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientAlreadyExistsException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientBadRequestException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientServerException;
import fr.gouv.vitam.logbook.common.parameters.Contexts;
import fr.gouv.vitam.logbook.common.parameters.LogbookOperationParameters;
import fr.gouv.vitam.logbook.common.parameters.LogbookParameterName;
import fr.gouv.vitam.logbook.common.parameters.LogbookParametersFactory;
import fr.gouv.vitam.logbook.common.parameters.LogbookTypeProcess;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClient;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClientFactory;
import fr.gouv.vitam.metadata.api.exception.MetaDataNotFoundException;
import fr.gouv.vitam.processing.common.ProcessingEntry;
import fr.gouv.vitam.processing.common.parameter.WorkerParameterName;
import fr.gouv.vitam.processing.management.client.ProcessingManagementClient;
import fr.gouv.vitam.processing.management.client.ProcessingManagementClientFactory;
import fr.gouv.vitam.storage.engine.common.exception.StorageNotFoundException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageAlreadyExistException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageServerException;
import fr.gouv.vitam.workspace.client.WorkspaceClient;
import fr.gouv.vitam.workspace.client.WorkspaceClientFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.text.ParseException;
import java.util.Set;

import static fr.gouv.vitam.common.database.utils.AccessContractRestrictionHelper.applyAccessContractRestrictionForUnitForSelect;
import static fr.gouv.vitam.common.json.JsonHandler.toJsonNode;
import static fr.gouv.vitam.common.json.JsonHandler.writeToInpustream;
import static fr.gouv.vitam.common.model.ProcessAction.RESUME;
import static fr.gouv.vitam.common.model.StatusCode.STARTED;
import static fr.gouv.vitam.common.thread.VitamThreadUtils.getVitamSession;
import static fr.gouv.vitam.logbook.common.parameters.Contexts.PRESERVATION;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

/**
 * AccessResourceImpl implements AccessResource
 */
@Path("/access-internal/v1")
public class AccessInternalResourceImpl extends ApplicationStatusResource implements AccessInternalResource {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(AccessInternalResourceImpl.class);
    /**
     * EXPORT DIP
     */
    private static final String EXPORT_DIP = "EXPORT_DIP";
    /**
     * UNITS
     */
    private static final String UNITS = "units";
    private static final String RESULTS = "$results";

    private static final String UNITS_URI = "/units";
    private static final String UNITS_RULES_URI = "/units/rules";

    /**
     * Access contract
     */
    private static final String ACCESS_CONTRACT = "AccessContract";
    private static final String REQUEST_IS_NOT_AN_UPDATE_OPERATION = "Request is not an update operation";

    // DIP
    private DipService unitDipService;
    private DipService objectDipService;

    private static final String END_OF_EXECUTION_OF_DSL_VITAM_FROM_ACCESS = "End of execution of DSL Vitam from Access";
    private static final String EXECUTION_OF_DSL_VITAM_FROM_ACCESS_ONGOING =
        "Execution of DSL Vitam from Access ongoing...";
    private static final String BAD_REQUEST_EXCEPTION = "Bad request Exception ";
    private static final String NOT_FOUND_EXCEPTION = "Not Found Exception ";
    private static final String ACCESS_MODULE = "ACCESS";
    private static final String CODE_VITAM = "code_vitam";
    private static final String ACCESS_RESOURCE_INITIALIZED = "AccessResource initialized";

    private final AccessInternalModule accessModule;
    private ArchiveUnitMapper archiveUnitMapper;
    private ObjectGroupMapper objectGroupMapper;

    private ObjectMapper objectMapper;
    private ProcessingManagementClientFactory processingManagementClientFactory;
    private final LogbookOperationsClientFactory logbookOperationsClientFactory;
    private final WorkspaceClientFactory workspaceClientFactory;

    /**
     * @param configuration to associate with AccessResourceImpl
     */
    public AccessInternalResourceImpl(AccessInternalConfiguration configuration) {
        this(new AccessInternalModuleImpl(), LogbookOperationsClientFactory.getInstance(),
            WorkspaceClientFactory.getInstance(), ProcessingManagementClientFactory.getInstance());
        WorkspaceClientFactory.changeMode(configuration.getUrlWorkspace());
        ProcessingManagementClientFactory.changeConfigurationUrl(configuration.getUrlProcessing());
    }

    /**
     * Mock constructor
     *
     * @param accessModule accessModule
     */
    AccessInternalResourceImpl(AccessInternalModule accessModule) {
        this(accessModule, LogbookOperationsClientFactory.getInstance(), WorkspaceClientFactory.getInstance(),
            ProcessingManagementClientFactory.getInstance());
    }

    /**
     * Test constructor
     *
     * @param accessModule                      accessModule
     * @param logbookOperationsClientFactory    logbookOperationsClientFactory
     * @param workspaceClientFactory            workspaceClientFactory
     * @param processingManagementClientFactory processingManagementClientFactory
     */
    @VisibleForTesting AccessInternalResourceImpl(AccessInternalModule accessModule,
        LogbookOperationsClientFactory logbookOperationsClientFactory,
        WorkspaceClientFactory workspaceClientFactory,
        ProcessingManagementClientFactory processingManagementClientFactory) {
        this.accessModule = accessModule;
        archiveUnitMapper = new ArchiveUnitMapper();
        objectGroupMapper = new ObjectGroupMapper();
        LOGGER.debug(ACCESS_RESOURCE_INITIALIZED);
        this.objectMapper = buildObjectMapper();
        this.unitDipService = new UnitDipServiceImpl(archiveUnitMapper, objectMapper);
        this.objectDipService = new ObjectGroupDipServiceImpl(objectGroupMapper, objectMapper);
        this.processingManagementClientFactory = processingManagementClientFactory;
        this.logbookOperationsClientFactory = logbookOperationsClientFactory;
        this.workspaceClientFactory = workspaceClientFactory;
    }


    /**
     * get Archive Unit list by query based on identifier
     *
     * @param queryDsl as JsonNode
     * @return an archive unit result list
     */
    @Override
    @GET
    @Path("/units")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUnits(JsonNode queryDsl) {
        LOGGER.debug(EXECUTION_OF_DSL_VITAM_FROM_ACCESS_ONGOING);
        Status status;
        JsonNode result = null;
        LOGGER.debug("DEBUG: start selectUnits {}", queryDsl);
        try {
            SanityChecker.checkJsonAll(queryDsl);
            checkEmptyQuery(queryDsl);
            result =
                accessModule
                    .selectUnit(applyAccessContractRestrictionForUnitForSelect(queryDsl,
                        getVitamSession().getContract()));
            LOGGER.debug("DEBUG {}", result);
            resetQuery(result, queryDsl);
            LOGGER.debug(END_OF_EXECUTION_OF_DSL_VITAM_FROM_ACCESS);
        } catch (final InvalidParseOperationException | InvalidCreateOperationException e) {
            LOGGER.error(BAD_REQUEST_EXCEPTION, e);
            // Unprocessable Entity not implemented by Jersey
            status = Status.BAD_REQUEST;
            return Response.status(status).entity(getErrorEntity(status, e.getMessage())).build();
        } catch (BadRequestException e) {
            LOGGER.error("Empty query is impossible", e);
            return buildErrorResponse(VitamCode.GLOBAL_EMPTY_QUERY, null);
        } catch (final Exception ve) {
            LOGGER.error(ve);
            status = Status.INTERNAL_SERVER_ERROR;
            return Response.status(status)
                .entity(new VitamError(status.name()).setHttpCode(status.getStatusCode())
                    .setContext(UNITS)
                    .setState(CODE_VITAM)
                    .setMessage(ve.getMessage())
                    .setDescription(status.getReasonPhrase()))
                .build();
        }
        return Response.status(Status.OK).entity(result).build();
    }

    /**
     * Select units with inherited rules
     *
     * @param queryDsl as JsonNode
     * @return an archive unit result list with inherited rules
     */
    @Override
    @GET
    @Path("/unitsWithInheritedRules")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response selectUnitsWithInheritedRules(JsonNode queryDsl) {
        LOGGER.debug(EXECUTION_OF_DSL_VITAM_FROM_ACCESS_ONGOING);
        Status status;
        JsonNode result;
        LOGGER.debug("DEBUG: start selectUnitsWithInheritedRules {}", queryDsl);
        try {
            SanityChecker.checkJsonAll(queryDsl);
            checkEmptyQuery(queryDsl);
            result = accessModule.selectUnitsWithInheritedRules(
                applyAccessContractRestrictionForUnitForSelect(queryDsl,
                    getVitamSession().getContract()));
            LOGGER.debug("DEBUG {}", result);
            resetQuery(result, queryDsl);
            LOGGER.debug(END_OF_EXECUTION_OF_DSL_VITAM_FROM_ACCESS);
        } catch (final InvalidParseOperationException | InvalidCreateOperationException e) {
            LOGGER.error(BAD_REQUEST_EXCEPTION, e);
            // Unprocessable Entity not implemented by Jersey
            status = Status.BAD_REQUEST;
            return Response.status(status).entity(getErrorEntity(status, e.getMessage())).build();
        } catch (BadRequestException e) {
            LOGGER.error("Empty query is impossible", e);
            return buildErrorResponse(VitamCode.GLOBAL_EMPTY_QUERY, null);
        } catch (final Exception ve) {
            LOGGER.error(ve);
            status = Status.INTERNAL_SERVER_ERROR;
            return Response.status(status)
                .entity(new VitamError(status.name()).setHttpCode(status.getStatusCode())
                    .setContext(UNITS)
                    .setState(CODE_VITAM)
                    .setMessage(ve.getMessage())
                    .setDescription(status.getReasonPhrase()))
                .build();
        }
        return Response.status(Status.OK).entity(result).build();
    }

    /**
     * get Archive Unit list by query based on identifier
     *
     * @param dslRequest as DipExportRequest
     * @return an archive unit result list
     */
    @Override
    @POST
    @Path("/dipexport")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response exportDIP(JsonNode dslRequest) {

        Status status;
        LOGGER.debug("DEBUG: start selectUnits {}", dslRequest);

        try {
            checkEmptyQuery(dslRequest);
            String operationId = getVitamSession().getRequestId();

            try (ProcessingManagementClient processingClient = processingManagementClientFactory.getClient();
                LogbookOperationsClient logbookOperationsClient = logbookOperationsClientFactory.getClient();
                WorkspaceClient workspaceClient = workspaceClientFactory.getClient()) {

                final LogbookOperationParameters initParameters =
                    LogbookParametersFactory.newLogbookOperationParameters(
                        GUIDReader.getGUID(operationId),
                        EXPORT_DIP,
                        GUIDReader.getGUID(operationId),
                        LogbookTypeProcess.EXPORT_DIP,
                        STARTED,
                        VitamLogbookMessages.getLabelOp("EXPORT_DIP.STARTED") + " : " + GUIDReader.getGUID(operationId),
                        GUIDReader.getGUID(operationId));

                logbookOperationsClient.create(initParameters);

                workspaceClient.createContainer(operationId);
                workspaceClient.putObject(operationId, "query.json", writeToInpustream(
                    applyAccessContractRestrictionForUnitForSelect(dslRequest,
                        getVitamSession().getContract())));

                ProcessingEntry processingEntry = new ProcessingEntry(operationId, EXPORT_DIP);
                Boolean mustLog =
                    ActivationStatus.ACTIVE.equals(getVitamSession().getContract().getAccessLog());
                processingEntry.getExtraParams().put(
                    WorkerParameterName.mustLogAccessOnObject.name(), Boolean.toString(mustLog));
                processingClient.initVitamProcess(Contexts.EXPORT_DIP.name(), processingEntry);

                // When
                RequestResponse<JsonNode> jsonNodeRequestResponse =
                    processingClient.executeOperationProcess(operationId, EXPORT_DIP,
                        Contexts.EXPORT_DIP.name(), RESUME.getValue());
                return jsonNodeRequestResponse.toResponse();
            } catch (ContentAddressableStorageServerException | ContentAddressableStorageAlreadyExistException |
                InvalidGuidOperationException | LogbookClientServerException | LogbookClientBadRequestException |
                LogbookClientAlreadyExistsException |
                VitamClientException | InternalServerException | InvalidCreateOperationException e) {
                LOGGER.error("Error while generating DIP", e);
                return Response.status(INTERNAL_SERVER_ERROR)
                    .entity(getErrorEntity(INTERNAL_SERVER_ERROR, e.getMessage())).build();
            }

        } catch (final InvalidParseOperationException e) {
            LOGGER.error(BAD_REQUEST_EXCEPTION, e);
            // Unprocessable Entity not implemented by Jersey
            status = Status.BAD_REQUEST;
            return Response.status(status).entity(getErrorEntity(status, e.getMessage())).build();
        } catch (BadRequestException e) {
            LOGGER.error("Empty query is impossible", e);
            return buildErrorResponse(VitamCode.GLOBAL_EMPTY_QUERY, null);
        }
    }

    /**
     * get Archive Unit list by query based on identifier
     *
     * @param dipExportRequest as DipExportRequest
     * @return an archive unit result list
     */
    @Override
    @POST
    @Path("/dipexport/usagefilter")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response exportDIPByUsageFilter(DipExportRequest dipExportRequest) {

        Status status;
        LOGGER.debug("DEBUG: start selectUnits {}", dipExportRequest.getDslRequest());
        LOGGER.debug("DEBUG: usage list to export {}", dipExportRequest.getDataObjectVersionToExport());

        try {
            checkEmptyQuery(dipExportRequest.getDslRequest());
            String operationId = getVitamSession().getRequestId();

            try (ProcessingManagementClient processingClient = processingManagementClientFactory.getClient();
                LogbookOperationsClient logbookOperationsClient = logbookOperationsClientFactory.getClient();
                WorkspaceClient workspaceClient = workspaceClientFactory.getClient()) {

                final LogbookOperationParameters initParameters =
                    LogbookParametersFactory.newLogbookOperationParameters(
                        GUIDReader.getGUID(operationId),
                        EXPORT_DIP,
                        GUIDReader.getGUID(operationId),
                        LogbookTypeProcess.EXPORT_DIP,
                        STARTED,
                        VitamLogbookMessages.getLabelOp("EXPORT_DIP.STARTED") + " : " + GUIDReader.getGUID(operationId),
                        GUIDReader.getGUID(operationId));

                // Add access contract rights
                addRightsStatementIdentifier(initParameters);

                logbookOperationsClient.create(initParameters);

                workspaceClient.createContainer(operationId);
                workspaceClient.putObject(operationId, "query.json", writeToInpustream(
                    applyAccessContractRestrictionForUnitForSelect(dipExportRequest.getDslRequest(),
                        getVitamSession().getContract())));

                if (dipExportRequest.getDataObjectVersionToExport() != null
                    && !dipExportRequest.getDataObjectVersionToExport().getDataObjectVersions().isEmpty()) {
                    workspaceClient.putObject(operationId, "dataObjectVersionFilter.json",
                        writeToInpustream(dipExportRequest.getDataObjectVersionToExport()));
                }

                ProcessingEntry processingEntry = new ProcessingEntry(operationId, EXPORT_DIP);
                Boolean mustLog =
                    ActivationStatus.ACTIVE.equals(getVitamSession().getContract().getAccessLog());
                processingEntry.getExtraParams().put(
                    WorkerParameterName.mustLogAccessOnObject.name(), Boolean.toString(mustLog));
                processingClient.initVitamProcess(Contexts.EXPORT_DIP.name(), processingEntry);

                // When
                RequestResponse<JsonNode> jsonNodeRequestResponse =
                    processingClient.executeOperationProcess(operationId, EXPORT_DIP,
                        Contexts.EXPORT_DIP.name(), RESUME.getValue());
                return jsonNodeRequestResponse.toResponse();
            } catch (ContentAddressableStorageServerException | ContentAddressableStorageAlreadyExistException |
                InvalidGuidOperationException | LogbookClientServerException | LogbookClientBadRequestException |
                LogbookClientAlreadyExistsException |
                VitamClientException | InternalServerException | InvalidCreateOperationException e) {
                LOGGER.error("Error while generating DIP", e);
                return Response.status(INTERNAL_SERVER_ERROR)
                    .entity(getErrorEntity(INTERNAL_SERVER_ERROR, e.getMessage())).build();
            }

        } catch (final InvalidParseOperationException e) {
            LOGGER.error(BAD_REQUEST_EXCEPTION, e);
            // Unprocessable Entity not implemented by Jersey
            status = Status.BAD_REQUEST;
            return Response.status(status).entity(getErrorEntity(status, e.getMessage())).build();
        } catch (BadRequestException e) {
            LOGGER.error("Empty query is impossible", e);
            return buildErrorResponse(VitamCode.GLOBAL_EMPTY_QUERY, null);
        }
    }

    private void addRightsStatementIdentifier(LogbookOperationParameters initParameters) {
        ObjectNode rightsStatementIdentifier = JsonHandler.createObjectNode();
        rightsStatementIdentifier
            .put(ACCESS_CONTRACT, getVitamSession().getContract().getIdentifier());
        initParameters.putParameterValue(LogbookParameterName.rightsStatementIdentifier,
            rightsStatementIdentifier.toString());
    }

    @Override
    @GET
    @Path("/dipexport/{id}/dip")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public Response findDIPByID(@PathParam("id") String id) {
        try {
            return accessModule.findDIPByOperationId(id);
        } catch (AccessInternalExecutionException e) {
            LOGGER.error(BAD_REQUEST_EXCEPTION, e);
            Status status = Status.BAD_REQUEST;
            return Response.status(status).entity(getErrorEntity(status, e.getMessage())).build();
        }
    }


    /**
     * Starts a reclassification workflow.
     */
    @Override
    @POST
    @Path("/reclassification")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response startReclassificationWorkflow(JsonNode reclassificationRequestJson) {
        return startReclassificationWorkflow(reclassificationRequestJson, RESUME);
    }

    /**
     * @param reclassificationRequestJson
     * @param processAction
     * @return
     */
    public Response startReclassificationWorkflow(JsonNode reclassificationRequestJson, ProcessAction processAction) {
        Status status;

        try {

            ParametersChecker.checkParameter("Missing reclassification request", reclassificationRequestJson);

            // Start workflow
            String operationId = getVitamSession().getRequestId();

            try (ProcessingManagementClient processingClient = processingManagementClientFactory.getClient();
                LogbookOperationsClient logbookOperationsClient = logbookOperationsClientFactory.getClient();
                WorkspaceClient workspaceClient = workspaceClientFactory.getClient()) {

                final LogbookOperationParameters initParameters =
                    LogbookParametersFactory.newLogbookOperationParameters(
                        GUIDReader.getGUID(operationId),
                        Contexts.RECLASSIFICATION.getEventType(),
                        GUIDReader.getGUID(operationId),
                        LogbookTypeProcess.RECLASSIFICATION,
                        STARTED,
                        VitamLogbookMessages.getLabelOp("RECLASSIFICATION.STARTED") + " : " +
                            GUIDReader.getGUID(operationId),
                        GUIDReader.getGUID(operationId));

                // Add access contract rights
                addRightsStatementIdentifier(initParameters);

                logbookOperationsClient.create(initParameters);

                workspaceClient.createContainer(operationId);

                workspaceClient.putObject(operationId, "request.json",
                    writeToInpustream(reclassificationRequestJson));

                processingClient.initVitamProcess(Contexts.RECLASSIFICATION.name(), operationId,
                    Contexts.RECLASSIFICATION.getEventType());

                RequestResponse<JsonNode> jsonNodeRequestResponse =
                    processingClient.executeOperationProcess(operationId, Contexts.RECLASSIFICATION.getEventType(),
                        Contexts.RECLASSIFICATION.name(), processAction.getValue());
                return jsonNodeRequestResponse.toResponse();
            }

        } catch (ContentAddressableStorageServerException | ContentAddressableStorageAlreadyExistException |
            InvalidGuidOperationException | LogbookClientServerException | LogbookClientBadRequestException |
            LogbookClientAlreadyExistsException |
            VitamClientException | InternalServerException e) {
            LOGGER.error("Error while starting unit reclassification workflow", e);
            return Response.status(INTERNAL_SERVER_ERROR)
                .entity(getErrorEntity(INTERNAL_SERVER_ERROR, e.getMessage())).build();
        } catch (final InvalidParseOperationException e) {
            LOGGER.error(BAD_REQUEST_EXCEPTION, e);
            // Unprocessable Entity not implemented by Jersey
            status = Status.BAD_REQUEST;
            return Response.status(status).entity(getErrorEntity(status, e.getMessage())).build();
        } catch (BadRequestException e) {
            LOGGER.error("Empty query is impossible", e);
            return buildErrorResponse(VitamCode.GLOBAL_EMPTY_QUERY, null);
        }
    }

    /**
     * Starts a elimination analysis workflow.
     */
    @Override
    @POST
    @Path("/elimination/analysis")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response startEliminationAnalysisWorkflow(EliminationRequestBody eliminationRequestBody) {

        return startEliminationWorkflow(eliminationRequestBody, Contexts.ELIMINATION_ANALYSIS);
    }

    /**
     * Starts a elimination action workflow.
     */
    @Override
    @POST
    @Path("/elimination/action")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response startEliminationActionWorkflow(EliminationRequestBody eliminationRequestBody) {

        return startEliminationWorkflow(eliminationRequestBody, Contexts.ELIMINATION_ACTION);
    }

    private Response startEliminationWorkflow(EliminationRequestBody eliminationRequestBody,
        Contexts eliminationWorkflowContext) {

        Status status;

        try {

            ParametersChecker.checkParameter("Missing elimination request", eliminationRequestBody);

            EliminationRequestBody eliminationRequestBodyWithAccessContractRestriction =
                new EliminationRequestBody(
                    eliminationRequestBody.getDate(),
                    applyAccessContractRestrictionForUnitForSelect(
                        eliminationRequestBody.getDslRequest(),
                        getVitamSession().getContract()));

            // Start workflow
            String operationId = getVitamSession().getRequestId();

            try (ProcessingManagementClient processingClient = processingManagementClientFactory.getClient();
                LogbookOperationsClient logbookOperationsClient = logbookOperationsClientFactory.getClient();
                WorkspaceClient workspaceClient = workspaceClientFactory.getClient()) {

                final LogbookOperationParameters initParameters =
                    LogbookParametersFactory.newLogbookOperationParameters(
                        GUIDReader.getGUID(operationId),
                        eliminationWorkflowContext.getEventType(),
                        GUIDReader.getGUID(operationId),
                        LogbookTypeProcess.ELIMINATION,
                        STARTED,
                        VitamLogbookMessages.getLabelOp(eliminationWorkflowContext.getEventType() + ".STARTED") +
                            " : " +
                            GUIDReader.getGUID(operationId),
                        GUIDReader.getGUID(operationId));

                // Add access contract rights
                addRightsStatementIdentifier(initParameters);

                logbookOperationsClient.create(initParameters);

                workspaceClient.createContainer(operationId);

                workspaceClient.putObject(operationId, "request.json",
                    writeToInpustream(eliminationRequestBodyWithAccessContractRestriction));

                processingClient.initVitamProcess(eliminationWorkflowContext.name(),
                    new ProcessingEntry(operationId, eliminationWorkflowContext.getEventType()));

                RequestResponse<JsonNode> jsonNodeRequestResponse =
                    processingClient.executeOperationProcess(operationId, eliminationWorkflowContext.getEventType(),
                        eliminationWorkflowContext.name(), RESUME.getValue());
                return jsonNodeRequestResponse.toResponse();
            }

        } catch (ContentAddressableStorageServerException | ContentAddressableStorageAlreadyExistException |
            InvalidGuidOperationException | LogbookClientServerException | LogbookClientBadRequestException | LogbookClientAlreadyExistsException |
            VitamClientException | InternalServerException e) {
            LOGGER.error("An error occurred during " + eliminationWorkflowContext.getEventType() + " workflow", e);
            return Response.status(INTERNAL_SERVER_ERROR)
                .entity(getErrorEntity(INTERNAL_SERVER_ERROR, e.getMessage())).build();
        } catch (final InvalidParseOperationException | InvalidCreateOperationException e) {
            LOGGER.error(BAD_REQUEST_EXCEPTION, e);
            // Un-processable Entity not implemented by Jersey
            status = Status.BAD_REQUEST;
            return Response.status(status).entity(getErrorEntity(status, e.getMessage())).build();
        } catch (BadRequestException e) {
            LOGGER.error("Empty query is impossible", e);
            return buildErrorResponse(VitamCode.GLOBAL_EMPTY_QUERY, null);
        }
    }

    /**
     * get Archive Unit list by query based on identifier
     *
     * @param queryDsl as JsonNode
     * @param idUnit   identifier
     * @return an archive unit result list
     */
    @Override
    @GET
    @Path("/units/{id_unit}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUnitById(JsonNode queryDsl,
        @PathParam("id_unit") String idUnit) {
        LOGGER.debug(EXECUTION_OF_DSL_VITAM_FROM_ACCESS_ONGOING);

        Status status;
        try {

            SanityChecker.checkJsonAll(queryDsl);
            SanityChecker.checkParameter(idUnit);
            JsonNode result =
                accessModule
                    .selectUnitbyId(
                        applyAccessContractRestrictionForUnitForSelect(queryDsl,
                            getVitamSession().getContract()), idUnit);
            resetQuery(result, queryDsl);

            LOGGER.debug(END_OF_EXECUTION_OF_DSL_VITAM_FROM_ACCESS);
            return Response.status(Status.OK).entity(result).build();
        } catch (final InvalidParseOperationException | InvalidCreateOperationException e) {
            LOGGER.error(BAD_REQUEST_EXCEPTION, e);
            // Unprocessable Entity not implemented by Jersey
            status = Status.BAD_REQUEST;
            return Response.status(status).entity(getErrorEntity(status, e.getMessage())).build();
        } catch (final AccessInternalExecutionException e) {
            LOGGER.error(e.getMessage(), e);
            status = Status.METHOD_NOT_ALLOWED;
            return Response.status(status).entity(getErrorEntity(status, e.getMessage())).build();
        }
    }

    @GET
    @Path("/units/{id_unit}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_XML)
    @Override
    public Response getUnitByIdWithXMLFormat(JsonNode queryDsl, @PathParam("id_unit") String idUnit) {
        Status status;
        try {
            SanityChecker.checkParameter(idUnit);
            SanityChecker.checkJsonAll(queryDsl);

            JsonNode result =
                accessModule
                    .selectUnitbyId(
                        applyAccessContractRestrictionForUnitForSelect(queryDsl,
                            getVitamSession().getContract()), idUnit);
            ArrayNode results = (ArrayNode) result.get(RESULTS);
            JsonNode unit = results.get(0);
            Response responseXmlFormat = unitDipService.jsonToXml(unit, idUnit);
            resetQuery(result, queryDsl);
            LOGGER.debug(END_OF_EXECUTION_OF_DSL_VITAM_FROM_ACCESS);
            return responseXmlFormat;
        } catch (final InvalidParseOperationException | InvalidCreateOperationException e) {
            LOGGER.error(BAD_REQUEST_EXCEPTION, e);
            // Unprocessable Entity not implemented by Jersey
            status = Status.BAD_REQUEST;
            return Response.status(status).entity(JsonHandler.unprettyPrint(getErrorEntity(status, e.getMessage())))
                .build();
        } catch (final AccessInternalExecutionException e) {
            LOGGER.error(e.getMessage(), e);
            status = Status.METHOD_NOT_ALLOWED;
            return Response.status(status).entity(JsonHandler.unprettyPrint(getErrorEntity(status, e.getMessage())))
                .build();
        }
    }

    /**
     * update archive units by Id with Json query
     *
     * @param requestId request identifier
     * @param queryDsl  DSK, null not allowed
     * @param idUnit    units identifier
     * @return a archive unit result list
     */
    @Override
    @PUT
    @Path("/units/{id_unit}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateUnitById(JsonNode queryDsl,
        @PathParam("id_unit") String idUnit, @HeaderParam(GlobalDataRest.X_REQUEST_ID) String requestId) {
        LOGGER.debug(EXECUTION_OF_DSL_VITAM_FROM_ACCESS_ONGOING);
        Status status;
        try {
            SanityChecker.checkJsonAll(queryDsl);
            SanityChecker.checkParameter(idUnit);
            SanityChecker.checkParameter(requestId);
            accessModule.checkClassificationLevel(queryDsl);
            JsonNode result = accessModule
                .updateUnitbyId(AccessContractRestrictionHelper.applyAccessContractRestrictionForUnitForUpdate(queryDsl,
                    getVitamSession().getContract()), idUnit, requestId);
            LOGGER.debug(END_OF_EXECUTION_OF_DSL_VITAM_FROM_ACCESS);
            return Response.status(Status.OK).entity(result).build();
        } catch (final IllegalArgumentException | InvalidParseOperationException | InvalidCreateOperationException e) {
            LOGGER.error(BAD_REQUEST_EXCEPTION, e);
            // Unprocessable Entity not implemented by Jersey
            status = Status.BAD_REQUEST;
            return Response.status(status).entity(getErrorEntity(status, e.getMessage())).build();
        } catch (final AccessInternalRuleExecutionException e) {
            LOGGER.error(e.getMessage(), e);
            return buildErrorResponse(VitamCode.ACCESS_INTERNAL_UPDATE_UNIT_CHECK_RULES, e.getMessage());
        } catch (final UpdatePermissionException e) {
            LOGGER.error(e.getMessage(), e);
            return buildErrorResponse(VitamCode.UPDATE_UNIT_PERMISSION, e.getMessage());
        } catch (final AccessInternalExecutionException e) {
            LOGGER.error(e.getMessage(), e);
            status = INTERNAL_SERVER_ERROR;
            return Response.status(status).entity(getErrorEntity(status, e.getMessage())).build();
        } catch (final MetaDataNotFoundException e) {
            LOGGER.error(NOT_FOUND_EXCEPTION, e);
            status = Status.NOT_FOUND;
            return Response.status(status).entity(getErrorEntity(status, e.getMessage())).build();
        }
    }

    @Override
    @GET
    @Path("/objects/{id_object_group}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getObjectGroup(@PathParam("id_object_group") String idObjectGroup, JsonNode query) {
        Status status;
        try {
            SanityChecker.checkJsonAll(query);
            SanityChecker.checkParameter(idObjectGroup);
            JsonNode result = accessModule
                .selectObjectGroupById(AccessContractRestrictionHelper
                        .applyAccessContractRestrictionForObjectGroupForSelect(query,
                            getVitamSession().getContract()),
                    idObjectGroup);
            return Response.status(Status.OK).entity(result).build();
        } catch (final InvalidParseOperationException | IllegalArgumentException |
            InvalidCreateOperationException exc) {
            LOGGER.error(exc);
            status = Status.PRECONDITION_FAILED;
            return Response.status(status).entity(getErrorEntity(status, exc.getMessage())).build();
        } catch (final AccessInternalExecutionException exc) {
            LOGGER.error(exc);
            status = INTERNAL_SERVER_ERROR;
            return Response.status(status).entity(getErrorEntity(status, exc.getMessage())).build();
        }
    }

    @GET
    @Path("/objects/{id_object_group}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_XML)
    @Override
    public Response getObjectByIdWithXMLFormat(JsonNode dslQuery, @PathParam("id_object_group") String objectId) {
        Status status;
        try {
            SanityChecker.checkParameter(objectId);
            SanityChecker.checkJsonAll(dslQuery);
            final JsonNode result = accessModule.selectObjectGroupById(
                AccessContractRestrictionHelper.applyAccessContractRestrictionForObjectGroupForSelect(dslQuery,
                    getVitamSession().getContract()),
                objectId);
            ArrayNode results = (ArrayNode) result.get(RESULTS);
            JsonNode objectGroup = results.get(0);
            Response responseXmlFormat = objectDipService.jsonToXml(objectGroup, objectId);
            return responseXmlFormat;
        } catch (InvalidParseOperationException | InvalidCreateOperationException e) {
            LOGGER.error(e);
            status = Status.BAD_REQUEST;
            return Response.status(status).entity(JsonHandler.unprettyPrint(getErrorEntity(status, e.getMessage())))
                .build();
        } catch (AccessInternalExecutionException e) {
            LOGGER.error(e.getMessage(), e);
            status = Status.METHOD_NOT_ALLOWED;
            return Response.status(status).entity(JsonHandler.unprettyPrint(getErrorEntity(status, e.getMessage())))
                .build();
        }
    }

    @GET
    @Path("/units/{id_unit}/object")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_XML)
    @Override
    public Response getObjectByUnitIdWithXMLFormat(JsonNode queryDsl, @PathParam("id_unit") String idUnit) {
        Status status;
        try {
            SanityChecker.checkParameter(idUnit);
            SanityChecker.checkJsonAll(queryDsl);
            //
            JsonNode result =
                accessModule
                    .selectUnitbyId(
                        applyAccessContractRestrictionForUnitForSelect(queryDsl,
                            getVitamSession().getContract()), idUnit);
            ArrayNode results = (ArrayNode) result.get(RESULTS);
            JsonNode objectGroup = results.get(0);
            // Response responseXmlFormat = unitDipService.jsonToXml(unit, idUnit);
            Response responseXmlFormat = objectDipService.jsonToXml(objectGroup, idUnit);
            resetQuery(result, queryDsl);
            LOGGER.debug(END_OF_EXECUTION_OF_DSL_VITAM_FROM_ACCESS);
            return responseXmlFormat;
        } catch (final InvalidParseOperationException | InvalidCreateOperationException e) {
            LOGGER.error(BAD_REQUEST_EXCEPTION, e);
            // Unprocessable Entity not implemented by Jersey
            status = Status.BAD_REQUEST;
            return Response.status(status).entity(JsonHandler.unprettyPrint(getErrorEntity(status, e.getMessage())))
                .build();
        } catch (final AccessInternalExecutionException e) {
            LOGGER.error(e.getMessage(), e);
            status = Status.METHOD_NOT_ALLOWED;
            return Response.status(status).entity(JsonHandler.unprettyPrint(getErrorEntity(status, e.getMessage())))
                .build();
        }
    }


    private Response asyncObjectStream(MultivaluedMap<String, String> multipleMap,
        String idObjectGroup, String idUnit, boolean post) {

        if (post) {
            if (!multipleMap.containsKey(GlobalDataRest.X_HTTP_METHOD_OVERRIDE)) {
                return Response.status(Status.PRECONDITION_FAILED)
                    .entity(getErrorStream(Status.PRECONDITION_FAILED, "method POST without Override = GET"))
                    .build();
            }
        }
        if (!multipleMap.containsKey(GlobalDataRest.X_TENANT_ID) ||
            !multipleMap.containsKey(GlobalDataRest.X_QUALIFIER) ||
            !multipleMap.containsKey(GlobalDataRest.X_VERSION)) {
            LOGGER.error("At least one required header is missing. Required headers: (" + VitamHttpHeader.TENANT_ID
                .name() + ", " + VitamHttpHeader.QUALIFIER.name() + ", " + VitamHttpHeader.VERSION.name() + ")");
            return Response.status(Status.PRECONDITION_FAILED)
                .entity(getErrorStream(Status.PRECONDITION_FAILED,
                    "At least one required header is missing. Required headers: (" + VitamHttpHeader.TENANT_ID
                        .name() + ", " + VitamHttpHeader.QUALIFIER.name() + ", " + VitamHttpHeader.VERSION.name() +
                        ")"))
                .build();
        }
        final String xQualifier = multipleMap.get(GlobalDataRest.X_QUALIFIER).get(0);
        final String xVersion = multipleMap.get(GlobalDataRest.X_VERSION).get(0);

        if (!getVitamSession().getContract().isEveryDataObjectVersion() &&
            !validUsage(xQualifier.split("_")[0])) {
            return Response.status(Status.UNAUTHORIZED)
                .entity(getErrorStream(Status.UNAUTHORIZED, "Qualifier unallowed"))
                .build();
        }
        try {

            SanityChecker.checkHeadersMap(multipleMap);
            HttpHeaderHelper.checkVitamHeadersMap(multipleMap);
            SanityChecker.checkParameter(idObjectGroup);
            return accessModule.getOneObjectFromObjectGroup(idObjectGroup, xQualifier,
                Integer.valueOf(xVersion), idUnit);
        } catch (final InvalidParseOperationException | IllegalArgumentException exc) {
            LOGGER.error(exc);
            return Response.status(Status.PRECONDITION_FAILED)
                .entity(getErrorStream(Status.PRECONDITION_FAILED, exc.getMessage()))
                .build();
        } catch (final AccessInternalExecutionException exc) {
            LOGGER.error(exc.getMessage(), exc);
            return Response.status(INTERNAL_SERVER_ERROR).entity(getErrorStream(INTERNAL_SERVER_ERROR,
                exc.getMessage())).build();
        } catch (MetaDataNotFoundException | StorageNotFoundException exc) {
            LOGGER.error(exc);
            return Response.status(Status.NOT_FOUND).entity(getErrorStream(Status.NOT_FOUND, exc.getMessage())).build();
        }
    }

    private boolean validUsage(String s) {
        final VitamSession vitamSession = getVitamSession();
        Set<String> versions = vitamSession.getContract().getDataObjectVersion();

        if (versions == null || versions.isEmpty()) {
            return true;
        }
        for (String version : versions) {
            if (version.equals(s)) {
                return true;
            }
        }
        return false;
    }

    private void checkEmptyQuery(JsonNode queryDsl)
        throws InvalidParseOperationException, BadRequestException {
        final SelectParserMultiple parser = new SelectParserMultiple();
        parser.parse(queryDsl.deepCopy());
        if (parser.getRequest().getNbQueries() == 0 && parser.getRequest().getRoots().isEmpty()) {
            throw new BadRequestException("Query cant be empty");
        }
    }

    @Override
    @GET
    @Path("/objects/{id_object_group}/{id_unit}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response getObjectStreamAsync(@Context HttpHeaders headers,
        @PathParam("id_object_group") String idObjectGroup, @PathParam("id_unit") String idUnit) {
        MultivaluedMap<String, String> multipleMap = headers.getRequestHeaders();
        return asyncObjectStream(multipleMap, idObjectGroup, idUnit, false);
    }

    @Override
    @GET
    @Path("/storageaccesslog")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response getAccessLogStreamAsync(@Context HttpHeaders headers, JsonNode params) {
        MultivaluedMap<String, String> multipleMap = headers.getRequestHeaders();

        if (!multipleMap.containsKey(GlobalDataRest.X_TENANT_ID)) {
            LOGGER.error("Header is missing. Required headers: (" + VitamHttpHeader.TENANT_ID.name() + ")");
            return Response.status(Status.PRECONDITION_FAILED)
                .entity(getErrorStream(Status.PRECONDITION_FAILED,
                    "Header is missing. Required headers: (" + VitamHttpHeader.TENANT_ID.name() + ")"))
                .build();
        }
        try {
            Integer tenantId = ParameterHelper.getTenantParameter();
            ParametersChecker.checkParameter("You must specify a valid tenant", tenantId);

            return accessModule.getAccessLog(params);
        } catch (IllegalArgumentException exc) {
            LOGGER.error(exc);
            return Response.status(Status.PRECONDITION_FAILED)
                .entity(getErrorStream(Status.PRECONDITION_FAILED, exc.getMessage()))
                .build();
        } catch (ParseException e) {
            LOGGER.error(e);
            return Response.status(Status.BAD_REQUEST).entity(getErrorStream(Status.BAD_REQUEST, e.getMessage()))
                .build();
        } catch (final AccessInternalExecutionException exc) {
            LOGGER.error(exc.getMessage(), exc);
            return Response.status(INTERNAL_SERVER_ERROR).entity(getErrorStream(INTERNAL_SERVER_ERROR,
                exc.getMessage())).build();
        } catch (StorageNotFoundException exc) {
            LOGGER.error(exc);
            return Response.status(Status.NOT_FOUND).entity(getErrorStream(Status.NOT_FOUND, exc.getMessage())).build();
        }
    }

    @Override
    @POST
    @Path(UNITS_URI)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response massUpdateUnits(JsonNode queryDsl) {

        LOGGER.debug("Start mass updating archive units with Dsl query {}", queryDsl);
        Status status;
        try {
            // Check sanity of json
            SanityChecker.checkJsonAll(queryDsl);

            // Check the writing rights
            if (!getVitamSession().getContract().getWritingPermission()) {
                status = Status.UNAUTHORIZED;
                return Response.status(status).entity(getErrorEntity(status, "Write permission not allowed")).build();
            }

            final RequestParserMultiple parser = RequestParserHelper.getParser(queryDsl);
            if (!(parser instanceof UpdateParserMultiple)) {
                parser.getRequest().reset();
                throw new IllegalArgumentException(REQUEST_IS_NOT_AN_UPDATE_OPERATION);
            } else {
                try {
                    OntologyUtils.addOntologyFieldsToBeUpdated((UpdateParserMultiple) parser);
                } catch (AdminManagementClientServerException | InvalidCreateOperationException |
                    InvalidParseOperationException e) {
                    LOGGER.error(e);
                    throw new AccessInternalExecutionException("Error while adding ontology information", e);
                }
            }

            boolean updateManagement = CheckSpecifiedFieldHelper.containsSpecifiedField(queryDsl, DataType.MANAGEMENT);
            Contexts context = updateManagement ? Contexts.MASS_UPDATE_UNIT_RULE : Contexts.MASS_UPDATE_UNIT_DESC;
            String operationId = getVitamSession().getRequestId();

            try (ProcessingManagementClient processingClient = processingManagementClientFactory.getClient();
                LogbookOperationsClient logbookOperationsClient = logbookOperationsClientFactory.getClient();
                WorkspaceClient workspaceClient = workspaceClientFactory.getClient()) {

                // Init logbook operation
                final LogbookOperationParameters initParameters =
                    LogbookParametersFactory.newLogbookOperationParameters(
                        GUIDReader.getGUID(operationId),
                        Contexts.MASS_UPDATE_UNIT_DESC.getEventType(),
                        GUIDReader.getGUID(operationId),
                        LogbookTypeProcess.MASS_UPDATE,
                        STARTED,
                        VitamLogbookMessages
                            .getCodeOp(Contexts.MASS_UPDATE_UNIT_DESC.getEventType(), STARTED),
                        GUIDReader.getGUID(operationId));

                // Add access contract rights
                addRightsStatementIdentifier(initParameters);
                logbookOperationsClient.create(initParameters);

                workspaceClient.createContainer(operationId);
                workspaceClient
                    .putObject(operationId, "query.json", writeToInpustream(
                        AccessContractRestrictionHelper.
                            applyAccessContractRestrictionForUnitForUpdate(queryDsl,
                                getVitamSession().getContract())));
                workspaceClient
                    .putObject(operationId, "actions.json",
                        writeToInpustream(JsonHandler.createObjectNode()));
                processingClient.initVitamProcess(Contexts.MASS_UPDATE_UNIT_DESC.name(), operationId,
                    Contexts.MASS_UPDATE_UNIT_DESC.getEventType());

                RequestResponse<JsonNode> requestResponse =
                    processingClient.executeOperationProcess(operationId, Contexts.MASS_UPDATE_UNIT_DESC.getEventType(),
                        Contexts.MASS_UPDATE_UNIT_DESC.name(),
                        RESUME.getValue());
                return requestResponse.toResponse();
            } catch (ContentAddressableStorageServerException | ContentAddressableStorageAlreadyExistException | LogbookClientBadRequestException |
                LogbookClientAlreadyExistsException | InvalidGuidOperationException | LogbookClientServerException | VitamClientException | InternalServerException e) {
                LOGGER.error("An error occured while mass updating archive units", e);
                return Response.status(INTERNAL_SERVER_ERROR)
                    .entity(getErrorEntity(INTERNAL_SERVER_ERROR, e.getMessage())).build();
            }
        } catch (InvalidParseOperationException | InvalidCreateOperationException | BadRequestException e) {
            LOGGER.error(BAD_REQUEST_EXCEPTION, e);
            status = Status.BAD_REQUEST;
            return Response.status(status).entity(getErrorEntity(status, e.getMessage())).build();

        } catch (VitamException e) {
            status = Status.INTERNAL_SERVER_ERROR;
            return Response.status(status).entity(getErrorEntity(status, e.getMessage())).build();
        }
    }

    @Override
    @POST
    @Path(UNITS_RULES_URI)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response massUpdateUnitsRules(MassUpdateUnitRuleRequest massUpdateUnitRuleRequest) {
        JsonNode queryDsl = massUpdateUnitRuleRequest.getDslRequest();
        RuleActions ruleActions = massUpdateUnitRuleRequest.getRuleActions();

        // TODO : refactor with unitMassUpdate
        LOGGER.debug("Start mass updating archive units with Dsl query {}", queryDsl);
        Status status;
        try {
            // Check sanity of json
            SanityChecker.checkJsonAll(queryDsl);

            // Check the writing rights
            if (!getVitamSession().getContract().getWritingPermission()) {
                status = Status.UNAUTHORIZED;
                return Response.status(status).entity(getErrorEntity(status, "Write permission not allowed")).build();
            }
            String operationId = getVitamSession().getRequestId();

            try (ProcessingManagementClient processingClient = processingManagementClientFactory.getClient();
                LogbookOperationsClient logbookOperationsClient = logbookOperationsClientFactory.getClient();
                WorkspaceClient workspaceClient = workspaceClientFactory.getClient()) {

                // Init logbook operation
                final LogbookOperationParameters initParameters =
                    LogbookParametersFactory.newLogbookOperationParameters(
                        GUIDReader.getGUID(operationId),
                        Contexts.MASS_UPDATE_UNIT_RULE.getEventType(),
                        GUIDReader.getGUID(operationId),
                        LogbookTypeProcess.MASS_UPDATE,
                        STARTED,
                        VitamLogbookMessages
                            .getCodeOp(Contexts.MASS_UPDATE_UNIT_RULE.getEventType(), STARTED),
                        GUIDReader.getGUID(operationId));

                // Add access contract rights
                addRightsStatementIdentifier(initParameters);
                logbookOperationsClient.create(initParameters);

                workspaceClient.createContainer(operationId);
                workspaceClient
                    .putObject(operationId, "query.json", writeToInpustream(
                        AccessContractRestrictionHelper.applyAccessContractRestrictionForUnitForUpdate(queryDsl,
                            getVitamSession().getContract())));
                workspaceClient
                    .putObject(operationId, "actions.json", writeToInpustream(ruleActions));
                processingClient.initVitamProcess(Contexts.MASS_UPDATE_UNIT_RULE.name(), operationId,
                    Contexts.MASS_UPDATE_UNIT_RULE.getEventType());

                RequestResponse<JsonNode> requestResponse =
                    processingClient.executeOperationProcess(operationId, Contexts.MASS_UPDATE_UNIT_RULE.getEventType(),
                        Contexts.MASS_UPDATE_UNIT_RULE.name(),
                        RESUME.getValue());
                return requestResponse.toResponse();
            } catch (ContentAddressableStorageServerException | ContentAddressableStorageAlreadyExistException | LogbookClientBadRequestException |
                LogbookClientAlreadyExistsException | InvalidGuidOperationException | LogbookClientServerException | VitamClientException | InternalServerException e) {
                LOGGER.error("An error occured while mass updating archive units", e);
                return Response.status(INTERNAL_SERVER_ERROR)
                    .entity(getErrorEntity(INTERNAL_SERVER_ERROR, e.getMessage())).build();
            }
        } catch (InvalidParseOperationException | InvalidCreateOperationException | BadRequestException e) {
            LOGGER.error(BAD_REQUEST_EXCEPTION, e);
            status = Status.BAD_REQUEST;
            return Response.status(status).entity(getErrorEntity(status, e.getMessage())).build();
        }
    }

    private VitamError getErrorEntity(Status status, String message) {
        String aMessage =
            (message != null && !message.trim().isEmpty()) ? message
                : (status.getReasonPhrase() != null ? status.getReasonPhrase() : status.name());

        return new VitamError(status.name()).setHttpCode(status.getStatusCode()).setContext(ACCESS_MODULE)
            .setState(CODE_VITAM).setMessage(status.getReasonPhrase()).setDescription(aMessage);
    }

    private InputStream getErrorStream(Status status, String message) {
        String aMessage =
            (message != null && !message.trim().isEmpty()) ? message
                : (status.getReasonPhrase() != null ? status.getReasonPhrase() : status.name());
        try {
            return writeToInpustream(new VitamError(status.name())
                .setHttpCode(status.getStatusCode()).setContext(ACCESS_MODULE)
                .setState(CODE_VITAM).setMessage(status.getReasonPhrase()).setDescription(aMessage));
        } catch (InvalidParseOperationException e) {
            return new ByteArrayInputStream("{ 'message' : 'Invalid VitamError message' }".getBytes());
        }
    }


    private Response buildErrorResponse(VitamCode vitamCode, String description) {
        if (description == null) {
            description = vitamCode.getMessage();
        }

        return Response.status(vitamCode.getStatus())
            .entity(new RequestResponseError().setError(new VitamError(VitamCodeHelper.getCode(vitamCode))
                .setContext(vitamCode.getService().getName()).setState(vitamCode.getDomain().getName())
                .setMessage(vitamCode.getMessage()).setDescription(description)).toString())
            .build();
    }

    private void resetQuery(JsonNode result, JsonNode queryDsl) {
        if (result != null && result.has(RequestResponseOK.TAG_CONTEXT)) {
            ((ObjectNode) result).set(RequestResponseOK.TAG_CONTEXT, queryDsl);
        }
    }

    private ObjectMapper buildObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.UPPER_CAMEL_CASE);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        SimpleModule module = new SimpleModule();

        module.addDeserializer(TextByLang.class, new TextByLangDeserializer());
        module.addDeserializer(LevelType.class, new LevelTypeDeserializer());
        module.addDeserializer(IdentifierType.class, new IdentifierTypeDeserializer());

        objectMapper.registerModule(module);

        return objectMapper;
    }

    /**
     * get Groups Objects list based on DSL query
     *
     * @param queryDsl as JsonNode
     * @return a group objects result list
     */
    @Override
    @GET
    @Path("/objects")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getObjects(JsonNode queryDsl) {
        LOGGER.debug(EXECUTION_OF_DSL_VITAM_FROM_ACCESS_ONGOING);
        Status status;
        JsonNode result = null;
        LOGGER.debug("DEBUG: start selectObjects {}", queryDsl);
        try {
            SanityChecker.checkJsonAll(queryDsl);
            checkEmptyQuery(queryDsl);
            result =
                accessModule
                    .selectObjects(AccessContractRestrictionHelper
                        .applyAccessContractRestrictionForObjectGroupForSelect(queryDsl,
                            getVitamSession().getContract()));
            LOGGER.debug("DEBUG {}", result);
            resetQuery(result, queryDsl);
            LOGGER.debug(END_OF_EXECUTION_OF_DSL_VITAM_FROM_ACCESS);
        } catch (final InvalidParseOperationException | InvalidCreateOperationException e) {
            LOGGER.error(BAD_REQUEST_EXCEPTION, e);
            // Unprocessable Entity not implemented by Jersey
            status = Status.BAD_REQUEST;
            return Response.status(status).entity(getErrorEntity(status, e.getMessage())).build();
        } catch (BadRequestException e) {
            LOGGER.error("Empty query is impossible", e);
            return buildErrorResponse(VitamCode.GLOBAL_EMPTY_QUERY, null);
        } catch (final Exception ve) {
            LOGGER.error(ve);
            status = Status.INTERNAL_SERVER_ERROR;
            return Response.status(status)
                .entity(new VitamError(status.name()).setHttpCode(status.getStatusCode())
                    .setContext(UNITS)
                    .setState(CODE_VITAM)
                    .setMessage(ve.getMessage())
                    .setDescription(status.getReasonPhrase()))
                .build();
        }
        return Response.status(Status.OK).entity(result).build();
    }

    @Path("/preservation")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response startPreservation(PreservationRequest preservationRequest) {

        try {
            ParametersChecker.checkParameter("Missing request", preservationRequest);
            String operationId = getVitamSession().getRequestId();

            AccessContractModel contract = getVitamSession().getContract();
            JsonNode dslQuery = preservationRequest.getDslQuery();
            JsonNode restrictedQuery = applyAccessContractRestrictionForUnitForSelect(dslQuery, contract);

            PreservationRequest restrictedRequest =
                new PreservationRequest(restrictedQuery, preservationRequest.getScenarioIdentifier(),
                    preservationRequest.getUsages(),preservationRequest.getVersion());

            try (ProcessingManagementClient processingClient = processingManagementClientFactory.getClient();
                LogbookOperationsClient logbookOperationsClient = logbookOperationsClientFactory.getClient();
                WorkspaceClient workspaceClient = workspaceClientFactory.getClient()) {

                String message = VitamLogbookMessages.getLabelOp(PRESERVATION.getEventType() + ".STARTED") + " : " +
                    GUIDReader.getGUID(operationId);

                LogbookOperationParameters initParameters = LogbookParametersFactory.newLogbookOperationParameters(
                    GUIDReader.getGUID(operationId),
                    PRESERVATION.getEventType(),
                    GUIDReader.getGUID(operationId),
                    LogbookTypeProcess.PRESERVATION,
                    STARTED,
                    message,
                    GUIDReader.getGUID(operationId)
                );

                logbookOperationsClient.create(initParameters);

                workspaceClient.createContainer(operationId);
                workspaceClient.putObject(operationId, "preservationRequest", writeToInpustream(restrictedRequest));

                //for CheckThresholdHandler
                workspaceClient
                    .putObject(operationId, "query.json", writeToInpustream(restrictedRequest.getDslQuery()));

                processingClient.initVitamProcess(PRESERVATION.name(),
                    new ProcessingEntry(operationId, PRESERVATION.getEventType()));

                return processingClient
                    .executeOperationProcess(operationId, PRESERVATION.getEventType(), PRESERVATION.name(),
                        RESUME.getValue())
                    .toResponse();
            }
        } catch (BadRequestException e) {
            return buildErrorResponse(VitamCode.GLOBAL_EMPTY_QUERY, null);
        } catch (Exception e) {
            LOGGER.error("Error on preservation request", e);
            return Response.status(INTERNAL_SERVER_ERROR)
                .entity(getErrorEntity(INTERNAL_SERVER_ERROR,
                    String.format("An error occurred during %s workflow", PRESERVATION.getEventType())))
                .build();
        }
    }
}
