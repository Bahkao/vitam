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
package fr.gouv.vitam.processing.engine.api;


import fr.gouv.vitam.processing.common.automation.IEventsProcessEngine;
import fr.gouv.vitam.processing.common.exception.ProcessingEngineException;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.model.PauseRecover;
import fr.gouv.vitam.processing.common.model.ProcessStep;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;

import java.util.Map;

/**
 * Process Engine Interface Provides access to all the services and to manage a workflow of operations.
 */

public interface ProcessEngine {


    /**
     * Set the state machine where the ProcessEngine return response on complete or on error
     * @param callback
     */
    void setCallback(IEventsProcessEngine callback);

    /**
     * Start the execution of the given step
     *
     * @param step
     * @param workerParameters
     * @param pauseRecover
     * @throws ProcessingException
     */
    void start(ProcessStep step, WorkerParameters workerParameters, Map<String, String> params, PauseRecover pauseRecover)
        throws ProcessingEngineException;

    /**
     * Pause the execution of the current step
     * Send message to the distributor to cancel the execution of the current step
     * @param operationId
     * @return true if pause applied, false else
     */
    boolean pause(String operationId);

    /**
     * Cancel the execution of the current step
     * Send message to the distributor to cancel the execution of the current step
     * @param operationId
     * @return true if pause applied, false else
     */
    boolean cancel(String operationId);
}
