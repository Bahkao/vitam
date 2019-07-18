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
package fr.gouv.vitam.common.database.translators.elasticsearch;

import fr.gouv.vitam.common.database.collections.DynamicParserTokens;
import fr.gouv.vitam.common.database.collections.VitamCollection;
import fr.gouv.vitam.common.database.parser.request.AbstractParser;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import org.elasticsearch.search.sort.SortBuilder;

import java.util.List;

/**
 * Select To Elasticsearch
 */
public class SelectToElasticsearch extends RequestToElasticsearch {

    /**
     * @param selectParser AbstractParser of unknown type
     */
    public SelectToElasticsearch(AbstractParser<?> selectParser) {
        super(selectParser);
    }

    /**
     * FindIterable.sort(orderby) for Elasticsearch
     *
     * @param score True to use if necessary score from ES
     * @return the orderBy Elasticsearch command
     * @throws InvalidParseOperationException
     */
    public List<SortBuilder> getFinalOrderBy(boolean score, DynamicParserTokens parserTokens) throws InvalidParseOperationException {
        List<SortBuilder> list = QueryToElasticsearch.getSorts(requestParser,
            requestParser.hasFullTextQuery() || VitamCollection.containMatch(), score, parserTokens);
        VitamCollection.setMatch(false);
        return list;
    }
}

