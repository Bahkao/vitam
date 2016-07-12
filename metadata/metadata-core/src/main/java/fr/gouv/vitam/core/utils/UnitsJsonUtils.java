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
package fr.gouv.vitam.core.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.core.database.collections.Result;
import fr.gouv.vitam.parser.request.parser.RequestParser;

/**
 * Units metadata tools
 */
public class UnitsJsonUtils {

    private static final VitamLogger LOGGER =
        VitamLoggerFactory.getInstance(UnitsJsonUtils.class);

    /**
     * create Json response
     * 
     * @param result contains final unit(s) list <br>
     *        can be empty
     * @param selectRequest
     * @return JsonNode {$hits{},$context{},$result:[{}....{}],} <br>
     *         $context will be added later (Access)</br>
     *         $result array of units(can be empty)
     * @throws InvalidParseOperationException thrown when json query is not valid
     */
    public static JsonNode populateJSONObjectResponse(Result result, RequestParser selectRequest)
        throws InvalidParseOperationException {

        ObjectNode jsonUnitListResponse = JsonHandler.createObjectNode();

        if (result != null && result.getFinal() != null) {
            ObjectNode hitsNode = JsonHandler.createObjectNode();
            hitsNode.put("total", result.getNbResult());
            hitsNode.put("size", result.getNbResult());
            hitsNode.put("limit", result.getNbResult());
            hitsNode.put("time_out", false);
            jsonUnitListResponse.set("$hint", hitsNode);
            ObjectNode contextNode = JsonHandler.createObjectNode();
            jsonUnitListResponse.set("$context", contextNode);
            if (result.getNbResult() > 0) {
                jsonUnitListResponse.set("$result", getJsonUnitObject(result.getFinal().get("Result")));
            } else {
                jsonUnitListResponse.set("$result", JsonHandler.createObjectNode());
            }
        }
        LOGGER.debug("MetaDataImpl / selectUnitsByQuery /Results: " + jsonUnitListResponse.toString());
        return jsonUnitListResponse;
    }

    private static JsonNode getJsonUnitObject(Object unit) throws InvalidParseOperationException {
        return JsonHandler.toJsonNode(unit);
    }


}
