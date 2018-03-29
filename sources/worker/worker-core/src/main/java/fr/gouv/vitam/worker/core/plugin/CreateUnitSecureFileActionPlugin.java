/**
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
 */
package fr.gouv.vitam.worker.core.plugin;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import fr.gouv.vitam.common.StringUtils;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.model.UpdateWorkflowConstants;
import fr.gouv.vitam.common.model.VitamAutoCloseable;
import fr.gouv.vitam.logbook.common.server.database.collections.LogbookLifeCycleUnit;
import fr.gouv.vitam.metadata.client.MetaDataClientFactory;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.storage.engine.client.StorageClientFactory;
import fr.gouv.vitam.worker.common.HandlerIO;

/**
 * CreateUnitSecureFileAction Plugin.
 *
 */
public class CreateUnitSecureFileActionPlugin extends CreateSecureFileActionPlugin implements VitamAutoCloseable {
    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(CreateUnitSecureFileActionPlugin.class);
    private static final String UNITS_CREATE_SECURED_FILE = "UNITS_CREATE_SECURED_FILE";
    private HandlerIO handlerIO;

    /**
     * Empty constructor
     */
    public CreateUnitSecureFileActionPlugin() {}

    @VisibleForTesting
    public CreateUnitSecureFileActionPlugin(MetaDataClientFactory metaDataClientFactory,StorageClientFactory storageClientFactory) {
        super(metaDataClientFactory,storageClientFactory);
    }
    @Override
    public ItemStatus execute(WorkerParameters params, HandlerIO handler) {
        handlerIO = handler;
        final ItemStatus itemStatus = new ItemStatus(UNITS_CREATE_SECURED_FILE);
        final String guid = StringUtils.substringBeforeLast(params.getObjectName(), ".");
        try {
            final ObjectNode unitJson = (ObjectNode) handlerIO
                .getJsonFromWorkspace(UpdateWorkflowConstants.UNITS_FOLDER + "/" + params.getObjectName());
            storeLifecycle(unitJson, guid, handler, LogbookLifeCycleUnit.class.getName());
            itemStatus.increment(StatusCode.OK);
        } catch (ProcessingException e) {
            LOGGER.error("Processing exception", e);
            itemStatus.increment(StatusCode.FATAL);
        }
        return new ItemStatus(UNITS_CREATE_SECURED_FILE).setItemsStatus(UNITS_CREATE_SECURED_FILE,
            itemStatus);
    }

    @Override
    public void checkMandatoryIOParameter(HandlerIO handler) throws ProcessingException {
        // Nothing to do
    }
}
