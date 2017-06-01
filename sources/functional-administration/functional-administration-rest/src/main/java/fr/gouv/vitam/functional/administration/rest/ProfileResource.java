/*
 *  Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2019)
 *  <p>
 *  contact.vitam@culture.gouv.fr
 *  <p>
 *  This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 *  high volumetry securely and efficiently.
 *  <p>
 *  This software is governed by the CeCILL 2.1 license under French law and abiding by the rules of distribution of free
 *  software. You can use, modify and/ or redistribute the software under the terms of the CeCILL 2.1 license as
 *  circulated by CEA, CNRS and INRIA at the following URL "http://www.cecill.info".
 *  <p>
 *  As a counterpart to the access to the source code and rights to copy, modify and redistribute granted by the license,
 *  users are provided only with a limited warranty and the software's author, the holder of the economic rights, and the
 *  successive licensors have only limited liability.
 *  <p>
 *  In this respect, the user's attention is drawn to the risks associated with loading, using, modifying and/or
 *  developing or reproducing the software by the user in light of its specific status of free software, that may mean
 *  that it is complicated to manipulate, and that also therefore means that it is reserved for developers and
 *  experienced professionals having in-depth computer knowledge. Users are therefore encouraged to load and test the
 *  software's suitability as regards their requirements in conditions enabling the security of their systems and/or data
 *  to be ensured and, more generally, to use and operate it in the same conditions as regards security.
 *  <p>
 *  The fact that you are presently reading this means that you have had knowledge of the CeCILL 2.1 license and that you
 *  accept its terms.
 */
package fr.gouv.vitam.functional.administration.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.error.VitamError;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamException;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.parameter.ParameterHelper;
import fr.gouv.vitam.common.server.application.AsyncInputStreamHelper;
import fr.gouv.vitam.common.stream.StreamUtils;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.functional.administration.client.model.ProfileModel;
import fr.gouv.vitam.functional.administration.common.exception.ProfileNotFoundException;
import fr.gouv.vitam.functional.administration.common.exception.ReferentialException;
import fr.gouv.vitam.functional.administration.common.server.AdminManagementConfiguration;
import fr.gouv.vitam.functional.administration.common.server.MongoDbAccessAdminImpl;
import fr.gouv.vitam.functional.administration.counter.VitamCounterService;
import fr.gouv.vitam.functional.administration.profile.api.ProfileService;
import fr.gouv.vitam.functional.administration.profile.api.impl.ProfileServiceImpl;
import fr.gouv.vitam.workspace.client.WorkspaceClientFactory;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import java.io.InputStream;
import java.util.List;

/**
 * This resource manage profiles create, update, find, ...
 */
@Path("/adminmanagement/v1")
@ApplicationPath("webresources")
public class ProfileResource {


    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(ProfileResource.class);
    private static final String PROFILE_JSON_IS_MANDATORY_PATAMETER =
        "The json input of profile is mandatory";
    private static final String PROFILE_FILE_IS_MANDATORY_PATAMETER =
        "The profile file is mandatory";
    private static final String PROFILE_ID_IS_MANDATORY_PATAMETER =
        "The profile id is mandatory";
    public static final String PROFILE_URI = "/profiles";

    private final MongoDbAccessAdminImpl mongoAccess;
    private final WorkspaceClientFactory workspaceClientFactory;
    private final VitamCounterService vitamCounterService;

    /**
     * @param configuration
     * @param mongoAccess
     * @param vitamCounterService
     */
    public ProfileResource(AdminManagementConfiguration configuration, MongoDbAccessAdminImpl mongoAccess, VitamCounterService vitamCounterService) {
        this.mongoAccess = mongoAccess;
        this.vitamCounterService = vitamCounterService;
        this.workspaceClientFactory = WorkspaceClientFactory.getInstance();
        WorkspaceClientFactory.changeMode(configuration.getWorkspaceUrl());
        LOGGER.debug("init Admin Management Resource server");
    }


    @VisibleForTesting
    public ProfileResource(WorkspaceClientFactory workspaceClientFactory, MongoDbAccessAdminImpl mongoAccess, VitamCounterService vitamCounterService) {
        this.mongoAccess = mongoAccess;
        this.workspaceClientFactory = workspaceClientFactory;
        this.vitamCounterService = vitamCounterService;

        LOGGER.debug("init Admin Management Resource server");
    }




    /**
     * Import a set of profiles.
     * If all the profiles are valid, they will be stored in the collection and indexed.
     * </BR> The input is invalid in the following situations : </BR>
     * <ul>
     * <li>The json is invalid</li>
     * <li>The json contains 2 ore many profiles having the same identifier</li>
     * <li>One or more mandatory field is missing</li>
     * <li>A field has an invalid format</li>
     * <li>One or many profile already exist in the database</li>
     * </ul>
     *
     * @param profileModelList as InputStream
     * @param uri the uri info
     * @return Response jersey response
     */
    @Path(PROFILE_URI)
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createProfiles(List<ProfileModel> profileModelList, @Context UriInfo uri) {
        ParametersChecker.checkParameter(PROFILE_JSON_IS_MANDATORY_PATAMETER, profileModelList);

        try (ProfileService profileService = new ProfileServiceImpl(mongoAccess, workspaceClientFactory, vitamCounterService)) {
            RequestResponse requestResponse = profileService.createProfiles(profileModelList);

            if (!requestResponse.isOk()) {
                return Response.status(requestResponse.getHttpCode()).entity(requestResponse).build();
            } else {
                return Response.created(uri.getRequestUri().normalize()).entity(requestResponse).build();
            }


        } catch (VitamException exp) {
            LOGGER.error(exp);
            return Response.status(Status.BAD_REQUEST)
                .entity(getErrorEntity(Status.BAD_REQUEST, exp.getMessage(), null)).build();
        } catch (Exception exp) {
            LOGGER.error("Unexpected server error {}", exp);
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                .entity(getErrorEntity(Status.INTERNAL_SERVER_ERROR, exp.getMessage(), null)).build();
        }
    }


    /**
     * Import a Profile file document (xsd or rng, ...)
     *
     * @param uri used to construct the created resource and send it back as location in the response
     * @param profileMetadataId id of the profile metadata
     * @param profileFile inputStream representing the data to import
     * @return The jersey Response
     */
    @Path(PROFILE_URI + "/{id}")
    @PUT
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    public Response importProfileFile(@Context UriInfo uri, @PathParam("id") String profileMetadataId,
        InputStream profileFile) {
        ParametersChecker.checkParameter(PROFILE_FILE_IS_MANDATORY_PATAMETER, profileFile);
        ParametersChecker.checkParameter(PROFILE_ID_IS_MANDATORY_PATAMETER, profileMetadataId);

        try (ProfileService profileService = new ProfileServiceImpl(mongoAccess, workspaceClientFactory, vitamCounterService)) {
            RequestResponse requestResponse = profileService.importProfileFile(profileMetadataId, profileFile);

            if (!requestResponse.isOk()) {
                return Response.status(requestResponse.getHttpCode()).entity(requestResponse).build();
            } else {

                return Response.created(uri.getRequestUri().normalize()).entity(requestResponse).build();
            }


        } catch (VitamException exp) {
            LOGGER.error(exp);
            return Response.status(Status.BAD_REQUEST)
                .entity(getErrorEntity(Status.BAD_REQUEST, exp.getMessage(), null)).build();
        } catch (Exception exp) {
            LOGGER.error("Unexpected server error {}", exp);
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                .entity(getErrorEntity(Status.INTERNAL_SERVER_ERROR, exp.getMessage(), null)).build();
        } finally {
            StreamUtils.closeSilently(profileFile);
        }
    }

    @GET
    @Path(PROFILE_URI + "/{id}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public void downloadProfileFile(@PathParam("id") String profileMetadataId,
        @Suspended final AsyncResponse asyncResponse) {

        ParametersChecker.checkParameter("Profile id should be filled", profileMetadataId);

        Integer tenantId = ParameterHelper.getTenantParameter();
        VitamThreadUtils.getVitamSession().setRequestId(GUIDFactory.newRequestIdGUID(tenantId));

        VitamThreadPoolExecutor.getDefaultExecutor()
            .execute(() -> {
                try (ProfileService profileService = new ProfileServiceImpl(mongoAccess, workspaceClientFactory, vitamCounterService)) {

                    profileService.downloadProfileFile(profileMetadataId, asyncResponse);

                }  catch (final ProfileNotFoundException exc) {
                    LOGGER.error(exc.getMessage(), exc);
                    AsyncInputStreamHelper.asyncResponseResume(asyncResponse,
                        Response.status(Status.NOT_FOUND)
                            .entity(getErrorEntity(Status.NOT_FOUND, exc.getMessage(), null).toString()
                            ).build());

                }  catch (final ReferentialException | InvalidParseOperationException exc) {
                    LOGGER.error(exc.getMessage(), exc);
                    AsyncInputStreamHelper.asyncResponseResume(asyncResponse,
                        Response.status(Status.INTERNAL_SERVER_ERROR)
                            .entity(getErrorEntity(Status.INTERNAL_SERVER_ERROR, exc.getMessage(), null).toString()
                            ).build());
                }
            });
    }


    /**
     * Find profiles by queryDsl
     *
     * @param queryDsl
     * @return
     */
    @GET
    @Path(PROFILE_URI)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response findProfiles(JsonNode queryDsl) {

        try (ProfileService profileService = new ProfileServiceImpl(mongoAccess, workspaceClientFactory, vitamCounterService)) {

            final List<ProfileModel> profileModelList = profileService.findProfiles(queryDsl);

            return Response.status(Status.OK)
                .entity(
                    new RequestResponseOK().addAllResults(profileModelList))
                .build();

        } catch (ReferentialException e) {
            LOGGER.error(e);
            return Response.status(Status.BAD_REQUEST)
                .entity(getErrorEntity(Status.BAD_REQUEST, e.getMessage(), null)).build();
        } catch (final InvalidParseOperationException e) {
            LOGGER.error(e);
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                .entity(getErrorEntity(Status.INTERNAL_SERVER_ERROR, e.getMessage(), null)).build();
        }
    }
    /**
     * Construct the error following input
     *
     * @param status Http error status
     * @param message The functional error message, if absent the http reason phrase will be used instead
     * @param code The functional error code, if absent the http code will be used instead
     * @return
     */
    private VitamError getErrorEntity(Status status, String message, String code) {
        String aMessage =
            (message != null && !message.trim().isEmpty()) ? message
                : (status.getReasonPhrase() != null ? status.getReasonPhrase() : status.name());
        String aCode = (code != null) ? code : String.valueOf(status.getStatusCode());
        return new VitamError(aCode).setHttpCode(status.getStatusCode())
            .setContext("FUNCTIONAL_ADMINISTRATION_MODULE")
            .setState("ko").setMessage(status.getReasonPhrase()).setDescription(aMessage);
    }


}
