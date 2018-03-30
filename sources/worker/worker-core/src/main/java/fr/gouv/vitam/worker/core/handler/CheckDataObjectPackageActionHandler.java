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

import java.util.ArrayList;
import java.util.List;

import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.model.UnitType;
import fr.gouv.vitam.common.model.processing.IOParameter;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.worker.common.HandlerIO;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageServerException;

/**
 * Check HEADER Handler
 */
public class CheckDataObjectPackageActionHandler extends ActionHandler {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(CheckDataObjectPackageActionHandler.class);
    private static final String HANDLER_ID = "CHECK_DATAOBJECTPACKAGE";
    private static final int CHECK_NO_OBJECT_INPUT_RANK = 0;

    /**
     * Empty Constructor
     *
     */
    public CheckDataObjectPackageActionHandler() {
        // empty constructor
    }

    /**
     * @return HANDLER_ID
     */
    public static String getId() {
        return HANDLER_ID;
    }

    @Override
    public ItemStatus execute(WorkerParameters params, HandlerIO handlerIO)
        throws ContentAddressableStorageServerException {
        final ItemStatus itemStatus = new ItemStatus(HANDLER_ID);
        try {
            if (Boolean.valueOf((String) handlerIO.getInput(CHECK_NO_OBJECT_INPUT_RANK))) {
                try (CheckNoObjectsActionHandler checkNoObjectsActionHandler = new CheckNoObjectsActionHandler();
                    CheckObjectsNumberActionHandler checkObjectsNumberActionHandler =
                        new CheckObjectsNumberActionHandler();
                    ExtractSedaActionHandler extractSedaActionHandler = new ExtractSedaActionHandler()) {
                    ItemStatus checkNoObjectStatus = checkNoObjectsActionHandler.execute(params, handlerIO);
                    itemStatus.setItemsStatus(CheckNoObjectsActionHandler.getId(), checkNoObjectStatus);

                    ItemStatus checkObjectNumberStatus = checkObjectsNumberActionHandler.execute(params, handlerIO);
                    itemStatus.setItemsStatus(CheckObjectsNumberActionHandler.getId(), checkObjectNumberStatus);

                    ItemStatus extractSedaStatus = extractSedaActionHandler.execute(params, handlerIO);
                    itemStatus.setItemsStatus(ExtractSedaActionHandler.getId(), extractSedaStatus);

                    if (extractSedaStatus.shallStop(true)) {
                        resetItemStatusMeter(itemStatus);
                        return new ItemStatus(HANDLER_ID).setItemsStatus(HANDLER_ID, itemStatus);
                    }
                }

            } else {
                try (CheckVersionActionHandler checkVersionActionHandler = new CheckVersionActionHandler();
                    CheckObjectsNumberActionHandler checkObjectsNumberActionHandler = new CheckObjectsNumberActionHandler();
                    ExtractSedaActionHandler extractSedaActionHandler = new ExtractSedaActionHandler();
                    CheckObjectUnitConsistencyActionHandler checkObjectUnitConsistencyActionHandler =
                        new CheckObjectUnitConsistencyActionHandler();) {
                    ItemStatus checkVersionStatus = checkVersionActionHandler.execute(params, handlerIO);
                    itemStatus.setItemsStatus(CheckVersionActionHandler.getId(), checkVersionStatus);

                    if (checkVersionStatus.shallStop(true)) {
                        resetItemStatusMeter(itemStatus);
                        return new ItemStatus(HANDLER_ID).setItemsStatus(HANDLER_ID, itemStatus);
                    }
                    ItemStatus checkObjectNumberStatus = checkObjectsNumberActionHandler.execute(params, handlerIO);
                    itemStatus.setItemsStatus(CheckObjectsNumberActionHandler.getId(), checkObjectNumberStatus);

                    ItemStatus extractSedaStatus = extractSedaActionHandler.execute(params, handlerIO);
                    itemStatus.setItemsStatus(ExtractSedaActionHandler.getId(), extractSedaStatus);

                    if (extractSedaStatus.shallStop(true)) {
                        resetItemStatusMeter(itemStatus);
                        return new ItemStatus(HANDLER_ID).setItemsStatus(HANDLER_ID, itemStatus);
                    }

                    handlerIO.getInput().clear();
                    List<IOParameter> inputList = new ArrayList<>();
                    inputList.add(
                        new IOParameter()
                            .setUri(handlerIO.getOutput(ExtractSedaActionHandler.OG_ID_TO_UNID_ID_IO_RANK)));
                    inputList.add(new IOParameter()
                        .setUri(handlerIO.getOutput(ExtractSedaActionHandler.OG_ID_TO_GUID_IO_MEMORY_RANK)));
                    handlerIO.addInIOParameters(inputList);
                    handlerIO.getOutput().clear();
                    ItemStatus checkObjectUnitConsistencyStatus =
                        checkObjectUnitConsistencyActionHandler.execute(params, handlerIO);
                    itemStatus.setItemsStatus(CheckObjectUnitConsistencyActionHandler.getId(),
                        checkObjectUnitConsistencyStatus);


                }


            }


        } catch (ProcessingException e) {
            LOGGER.error(e);
            itemStatus.increment(StatusCode.FATAL);
        }


        resetItemStatusMeter(itemStatus);

        return new ItemStatus(HANDLER_ID).setItemsStatus(HANDLER_ID, itemStatus);
    }

    @Override
    public void checkMandatoryIOParameter(HandlerIO handler) throws ProcessingException {

    }

    /**
     * Reset the statusMeter of the specified itemStatus
     * @param itemStatus
     */
    private void resetItemStatusMeter(ItemStatus itemStatus){
        itemStatus.reinitStatusMeter();
        //counter for DATAOBJECTPACKAGE is always 1
        itemStatus.setStatusMeterValue(itemStatus.getGlobalStatus(),1);

    }

}
