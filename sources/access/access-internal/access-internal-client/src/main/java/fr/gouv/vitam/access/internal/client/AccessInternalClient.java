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
package fr.gouv.vitam.access.internal.client;

import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;

import fr.gouv.vitam.access.internal.common.exception.AccessInternalClientNotFoundException;
import fr.gouv.vitam.access.internal.common.exception.AccessInternalClientServerException;
import fr.gouv.vitam.access.internal.common.exception.AccessInternalRuleExecutionException;
import fr.gouv.vitam.common.client.MockOrRestClient;
import fr.gouv.vitam.common.exception.AccessUnauthorizedException;
import fr.gouv.vitam.common.exception.BadRequestException;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.NoWritingPermissionException;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.logbook.common.exception.LogbookClientException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientServerException;

/**
 * Access client interface
 */
public interface AccessInternalClient extends MockOrRestClient {

    /**
     * Select Units
     *
     * @param selectQuery the query used to select units
     * @return a response containing a json node object including DSL queries and results
     * @throws InvalidParseOperationException        if the query is not well formatted
     * @throws AccessInternalClientServerException   if the server encountered an exception
     * @throws AccessInternalClientNotFoundException if the requested unit does not exist
     * @throws AccessUnauthorizedException
     * @throws BadRequestException                   if empty query is found
     */
    RequestResponse<JsonNode> selectUnits(JsonNode selectQuery)
        throws InvalidParseOperationException, AccessInternalClientServerException,
        AccessInternalClientNotFoundException, AccessUnauthorizedException, BadRequestException;

    /**
     * select Unit By Id
     *
     * @param sqlQuery the query to be executed
     * @param id       the id of the unit
     * @return a response containing a json node object including DSL queries, context and results
     * @throws InvalidParseOperationException        if the query is not well formatted
     * @throws AccessInternalClientServerException   if the server encountered an exception
     * @throws AccessInternalClientNotFoundException if the requested unit does not exist
     * @throws AccessUnauthorizedException
     */
    RequestResponse<JsonNode> selectUnitbyId(JsonNode sqlQuery, String id)
        throws InvalidParseOperationException, AccessInternalClientServerException,
        AccessInternalClientNotFoundException, AccessUnauthorizedException;

    /**
     * update Unit By Id
     *
     * @param updateQuery the query to be executed as an update
     * @param unitId      the id of the unit
     * @return a response containing a json node object including DSL queries, context and results
     * @throws InvalidParseOperationException        if the query is not well formatted
     * @throws AccessInternalClientServerException   if the server encountered an exception
     * @throws AccessInternalClientNotFoundException if the requested unit does not exist
     * @throws AccessUnauthorizedException
     * @throws AccessInternalRuleExecutionException
     */
    RequestResponse<JsonNode> updateUnitbyId(JsonNode updateQuery, String unitId)
        throws InvalidParseOperationException, AccessInternalClientServerException,
        AccessInternalClientNotFoundException, NoWritingPermissionException, AccessUnauthorizedException;

    /**
     * Retrieve an ObjectGroup as Json data based on the provided ObjectGroup id
     *
     * @param selectObjectQuery the query to be executed
     * @param objectId          the Id of the ObjectGroup
     * @return a response containing a json node object including DSL queries, context and results
     * @throws InvalidParseOperationException        if the query is not well formatted
     * @throws AccessInternalClientServerException   if the server encountered an exception
     * @throws AccessInternalClientNotFoundException if the requested object does not exist
     * @throws AccessUnauthorizedException
     */
    RequestResponse<JsonNode> selectObjectbyId(JsonNode selectObjectQuery, String objectId)
        throws InvalidParseOperationException, AccessInternalClientServerException,
        AccessInternalClientNotFoundException, AccessUnauthorizedException;

    /**
     * Retrieve an Object data as an input stream
     *
     * @param selectObjectQuery the query to be executed
     * @param objectGroupId     the Id of the ObjectGroup
     * @param usage             the requested usage
     * @param version           the requested version of the usage
     * @return Response containing InputStream for the object data
     * @throws InvalidParseOperationException        if the query is not well formatted
     * @throws AccessInternalClientServerException   if the server encountered an exception
     * @throws AccessInternalClientNotFoundException if the requested object does not exist
     * @throws AccessUnauthorizedException
     */
    Response getObject(JsonNode selectObjectQuery, String objectGroupId, String usage, int version)
        throws InvalidParseOperationException, AccessInternalClientServerException,
        AccessInternalClientNotFoundException, AccessUnauthorizedException;


    /**
     * selectOperation
     *
     * @param select
     * @return a response containing a json node
     * @throws LogbookClientException
     * @throws InvalidParseOperationException
     * @throws AccessUnauthorizedException
     */
    RequestResponse<JsonNode> selectOperation(JsonNode select)
        throws LogbookClientException, InvalidParseOperationException, AccessUnauthorizedException;

    /**
     * selectOperationbyId
     *
     * @param processId ID of the operation
     * @param queryDsl  query to be executed
     * @return a response containing a json node
     * @throws LogbookClientException
     * @throws InvalidParseOperationException
     * @throws AccessUnauthorizedException
     */
    RequestResponse<JsonNode> selectOperationById(String processId, JsonNode queryDsl)
        throws LogbookClientException, InvalidParseOperationException, AccessUnauthorizedException;

    /**
     * selectUnitLifeCycleById
     *
     * @param idUnit
     * @param queryDsl query to be executed
     * @return a response containing a json node
     * @throws LogbookClientException
     * @throws InvalidParseOperationException
     * @throws AccessUnauthorizedException
     */
    RequestResponse<JsonNode> selectUnitLifeCycleById(String idUnit, JsonNode queryDsl)
        throws LogbookClientException, InvalidParseOperationException, AccessUnauthorizedException;

    /**
     * selectUnitLifeCycleById
     *
     * @param queryDsl
     * @return a response containing a json node
     * @throws LogbookClientException
     * @throws InvalidParseOperationException
     * @throws AccessUnauthorizedException
     */
    RequestResponse<JsonNode> selectUnitLifeCycle(JsonNode queryDsl)
        throws LogbookClientException, InvalidParseOperationException, AccessUnauthorizedException;

    /**
     * selectObjectGroupLifeCycleById
     *
     * @param idObject
     * @param queryDsl query to be executed
     * @return a response containing a json node
     * @throws LogbookClientException
     * @throws InvalidParseOperationException
     * @throws AccessUnauthorizedException
     */
    RequestResponse<JsonNode> selectObjectGroupLifeCycleById(String idObject, JsonNode queryDsl)
        throws LogbookClientException, InvalidParseOperationException, AccessUnauthorizedException;

    /**
     * Checks operation traceability
     *
     * @param query to be executed
     * @return a response containing a json node
     * @throws LogbookClientServerException
     * @throws AccessUnauthorizedException
     */
    RequestResponse<JsonNode> checkTraceabilityOperation(JsonNode query)
        throws LogbookClientServerException, InvalidParseOperationException, AccessUnauthorizedException;

    /**
     * @param operationId
     * @return a response containing the traceability file
     * @throws AccessUnauthorizedException
     */
    Response downloadTraceabilityFile(String operationId)
        throws AccessInternalClientServerException, AccessInternalClientNotFoundException,
        InvalidParseOperationException, AccessUnauthorizedException;

    /**
     * @param queryDsl
     * @param idUnit
     * @return unit with a xml representation
     */
    Response getUnitByIdWithXMLFormat(JsonNode queryDsl, String idUnit)
        throws AccessInternalClientServerException, AccessInternalClientNotFoundException, AccessUnauthorizedException,
        InvalidParseOperationException;


    /**
     * Retrieve object group by id with XML format (DIP)
     *
     * @param queryDsl the given query dsl
     * @param objectId the given object group id
     * @return unit with a xml representation
     */
    Response getObjectByIdWithXMLFormat(JsonNode queryDsl, String objectId)
        throws AccessInternalClientServerException, AccessInternalClientNotFoundException, AccessUnauthorizedException,
        InvalidParseOperationException;

    /**
     * Retrieve object group by unit id with XML format (DIP)
     *
     * @param queryDsl the given query dsl
     * @param unitId the given object group id
     * @return unit with a xml representation
     */
    Response getObjectByUnitIdWithXMLFormat(JsonNode queryDsl, String unitId)
        throws AccessInternalClientServerException, AccessInternalClientNotFoundException, AccessUnauthorizedException,
        InvalidParseOperationException;

    /**
     * launch a DIP operation by a DSL query
     * @param queryJson query for the DIP creation
     * @return
     * @throws AccessInternalClientServerException
     */
    RequestResponse<JsonNode> exportDIP(JsonNode queryJson) throws AccessInternalClientServerException;

    /**
     * get a zip file containing a DIP by an operation id
     * @param id operationId
     * @return stream containing zip file
     * @throws AccessInternalClientServerException
     */
    Response findDIPByID(String id) throws AccessInternalClientServerException;
}
