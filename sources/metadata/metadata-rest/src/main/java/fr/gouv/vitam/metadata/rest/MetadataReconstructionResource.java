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
package fr.gouv.vitam.metadata.rest;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.common.annotations.VisibleForTesting;

import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.database.offset.OffsetRepository;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.AuthenticationLevel;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.security.rest.VitamAuthentication;
import fr.gouv.vitam.metadata.core.database.collections.VitamRepositoryProvider;
import fr.gouv.vitam.metadata.core.model.ReconstructionRequestItem;
import fr.gouv.vitam.metadata.core.model.ReconstructionResponseItem;
import fr.gouv.vitam.metadata.core.reconstruction.ReconstructionService;

/**
 * Metadata reconstruction resource.
 */
@Path("/metadata/v1")
public class MetadataReconstructionResource {

    /**
     * Vitam Logger.
     */
    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(MetadataReconstructionResource.class);

    private final String RECONSTRUCTION_URI = "/reconstruction";

    /**
     * Error/Exceptions messages.
     */
    private static final String RECONSTRUCTION_JSON_MONDATORY_PARAMETERS_MSG =
        "the Json input of reconstruction's parameters is mondatory.";
    private static final String RECONSTRUCTION_EXCEPTION_MSG =
        "ERROR: Exception has been thrown when reconstructing Vitam collections: ";

    /**
     * Reconstruction service.
     */
    private ReconstructionService reconstructionService;

    /**
     * Constructor
     *
     * @param vitamRepositoryProvider vitamRepositoryProvider
     * @param offsetRepository
     */
    public MetadataReconstructionResource(VitamRepositoryProvider vitamRepositoryProvider,
        OffsetRepository offsetRepository) {
        this.reconstructionService = new ReconstructionService(vitamRepositoryProvider, offsetRepository);
    }

    /**
     * Constructor for tests
     * 
     * @param reconstructionService reconstructionService
     */
    @VisibleForTesting
    public MetadataReconstructionResource(ReconstructionService reconstructionService) {
        this.reconstructionService = reconstructionService;
    }

    /**
     * API to access and launch the Vitam reconstruction service for metadatas.<br/>
     *
     * @param reconstructionItems list of reconstruction request items
     * @return the response
     */
    @Path(RECONSTRUCTION_URI)
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @VitamAuthentication(authentLevel = AuthenticationLevel.BASIC_AUTHENT)
    public Response reconstructCollection(List<ReconstructionRequestItem> reconstructionItems) {
        ParametersChecker.checkParameter(RECONSTRUCTION_JSON_MONDATORY_PARAMETERS_MSG, reconstructionItems);

        List<ReconstructionResponseItem> responses = new ArrayList<>();
        if (!reconstructionItems.isEmpty()) {
            LOGGER.debug(String
                .format("Starting reconstruction Vitam service with the json parameters : (%s)", reconstructionItems));

            reconstructionItems.forEach(item -> {
                LOGGER.debug(String.format(
                    "Starting reconstruction for the collection {%s} on the tenant (%s) with (%s) elements",
                    item.getCollection(), item.getTenant(), item.getLimit()));
                try {
                    responses.add(reconstructionService.reconstruct(item));
                } catch (IllegalArgumentException e) {
                    LOGGER.error(RECONSTRUCTION_EXCEPTION_MSG, e);
                    responses.add(new ReconstructionResponseItem(item, StatusCode.KO));
                }
            });
        }

        return Response.ok().entity(responses).build();
    }
}
