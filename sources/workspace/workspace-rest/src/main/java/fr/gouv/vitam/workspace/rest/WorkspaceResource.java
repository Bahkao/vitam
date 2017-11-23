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
package fr.gouv.vitam.workspace.rest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.gouv.vitam.common.CommonMediaType;
import fr.gouv.vitam.common.GlobalDataRest;
import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.digest.DigestType;
import fr.gouv.vitam.common.error.VitamCode;
import fr.gouv.vitam.common.error.VitamError;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.server.application.VitamHttpHeader;
import fr.gouv.vitam.common.server.application.resources.ApplicationStatusResource;
import fr.gouv.vitam.common.storage.ContainerInformation;
import fr.gouv.vitam.common.storage.StorageConfiguration;
import fr.gouv.vitam.common.storage.constants.ErrorMessage;
import fr.gouv.vitam.common.stream.StreamUtils;
import fr.gouv.vitam.common.stream.VitamAsyncInputStreamResponse;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageAlreadyExistException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageCompressedFileException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageNotFoundException;
import fr.gouv.vitam.workspace.api.exception.ZipFilesNameNotAllowedException;
import fr.gouv.vitam.workspace.common.CompressInformation;
import fr.gouv.vitam.workspace.common.WorkspaceFileSystem;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.compressors.CompressorException;

/**
 * The Workspace Resource.
 */
@Path("/workspace/v1")
@javax.ws.rs.ApplicationPath("webresources")
public class WorkspaceResource extends ApplicationStatusResource {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(WorkspaceResource.class);

    private static final String FOLDER_NAME = "folderName";

    private static final String OBJECT_NAME = "objectName";

    private static final String CONTAINER_NAME = "containerName";

    private final WorkspaceFileSystem workspace;

    /**
     * Constructor used to configure a workspace
     *
     * @param configuration the storage config
     */
    public WorkspaceResource(StorageConfiguration configuration) {
        try {
            workspace = new WorkspaceFileSystem(configuration);
        } catch (IOException ex) {
            LOGGER.error("cannot load WorkspaceFileSystem : ", ex);
            throw new IllegalStateException(ex);
        }
        LOGGER.info("init Workspace Resource server");
    }

    /**
     * creates a container into the workspace
     *
     * @param containerName as path param
     * @return Response
     */
    @Path("/containers/{containerName}")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response createContainer(@PathParam(CONTAINER_NAME) String containerName) {
        try {
            ParametersChecker.checkParameter(ErrorMessage.CONTAINER_NAME_IS_A_MANDATORY_PARAMETER.getMessage(),
                containerName);
            workspace.createContainer(containerName);
        } catch (final IllegalArgumentException e) {
            LOGGER.error(e);
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (final ContentAddressableStorageAlreadyExistException e) {
            LOGGER.info(ErrorMessage.CONTAINER_ALREADY_EXIST.getMessage() + containerName + " => " + e.getMessage());
            return Response.status(Status.CONFLICT).entity(containerName).build();
        } catch (Exception e) {
            LOGGER.error(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(containerName).build();
        }

        return Response.status(Status.CREATED).entity(containerName).build();
    }

    /**
     * deletes a container in the workspace
     *
     * @param containerName path param of container name
     * @param recursive     true if the container should be deleted recursively
     * @return Response
     */
    @Path("/containers/{containerName}")
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteContainer(@PathParam(CONTAINER_NAME) String containerName,
        @HeaderParam(GlobalDataRest.X_RECURSIVE) boolean recursive) {
        try {
            ParametersChecker.checkParameter(ErrorMessage.CONTAINER_NAME_IS_A_MANDATORY_PARAMETER.getMessage(),
                containerName);
            workspace.deleteContainer(containerName, recursive);
        } catch (final ContentAddressableStorageNotFoundException e) {
            LOGGER.error(ErrorMessage.CONTAINER_NOT_FOUND.getMessage() + containerName + " => " + e.getMessage());
            return Response.status(Status.NOT_FOUND).entity(containerName).build();
        } catch (final IllegalArgumentException e) {
            LOGGER.error(e);
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (final Exception e) {
            LOGGER.error(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(containerName).build();
        }

        return Response.status(Status.NO_CONTENT).entity(containerName).build();
    }

    /**
     * checks if a container exists in the workspace
     *
     * @param containerName path param for container name
     * @return Response
     */
    @Path("/containers/{containerName}")
    @HEAD
    @Produces(MediaType.APPLICATION_JSON)
    public Response isExistingContainer(@PathParam(CONTAINER_NAME) String containerName) {
        try {
            ParametersChecker.checkParameter(ErrorMessage.CONTAINER_NAME_IS_A_MANDATORY_PARAMETER.getMessage(),
                containerName);
            final boolean exists = workspace.isExistingContainer(containerName);
            if (exists) {
                return Response.status(Status.OK).entity(containerName).build();
            } else {
                return Response.status(Status.NOT_FOUND).build();
            }
        } catch (final IllegalArgumentException e) {
            LOGGER.error(e);
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (final Exception e) {
            LOGGER.error(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(containerName).build();
        }
    }


    /**
     * Count the number of binary files in the container
     *
     * @param containerName path param for container name
     * @return Response containing the number of objects in "objectNumber"
     */
    @Path("/containers/{containerName}/count")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response countObjects(@PathParam(CONTAINER_NAME) String containerName) {

        try {
            ParametersChecker.checkParameter(ErrorMessage.CONTAINER_NAME_IS_A_MANDATORY_PARAMETER.getMessage(),
                containerName);
            final long objectNumber = workspace.countObjects(containerName);
            // TODO should use a common model object, but since it should be common with driver, where ?
            final ObjectNode result = JsonHandler.createObjectNode();
            result.put("objectNumber", objectNumber);
            return Response.status(Status.OK).entity(result).build();
        } catch (final IllegalArgumentException e) {
            LOGGER.error(e);
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (final ContentAddressableStorageNotFoundException exc) {
            LOGGER.error(ErrorMessage.CONTAINER_NOT_FOUND.getMessage() + containerName, exc);
            return Response.status(Status.NOT_FOUND).entity(containerName).build();
        } catch (final Exception e) {
            LOGGER.error(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(containerName).build();
        }
    }

    /**
     * Get container information like capacity
     *
     * @param containerName the container name
     * @return a Json with usableSpace and usedSpace information
     */
    @Path("/container/{containerName}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getContainerInformation(@PathParam(CONTAINER_NAME) String containerName) {
        try {
            ParametersChecker.checkParameter(ErrorMessage.CONTAINER_NAME_IS_A_MANDATORY_PARAMETER.getMessage(),
                containerName);
            final ContainerInformation containerInformation = workspace.getContainerInformation(containerName);
            return Response.status(Status.OK).entity(containerInformation).build();
        } catch (final IllegalArgumentException e) {
            LOGGER.error(e);
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (final ContentAddressableStorageNotFoundException exc) {
            LOGGER.error(ErrorMessage.CONTAINER_NOT_FOUND.getMessage() + containerName, exc);
            return Response.status(Status.NOT_FOUND).entity(containerName).build();
        } catch (final Exception e) {
            LOGGER.error(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(containerName).build();
        }
    }

    /**
     * creates a folder into a container
     *
     * @param containerName path param of container name
     * @param folderName    path param of folder
     * @return Response
     */
    @Path("/containers/{containerName}/folders/{folderName:.*}")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response createFolder(@PathParam(CONTAINER_NAME) String containerName,
        @PathParam(FOLDER_NAME) String folderName) {
        try {
            ParametersChecker.checkParameter(ErrorMessage.CONTAINER_NAME_IS_A_MANDATORY_PARAMETER.getMessage(),
                containerName, folderName);
            workspace.createFolder(containerName, folderName);
        } catch (final IllegalArgumentException e) {
            LOGGER.error(e);
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (final ContentAddressableStorageAlreadyExistException e) {
            LOGGER.info(ErrorMessage.FOLDER_ALREADY_EXIST.getMessage() + containerName + "/" + folderName, e);
            return Response.status(Status.CONFLICT).entity(containerName + "/" + folderName).build();
        } catch (final ContentAddressableStorageNotFoundException e) {
            LOGGER.error(ErrorMessage.FOLDER_NOT_FOUND.getMessage() + containerName + "/" + folderName, e);
            return Response.status(Status.NOT_FOUND).entity(containerName + "/" + folderName).build();
        } catch (final Exception e) {
            LOGGER.error(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(containerName).build();
        }

        return Response.status(Status.CREATED).entity(containerName + "/" + folderName).build();
    }

    /**
     * deletes a folder in a container
     *
     * @param containerName path param for container name
     * @param folderName    path param for folder name
     * @return Response
     */
    @Path("/containers/{containerName}/folders/{folderName:.*}")
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteFolder(@PathParam(CONTAINER_NAME) String containerName,
        @PathParam(FOLDER_NAME) String folderName) {

        try {
            ParametersChecker.checkParameter(ErrorMessage.CONTAINER_NAME_IS_A_MANDATORY_PARAMETER.getMessage(),
                containerName, folderName);
            workspace.deleteFolder(containerName, folderName);
        } catch (final IllegalArgumentException e) {
            LOGGER.error(e);
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (final ContentAddressableStorageNotFoundException e) {
            LOGGER.error(ErrorMessage.FOLDER_NOT_FOUND.getMessage() + containerName + "/" + folderName, e);
            return Response.status(Status.NOT_FOUND).entity(containerName + "/" + folderName).build();
        } catch (final Exception e) {
            LOGGER.error(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(containerName).build();
        }

        return Response.status(Status.NO_CONTENT).entity(containerName + "/" + folderName).build();
    }

    /**
     * checks if a folder exists in a container
     *
     * @param containerName path param for container name
     * @param folderName    path param for folder name
     * @return Response
     */
    @Path("/containers/{containerName}/folders/{folderName:.*}")
    @HEAD
    @Produces(MediaType.APPLICATION_JSON)
    public Response isExistingFolder(@PathParam(CONTAINER_NAME) String containerName,
        @PathParam(FOLDER_NAME) String folderName) {
        try {
            ParametersChecker.checkParameter(ErrorMessage.CONTAINER_NAME_IS_A_MANDATORY_PARAMETER.getMessage(),
                containerName, folderName);
            final boolean exists = workspace.isExistingFolder(containerName, folderName);
            if (exists) {
                return Response.status(Status.OK).entity(containerName + "/" + folderName).build();
            } else {
                return Response.status(Status.NOT_FOUND).build();
            }
        } catch (final IllegalArgumentException e) {
            LOGGER.error(e);
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (final Exception e) {
            LOGGER.error(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(containerName).build();
        }
    }

    /**
     * uncompress a sip into the workspace
     *
     * @param stream        data input stream
     * @param containerName name of container
     * @param folderName    name of folder
     * @param archiveType   the type of archive
     * @return Response
     */
    @Path("/containers/{containerName}/folders/{folderName:.*}")
    @PUT
    @Consumes({CommonMediaType.ZIP, CommonMediaType.GZIP, CommonMediaType.TAR, CommonMediaType.BZIP2})
    @Produces(MediaType.APPLICATION_JSON)
    public Response uncompressObject(InputStream stream,
        @PathParam(CONTAINER_NAME) String containerName,
        @PathParam(FOLDER_NAME) String folderName, @HeaderParam(HttpHeaders.CONTENT_TYPE) String archiveType) {

        try {
            ParametersChecker.checkParameter(ErrorMessage.CONTAINER_FOLDER_NAMES_ARE_A_MANDATORY_PARAMETER.getMessage(),
                containerName, folderName);
            try {
                workspace.uncompressObject(containerName, folderName, archiveType, stream);
            } catch (final ContentAddressableStorageNotFoundException e) {
                LOGGER.error(ErrorMessage.CONTAINER_NOT_FOUND.getMessage() + containerName, e);
                return Response.status(Status.NOT_FOUND).entity(containerName).build();
            } catch (final ContentAddressableStorageAlreadyExistException e) {
                LOGGER.info(ErrorMessage.CONTAINER_ALREADY_EXIST.getMessage() + containerName, e);
                return Response.status(Status.CONFLICT).entity(containerName).build();
            } catch (final ZipFilesNameNotAllowedException e) {
                LOGGER.error(e);
                final VitamError vitamError = getVitamError(VitamCode.WORKSPACE_NOT_ACCEPTABLE_FILES, e.getMessage());
                return Response.status(Status.NOT_ACCEPTABLE)
                    .entity(vitamError)
                    .build();
            } catch (final ContentAddressableStorageCompressedFileException e) {
                LOGGER.error(e);
                final Status status = Status.BAD_REQUEST;
                final VitamError vitamError = getVitamError(VitamCode.WORKSPACE_BAD_REQUEST, e.getMessage());
                return Response.status(status)
                    .entity(vitamError)
                    .build();

            } catch (final ContentAddressableStorageException e) {
                LOGGER.error(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
                return Response.status(Status.INTERNAL_SERVER_ERROR).entity(containerName).build();
            }
            return Response.status(Status.CREATED).entity(containerName).build();
        } catch (final IllegalArgumentException e) {
            LOGGER.error(e);
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        } finally {
            StreamUtils.closeSilently(stream);
        }

    }

    private VitamError getVitamError(VitamCode vitamCode, String msg) {
        return new VitamError(vitamCode.name()).setMessage(msg).setState("ko")
            .setHttpCode(vitamCode.getStatus().getStatusCode()).setDescription(msg)
            .setContext(vitamCode.getService().getName());
    }

    /**
     * zip a specific folder into a other directory
     *
     * @param containerName
     * @return
     */
    @Path("/containers/{containerName}")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response compress(@PathParam(CONTAINER_NAME) String containerName, CompressInformation compressInformation) {

        try {
            ParametersChecker.checkParameter(ErrorMessage.CONTAINER_FOLDER_NAMES_ARE_A_MANDATORY_PARAMETER.getMessage(),
                containerName, compressInformation);
            workspace.compress(containerName, compressInformation.getFiles(), compressInformation.getOutputFile());
            return Response.status(Status.CREATED).build();

        } catch (final IllegalArgumentException e) {
            LOGGER.error(e);
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (CompressorException | ArchiveException | IOException e) {
            LOGGER.error(e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }

    /**
     * gets the list of object from folder
     *
     * @param containerName name of container
     * @param folderName    name of folder
     * @return Response
     */
    @Path("/containers/{containerName}/folders/{folderName:.*}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUriDigitalObjectListByFolder(@PathParam(CONTAINER_NAME) String containerName,
        @PathParam(FOLDER_NAME) String folderName) {

        List<URI> uriList = null;
        try {
            ParametersChecker.checkParameter(ErrorMessage.CONTAINER_FOLDER_NAMES_ARE_A_MANDATORY_PARAMETER.getMessage(),
                containerName, folderName);
            uriList = workspace.getListUriDigitalObjectFromFolder(containerName, folderName);

        } catch (final ContentAddressableStorageNotFoundException eNotFoundException) {
            LOGGER.error(ErrorMessage.FOLDER_NOT_FOUND.getMessage() + containerName + "/" + folderName,
                eNotFoundException);
            return Response.status(Status.NOT_FOUND).entity(containerName).build();
        } catch (final ContentAddressableStorageException eAddressableStorageException) {
            LOGGER.error(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), eAddressableStorageException);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Collections.<URI>emptyList()).build();
        } catch (final IllegalArgumentException e) {
            LOGGER.error(e);
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        }

        if (uriList == null || uriList.isEmpty()) {
            return Response.status(Status.NO_CONTENT).entity(Collections.<URI>emptyList()).build();
        }
        return Response.status(Status.OK).entity(uriList).build();

    }

    /**
     * puts an object into a container
     *
     * @param stream        data input stream
     * @param objectName    name of data object
     * @param containerName name of container
     * @return Response
     */
    @Path("/containers/{containerName}/objects/{objectName:.*}")
    @POST
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    public Response putObject(InputStream stream, @PathParam(CONTAINER_NAME) String containerName,
        @PathParam(OBJECT_NAME) String objectName) {
        try {
            ParametersChecker.checkParameter(ErrorMessage.CONTAINER_NAME_IS_A_MANDATORY_PARAMETER.getMessage(),
                containerName, objectName);
            workspace.putObject(containerName, objectName, stream);
            return Response.status(Status.CREATED).entity(containerName + "/" + objectName).build();
        } catch (final IllegalArgumentException e) {
            LOGGER.error(e);
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (final ContentAddressableStorageNotFoundException e) {
            LOGGER.error(ErrorMessage.OBJECT_NOT_FOUND.getMessage() + containerName, e);
            return Response.status(Status.NOT_FOUND).entity(containerName).build();
        } catch (final ContentAddressableStorageException e) {
            LOGGER.error(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(containerName).build();
        } finally {
            StreamUtils.closeSilently(stream);
        }
    }

    /**
     * Deletes an objects in a container *
     *
     * @param containerName container name
     * @param objectName    object name
     * @return Response
     */
    @Path("/containers/{containerName}/objects/{objectName:.*}")
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteObject(@PathParam(CONTAINER_NAME) String containerName,
        @PathParam(OBJECT_NAME) String objectName) {

        try {
            ParametersChecker.checkParameter(ErrorMessage.CONTAINER_NAME_IS_A_MANDATORY_PARAMETER.getMessage(),
                containerName, objectName);
            workspace.deleteObject(containerName, objectName);
        } catch (final IllegalArgumentException e) {
            LOGGER.error(e);
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (final ContentAddressableStorageNotFoundException e) {
            LOGGER.error(ErrorMessage.OBJECT_NOT_FOUND.getMessage() + containerName, e);
            return Response.status(Status.NOT_FOUND).entity(containerName).build();
        } catch (final Exception e) {
            LOGGER.error(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(containerName).build();
        }

        return Response.status(Status.NO_CONTENT).entity(containerName).build();
    }

    /**
     * gets an objects from a container in the workspace
     *
     * @param containerName name of container
     * @param objectName    name of object
     * @return response
     * @throws IOException when there is an error of get object
     */
    @Path("/containers/{containerName}/objects/{objectName:.*}")
    @GET
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response getObject(@PathParam(CONTAINER_NAME) String containerName,
        @PathParam(OBJECT_NAME) String objectName) {
        return getObjectResponse(containerName, objectName);
    }

    /**
     * gets an objects from a container in the workspace
     *
     * @param containerName name of container
     * @param objectName    name of object
     * @return Response
     * @throws IOException when there is an error of get object
     */
    @Path("/containers/{containerName}/objects/{objectName:.*}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getObjectInformation(@PathParam(CONTAINER_NAME) String containerName,
        @PathParam(OBJECT_NAME) String objectName) throws IOException {
        JsonNode jsonResultNode;
        try {
            ParametersChecker.checkParameter(ErrorMessage.CONTAINER_NAME_IS_A_MANDATORY_PARAMETER.getMessage(),
                containerName, objectName);
            jsonResultNode = workspace.getObjectInformation(containerName, objectName);
        } catch (final IllegalArgumentException e) {
            LOGGER.error(e);
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (final ContentAddressableStorageNotFoundException e) {
            LOGGER.error(ErrorMessage.OBJECT_NOT_FOUND.getMessage() + containerName, e);
            return Response.status(Status.NOT_FOUND).entity(containerName).build();
        } catch (final ContentAddressableStorageException e) {
            LOGGER.error(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(containerName).build();
        }

        return Response.status(Status.OK).entity(jsonResultNode).build();
    }

    /**
     * checks if a object exists in an container or compute object Digest
     *
     * @param containerName name of container
     * @param objectName    name of object
     * @param algo          path parameter of algo
     * @return Response
     */
    @Path("/containers/{containerName}/objects/{objectName:.*}")
    @HEAD
    @Produces(MediaType.APPLICATION_JSON)
    public Response computeObjectDigest(@PathParam(CONTAINER_NAME) String containerName,
        @PathParam(OBJECT_NAME) String objectName, @HeaderParam(GlobalDataRest.X_DIGEST_ALGORITHM) String algo) {

        try {
            ParametersChecker.checkParameter(ErrorMessage.CONTAINER_OBJECT_NAMES_ARE_A_MANDATORY_PARAMETER.getMessage(),
                containerName, objectName);
        } catch (final IllegalArgumentException e) {
            LOGGER.error(e);
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        }

        if (algo != null) {
            LOGGER.debug(GlobalDataRest.X_DIGEST_ALGORITHM + " : " + algo);
            String messageDigest = null;
            try {
                messageDigest = workspace.computeObjectDigest(containerName, objectName, DigestType.fromValue(algo));
            } catch (final ContentAddressableStorageNotFoundException e) {
                LOGGER.error(ErrorMessage.OBJECT_NOT_FOUND.getMessage() + containerName, e);
                return Response.status(Status.NOT_FOUND)
                    .header(GlobalDataRest.X_DIGEST_ALGORITHM, algo)
                    .entity(containerName + "/" + objectName).build();
            } catch (final ContentAddressableStorageException e) {
                LOGGER.error(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
                return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .header(GlobalDataRest.X_DIGEST_ALGORITHM, algo)
                    .entity(containerName + "/" + objectName).build();
            }

            return Response.status(Status.OK)
                .header(GlobalDataRest.X_DIGEST_ALGORITHM, algo)
                .header(GlobalDataRest.X_DIGEST, messageDigest).build();
        } else {
            try {
                boolean exists = workspace.isExistingObject(containerName, objectName);

                if (exists) {
                    return Response.status(Status.OK).entity(containerName + "/" + objectName).build();
                } else {
                    return Response.status(Status.NOT_FOUND).build();
                }
            } catch (final Exception e) {
                LOGGER.error(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
                return Response.status(Status.INTERNAL_SERVER_ERROR).entity(containerName).build();
            }

        }
    }

    private Response getObjectResponse(String containerName, String objectName) {
        try {
            ParametersChecker.checkParameter(ErrorMessage.CONTAINER_NAME_IS_A_MANDATORY_PARAMETER.getMessage(),
                containerName, objectName);

            Response response = workspace.getObject(containerName, objectName);
            Map<String, String> headers = new HashMap<>();
            headers.put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM);
            headers.put(VitamHttpHeader.X_CONTENT_LENGTH.getName(),
                response.getHeaderString(VitamHttpHeader.X_CONTENT_LENGTH.getName()));
            return new VitamAsyncInputStreamResponse(response,
                Status.OK, headers);
        } catch (final IllegalArgumentException e) {
            LOGGER.error(e);
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (final ContentAddressableStorageNotFoundException e) {
            LOGGER.error(ErrorMessage.OBJECT_NOT_FOUND.getMessage() + containerName, e);
            return Response.status(Status.NOT_FOUND).entity(containerName).build();
        } catch (final ContentAddressableStorageException e) {
            LOGGER.error(ErrorMessage.INTERNAL_SERVER_ERROR.getMessage(), e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }

    }

}

