package fr.gouv.vitam.worker.core.plugin.reclassification.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import fr.gouv.vitam.common.database.builder.query.QueryHelper;
import fr.gouv.vitam.common.database.builder.query.VitamFieldsHelper;
import fr.gouv.vitam.common.database.builder.request.multiple.SelectMultiQuery;
import fr.gouv.vitam.common.database.parser.request.multiple.SelectParserMultiple;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.model.UnitType;
import fr.gouv.vitam.common.model.administration.AccessContractModel;
import fr.gouv.vitam.metadata.client.MetaDataClient;
import fr.gouv.vitam.worker.core.plugin.reclassification.model.UnitGraphInfo;
import org.apache.commons.collections4.SetUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class UnitGraphInfoLoaderTest {

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private MetaDataClient metaDataClient;

    private UnitGraphInfoLoader unitGraphInfoLoader = new UnitGraphInfoLoader();

    private static final String originatingAgency = "MySP";
    private static final String includedRootId = "IncludedRootId";
    private static final String excludedRootId = "ExcludedRootId";

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testSelectUnitsByQueryDslAndAccessContract() throws Exception {

        // Given
        AccessContractModel accessContractModel = createAccessContractWithRestrictions();

        SelectMultiQuery selectMultiQuery = new SelectMultiQuery();
        selectMultiQuery.addQueries(QueryHelper.in(VitamFieldsHelper.id(), "MyId1", "MyId2", "MyId3"));

        doReturn(buildResponseJsonNode("MyId1", "MyId3"))
            .when(metaDataClient).selectUnits((any()));

        // When
        Set<String> ids = unitGraphInfoLoader
            .selectUnitsByQueryDslAndAccessContract(metaDataClient, selectMultiQuery, accessContractModel);

        // Then
        assertThat(ids).containsExactlyInAnyOrder("MyId1", "MyId3");

        // Insure access contract restrictions applied
        ArgumentCaptor<JsonNode> queryArgumentCaptor = ArgumentCaptor.forClass(JsonNode.class);
        verify(metaDataClient).selectUnits((queryArgumentCaptor.capture()));
        String query = JsonHandler.unprettyPrint(queryArgumentCaptor.getValue());
        assertThat(query).contains(originatingAgency);
        assertThat(query).contains(includedRootId);
        assertThat(query).contains(excludedRootId);
        assertThat(query).contains("MyId1");
    }

    @Test
    public void testSelectUnitsByIdsAndAccessContract_emptySet() throws Exception {

        // Given
        AccessContractModel accessContractModel = createAccessContractWithRestrictions();

        SelectMultiQuery selectMultiQuery = new SelectMultiQuery();
        selectMultiQuery.addQueries(QueryHelper.in(VitamFieldsHelper.id(), "MyId1", "MyId2", "MyId3"));

        doReturn(buildResponseJsonNode())
            .when(metaDataClient).selectUnits((any()));

        // When
        Set<String> foundIds = unitGraphInfoLoader
            .selectUnitsByIdsAndAccessContract(metaDataClient, SetUtils.emptySet(), accessContractModel);

        // Then
        assertThat(foundIds).isEmpty();
        verifyZeroInteractions(metaDataClient);
    }

    @Test
    public void testSelectUnitsByIdsAndAccessContract_LotsOfUnitIds() throws Exception {

        // Given
        AccessContractModel accessContractModel = createAccessContractWithRestrictions();

        SelectMultiQuery selectMultiQuery = new SelectMultiQuery();
        selectMultiQuery.addQueries(QueryHelper.in(VitamFieldsHelper.id(), "MyId1", "MyId2", "MyId3"));

        Set<String> ids = IntStream.rangeClosed(1, UnitGraphInfoLoader.MAX_ELASTIC_SEARCH_IN_REQUEST_SIZE + 1)
            .mapToObj(i -> "MyId" + i)
            .collect(Collectors.toSet());

        when(metaDataClient.selectUnits(any())).thenReturn(
            buildResponseJsonNode("MyId1", "MyId2"),
            buildResponseJsonNode("MyId1001"));

        // When
        Set<String> foundIds = unitGraphInfoLoader
            .selectUnitsByIdsAndAccessContract(metaDataClient, ids, accessContractModel);

        // Then
        assertThat(foundIds).containsExactlyInAnyOrder("MyId1", "MyId2", "MyId1001");
        ArgumentCaptor<JsonNode> queryArgumentCaptor = ArgumentCaptor.forClass(JsonNode.class);
        verify(metaDataClient, times(2)).selectUnits((queryArgumentCaptor.capture()));

        // Insure access contract restrictions applied
        String query = JsonHandler.unprettyPrint(queryArgumentCaptor.getValue());
        assertThat(query).contains(originatingAgency);
        assertThat(query).contains(includedRootId);
        assertThat(query).contains(excludedRootId);
    }

    private AccessContractModel createAccessContractWithRestrictions() {
        AccessContractModel accessContractModel = new AccessContractModel();
        accessContractModel.setEveryOriginatingAgency(false);
        accessContractModel.setOriginatingAgencies(new HashSet<>(Arrays.asList(originatingAgency)));
        accessContractModel.setRootUnits(new HashSet<>(Arrays.asList(includedRootId)));
        accessContractModel.setExcludedRootUnits(new HashSet<>(Arrays.asList(excludedRootId)));
        return accessContractModel;
    }

    private JsonNode buildResponseJsonNode(String... ids) {

        ArrayNode results = JsonHandler.createArrayNode();
        for (String id : ids) {
            results.add(JsonHandler.createObjectNode().put("#id", id));
        }
        return JsonHandler.createObjectNode()
            .set("$results", results);
    }

    @Test
    public void selectAllUnitGraphByIds()
        throws Exception {

        // Given : basic graph
        /*
         *    1   4   5
         *    ↑ ↗   ↗ ↑
         *    2 ➝ 6   9
         *    ↑   ↑   ↑
         *    3   7   10
         *    ↑       ↑
         *    8       11
         */

        Map<String, List<String>> unitsWithParents = new HashMap<>();
        unitsWithParents.put("1", Arrays.asList());
        unitsWithParents.put("2", Arrays.asList("1", "4", "6"));
        unitsWithParents.put("3", Arrays.asList("2"));
        unitsWithParents.put("4", Arrays.asList());
        unitsWithParents.put("5", Arrays.asList());
        unitsWithParents.put("6", Arrays.asList("5"));
        unitsWithParents.put("7", Arrays.asList("6"));
        unitsWithParents.put("8", Arrays.asList("3"));
        unitsWithParents.put("9", Arrays.asList("5"));
        unitsWithParents.put("10", Arrays.asList("9"));
        unitsWithParents.put("11", Arrays.asList("10"));

        when(metaDataClient.selectUnits(any())).then(args -> {

            // Parse { $in: "#id": [] } query
            JsonNode dsl = args.getArgument(0);
            SelectParserMultiple parserMultiple = new SelectParserMultiple();
            parserMultiple.parse(dsl);
            ArrayNode jsonIds = (ArrayNode) parserMultiple.getRequest().getQueries().get(0).getNode("$in").get(VitamFieldsHelper.id());
            Set<String> ids = new HashSet<>();
            jsonIds.elements().forEachRemaining(e -> ids.add(e.asText()));

            // Return expected units by ids
            ArrayNode results = JsonHandler.createArrayNode();
            for (String id : ids) {
                results.add(JsonHandler.createObjectNode()
                    .put(VitamFieldsHelper.id(), id)
                    .put(VitamFieldsHelper.unitType(), UnitType.INGEST.name())
                    .set(VitamFieldsHelper.unitups(), JsonHandler.toJsonNode(unitsWithParents.get(id))));
            }
            return JsonHandler.createObjectNode().set("$results", results);

        });

        // When
        Map<String, UnitGraphInfo> unitGraphInfoMap =
            unitGraphInfoLoader.selectAllUnitGraphByIds(metaDataClient, new HashSet<>(Arrays.asList("8", "10")));

        // Then
        assertThat(unitGraphInfoMap).hasSize(9);
        assertThat(unitGraphInfoMap.keySet()).containsExactlyInAnyOrder("1", "2", "3", "4", "5", "6", "8", "9", "10");
        // Expected full parent graph loading in 4 invocations
        // > 8, 10
        // > 3, 9
        // > 2, 5
        // > 1, 4, 6
        verify(metaDataClient, times(4)).selectUnits(any());
    }
}
