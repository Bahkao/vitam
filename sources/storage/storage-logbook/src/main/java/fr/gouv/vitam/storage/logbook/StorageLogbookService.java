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
package fr.gouv.vitam.storage.logbook;

import com.fasterxml.jackson.databind.JsonNode;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.storage.LogInformation;
import fr.gouv.vitam.storage.engine.common.exception.StorageException;
import fr.gouv.vitam.storage.logbook.parameters.StorageLogbookParameters;

import java.io.IOException;
import java.util.List;

/**
 * Storage Logbook interface. It describes methods to be implemented.
 */
public interface StorageLogbookService {

    /**
     * Add a storage logbook entry <br>
     * <br>
     *
     * @param parameters the entry parameters
     * @throws StorageException if an error is encountered
     */
    void append(Integer tenant, StorageLogbookParameters parameters) throws StorageException, IOException;

    /**
     * Not implemented yet
     */
    void close();

    /**
     * Select a list of operations for a specified object
     *
     * @param objectId the id of the object
     * @return List of operations for this object Id
     * @throws StorageException if any error is encountered
     */
    List<StorageLogbookParameters> selectOperationsbyObjectId(String objectId) throws StorageException;

    /**
     * Select a list of operations for a specified objectgroup
     *
     * @param objectGroupId the id of the object group
     * @return List of operations for this object Id
     * @throws StorageException if any error is encountered
     */
    List<StorageLogbookParameters> selectOperationsbyObjectGroupId(String objectGroupId) throws StorageException;

    /**
     * Select a list of operations for a specified request
     *
     * @param select the request in JsonNode format
     * @return a List of operations
     * @throws StorageException               if any error is encountered
     * @throws InvalidParseOperationException if the select request is not correct
     */
    List<StorageLogbookParameters> selectOperationsWithASelect(JsonNode select)
        throws StorageException, InvalidParseOperationException;

    /**
     * @param tenantId
     */
    LogInformation generateSecureStorage(Integer tenantId) throws IOException;

    void stopAppenderLoggerAndSecureLastLogs(Integer tenantId) throws IOException;

}
