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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.filters.FiltersAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.filters.FiltersAggregator.KeyedFilter;
import org.elasticsearch.search.aggregations.bucket.range.date.DateRangeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import fr.gouv.vitam.common.database.builder.facet.Facet;
import fr.gouv.vitam.common.database.builder.query.BooleanQuery;
import fr.gouv.vitam.common.database.builder.query.Query;
import fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.FACETARGS;
import fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.QUERY;
import fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.QUERYARGS;
import fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.RANGEARGS;
import fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.SELECTFILTER;
import fr.gouv.vitam.common.database.builder.request.exception.InvalidCreateOperationException;
import fr.gouv.vitam.common.database.builder.request.multiple.SelectMultiQuery;
import fr.gouv.vitam.common.database.collections.VitamCollection;
import fr.gouv.vitam.common.database.parser.query.ParserTokens;
import fr.gouv.vitam.common.database.parser.query.QueryParserHelper;
import fr.gouv.vitam.common.database.parser.request.AbstractParser;
import fr.gouv.vitam.common.database.parser.request.GlobalDatasParser;
import fr.gouv.vitam.common.database.parser.request.adapter.VarNameAdapter;
import fr.gouv.vitam.common.database.server.mongodb.VitamDocument;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;

/**
 * Elasticsearch Translator
 */
public class QueryToElasticsearch {

    private static final VitamLogger LOGGER =
        VitamLoggerFactory.getInstance(QueryToElasticsearch.class);

    private static final String UNDERSCORE_UID = "_uid";
    private static final String FUZZINESS = "AUTO";

    private QueryToElasticsearch() {
        // Empty constructor
    }

    /**
     * @param field String
     * @param roots Set of String
     * @return the filter associated with the roots
     */
    public static QueryBuilder getRoots(final String field, final Collection<String> roots) {
        String[] values = new String[roots.size()];
        values = roots.toArray(values);

        // NB: terms and not term since multiple values
        return QueryBuilders.termsQuery(field, values);
    }

    /**
     * @param query
     * @param field String
     * @param roots Set of String
     * @return the filter associated with the roots
     */
    public static void addRoots(BoolQueryBuilder query, final String field, final Collection<String> roots, int depth) {
        String[] values = new String[roots.size()];
        values = roots.toArray(values);

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        for (int i = 1; i <= depth; i++) {
            boolQueryBuilder.should(QueryBuilders.termsQuery(field + "." + i, values));
        }

        query.filter(boolQueryBuilder);
        // NB: terms and not term since multiple values
    }

    /**
     * Merge a request and a root filter
     *
     * @param command QueryBuilder
     * @param roots QueryBuilder
     * @return the complete request
     */
    public static QueryBuilder getFullCommand(final QueryBuilder command, final QueryBuilder roots) {
        return QueryBuilders.boolQuery()
            .must(command)
            .must(roots);
    }

    /**
     * Generate sort list from order by ES query orders : {field1 : -1, field2 : 1} or [{field1 : -1, field2 :
     * 1},{field3 : -1}]<br>
     * <br>
     * <b>Note</b> : if the query contains a match and the collection allows to use score, the socre is added to the
     * sort<br>
     * <br>
     *
     * @param requestParser the original parser
     * @param hasFullText True to add scoreSort
     * @param score True will add score first
     * @return list of order by as sort objects
     * @throws InvalidParseOperationException if the orderBy is not valid
     */
    public static List<SortBuilder> getSorts(final AbstractParser<?> requestParser, boolean hasFullText, boolean score)
        throws InvalidParseOperationException {
        final JsonNode orderby = requestParser.getRequest().getFilter()
            .get(SELECTFILTER.ORDERBY.exactToken());
        int size = score && requestParser.hasFullTextQuery() ? 1 : 0;
        if (orderby != null && orderby.size() > 0) {
            size += orderby.size();
        }
        final List<SortBuilder> sorts = new ArrayList<>(size);
        if (orderby == null || orderby.size() == 0) {
            if (score && requestParser.hasFullTextQuery()) {
                sorts.add(SortBuilders.scoreSort().order(SortOrder.DESC));
                return sorts;
            }
            return null;
        }
        final Iterator<Entry<String, JsonNode>> iterator = orderby.fields();
        if (!iterator.hasNext()) {
            if (score && requestParser.hasFullTextQuery()) {
                sorts.add(SortBuilders.scoreSort().order(SortOrder.DESC));
                return sorts;
            }
            return null;
        }
        boolean scoreNotAdded = true;
        while (iterator.hasNext()) {
            final Entry<String, JsonNode> entry = iterator.next();
            String key = entry.getKey();
            // special case of _id
            if (key.equals(VitamDocument.ID)) {
                key = UNDERSCORE_UID;
            }
            if (scoreNotAdded && score && requestParser.hasFullTextQuery() &&
                !ParserTokens.PROJECTIONARGS.isNotAnalyzed(entry.getKey())) {
                // First time we get an analyzed sort by
                scoreNotAdded = false;
                if ("_score".equals(entry.getKey()) || "#score".equals(entry.getKey())) {
                    if (entry.getValue().asInt() < 0) {
                        sorts.add(SortBuilders.scoreSort().order(SortOrder.DESC));
                    } else {
                        sorts.add(SortBuilders.scoreSort().order(SortOrder.ASC));
                    }
                    continue;
                } else {
                    sorts.add(SortBuilders.scoreSort().order(SortOrder.DESC));
                }
            }

            final FieldSortBuilder fieldSort = SortBuilders.fieldSort(key);
            if (entry.getValue().asInt() < 0) {
                fieldSort.order(SortOrder.DESC);
                sorts.add(fieldSort);
            } else {
                fieldSort.order(SortOrder.ASC);
                sorts.add(fieldSort);
            }
        }
        if (scoreNotAdded && score && requestParser.hasFullTextQuery()) {
            // Last filter if not yet added
            scoreNotAdded = false;
            sorts.add(SortBuilders.scoreSort().order(SortOrder.DESC));
        }
        return sorts;
    }


    /**
     * @param query Query
     * @return the associated QueryBuilder
     * @throws InvalidParseOperationException if query could not parse to command
     */
    public static QueryBuilder getCommand(final Query query, VarNameAdapter adapter)
        throws InvalidParseOperationException {
        final QUERY req = query.getQUERY();
        final JsonNode content = query.getNode(req.exactToken());
        switch (req) {
            case AND:
            case NOT:
            case OR:
                return andOrNotCommand(req, query, adapter);
            case EXISTS:
            case MISSING:
                return existsMissingCommand(req, content);
            case FLT:
            case MLT:
                return xltCommand(req, content);
            case MATCH:
            case MATCH_ALL:
            case MATCH_PHRASE:
            case MATCH_PHRASE_PREFIX:
                return matchCommand(req, content);
            case SEARCH:
                return searchCommand(req, content);
            case SUBOBJECT:
                return nestedSearchCommand(req, content, adapter);
            case NIN:
            case IN:
                return inCommand(req, content);
            case RANGE:
                return rangeCommand(req, content);
            case REGEX:
                return regexCommand(req, content);
            case TERM:
                return termCommand(req, content);
            case WILDCARD:
                return wildcardCommand(req, content);
            case EQ:
            case NE:
                return eqCommand(req, content);
            case GT:
            case GTE:
            case LT:
            case LTE:
                return compareCommand(req, content);
            case ISNULL:
                return isNullCommand(req, content);
            case SIZE:
                return sizeCommand(req, content);
            case NOP:
                return QueryBuilders.matchAllQuery();
            case PATH:
                return pathCommand(req, content);
            case GEOMETRY:
            case BOX:
            case POLYGON:
            case CENTER:
            case GEOINTERSECTS:
            case GEOWITHIN:
            default:
        }
        throw new InvalidParseOperationException("Invalid command: " + req.exactToken());
    }

    /**
     * @param content JsonNode
     * @return the Path Command
     */
    private static QueryBuilder pathCommand(final QUERY query, final JsonNode content) {

        // Unsupported command. May be deleted without prior notice.
        logUnsupportedCommand(query, content, "Deprecated. Should not be invoked anymore.");

        final ArrayNode array = (ArrayNode) content;
        final String[] values = new String[array.size()];
        int i = 0;
        for (final JsonNode node : array) {
            values[i++] = node.asText();
        }
        return QueryBuilders.termsQuery("_id", values);
    }

    /**
     * $size : { name : length }
     *
     * @param query QUERY
     * @param content JsonNode
     * @return the size Command
     * @throws InvalidParseOperationException if check unicity is in error
     */
    private static QueryBuilder sizeCommand(final QUERY query, final JsonNode content)
        throws InvalidParseOperationException {

        // Unsupported command. May be deleted without prior notice.
        logUnsupportedCommand(query, content, "Deprecated. Should not be invoked anymore.");

        final Entry<String, JsonNode> element = JsonHandler.checkUnicity(query.exactToken(), content);
        final Script script = new Script("doc['" + element.getKey() + "'].values.length == " + element.getValue());
        if (element.getKey().equals(VitamDocument.ID)) {
            logWarnUnsupportedIdForCommand(query, content);
        }
        return QueryBuilders.scriptQuery(script);
    }

    /**
     * $gt : { name : value } $gte : { name : value } $lt : { name : value } $lte : { name : value }
     *
     * @param query QUERY
     * @param content JsonNode
     * @return the compare Command
     * @throws InvalidParseOperationException if check unicity is in error
     */
    private static QueryBuilder compareCommand(final QUERY query, final JsonNode content)
        throws InvalidParseOperationException {
        final Entry<String, JsonNode> element = JsonHandler.checkUnicity(query.exactToken(), content);

        String key = element.getKey();

        if (!ParserTokens.PROJECTIONARGS.isNotAnalyzed(key)) {
            // Unsupported mode. May be updated without prior notice.
            logUnsupportedCommand(query, content, "Analyzed field: '" + key + "'");
        } else {
            logCommand(query, content);
        }

        // special case of _id
        boolean isId = false;
        if (key.equals(VitamDocument.ID)) {
            logWarnUnsupportedIdForCommand(query, content);
            key = UNDERSCORE_UID;
            isId = true;
        }

        JsonNode node = element.getValue();
        Object value = GlobalDatasParser.getValue(node);
        if (isId) {
            value = VitamCollection.getTypeunique() + "#" + value.toString();
        }
        switch (query) {
            case GT:
                return QueryBuilders.rangeQuery(key).gt(value);
            case GTE:
                return QueryBuilders.rangeQuery(key).gte(value);
            case LT:
                return QueryBuilders.rangeQuery(key).lt(value);
            case LTE:
            default:
                return QueryBuilders.rangeQuery(key).lte(value);
        }

    }

    /**
     * $mlt : { $fields : [ name1, name2 ], $like : like_text } $flt : { $fields : [ name1, name2 ], $like : like_text }
     *
     * @param query QUERY
     * @param content JsonNode
     * @return the xlt Command
     * @throws InvalidParseOperationException if check unicity is in error
     */
    private static QueryBuilder xltCommand(final QUERY query, final JsonNode content)
        throws InvalidParseOperationException {

        // Unsupported command. May be deleted without prior notice.
        logUnsupportedCommand(query, content, "Deprecated. Should not be invoked anymore.");

        final ArrayNode fields = (ArrayNode) content.get(QUERYARGS.FIELDS.exactToken());
        final JsonNode like = content.get(QUERYARGS.LIKE.exactToken());
        if (fields == null || like == null || fields.size() == 0) {
            throw new InvalidParseOperationException("Incorrect command: " + query.exactToken() + " : " + query);
        }
        final String slike = like.toString();
        if (slike.trim().isEmpty()) {
            throw new InvalidParseOperationException("Incorrect command: " + query.exactToken() + " : " + query);
        }
        final String[] names = new String[fields.size()];
        int i = 0;
        for (final JsonNode name : fields) {
            if (VitamDocument.ID.equals(name.toString())) {
                logWarnUnsupportedIdForCommand(query, content);
            }
            names[i++] = name.toString();
        }
        switch (query) {
            case FLT:
                if (names.length > 1) {
                    final BoolQueryBuilder builder = QueryBuilders.boolQuery().minimumNumberShouldMatch(1);
                    for (final String name : names) {
                        builder.should(QueryBuilders.matchQuery(name, slike).fuzziness(FUZZINESS));
                    }
                    return builder;
                } else if (names.length == 1) {
                    return QueryBuilders.matchQuery(names[0], slike).fuzziness(FUZZINESS);
                }
            case MLT:
            default:
                // Note : array size increase is now needed (addLikeText(slike) is removed in ES5), and the array is
                // small, so array copy should be relatively painless.
                final String[] newNames = Arrays.copyOf(names, names.length + 1);
                newNames[names.length] = slike;
                return QueryBuilders.moreLikeThisQuery(newNames);
        }
    }

    /**
     * $search : { name : searchParameter }
     *
     * @param query QUERY
     * @param content JsonNode
     * @return the search Command
     * @throws InvalidParseOperationException if check unicity is in error
     */
    private static QueryBuilder searchCommand(final QUERY query, final JsonNode content)
        throws InvalidParseOperationException {

        final Entry<String, JsonNode> element = JsonHandler.checkUnicity(query.exactToken(), content);
        final String attribute = element.getKey();

        if (ParserTokens.PROJECTIONARGS.isNotAnalyzed(attribute)) {
            // Unsupported mode. May be updated without prior notice.
            logUnsupportedCommand(query, content, "Not_analyzed field: '" + attribute + "'");
        } else {
            logCommand(query, content);
        }

        return QueryBuilders.simpleQueryStringQuery(element.getValue().asText()).field(attribute);
    }

    /**
     * $subobject : { name : searchParameter }
     *
     * @param query QUERY
     * @param content JsonNode
     * @return the search Command
     * @throws InvalidParseOperationException if check unicity is in error
     */
    private static QueryBuilder nestedSearchCommand(final QUERY query, final JsonNode content, VarNameAdapter adapter)
            throws InvalidParseOperationException {

        final Entry<String, JsonNode> element = JsonHandler.checkUnicity(query.exactToken(), content);
        final String attribute = element.getKey();

        if (ParserTokens.PROJECTIONARGS.isNotAnalyzed(attribute)) {
            // Unsupported mode. May be updated without prior notice.
            logUnsupportedCommand(query, content, "Not_analyzed field: '" + attribute + "'");
        } else {
            logCommand(query, content);
        }

        if(content == null || !content.fields().hasNext() || !content.fields().next().getValue().fields().hasNext()) {
            throw new InvalidParseOperationException("$subobject query is not valid");
        }

        String path = content.fields().next().getKey();
        JsonNode subQueryJson = content.fields().next().getValue();
        Query subQuery;
        try {
            subQuery = QueryParserHelper.query(subQueryJson.fields().next().getKey(), subQueryJson.fields().next().getValue(), adapter);
        } catch (InvalidCreateOperationException e) {
            throw new InvalidParseOperationException("$subobject query is not valid");
        }
        return QueryBuilders.nestedQuery(path, getCommand(subQuery, adapter), ScoreMode.Avg);
    }

    /**
     * $match : { name : words }
     *
     * @param query QUERY
     * @param content JsonNode
     * @return the match Command
     * @throws InvalidParseOperationException if check unicity is in error
     */
    private static QueryBuilder matchCommand(final QUERY query, final JsonNode content)
        throws InvalidParseOperationException {

        final JsonNode max = ((ObjectNode) content).remove(QUERYARGS.MAX_EXPANSIONS.exactToken());
        final Entry<String, JsonNode> element = JsonHandler.checkUnicity(query.exactToken(), content);
        final String attribute = element.getKey();

        // Unsupported match over analyzed field
        if (ParserTokens.PROJECTIONARGS.isNotAnalyzed(attribute)) {
            return matchCommandOverNonAnalyzedField(query, content, element, attribute);
        }

        // Unsupported max_expansions
        if (max != null && !max.isMissingNode()) {
            return matchCommandWithMaxExpansions(query, content, max, element);
        }

        logCommand(query, content);

        switch (query) {
            case MATCH:
                return QueryBuilders.matchQuery(element.getKey(), element.getValue().asText())
                    .operator(Operator.OR);
            case MATCH_ALL:
                return QueryBuilders.matchQuery(element.getKey(), element.getValue().asText())
                    .operator(Operator.AND);
            case MATCH_PHRASE:
                return QueryBuilders.matchPhraseQuery(element.getKey(), element.getValue().asText());
            case MATCH_PHRASE_PREFIX:
                return QueryBuilders.matchPhrasePrefixQuery(element.getKey(), element.getValue().asText());
            default:
                throw new InvalidParseOperationException("Not correctly parsed: " + query);
        }
    }

    /**
     * $match : { name : words, $max_expansions : n }. Unsupported $max_expansions mode to be removed later.
     *
     * @param query QUERY
     * @param content JsonNode
     * @return the match Command
     * @throws InvalidParseOperationException if check unicity is in error
     * @deprecated Unsupported case. Should/will be removed without prior notice.
     */
    private static QueryBuilder matchCommandWithMaxExpansions(QUERY query, JsonNode content, JsonNode max,
        Entry<String, JsonNode> element)
        throws InvalidParseOperationException {

        logUnsupportedCommand(query, content, "Unsupported max_expansions operator");

        switch (query) {
            case MATCH:
                return QueryBuilders.matchQuery(element.getKey(), element.getValue().asText())
                    .maxExpansions(max.asInt()).operator(Operator.OR);
            case MATCH_ALL:
                return QueryBuilders.matchQuery(element.getKey(), element.getValue().asText())
                    .maxExpansions(max.asInt()).operator(Operator.AND);
            case MATCH_PHRASE:
                return QueryBuilders.matchPhraseQuery(element.getKey(), element.getValue().asText());
            // Note : the method maxExpansions(max.asInt()) is removed in ES5, with no documented replacement.
            case MATCH_PHRASE_PREFIX:
                return QueryBuilders.matchPhrasePrefixQuery(element.getKey(), element.getValue().asText())
                    .maxExpansions(max.asInt());
            default:
                throw new InvalidParseOperationException("Not correctly parsed: " + query);
        }
    }

    /**
     * $match : { name : words } for non analyzed fields. Unsupported use case to be removed later.
     *
     * @param query QUERY
     * @param content JsonNode
     * @return the match Command
     * @throws InvalidParseOperationException if check unicity is in error
     * @deprecated Unsupported cases. Should/will be removed without prior notice.
     */
    private static QueryBuilder matchCommandOverNonAnalyzedField(QUERY query, JsonNode content,
        Entry<String, JsonNode> element, String attribute) {

        logUnsupportedCommand(query, content, "Not_analyzed field: '" + attribute + "'");

        switch (query) {
            case MATCH:
                return QueryBuilders.termsQuery(element.getKey(), element.getValue().toString().split(" "));
            case MATCH_ALL:
            case MATCH_PHRASE:
            case MATCH_PHRASE_PREFIX:
            default:
                return QueryBuilders.termQuery(element.getKey(), element.getValue().toString());
        }
    }

    /**
     * $in : { name : [ value1, value2, ... ] }
     *
     * @param query QUERY
     * @param content JsonNode
     * @return the in Command
     * @throws InvalidParseOperationException if check unicity is in error
     */
    private static QueryBuilder inCommand(final QUERY query, final JsonNode content)
        throws InvalidParseOperationException {
        final Entry<String, JsonNode> element = JsonHandler.checkUnicity(query.exactToken(), content);
        String key = element.getKey();

        // Unsupported command for analyzed field
        if (!ParserTokens.PROJECTIONARGS.isNotAnalyzed(key)) {
            return inCommandOverAnalyzedField(query, content);
        }

        List<JsonNode> nodes = new ArrayList<>();
        JsonNode node = element.getValue();
        if (node instanceof ArrayNode) {

            logCommand(query, content);

            for (JsonNode jsonNode : node) {
                nodes.add(jsonNode);
            }
        } else {

            // TODO : Check no usages from whitin Vitam & remove this unsupported usecase.
            logUnsupportedCommand(query, content, "Expecting value list");
            nodes.add(node);
        }

        final Set<Object> set = new HashSet<>();
        for (final JsonNode value : nodes) {
            set.add(getAsObject(value));
        }
        final QueryBuilder query2 = QueryBuilders.termsQuery(key, set);
        if (query == QUERY.NIN) {
            return QueryBuilders.boolQuery().mustNot(query2);
        }
        return query2;
    }

    /**
     * $in : { name : [ value1, value2, ... ] } for analyzed fields. Unsupported use case to be removed later.
     *
     * @param query QUERY
     * @param content JsonNode
     * @return the in Command
     * @throws InvalidParseOperationException if check unicity is in error
     * @deprecated Unsupported cases should/will be removed without prior notice.
     */
    private static QueryBuilder inCommandOverAnalyzedField(final QUERY query, final JsonNode content)
        throws InvalidParseOperationException {
        final Entry<String, JsonNode> element = JsonHandler.checkUnicity(query.exactToken(), content);
        String key = element.getKey();

        logUnsupportedCommand(query, content, "Analyzed field: '" + key + "'");

        List<JsonNode> nodes = new ArrayList<>();
        JsonNode node = element.getValue();
        if (node instanceof ArrayNode) {
            for (JsonNode jsonNode : node) {
                nodes.add(jsonNode);
            }
        } else {
            nodes.add(node);
        }

        final Set<Object> set = new HashSet<>();
        for (final JsonNode value : nodes) {
            set.add(getAsObject(value));
        }
        final QueryBuilder query2;

        final BoolQueryBuilder builder = new BoolQueryBuilder().minimumNumberShouldMatch(1);
        for (final Object object : set) {
            builder.should(QueryBuilders.matchQuery(key, object).operator(Operator.OR));
        }
        VitamCollection.setMatch(true);
        query2 = builder;

        if (query == QUERY.NIN) {
            return QueryBuilders.boolQuery().mustNot(query2);
        }
        return query2;
    }


    /**
     * $range : { name : { $gte : value, $lte : value } }
     *
     * @param query QUERY
     * @param content JsonNode
     * @return the range Command
     * @throws InvalidParseOperationException if check unicity is in error
     */
    private static QueryBuilder rangeCommand(final QUERY query, final JsonNode content)
        throws InvalidParseOperationException {
        final Entry<String, JsonNode> element = JsonHandler.checkUnicity(query.exactToken(), content);

        String key = element.getKey();

        if (!ParserTokens.PROJECTIONARGS.isNotAnalyzed(key)) {
            // Unsupported mode. May be updated without prior notice.
            logUnsupportedCommand(query, content, "Analyzed field: '" + key + "'");
        } else {
            logCommand(query, content);
        }

        if (VitamDocument.ID.equals(key)) {
            logWarnUnsupportedIdForCommand(query, content);
        }

        final RangeQueryBuilder range = QueryBuilders.rangeQuery(key);
        for (final Iterator<Entry<String, JsonNode>> iterator = element.getValue().fields(); iterator.hasNext();) {
            final Entry<String, JsonNode> requestItem = iterator.next();
            RANGEARGS arg;
            try {
                final String skey = requestItem.getKey();
                if (skey.startsWith("$")) {
                    arg = RANGEARGS.valueOf(skey.substring(1).toUpperCase());
                } else {
                    throw new InvalidParseOperationException("Invalid Range query command: " + requestItem);
                }
            } catch (final IllegalArgumentException e) {
                throw new InvalidParseOperationException("Invalid Range query command: " + requestItem, e);
            }
            JsonNode node = requestItem.getValue();

            switch (arg) {
                case GT:
                    range.gt(getAsObject(node));
                    break;
                case GTE:
                    range.gte(getAsObject(node));
                    break;
                case LT:
                    range.lt(getAsObject(node));
                    break;
                case LTE:
                default:
                    range.lte(getAsObject(node));
            }
        }
        return range;
    }


    /**
     * $regex : { name : regex }
     *
     * @param query QUERY
     * @param content JsonNode
     * @return the regex Command
     * @throws InvalidParseOperationException if check unicity is in error
     */
    private static QueryBuilder regexCommand(final QUERY query, final JsonNode content)
        throws InvalidParseOperationException {
        final Entry<String, JsonNode> entry = JsonHandler.checkUnicity(query.exactToken(), content);
        String key = entry.getKey();

        // Analyzed fields are not supported
        if (!ParserTokens.PROJECTIONARGS.isNotAnalyzed(key)) {
            return regexCommandOverAnalyzedField(query, content);
        }

        // special case of _id (cannot be queried)
        if (key.equals(VitamDocument.ID)) {
            return regexCommandOverIdField(query, content);
        }

        String value = entry.getValue().asText();
        logCommand(query, content);

        return QueryBuilders.regexpQuery(key, value);
    }

    /**
     * Handles $regex : { name : regex } for _id field. Unsupported use case to be removed later.
     *
     * @param query QUERY
     * @param content JsonNode
     * @return the regex Command
     * @throws InvalidParseOperationException if check unicity is in error
     * @deprecated Unsupported cases should/will be removed without prior notice.
     */
    private static QueryBuilder regexCommandOverIdField(final QUERY query, final JsonNode content)
        throws InvalidParseOperationException {
        final Entry<String, JsonNode> entry = JsonHandler.checkUnicity(query.exactToken(), content);

        String key = entry.getKey();

        logUnsupportedCommand(query, content, "Unsupported ID field");

        String value =
            entry.getValue().asText().replaceAll("[\\.\\?\\+\\*\\|\\{\\}\\[\\]\\(\\)\\\"\\\\\\#\\@\\&\\<\\>\\~]", " ");
        value = removeAllDoubleSpace(value);
        return QueryBuilders.termsQuery(key, value.split(" "));
    }


    /**
     * Handles $regex : { name : regex } for analyzed fields. Unsupported use case to be removed later.
     *
     * @param query QUERY
     * @param content JsonNode
     * @return the regex Command
     * @throws InvalidParseOperationException if check unicity is in error
     * @deprecated Unsupported cases should/will be removed without prior notice.
     */
    private static QueryBuilder regexCommandOverAnalyzedField(final QUERY query, final JsonNode content)
        throws InvalidParseOperationException {
        final Entry<String, JsonNode> entry = JsonHandler.checkUnicity(query.exactToken(), content);

        // special case of _id
        String key = entry.getKey();
        String value = "/" + entry.getValue().asText() + "/";

        logUnsupportedCommand(query, content, "Analyzed field: '" + key + "'");

        return QueryBuilders.regexpQuery(key, value);
    }

    /**
     * $term : { name : term, name : term }
     *
     * @param query QUERY
     * @param content JsonNode
     * @return the term Command
     * @throws InvalidParseOperationException if check unicity is in error
     */
    private static QueryBuilder termCommand(final QUERY query, final JsonNode content)
        throws InvalidParseOperationException {

        // Unsupported command. May be deleted without prior notice.
        logUnsupportedCommand(query, content, "Deprecated. Should not be invoked anymore.");

        boolean multiple = false;
        BoolQueryBuilder query2 = null;
        if (content.size() > 1) {
            multiple = true;
            query2 = QueryBuilders.boolQuery();
        }
        for (final Iterator<Entry<String, JsonNode>> iterator = content.fields(); iterator.hasNext();) {
            final Entry<String, JsonNode> requestItem = iterator.next();
            String key = requestItem.getKey();
            if (VitamDocument.ID.equals(key)) {
                logWarnUnsupportedIdForCommand(query, content);
            }
            JsonNode node = requestItem.getValue();

            if (node.isNumber()) {
                if (!multiple) {
                    return QueryBuilders.termQuery(key, getAsObject(node));
                }
                query2.must(QueryBuilders.termQuery(key, getAsObject(node)));
            } else {
                final String val = node.asText();
                QueryBuilder query3;
                if (ParserTokens.PROJECTIONARGS.isNotAnalyzed(key)) {
                    query3 = QueryBuilders.termQuery(key, val);
                } else {
                    query3 = QueryBuilders.matchQuery(key, val).operator(Operator.AND);
                    VitamCollection.setMatch(true);
                }
                if (!multiple) {
                    return query3;
                }
                query2.must(query3);
            }
        }
        return query2;
    }


    /**
     * $wildcard : { name : expression }
     *
     * @param query QUERY
     * @param content JsonNode
     * @return the wildcard Command
     */
    private static QueryBuilder wildcardCommand(final QUERY query, final JsonNode content)
        throws InvalidParseOperationException {
        final Entry<String, JsonNode> entry = JsonHandler.checkUnicity(query.exactToken(), content);
        String key = entry.getKey();
        final JsonNode node = entry.getValue();
        String val = node.asText();

        if (!ParserTokens.PROJECTIONARGS.isNotAnalyzed(key)) {
            // Unsupported wildcard with analyzed field
            logUnsupportedCommand(query, content, "Analyzed field: '" + key + "'");
        } else {
            // Not analyzed mode...
            if (key.equals(VitamDocument.ID)) {
                // special case of _id (cannot be queried)
                logUnsupportedCommand(query, content, "Unsupported ID field");
            } else {
                // OK Mode
                logCommand(query, content);
            }
        }

        return QueryBuilders.wildcardQuery(key, val);
    }

    @Deprecated
    private static String removeAllDoubleSpace(String value) {
        String oldValue = value;
        String newValue = oldValue.replace("  ", " ");
        while (newValue.length() != oldValue.length()) {
            oldValue = newValue;
            newValue = oldValue.replace("  ", " ");
        }
        return newValue;
    }

    /**
     * $eq : { name : value }
     *
     * @param query QUERY
     * @param content JsonNode
     * @return the eq Command
     * @throws InvalidParseOperationException if check unicity is in error
     */
    private static QueryBuilder eqCommand(final QUERY query, final JsonNode content)
        throws InvalidParseOperationException {
        final Entry<String, JsonNode> entry = JsonHandler.checkUnicity(query.exactToken(), content);

        String key = entry.getKey();
        JsonNode node = entry.getValue();

        // Unsupported use case.
        if (!ParserTokens.PROJECTIONARGS.isNotAnalyzed(key)) {
            return eqCommandOverAnalyzedField(query, content);
        }

        logCommand(query, content);

        final QueryBuilder query2 = QueryBuilders.termQuery(key, getAsObject(node));
        if (query == QUERY.NE) {
            return QueryBuilders.boolQuery().mustNot(query2);
        }
        return query2;
    }

    /**
     * $eq : { name : value } for analyzed fields. Unsupported use case to be removed later.
     *
     * @param query QUERY
     * @param content JsonNode
     * @return the eq Command
     * @throws InvalidParseOperationException if check unicity is in error
     * @deprecated Unsupported cases should/will be removed without prior notice.
     */
    private static QueryBuilder eqCommandOverAnalyzedField(QUERY query, JsonNode content)
        throws InvalidParseOperationException {

        final Entry<String, JsonNode> entry = JsonHandler.checkUnicity(query.exactToken(), content);

        String key = entry.getKey();
        JsonNode node = entry.getValue();

        // Unsupported eq with analyzed field
        logUnsupportedCommand(query, content, "Analyzed field: '" + key + "'");

        final QueryBuilder query2 = QueryBuilders.matchQuery(key, getAsObject(node)).operator(Operator.AND);
        VitamCollection.setMatch(true);
        if (query == QUERY.NE) {
            return QueryBuilders.boolQuery().mustNot(query2);
        }
        return query2;
    }

    /**
     * $exists : name
     *
     * @param query QUERY
     * @param content JsonNode
     * @return the exist Command
     * @throws InvalidParseOperationException if check unicity is in error
     */
    private static QueryBuilder existsMissingCommand(final QUERY query, final JsonNode content)
        throws InvalidParseOperationException {

        String fieldname = content.asText();
        if (VitamDocument.ID.equals(fieldname)) {
            logWarnUnsupportedIdForCommand(query, content);
            fieldname = UNDERSCORE_UID;
        }
        final QueryBuilder queryBuilder = QueryBuilders.existsQuery(fieldname);
        switch (query) {
            case MISSING:

                // Unsupported command. May be deleted without prior notice.
                logUnsupportedCommand(query, content, "Deprecated. Should not be invoked anymore.");

                return QueryBuilders.boolQuery().mustNot(queryBuilder);
            case EXISTS:

                logCommand(query, content);

                return queryBuilder;

            default:
                throw new InvalidParseOperationException("Not correctly parsed: " + query);
        }
    }

    /**
     * $isNull : name
     *
     * @param query QUERY
     * @param content JsonNode
     * @return the isNull Command
     * @throws InvalidParseOperationException if check unicity is in error
     */
    private static QueryBuilder isNullCommand(final QUERY query, final JsonNode content)
        throws InvalidParseOperationException {

        // Unsupported command. May be deleted without prior notice.
        logUnsupportedCommand(query, content, "Deprecated. Should not be invoked anymore.");

        String fieldname = content.asText();
        if (VitamDocument.ID.equals(fieldname)) {
            logWarnUnsupportedIdForCommand(query, content);
            fieldname = UNDERSCORE_UID;
        }
        return QueryBuilders.boolQuery().mustNot(QueryBuilders.existsQuery(fieldname));
    }


    /**
     * $and : [ expression1, expression2, ... ] $or : [ expression1, expression2, ... ] $not : [ expression1,
     * expression2, ... ]
     *
     * @param req QUERY
     * @param query JsonNode
     * @return the and Or Not Command
     * @throws InvalidParseOperationException if check unicity is in error
     */
    private static QueryBuilder andOrNotCommand(final QUERY query, final Query req, VarNameAdapter adapter)
        throws InvalidParseOperationException {

        logCommand(query, req.getCurrentObject());

        final BooleanQuery nthrequest = (BooleanQuery) req;
        final List<Query> sub = nthrequest.getQueries();
        final BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        for (int i = 0; i < sub.size(); i++) {
            switch (query) {
                case AND:
                    boolQueryBuilder.must(getCommand(sub.get(i), adapter));
                    break;
                case NOT:
                    boolQueryBuilder.mustNot(getCommand(sub.get(i), adapter));
                    break;
                case OR:
                default:
                    boolQueryBuilder.minimumNumberShouldMatch(1).should(getCommand(sub.get(i), adapter));
            }
        }
        return boolQueryBuilder;
    }

    /**
     * Create ES facets from request parser
     *
     * @param requestParser parser
     * @return list of facets
     * @throws InvalidParseOperationException if could not create ES facets
     */
    public static List<AggregationBuilder> getFacets(final AbstractParser<?> requestParser)
        throws InvalidParseOperationException {
        List<AggregationBuilder> builders = new ArrayList<>();
        if (requestParser.getRequest() instanceof SelectMultiQuery) {
            List<Facet> facets = ((SelectMultiQuery) requestParser.getRequest()).getFacets();
            for (Facet facet : facets) {
                switch (facet.getCurrentTokenFACET()) {
                    case TERMS:
                        termsFacet(builders, facet);
                        break;
                    case DATE_RANGE:
                        dateRangeFacet(builders, facet);
                        break;
                    case FILTERS:
                        filtersFacet(builders, facet, requestParser.getAdapter());
                        break;
                    default:
                        break;
                }

            }
        }
        return builders;

    }

    /**
     * Add date_range es facet from facet
     *
     * @param builders es facets
     * @param facet facet
     */
    private static void dateRangeFacet(List<AggregationBuilder> builders, Facet facet) {
        DateRangeAggregationBuilder dateRangeBuilder = AggregationBuilders.dateRange(facet.getName());//
        JsonNode dateRange = facet.getCurrentFacet().get(facet.getCurrentTokenFACET().exactToken());
        dateRangeBuilder.field(dateRange.get(FACETARGS.FIELD.exactToken()).asText());
        dateRangeBuilder.format(dateRange.get(FACETARGS.FORMAT.exactToken()).asText());
        JsonNode ranges = dateRange.get(FACETARGS.RANGES.exactToken());
        ranges.forEach(item -> {
            JsonNode from = item.get(FACETARGS.FROM.exactToken());
            JsonNode to = item.get(FACETARGS.TO.exactToken());
            if (from != null && !(from instanceof NullNode) && to != null && !(to instanceof NullNode)) {
                dateRangeBuilder.addRange(from.asText(), to.asText());
            } else if (from != null && !(from instanceof NullNode)) {
                dateRangeBuilder.addUnboundedFrom(from.asText());
            } else if (to != null && !(to instanceof NullNode)) {
                dateRangeBuilder.addUnboundedTo(to.asText());
            }
        });
        builders.add(dateRangeBuilder);
    }

    /**
     * Add terms es facet from facet
     *
     * @param builders es facets
     * @param facet facet
     */
    private static void termsFacet(List<AggregationBuilder> builders, Facet facet) {
        TermsAggregationBuilder termsBuilder = AggregationBuilders.terms(facet.getName());
        JsonNode terms = facet.getCurrentFacet().get(facet.getCurrentTokenFACET().exactToken());
        termsBuilder.field(terms.get(FACETARGS.FIELD.exactToken()).asText());
        if (terms.has(FACETARGS.SIZE.exactToken())) {
            termsBuilder.size(terms.get(FACETARGS.SIZE.exactToken()).asInt());
        }
        builders.add(termsBuilder);
    }


    /**
     * Add filters es facet from facet
     *
     * @param builders es facets
     * @param facet facet
     */
    private static void filtersFacet(List<AggregationBuilder> builders, Facet facet, VarNameAdapter adapter)
        throws InvalidParseOperationException {
        JsonNode filtersFacetNode = facet.getCurrentFacet().get(facet.getCurrentTokenFACET().exactToken());

        Map<String, Query> filtersMap = new HashMap<>();
        ArrayNode filtersNode = (ArrayNode) filtersFacetNode.get(FACETARGS.QUERY_FILTERS.exactToken());
        for (JsonNode node : filtersNode) {
            String key = node.get(FACETARGS.NAME.exactToken()).asText();
            JsonNode queryNode = node.get(FACETARGS.QUERY.exactToken());
            final Entry<String, JsonNode> queryItem = JsonHandler.checkUnicity("RootRequest", queryNode);
            try {
                Query query = QueryParserHelper.query(queryItem.getKey(), queryItem.getValue(), adapter);
                filtersMap.put(key, query);

            } catch (InvalidCreateOperationException e) {
                throw new InvalidParseOperationException(e);
            }
        }

        List<KeyedFilter> keyFilters = new ArrayList<>();
        for (Map.Entry<String, Query> entry : filtersMap.entrySet()) {
            keyFilters.add(new KeyedFilter(entry.getKey(), getCommand(entry.getValue(), adapter)));
        }
        KeyedFilter[] keyFiltersArray = keyFilters.stream().toArray(KeyedFilter[]::new);
        FiltersAggregationBuilder filtersBuilder = AggregationBuilders.filters(facet.getName(), keyFiltersArray);
        builders.add(filtersBuilder);
    }

    /**
     * @param value
     * @return JsonNode as Object
     */

    private static final Object getAsObject(JsonNode value) {
        if (value.isBoolean()) {
            return value.asBoolean();
        } else if (value.canConvertToLong()) {
            return value.asLong();
        } else if (value.isDouble()) {
            return value.asDouble();
        } else {
            return value.asText();
        }
    }

    /**
     * Helper method for logging/dumping supported queries
     *
     * @param query
     * @param content
     */
    private static void logCommand(QUERY query, JsonNode content) {
        LOGGER.debug(String.format("Command #QUERY: %s . Content: %s", query, content));
    }

    /**
     * Helper method for logging tricky queries dealing with "id" field Aim of this log is to check if search based on
     * id field is used with other operator than eq, ne, in, nin
     *
     * @param query
     * @param content
     */
    private static void logWarnUnsupportedIdForCommand(QUERY query, JsonNode content) {
        LOGGER.warn(String.format("Command #QUERY: %s using id is not recommended. Content: %s", query, content));
    }

    /**
     * Logs a warning message for unsupported cases. Unsupported cases should/will be removed without prior notice.
     *
     * @param query the query
     * @param content the json content
     * @param message the error message
     * @deprecated Used to dump unsupported usages for queries.
     */
    private static void logUnsupportedCommand(QUERY query, JsonNode content, String message) {
        LOGGER.warn(String.format("UNSUPPORTED command #QUERY: %s. Message: %s. Content: %s", query, message, content));
    }
}
