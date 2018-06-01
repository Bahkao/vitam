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
package fr.gouv.vitam.worker.core.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import fr.gouv.vitam.common.database.builder.request.exception.InvalidCreateOperationException;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamFatalRuntimeException;
import fr.gouv.vitam.common.exception.VitamKoRuntimeException;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.IngestWorkflowConstants;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.logbook.common.exception.LogbookClientException;
import fr.gouv.vitam.logbook.common.parameters.Contexts;
import fr.gouv.vitam.logbook.lifecycles.client.LogbookLifeCyclesClient;
import fr.gouv.vitam.logbook.lifecycles.client.LogbookLifeCyclesClientFactory;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClientFactory;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.parameter.WorkerParameterName;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.worker.common.HandlerIO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Plugin for object group lifecycle traceability initialization
 */
public class ListObjectGroupLifecycleTraceabilityActionHandler extends ListLifecycleTraceabilityActionHandler {

    private static final VitamLogger LOGGER =
        VitamLoggerFactory.getInstance(ListObjectGroupLifecycleTraceabilityActionHandler.class);
    private static final String HANDLER_ID = "PREPARE_OG_LFC_TRACEABILITY";

    /**
     * Empty constructor
     */
    public ListObjectGroupLifecycleTraceabilityActionHandler() {
    }

    /**
     * Constructor for testing
     */
    @VisibleForTesting
    ListObjectGroupLifecycleTraceabilityActionHandler(
        LogbookLifeCyclesClientFactory logbookLifeCyclesClientFactory,
        LogbookOperationsClientFactory logbookOperationsClientFactory, int batchSize) {
        super(logbookLifeCyclesClientFactory, logbookOperationsClientFactory, batchSize);
    }

    @Override
    public ItemStatus execute(WorkerParameters params, HandlerIO handler) {

        String temporizationDelayInSecondsStr =
            params.getMapParameters().get(WorkerParameterName.lifecycleTraceabilityTemporizationDelayInSeconds);
        String lifecycleTraceabilityMaxEntriesStr =
            params.getMapParameters().get(WorkerParameterName.lifecycleTraceabilityMaxEntries);
        int temporizationDelayInSeconds =
            Integer.parseInt(temporizationDelayInSecondsStr);
        int lifecycleTraceabilityMaxEntries =
            Integer.parseInt(lifecycleTraceabilityMaxEntriesStr);

        final ItemStatus itemStatus = new ItemStatus(HANDLER_ID);
        try {
            selectAndExportLifecycles(temporizationDelayInSeconds, lifecycleTraceabilityMaxEntries,
                IngestWorkflowConstants.OBJECT_GROUP_FOLDER, Contexts.OBJECTGROUP_LFC_TRACEABILITY.getEventType(),
                handler);
            itemStatus.increment(StatusCode.OK);
        } catch (LogbookClientException | VitamFatalRuntimeException e) {
            LOGGER.error("Logbook exception", e);
            itemStatus.increment(StatusCode.FATAL);
        } catch (ProcessingException | InvalidParseOperationException | InvalidCreateOperationException
            | VitamKoRuntimeException e) {
            LOGGER.error("Processing exception", e);
            itemStatus.increment(StatusCode.KO);
        }
        return new ItemStatus(HANDLER_ID).setItemsStatus(HANDLER_ID,
            itemStatus);
    }

    @Override
    protected List<JsonNode> getRawLifecyclesByLastPersistedDate(LocalDateTime startDate, LocalDateTime endDate,
        LogbookLifeCyclesClient logbookLifeCyclesClient, int limit)
        throws LogbookClientException, InvalidParseOperationException {
        return logbookLifeCyclesClient
            .getRawObjectGroupLifecyclesByLastPersistedDate(startDate, endDate, limit);
    }

    /**
     * @return HANDLER_ID
     */
    public static String getId() {
        return HANDLER_ID;
    }

    @Override
    public void checkMandatoryIOParameter(HandlerIO handler) throws ProcessingException {
        // Nothing to check
    }
}
