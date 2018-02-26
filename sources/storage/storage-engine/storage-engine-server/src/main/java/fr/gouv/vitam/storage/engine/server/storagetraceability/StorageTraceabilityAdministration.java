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
package fr.gouv.vitam.storage.engine.server.storagetraceability;

import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;

import fr.gouv.vitam.common.guid.GUID;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.parameter.ParameterHelper;
import fr.gouv.vitam.common.timestamp.TimestampGenerator;
import fr.gouv.vitam.logbook.common.exception.LogbookClientAlreadyExistsException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientBadRequestException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientServerException;
import fr.gouv.vitam.logbook.common.exception.TraceabilityException;
import fr.gouv.vitam.logbook.common.traceability.LogbookTraceabilityHelper;
import fr.gouv.vitam.logbook.common.traceability.TraceabilityService;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClient;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClientFactory;
import fr.gouv.vitam.storage.engine.server.storagelog.StorageLogException;
import fr.gouv.vitam.workspace.client.WorkspaceClient;
import fr.gouv.vitam.workspace.client.WorkspaceClientFactory;

public class StorageTraceabilityAdministration {

    public static final String STORAGE_LOGBOOK_OPERATION_ZIP = "StorageLogbookOperation";
    private final TraceabilityLogbookService traceabilityLogbookService;
    private final LogbookOperationsClient logbookOperations;
    private final WorkspaceClient workspaceClient;
    private final TimestampGenerator timestampGenerator;
    private final int operationTraceabilityOverlapDelayInSeconds;
    private final File tmpFolder;
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HH-mm-ss");

    public StorageTraceabilityAdministration(TraceabilityLogbookService traceabilityLogbookService,
        String tmpFolder, TimestampGenerator timestampGenerator, Integer operationTraceabilityOverlapDelay) {
        this.traceabilityLogbookService = traceabilityLogbookService;
        this.timestampGenerator = timestampGenerator;
        this.workspaceClient = WorkspaceClientFactory.getInstance().getClient();
        this.logbookOperations = LogbookOperationsClientFactory.getInstance().getClient();
        this.operationTraceabilityOverlapDelayInSeconds =
            validateAndGetTraceabilityOverlapDelay(operationTraceabilityOverlapDelay);
        this.tmpFolder = new File(tmpFolder);
        this.tmpFolder.mkdir();
    }
    
    public StorageTraceabilityAdministration(TraceabilityLogbookService traceabilityLogbookService,
        LogbookOperationsClient mockedLogbookOperations, File mockedFile, WorkspaceClient mockedWorkspaceClient,
        TimestampGenerator timestampGenerator, Integer operationTraceabilityOverlapDelay) {
        this.traceabilityLogbookService = traceabilityLogbookService;
        this.logbookOperations = mockedLogbookOperations;
        this.timestampGenerator = timestampGenerator;
        this.workspaceClient = mockedWorkspaceClient;
        this.operationTraceabilityOverlapDelayInSeconds =
            validateAndGetTraceabilityOverlapDelay(operationTraceabilityOverlapDelay);
        this.tmpFolder = mockedFile;
        
    }

    private int validateAndGetTraceabilityOverlapDelay(Integer operationTraceabilityOverlapDelay) {
        if (operationTraceabilityOverlapDelay == null) {
            return 0;
       }
       if (operationTraceabilityOverlapDelay < 0) {
           throw new IllegalArgumentException("Operation traceability overlap delay cannot be negative");
       }
       return operationTraceabilityOverlapDelay;
    }

    public GUID generateTraceabilityStorageLogbook()
        throws TraceabilityException, IOException, StorageLogException,
        LogbookClientBadRequestException, LogbookClientAlreadyExistsException, LogbookClientServerException {

        Integer tenantId = ParameterHelper.getTenantParameter();
        final GUID eip = GUIDFactory.newOperationLogbookGUID(tenantId);
        
        LogbookTraceabilityHelper traceabilityHelper =
            new LogbookStorageTraceabilityHelper(logbookOperations, workspaceClient, traceabilityLogbookService,
                eip, operationTraceabilityOverlapDelayInSeconds);

        TraceabilityService service = new TraceabilityService(timestampGenerator, traceabilityHelper, tenantId, tmpFolder);

        service.secureData();

        return eip;
    }

}


