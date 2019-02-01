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
package fr.gouv.vitam.logbook.common.server.reconstruction;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.mongodb.util.JSON;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.error.VitamError;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamRuntimeException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.model.logbook.LogbookEventOperation;
import fr.gouv.vitam.logbook.common.server.database.collections.LogbookOperation;
import fr.gouv.vitam.storage.engine.client.StorageClient;
import fr.gouv.vitam.storage.engine.client.StorageClientFactory;
import fr.gouv.vitam.storage.engine.client.exception.StorageServerClientException;
import fr.gouv.vitam.storage.engine.common.exception.StorageNotFoundException;
import fr.gouv.vitam.storage.engine.common.model.DataCategory;
import fr.gouv.vitam.storage.engine.common.model.OfferLog;
import fr.gouv.vitam.storage.engine.common.model.Order;

/**
 * Service used to recover a Backup copy of logbook operation Vitam collection.<br/>
 */

public class RestoreBackupService {

    /**
     * Vitam Logger.
     */
    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(RestoreBackupService.class);

    private StorageClientFactory storageClientFactory;

    /**
     * Constructor
     */
    public RestoreBackupService() {
        this(StorageClientFactory.getInstance());
    }

    /**
     * Constructor for tests
     * 
     * @param storageClientFactory storage client factory
     */
    @VisibleForTesting
    public RestoreBackupService(StorageClientFactory storageClientFactory) {
        this.storageClientFactory = storageClientFactory;
    }

    /**
     * Retrieve list of offer log defining objects to reconstruct from offer log
     * 
     * @param strategy storage strategy
     * @param offset offset
     * @param limit limit
     * @return list of offer log by bulk
     * @throws VitamRuntimeException storage error
     * @throws IllegalArgumentException input error
     */
    public List<List<OfferLog>> getListing(String strategy, long offset, int limit) {
        LOGGER.info(String.format(
            "[Reconstruction]: Retrieve listing of {%s} Collection on {%s} Vitam strategy from {%s} offset with {%s} limit",
            DataCategory.BACKUP_OPERATION.name(), strategy, offset, limit));
        try (StorageClient storageClient = storageClientFactory.getClient()) {
            DataCategory type = DataCategory.BACKUP_OPERATION;

            RequestResponse<OfferLog> result = storageClient.getOfferLogs(strategy, type, offset, limit, Order.ASC);
            if (result.isOk()) {
                if (!((RequestResponseOK<OfferLog>) result).getResults().isEmpty()) {
                    List<OfferLog> results = ((RequestResponseOK<OfferLog>) result).getResults();
                    return Lists.partition(results, VitamConfiguration.getRestoreBulkSize());
                }
            } else {
                throw new VitamRuntimeException(
                    String.format("ERROR: VitamError has been returned when using storage service: {%s}",
                        ((VitamError) result).toString()));
            }
        } catch (StorageServerClientException e) {
            throw new VitamRuntimeException("ERROR: Exception has been thrown when using storage service:", e);
        }
        return new ArrayList<>();
    }


    /**
     * Load data from storage
     * 
     * @param strategy storage strategy
     * @param filename name of file to load
     * @param offset offset
     * @return data
     * @throws VitamRuntimeException storage error
     * @throws IllegalArgumentException input error
     */
    public LogbookBackupModel loadData(String strategy, String filename,
        long offset) {
        LOGGER
            .info(String.format(
                "[Reconstruction]: Retrieve file {%s} from storage of {%s} Collection on {%s} Vitam strategy",
                filename, DataCategory.BACKUP_OPERATION.name(), strategy));
        InputStream inputStream = null;
        try (StorageClient storageClient = storageClientFactory.getClient()) {
            DataCategory type = DataCategory.BACKUP_OPERATION;
            Response response = storageClient.getContainerAsync(strategy, filename, type);
            if (response != null && response.getStatus() == Response.Status.OK.getStatusCode()) {
                inputStream = storageClient.getContainerAsync(strategy, filename, type).readEntity(InputStream.class);
                LogbookOperation logbookOperationDocument =
                    new LogbookOperation(JsonHandler.getFromInputStream(inputStream, JsonNode.class));
                LogbookBackupModel logbookBackupModel = new LogbookBackupModel();
                logbookBackupModel.setLogbookOperation(logbookOperationDocument);
                logbookBackupModel.setLogbookId(logbookOperationDocument.getId());
                logbookBackupModel.setOffset(offset);
                populateAccessionRegisterDetails(logbookBackupModel, logbookOperationDocument);
                return logbookBackupModel;
            }
        } catch (StorageServerClientException | StorageNotFoundException | InvalidParseOperationException e) {
            throw new VitamRuntimeException("ERROR: Exception has been thrown when using storage service:", e);
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (final IOException ioe) {
                LOGGER.error(ioe);
            }
        }

        return null;
    }

    private void populateAccessionRegisterDetails(LogbookBackupModel logbookBackupModel,
        final LogbookOperation logbookOperationDocument)
        throws InvalidParseOperationException {
        fr.gouv.vitam.common.model.logbook.LogbookOperation logbookOperation =
            JsonHandler.getFromString(JSON.serialize(logbookOperationDocument),
                fr.gouv.vitam.common.model.logbook.LogbookOperation.class);
        Optional<LogbookEventOperation> accessionRegisterValidEvent = logbookOperation.getEvents().stream()
            .filter(event -> "INGEST".equals(event.getEvTypeProc()))
            .filter(event -> "ACCESSION_REGISTRATION".equals(event.getEvType()))
            .filter(event -> StatusCode.OK.name().equals(event.getOutcome()) ||
                StatusCode.WARNING.name().equals(event.getOutcome()))
            .filter(event -> event.getEvDetData() != null && event.getEvDetData().contains("Volumetry"))
            .findFirst();

        if (accessionRegisterValidEvent.isPresent()) {
            JsonNode accessionRegisters =
                JsonHandler.getFromString(accessionRegisterValidEvent.get().getEvDetData(), JsonNode.class);
            if (accessionRegisters.get("Volumetry") != null && accessionRegisters.get("Volumetry").isArray()) {
                ArrayNode volumetry = (ArrayNode) accessionRegisters.get("Volumetry");
                for (JsonNode item : volumetry) {
                    logbookBackupModel.getAccessionRegisters().add(item);
                }
            } else {
                throw new InvalidParseOperationException("ERROR: Accession register detail invalid in operation event");
            }
        } else {
            LOGGER.debug("[Reconstruction]: Accession register detail not found in operation");
        }


    }

}
