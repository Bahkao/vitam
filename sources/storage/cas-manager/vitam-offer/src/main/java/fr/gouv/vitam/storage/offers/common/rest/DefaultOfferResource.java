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
package fr.gouv.vitam.storage.offers.common.rest;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import fr.gouv.vitam.common.server.application.VitamHttpHeader;
import fr.gouv.vitam.common.storage.cas.container.api.ObjectContent;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import fr.gouv.vitam.common.CommonMediaType;
import fr.gouv.vitam.common.GlobalDataRest;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.client.VitamRequestIterator;
import fr.gouv.vitam.common.digest.DigestType;
import fr.gouv.vitam.common.error.VitamCode;
import fr.gouv.vitam.common.error.VitamCodeHelper;
import fr.gouv.vitam.common.error.VitamError;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.RequestResponseError;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.security.SanityChecker;
import fr.gouv.vitam.common.server.application.resources.ApplicationStatusResource;
import fr.gouv.vitam.common.storage.constants.ErrorMessage;
import fr.gouv.vitam.common.stream.SizedInputStream;
import fr.gouv.vitam.common.stream.StreamUtils;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.storage.driver.model.StorageMetadataResult;
import fr.gouv.vitam.storage.engine.common.StorageConstants;
import fr.gouv.vitam.storage.engine.common.model.DataCategory;
import fr.gouv.vitam.storage.engine.common.model.ObjectInit;
import fr.gouv.vitam.storage.engine.common.model.OfferLog;
import fr.gouv.vitam.storage.engine.common.model.request.OfferLogRequest;
import fr.gouv.vitam.storage.offers.common.core.DefaultOfferService;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageAlreadyExistException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageNotFoundException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageServerException;

/**
 * Default offer REST Resource
 */
@Path("/offer/v1")
@javax.ws.rs.ApplicationPath("webresources")
public class DefaultOfferResource extends ApplicationStatusResource {

    private static final String MISSING_THE_TENANT_ID_X_TENANT_ID =
        "Missing the tenant ID (X-Tenant-Id) or wrong object Type";
    private static final String MISSING_THE_BODY = "Missing the body object";
    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(DefaultOfferResource.class);
    private static final String DEFAULT_OFFER_MODULE = "DEFAULT_OFFER";
    private static final String CODE_VITAM = "code_vitam";

    private DefaultOfferService defaultOfferService;

    /**
     * Constructor
     *
     * @param defaultOfferService
     */
    public DefaultOfferResource(DefaultOfferService defaultOfferService) {
        LOGGER.debug("DefaultOfferResource initialized");
        this.defaultOfferService = defaultOfferService;
    }

    /**
     * Get the information on the offer objects collection (free and used capacity, etc)
     *
     * @param xTenantId XtenantId
     * @param type The container type
     * @return information on the offer objects collection
     */
    // TODO P1 : review java method name
    // FIXME P1 il manque le /container/id/
    @HEAD
    @Path("/objects/{type}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCapacity(@HeaderParam(GlobalDataRest.X_TENANT_ID) String xTenantId,
        @PathParam("type") DataCategory type) {
        if (Strings.isNullOrEmpty(xTenantId)) {
            LOGGER.error(MISSING_THE_TENANT_ID_X_TENANT_ID);
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        final String containerName = buildContainerName(type, xTenantId);
        try {
            ObjectNode result = (ObjectNode) defaultOfferService.getCapacity(containerName);
            Response.ResponseBuilder response = Response.status(Status.OK);
            response.header("X-Usable-Space", result.get("usableSpace"));
            response.header(GlobalDataRest.X_TENANT_ID, xTenantId);
            return response.build();
        } catch (final ContentAddressableStorageNotFoundException exc) {
            LOGGER.error(ErrorMessage.CONTAINER_NOT_FOUND.getMessage() + containerName, exc);
            return Response.status(Response.Status.NOT_FOUND).build();
        } catch (final ContentAddressableStorageServerException exc) {
            LOGGER.error(exc);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get container object list
     *
     * @param xcursor if true means new query, if false means end of query from client side
     * @param xcursorId if present, means continue on cursor
     * @param xTenantId the tenant id
     * @param type object type
     * @return an iterator with each object metadata (actually only the id)
     */
    @GET
    @Path("/objects/{type}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getContainerList(@HeaderParam(GlobalDataRest.X_CURSOR) boolean xcursor,
        @HeaderParam(GlobalDataRest.X_CURSOR_ID) String xcursorId,
        @HeaderParam(GlobalDataRest.X_TENANT_ID) String xTenantId,
        @PathParam("type") DataCategory type) {
        try {
            if (Strings.isNullOrEmpty(xTenantId)) {
                LOGGER.error(MISSING_THE_TENANT_ID_X_TENANT_ID);
                final Response.ResponseBuilder builder = Response.status(Status.BAD_REQUEST);
                return VitamRequestIterator.setHeaders(builder, xcursor, null).build();
            }
            Status status;
            String cursorId = xcursorId;
            if (VitamRequestIterator.isEndOfCursor(xcursor, xcursorId)) {
                defaultOfferService.finalizeCursor(buildContainerName(type, xTenantId), xcursorId);
                final Response.ResponseBuilder builder = Response.status(Status.NO_CONTENT);
                return VitamRequestIterator.setHeaders(builder, xcursor, null).build();
            }

            if (VitamRequestIterator.isNewCursor(xcursor, xcursorId)) {
                try {
                    cursorId = defaultOfferService.createCursor(buildContainerName(type, xTenantId));
                } catch (ContentAddressableStorageNotFoundException | ContentAddressableStorageServerException exc) {
                    LOGGER.error(exc);
                    status = Status.INTERNAL_SERVER_ERROR;
                    final Response.ResponseBuilder builder = Response.status(status)
                        .entity(new VitamError(status.name()).setHttpCode(status.getStatusCode())
                            .setContext("default-offer")
                            .setState("code_vitam").setMessage(status.getReasonPhrase())
                            .setDescription(exc.getMessage()));
                    return VitamRequestIterator.setHeaders(builder, xcursor, null).build();
                }
            }

            final RequestResponseOK<JsonNode> responseOK = new RequestResponseOK<JsonNode>();

            if (defaultOfferService.hasNext(buildContainerName(type, xTenantId), cursorId)) {
                try {
                    List<JsonNode> list = defaultOfferService.next(buildContainerName(type, xTenantId), cursorId);
                    responseOK.addAllResults(list);
                    LOGGER.debug("Result {}", responseOK);
                    final Response.ResponseBuilder builder = Response
                        .status(defaultOfferService.hasNext(buildContainerName(type, xTenantId), cursorId)
                            ? Status.PARTIAL_CONTENT
                            : Status.OK).entity(responseOK);
                    return VitamRequestIterator.setHeaders(builder, xcursor, cursorId).build();
                } catch (ContentAddressableStorageNotFoundException exc) {
                    LOGGER.error(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), exc);
                    status = Status.INTERNAL_SERVER_ERROR;
                    final Response.ResponseBuilder builder = Response.status(status)
                        .entity(new VitamError(status.name()).setHttpCode(status.getStatusCode())
                            .setContext(DEFAULT_OFFER_MODULE)
                            .setState(CODE_VITAM).setMessage(status.getReasonPhrase())
                            .setDescription(exc.getMessage()));
                    return VitamRequestIterator.setHeaders(builder, xcursor, null).build();
                }
            } else {
                defaultOfferService.finalizeCursor(buildContainerName(type, xTenantId), xcursorId);
                final Response.ResponseBuilder builder = Response.status(Status.NO_CONTENT);
                return VitamRequestIterator.setHeaders(builder, xcursor, null).build();
            }
        } catch (Exception e) {
            LOGGER.error(e);
            final Response.ResponseBuilder builder = Response.status(Status.BAD_REQUEST);
            return VitamRequestIterator.setHeaders(builder, xcursor, null).build();
        }
    }

    /**
     * Get log of objects from container
     *
     * @param xTenantId the tenant id
     * @param type object type
     * @param offerLogRequest request params
     * @return list of objects infos
     */
    @GET
    @Path("/objects/{type}/logs")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getOfferLogs(@HeaderParam(GlobalDataRest.X_TENANT_ID) String xTenantId,
        @PathParam("type") DataCategory type, OfferLogRequest offerLogRequest) {
        try {
            if (offerLogRequest == null) {
                LOGGER.error(MISSING_THE_BODY);
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
            if (Strings.isNullOrEmpty(xTenantId)) {
                LOGGER.error(MISSING_THE_TENANT_ID_X_TENANT_ID);
                return Response.status(Response.Status.BAD_REQUEST).build();
            }

            final RequestResponseOK<OfferLog> responseOK = new RequestResponseOK<OfferLog>();
            final String containerName = buildContainerName(type, xTenantId);

            try {
                List<OfferLog> offerLogs =
                    defaultOfferService.getOfferLogs(containerName, offerLogRequest.getOffset(),
                        offerLogRequest.getLimit(), offerLogRequest.getOrder());
                responseOK.addAllResults(offerLogs).setHttpCode(Status.OK.getStatusCode());
                LOGGER.debug("Result {}", responseOK);
                return Response.status(Status.OK).entity(JsonHandler.writeAsString(responseOK)).build();
            } catch (ContentAddressableStorageException exc) {
                LOGGER.error(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), exc);
                return VitamCodeHelper.toVitamError(VitamCode.STORAGE_GET_OFFER_LOG_ERROR, exc.getMessage())
                    .toResponse();
            }
        } catch (Exception e) {
            LOGGER.error(e);
            return Response.status(Status.BAD_REQUEST).build();
        }
    }

    /**
     * Get the object data or digest from its id.
     * <p>
     * HEADER X-Tenant-Id (mandatory) : tenant's identifier HEADER "X-type" (optional) : data (dfault) or digest
     * </p>
     *
     * @param type Object type
     * @param objectId object id :.+ in order to get all path if some '/' are provided
     * @param headers http header
     * @return response
     * @throws IOException when there is an error of get object
     */
    @GET
    @Path("/objects/{type}/{id_object}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_OCTET_STREAM, CommonMediaType.ZIP})
    public Response getObject(@PathParam("type") DataCategory type, @NotNull @PathParam("id_object") String objectId,
        @Context HttpHeaders headers) {
        final String xTenantId = headers.getHeaderString(GlobalDataRest.X_TENANT_ID);
        try {
            SanityChecker.checkParameter(objectId);
            if (Strings.isNullOrEmpty(xTenantId)) {
                LOGGER.error(MISSING_THE_TENANT_ID_X_TENANT_ID);
                return Response.status(Status.PRECONDITION_FAILED).build();
            }
            final String containerName = buildContainerName(type, xTenantId);
            ObjectContent objectContent = defaultOfferService.getObject(containerName, objectId);
            return Response.ok(objectContent.getInputStream())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM)
                .header(VitamHttpHeader.X_CONTENT_LENGTH.getName(), objectContent.getSize())
                .build();
        } catch (final ContentAddressableStorageNotFoundException e) {
            LOGGER.error(e);
            return buildErrorResponse(VitamCode.STORAGE_NOT_FOUND);
        } catch (final ContentAddressableStorageException | InvalidParseOperationException e) {
            LOGGER.error(e);
            return buildErrorResponse(VitamCode.STORAGE_TECHNICAL_INTERNAL_ERROR);
        }
    }

    /**
     * Initialise a new object.
     * <p>
     * HEADER X-Command (mandatory) : INIT <br>
     * HEADER X-Tenant-Id (mandatory) : tenant's identifier
     * </p>
     *
     *
     * @param type New object's type
     * @param objectGUID the GUID Of the object
     * @param headers http header
     * @param objectInit data for object creation
     * @return structured response with the object id
     */
    // TODO - us#1982 - to be changed with this story - tenantId to stay in the
    // header but path (type unit or object) in
    // the uri
    @POST
    @Path("/objects/{type}/{guid:.+}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response postObject(@PathParam("guid") String objectGUID, @PathParam("type") DataCategory type,
        @Context HttpHeaders headers, ObjectInit objectInit) {
        final String xTenantId = headers.getHeaderString(GlobalDataRest.X_TENANT_ID);
        if (objectInit == null) {
            LOGGER.error(MISSING_THE_BODY);
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        if (Strings.isNullOrEmpty(xTenantId)) {
            LOGGER.error(MISSING_THE_TENANT_ID_X_TENANT_ID);
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        final String containerName = buildContainerName(type, xTenantId);
        if (!objectInit.getType().equals(type)) {
            LOGGER.error(MISSING_THE_TENANT_ID_X_TENANT_ID);
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        final String xCommandHeader = headers.getHeaderString(GlobalDataRest.X_COMMAND);
        if (xCommandHeader == null || !xCommandHeader.equals(StorageConstants.COMMAND_INIT)) {
            LOGGER.error("Missing the INIT required command (X-Command header)");
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        try {
            SanityChecker.checkParameter(objectGUID);
            final ObjectInit objectInitFilled = defaultOfferService.initCreateObject(containerName, objectInit,
                objectGUID);
            LOGGER.info("ContainerName: " + containerName + " ObjectGUID " + objectGUID);
            return Response.status(Response.Status.CREATED).entity(objectInitFilled).build();
        } catch (final ContentAddressableStorageAlreadyExistException e) {
            LOGGER.error("ContainerName: " + containerName + " ObjectGUID " + objectGUID, e);
            return Response.status(Response.Status.CONFLICT).build();
        } catch (final ContentAddressableStorageException | InvalidParseOperationException exc) {
            LOGGER.error(exc);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Write a new chunk in an object or end its creation.<br>
     * Replaces units and objectGroups object type if exist
     * <p>
     * HEADER X-Command (mandatory) : WRITE/END HEADER X-Tenant-Id (mandatory) : tenant's identifier
     * </p>
     *
     * @param type Object type to update
     * @param objectId object id
     * @param headers http header
     * @param input object data
     * @return structured response with the object id (and new digest ?)
     */
    // TODO - us#1982 - to be changed with this story - tenantId to stay in the
    // header but path (type unit or object) in
    // the uri
    @PUT
    @Path("/objects/{type}/{id:.+}")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    public Response putObject(@PathParam("type") DataCategory type, @PathParam("id") String objectId,
        @Context HttpHeaders headers, InputStream input) {
        try {
            final String xTenantId = headers.getHeaderString(GlobalDataRest.X_TENANT_ID);
            if (Strings.isNullOrEmpty(xTenantId)) {
                LOGGER.error(MISSING_THE_TENANT_ID_X_TENANT_ID);
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
            final String containerName = buildContainerName(type, xTenantId);
            final String xCommandHeader = headers.getHeaderString(GlobalDataRest.X_COMMAND);
            if (xCommandHeader == null || !xCommandHeader.equals(StorageConstants.COMMAND_WRITE) &&
                !xCommandHeader.equals(StorageConstants.COMMAND_END)) {

                LOGGER.error("Missing the WRITE or END required command (X-Command header), {} found",
                    xCommandHeader);
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
            try {
                SanityChecker.checkParameter(objectId);
                final SizedInputStream sis = new SizedInputStream(input);
                final String size = headers.getHeaderString(GlobalDataRest.VITAM_CONTENT_LENGTH);

                Long inputStreamSize;
                try {
                    inputStreamSize = Long.valueOf(size);
                } catch (NumberFormatException e) {
                    LOGGER.error("Bad or missing size '" + size + "'");
                    return Response.status(Response.Status.BAD_REQUEST).build();
                }
                DigestType digestType;
                String xDigestAlgorithm = headers.getHeaderString(GlobalDataRest.X_DIGEST_ALGORITHM);
                if (StringUtils.isEmpty(xDigestAlgorithm)) {
                	digestType = VitamConfiguration.getDefaultDigestType();
                } else {
                	digestType = DigestType.fromValue(xDigestAlgorithm);
                }
                final String digest =
                    defaultOfferService.createObject(containerName, objectId, sis,
                        xCommandHeader.equals(StorageConstants.COMMAND_END), type, inputStreamSize, digestType);
                return Response.status(Response.Status.CREATED)
                    .entity("{\"digest\":\"" + digest + "\",\"size\":\"" + sis.getSize() + "\"}").build();
            } catch (IOException | ContentAddressableStorageException exc) {
                LOGGER.error("Cannot create object", exc);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            LOGGER.error(e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        } finally {
            StreamUtils.closeSilently(input);
        }
    }

    /**
     * Delete an Object
     *
     * @param xTenantId the tenantId
     * @param xDigestAlgorithm the digest algorithm
     * @param type Object type to delete
     * @param idObject the id of the object to be tested
     * @return the response with a specific HTTP status
     */
    @DELETE
    @Path("/objects/{type}/{id:.+}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteObject(@HeaderParam(GlobalDataRest.X_TENANT_ID) String xTenantId,
        @HeaderParam(GlobalDataRest.X_DIGEST_ALGORITHM) String xDigestAlgorithm, @PathParam("type") DataCategory
        type,
        @PathParam("id") String idObject) {
        if (Strings.isNullOrEmpty(xTenantId)) {
            LOGGER.error(MISSING_THE_TENANT_ID_X_TENANT_ID);
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        try {
            SanityChecker.checkParameter(idObject);
            VitamThreadUtils.getVitamSession()
                .setRequestId(GUIDFactory.newRequestIdGUID(Integer.parseInt(xTenantId)));
            final String containerName = buildContainerName(type, xTenantId);
            defaultOfferService.deleteObject(containerName, idObject, type);
            return Response.status(Response.Status.OK)
                .entity("{\"id\":\"" + idObject + "\",\"status\":\"" + Response.Status.OK.toString() + "\"}")
                .build();
        } catch (ContentAddressableStorageNotFoundException e) {
            LOGGER.error(e);
            return Response.status(Response.Status.NOT_FOUND).build();
        } catch (ContentAddressableStorageException | InvalidParseOperationException e) {
            LOGGER.error(e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }

    }

    /**
     * Test the existence of an object
     *
     * HEADER X-Tenant-Id (mandatory) : tenant's identifier
     *
     * @param type Object type to test
     * @param idObject the id of the object to be tested
     * @param xTenantId the id of the tenant
     * @return the response with a specific HTTP status. If none of DIGEST or
     * DIGEST_ALGORITHM headers is given, an existence test is done and
     * can return 204/404 as response. If only DIGEST or only
     * DIGEST_ALGORITHM header is given, a not implemented exception is
     * thrown. Later, this should respond with 200/409. If both DIGEST
     * and DIGEST_ALGORITHM header are given, a full digest check is
     * done and can return 200/409 as response
     */
    @HEAD
    @Path("/objects/{type}/{id:.+}")
    public Response checkObjectExistence(@PathParam("type") DataCategory type, @PathParam("id") String idObject,
        @HeaderParam(GlobalDataRest.X_TENANT_ID) String xTenantId) {
        if (Strings.isNullOrEmpty(xTenantId)) {
            LOGGER.error(MISSING_THE_TENANT_ID_X_TENANT_ID);
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        String containerName = buildContainerName(type, xTenantId);

        try {
            SanityChecker.checkParameter(idObject);
            if (defaultOfferService.isObjectExist(containerName, idObject)) {
                return Response.status(Response.Status.NO_CONTENT).build();
            } else {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        } catch (final ContentAddressableStorageException | InvalidParseOperationException e) {
            LOGGER.error(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get metadata of the object type.
     *
     * @param type Object type to test
     * @param idObject the id of the object to be tested
     * @param xTenantId the id of the tenant
     * @return metadatas
     */
    @GET
    @Path("/objects/{type}/{id:.+}/metadatas")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getObjectMetadata(@PathParam("type") DataCategory type, @PathParam("id") String idObject,
        @HeaderParam(GlobalDataRest.X_TENANT_ID) String xTenantId,
        @HeaderParam(GlobalDataRest.X_OFFER_NO_CACHE) Boolean noCache) {

        if (Strings.isNullOrEmpty(xTenantId) || noCache == null) {
            LOGGER.error("Missing tenant ID (X-Tenant-Id) or noCache");
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        final String containerName = buildContainerName(type, xTenantId);
        try {
            SanityChecker.checkParameter(idObject);
            StorageMetadataResult result = defaultOfferService.getMetadatas(containerName, idObject, noCache);
            return Response.status(Response.Status.OK).entity(result).build();
        } catch (ContentAddressableStorageNotFoundException e) {
            LOGGER.error(e);
            return Response.status(Response.Status.NOT_FOUND).build();
        } catch (ContentAddressableStorageException | IOException | InvalidParseOperationException e) {
            LOGGER.error(e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Add error response using with vitamCode
     *
     * @param vitamCode vitam error Code
     */

    private Response buildErrorResponse(VitamCode vitamCode) {
        return Response.status(vitamCode.getStatus()).entity(new RequestResponseError().setError(
            new VitamError(VitamCodeHelper.getCode(vitamCode))
                .setContext(vitamCode.getService().getName())
                .setState(vitamCode.getDomain().getName())
                .setMessage(vitamCode.getMessage())
                .setDescription(vitamCode.getMessage()))
            .toString()).build();
    }

    private String buildContainerName(DataCategory type, String tenantId) {
        if (type == null || Strings.isNullOrEmpty(type.getFolder()) || Strings.isNullOrEmpty(tenantId)) {
            return null;
        }
        String environmentName = Strings.isNullOrEmpty(VitamConfiguration.getEnvironmentName()) ?
            "" :
            VitamConfiguration.getEnvironmentName() + "_";

        return environmentName + tenantId + "_" + type.getFolder();
    }
}
