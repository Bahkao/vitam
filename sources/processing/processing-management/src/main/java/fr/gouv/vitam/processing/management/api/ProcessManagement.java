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
package fr.gouv.vitam.processing.management.api;


import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.VitamAutoCloseable;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.exception.WorkflowNotFoundException;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;

/**
 * ProcessManagement interface
 *
 * This service will be invoked by Ingest Module
 *
 */
public interface ProcessManagement extends VitamAutoCloseable {
    /**
     * execute Vitam process by workflow id
     *
     * @param workParams null not allowed
     * @param workflowId null not allowed
     * @return Response :global process response such as OK, KO, FATAL,WARNING
     * @throws WorkflowNotFoundException thrown if the workflow was not found
     * @throws ProcessingException thrown in case of a technical exception in the execution
     * @throws IllegalArgumentException thrown in case parameters workParams or workflowId are null
     * 
     */
    ItemStatus submitWorkflow(WorkerParameters workParams, String workflowId) throws ProcessingException;
}
