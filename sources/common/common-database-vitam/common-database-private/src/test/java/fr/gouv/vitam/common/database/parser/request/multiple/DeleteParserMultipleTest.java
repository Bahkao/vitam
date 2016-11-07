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
package fr.gouv.vitam.common.database.parser.request.multiple;

import static fr.gouv.vitam.common.database.builder.query.QueryHelper.and;
import static fr.gouv.vitam.common.database.builder.query.QueryHelper.eq;
import static fr.gouv.vitam.common.database.builder.query.QueryHelper.exists;
import static fr.gouv.vitam.common.database.builder.query.QueryHelper.flt;
import static fr.gouv.vitam.common.database.builder.query.QueryHelper.gt;
import static fr.gouv.vitam.common.database.builder.query.QueryHelper.in;
import static fr.gouv.vitam.common.database.builder.query.QueryHelper.isNull;
import static fr.gouv.vitam.common.database.builder.query.QueryHelper.lte;
import static fr.gouv.vitam.common.database.builder.query.QueryHelper.match;
import static fr.gouv.vitam.common.database.builder.query.QueryHelper.matchPhrase;
import static fr.gouv.vitam.common.database.builder.query.QueryHelper.matchPhrasePrefix;
import static fr.gouv.vitam.common.database.builder.query.QueryHelper.missing;
import static fr.gouv.vitam.common.database.builder.query.QueryHelper.ne;
import static fr.gouv.vitam.common.database.builder.query.QueryHelper.nin;
import static fr.gouv.vitam.common.database.builder.query.QueryHelper.not;
import static fr.gouv.vitam.common.database.builder.query.QueryHelper.or;
import static fr.gouv.vitam.common.database.builder.query.QueryHelper.path;
import static fr.gouv.vitam.common.database.builder.query.QueryHelper.range;
import static fr.gouv.vitam.common.database.builder.query.QueryHelper.regex;
import static fr.gouv.vitam.common.database.builder.query.QueryHelper.search;
import static fr.gouv.vitam.common.database.builder.query.QueryHelper.size;
import static fr.gouv.vitam.common.database.builder.query.QueryHelper.term;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;

import fr.gouv.vitam.common.database.builder.query.Query;
import fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.FILTERARGS;
import fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.MULTIFILTER;
import fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.SELECTFILTER;
import fr.gouv.vitam.common.database.builder.request.multiple.Delete;
import fr.gouv.vitam.common.database.parser.request.adapter.VarNameAdapter;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogLevel;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;

@SuppressWarnings("javadoc")
public class DeleteParserMultipleTest {
    private static JsonNode exampleBothEsMd;

    private static JsonNode exampleMd;

    @BeforeClass
    public static void init() throws InvalidParseOperationException {
        VitamLoggerFactory.setLogLevel(VitamLogLevel.INFO);
        exampleMd = JsonHandler.getFromString("{ $roots : [ 'id0' ], $query : [ " + "{ $path : [ 'id1', 'id2'] }," +
            "{ $and : [ " + "{$exists : 'mavar1'}, " + "{$missing : 'mavar2'}, " + "{$isNull : 'mavar3'}, " +
            "{ $or : [ " +
            "{$in : { 'mavar4' : [1, 2, 'maval1'] }}, " + "{ $nin : { 'mavar5' : ['maval2', true] } } ] } ] }," +
            "{ $not : [ " + "{ $size : { 'mavar5' : 5 } }, " + "{ $gt : { 'mavar6' : 7 } }, " +
            "{ $lte : { 'mavar7' : 8 } } ] , $exactdepth : 4}," + "{ $not : [ " + "{ $eq : { 'mavar8' : 5 } }, " +
            "{ $ne : { 'mavar9' : 'ab' } }, " +
            "{ $range : { 'mavar10' : { $gte : 12, $lte : 20} } } ], $depth : 1}, " +
            "{ $and : [ { $term : { 'mavar14' : 'motMajuscule', 'mavar15' : 'simplemot' } } ] }, " +
            "{ $regex : { 'mavar14' : '^start?aa.*' } } " + "], " + "$filter : {$mult : false } }");
        exampleBothEsMd =
            JsonHandler.getFromString("{ $roots : [ 'id0' ], $query : [ " + "{ $path : [ 'id1', 'id2'] }," +
                "{ $and : [ " + "{$exists : 'mavar1'}, " + "{$missing : 'mavar2'}, " + "{$isNull : 'mavar3'}, " +
                "{ $or : [ " +
                "{$in : { 'mavar4' : [1, 2, 'maval1'] }}, " + "{ $nin : { 'mavar5' : ['maval2', true] } } ] } ] }," +
                "{ $not : [ " + "{ $size : { 'mavar5' : 5 } }, " + "{ $gt : { 'mavar6' : 7 } }, " +
                "{ $lte : { 'mavar7' : 8 } } ] , $exactdepth : 4}," + "{ $not : [ " + "{ $eq : { 'mavar8' : 5 } }, { " +
                "$ne : { 'mavar9' : 'ab' } }, { " +
                "$range : { 'mavar10' : { $gte : 12, $lte : 20} } } ], $depth : 1}," +
                "{ $match_phrase : { 'mavar11' : 'ceci est une phrase' }, $depth : 0}," +
                "{ $match_phrase_prefix : { 'mavar11' : 'ceci est une phrase', $max_expansions : 10 }, $depth : 0}," +
                "{ $flt : { $fields : [ 'mavar12', 'mavar13' ], $like : 'ceci est une phrase' }, $depth : 1}," +
                "{ $and : [ " +
                "{ $search : { 'mavar13' : 'ceci est une phrase' } }, " +
                "{ $regex : { 'mavar14' : '^start?aa.*' } } ] }," +
                "{ $and : [ { $term : { 'mavar14' : 'motMajuscule', 'mavar15' : 'simplemot' } } ] }, " + "{ $and : [ " +
                "{ $term : { 'mavar16' : 'motMajuscule', 'mavar17' : 'simplemot' } }, " +
                "{ $or : [ {$eq : { 'mavar19' : 'abcd' } }, { $match : { 'mavar18' : 'quelques mots' } } ] } ] }, " +
                "{ $regex : { 'mavar14' : '^start?aa.*' } } " + "], " + "$filter : {$mult : false } }");
    }

    @Test
    public void testParse() {
        try {
            final DeleteParserMultiple request1 = new DeleteParserMultiple();
            request1.parse(exampleBothEsMd);
            assertTrue("Should refuse the request since ES is not allowed",
                request1.hasFullTextQuery());
            request1.parse(exampleMd);
            assertFalse("Should accept the request since ES is not allowed",
                request1.hasFullTextQuery());
        } catch (final Exception e) {}
        try {
            final DeleteParserMultiple request1 = new DeleteParserMultiple();
            request1.parse(exampleBothEsMd);
            assertNotNull(request1);
            assertTrue("Should refuse the request since ES is not allowed",
                request1.hasFullTextQuery());
            final Delete delete = new Delete();
            delete.addRoots("id0");
            delete.addQueries(path("id1", "id2"));
            delete.addQueries(
                and().add(exists("mavar1"), missing("mavar2"), isNull("mavar3"),
                    or().add(in("mavar4", 1, 2).add("maval1"),
                        nin("mavar5", "maval2").add(true))));
            delete.addQueries(
                not().add(size("mavar5", 5), gt("mavar6", 7), lte("mavar7", 8))
                    .setExactDepthLimit(4));
            delete.addQueries(not()
                .add(eq("mavar8", 5), ne("mavar9", "ab"),
                    range("mavar10", 12, true, 20, true))
                .setDepthLimit(1));
            delete.addQueries(matchPhrase("mavar11", "ceci est une phrase")
                .setRelativeDepthLimit(0));
            delete.addQueries(matchPhrasePrefix("mavar11", "ceci est une phrase")
                .setMatchMaxExpansions(10)
                .setDepthLimit(0));
            delete.addQueries(flt("ceci est une phrase", "mavar12", "mavar13")
                .setRelativeDepthLimit(1));
            delete.addQueries(and().add(search("mavar13", "ceci est une phrase"),
                regex("mavar14", "^start?aa.*")));

            delete.addQueries(and()
                .add(term("mavar14", "motMajuscule").add("mavar15", "simplemot")));
            delete.addQueries(
                and().add(term("mavar16", "motMajuscule").add("mavar17", "simplemot"),
                    or().add(eq("mavar19", "abcd"),
                        match("mavar18", "quelques mots"))));
            delete.addQueries(regex("mavar14", "^start?aa.*"));

            delete.setMult(false);
            final DeleteParserMultiple request2 = new DeleteParserMultiple();
            request2.parse(delete.getFinalDelete());
            assertNotNull(request2);
            final List<Query> query1 = request1.getRequest().getQueries();
            final List<Query> query2 = request2.getRequest().getQueries();
            for (int i = 0; i < query1.size(); i++) {
                if (!query1.get(i).toString().equals(query2.get(i).toString())) {
                    System.err.println(query1.get(i));
                    System.err.println(query2.get(i));
                }
                assertTrue("TypeRequest should be equal",
                    query1.get(i).toString().equals(query2.get(i).toString()));
            }
            assertTrue("Filter should be equal",
                request1.getRequest().getFilter().toString()
                    .equals(request2.getRequest().getFilter().toString()));
            assertEquals(request1.getLastDepth(), request2.getLastDepth());
            assertEquals(request1.hasFullTextQuery(), request2.hasFullTextQuery());
            assertEquals(request1.getRequest().getRoots().toString(),
                request2.getRequest().getRoots().toString());
            assertEquals(request1.getRequest().getFinalDelete().toString(),
                request2.getRequest().getFinalDelete().toString());
            assertTrue("Command should be equal",
                request1.toString().equals(request2.toString()));
        } catch (final Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void testDeleteParser() throws InvalidParseOperationException {
        final DeleteParserMultiple request = new DeleteParserMultiple();
        request.parse(exampleMd);
        assertNotNull(request);
        final DeleteParserMultiple request2 = new DeleteParserMultiple(new VarNameAdapter());
        assertNotNull(request2);
    }

    @Test
    public void testFilterParse() {
        final DeleteParserMultiple request = new DeleteParserMultiple();
        final Delete delete = new Delete();
        try {
            // empty
            request.filterParse(delete.getFilter());
            assertNull("Hint should be null",
                request.getRequest().getFilter().get(SELECTFILTER.HINT.exactToken()));
            assertNull("Limit should be null", request.getRequest().getFilter()
                .get(SELECTFILTER.LIMIT.exactToken()));
            assertNull("Offset should be null", request.getRequest().getFilter()
                .get(SELECTFILTER.OFFSET.exactToken()));
            assertNull("OrderBy should be null", request.getRequest().getFilter()
                .get(SELECTFILTER.ORDERBY.exactToken()));
            assertNull("Mult should be null",
                request.getRequest().getFilter().get(MULTIFILTER.MULT.exactToken()));
            // hint set
            delete.addHintFilter(FILTERARGS.CACHE.exactToken());
            request.filterParse(delete.getFilter());
            assertEquals("Hint should be True", FILTERARGS.CACHE.exactToken(),
                request.getRequest().getFilter().get(SELECTFILTER.HINT.exactToken())
                    .get(0).asText());
            // hint reset
            delete.resetHintFilter();
            request.filterParse(delete.getFilter());
            assertNull("Hint should be null",
                request.getRequest().getFilter().get(SELECTFILTER.HINT.exactToken()));
            // multi set
            delete.setMult(false);
            request.filterParse(delete.getFilter());
            assertEquals(false,
                request.getRequest().getFilter().get(MULTIFILTER.MULT.exactToken())
                    .asBoolean());
            delete.setMult(true);
            request.filterParse(delete.getFilter());
            assertEquals(true,
                request.getRequest().getFilter().get(MULTIFILTER.MULT.exactToken())
                    .asBoolean());
        } catch (final InvalidParseOperationException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
}
