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

package fr.gouv.vitam.common.database.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.annotations.VisibleForTesting;
import fr.gouv.vitam.common.database.builder.query.action.Action;
import fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken;
import fr.gouv.vitam.common.database.parser.query.ParserTokens;
import fr.gouv.vitam.common.database.parser.request.AbstractParser;
import fr.gouv.vitam.common.database.parser.request.GlobalDatasParser;
import fr.gouv.vitam.common.database.parser.request.adapter.VarNameAdapter;
import fr.gouv.vitam.common.database.parser.request.multiple.UpdateParserMultiple;
import fr.gouv.vitam.common.database.parser.request.single.UpdateParserSingle;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.QueryPattern;
import fr.gouv.vitam.common.model.massupdate.RuleAction;
import fr.gouv.vitam.common.model.massupdate.RuleActions;
import fr.gouv.vitam.common.model.massupdate.RuleCategoryAction;

/**
 * Tools to update a Mongo document (as json) with a dsl query.
 */
public class MongoDbInMemory {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(MongoDbInMemory.class);

    private static final String RULES_KEY = "Rules";
    private static final String RULE_KEY = "Rule";
    private static final String START_DATE_KEY = "StartDate";
    private static final String END_DATE_KEY = "EndDate";
    private static final String MANAGEMENT_KEY = "_mgt";
    private static final String FINAL_ACTION_KEY = "FinalAction";

    private final JsonNode originalDocument;

    private JsonNode updatedDocument;

    private Set<String> updatedFields;

    /**
     * @param originalDocument
     */
    public MongoDbInMemory(JsonNode originalDocument) {
        this.originalDocument = originalDocument;
        updatedDocument = originalDocument.deepCopy();
        updatedFields = new HashSet<>();
    }

    /**
     * Update the originalDocument with the given request. If the Document is a MetadataDocument (Unit/ObjectGroup) it
     * should use a MultipleQuery Parser
     * @param request The given update request
     * @param isMultiple true if the UpdateParserMultiple must be used (Unit/ObjectGroup)
     * @param varNameAdapter VarNameAdapter to use
     * @return the updated document
     * @throws InvalidParseOperationException
     */
    public JsonNode getUpdateJson(JsonNode request, boolean isMultiple, VarNameAdapter varNameAdapter)
        throws InvalidParseOperationException {
        final AbstractParser<?> parser;

        if (isMultiple) {
            parser = new UpdateParserMultiple(varNameAdapter);
        } else {
            parser = new UpdateParserSingle(varNameAdapter);
        }

        parser.parse(request);

        return getUpdateJson(parser);
    }

    /**
     * Update the originalDocument with the given parser (containing the request)
     * @param requestParser The given parser containing the update request
     * @return the updated document
     * @throws InvalidParseOperationException
     */
    public JsonNode getUpdateJson(AbstractParser<?> requestParser) throws InvalidParseOperationException {
        List<Action> actions = requestParser.getRequest().getActions();
        if (actions == null || actions.isEmpty()) {
            LOGGER.info("No action on request");
            return updatedDocument;
        } else {
            for (Action action : actions) {
                final BuilderToken.UPDATEACTION req = action.getUPDATEACTION();
                final JsonNode content = action.getCurrentAction().get(req.exactToken());
                switch (req) {
                    case ADD:
                        add(req, content);
                        break;
                    case INC:
                        inc(req, content);
                        break;
                    case MIN:
                        min(req, content);
                        break;
                    case MAX:
                        max(req, content);
                        break;
                    case POP:
                        pop(req, content);
                        break;
                    case PULL:
                        pull(req, content);
                        break;
                    case PUSH:
                        push(req, content);
                        break;
                    case RENAME:
                        rename(req, content);
                        break;
                    case SET:
                        set(content);
                        break;
                    case UNSET:
                        unset(content);
                        break;
                    case SETREGEX:
                        setRegex(content);
                        break;
                    default:
                        break;
                }
            }
        }
        return updatedDocument;
    }

    /**
     * Update the originalDocument with the given ruleActions
     * @param ruleActions The given ruleActions containing the updates
     * @return the updated document
     * @throws InvalidParseOperationException
     */
    public JsonNode getUpdateJsonForRule(RuleActions ruleActions) throws InvalidParseOperationException {
        if(ruleActions != null) {
            final ObjectNode initialMgt = (ObjectNode) getOrCreateEmptyNodeByName(updatedDocument, MANAGEMENT_KEY, false);

            // deal with adds
            applyAddRuleAction(ruleActions.getAdd(), initialMgt);
            // deal with update
            applyUpdateRuleAction(ruleActions.getUpdate(), initialMgt);
            // deal with delete
            applyDeleteRuleAction(ruleActions.getDelete(), initialMgt);

            JsonHandler.setNodeInPath((ObjectNode) updatedDocument, MANAGEMENT_KEY, initialMgt, true);
        }

        return updatedDocument;
    }

    private void applyAddRuleAction(final List<Map<String, RuleCategoryAction>> ruleActions, final ObjectNode initialMgt) {
        if(ruleActions == null || ruleActions.isEmpty())
            return;

        ruleActions.stream().flatMap(item-> item.entrySet().stream()).forEach((Map.Entry<String, RuleCategoryAction> entry) -> {
            String category = entry.getKey();
            RuleCategoryAction ruleCategoryAction = entry.getValue();

            ObjectNode initialRuleCategory = (ObjectNode) getOrCreateEmptyNodeByName(initialMgt, category, false);

            // set final action if any
            String finalAction = ruleCategoryAction.getFinalAction();
            if (finalAction != null) {
                initialRuleCategory.put(FINAL_ACTION_KEY, finalAction);
            }

            // add rules
            if (ruleCategoryAction.getRules() != null && !ruleCategoryAction.getRules().isEmpty()) {
                ArrayNode initialRules = (ArrayNode) getOrCreateEmptyNodeByName(initialRuleCategory, RULES_KEY, true);
                ruleCategoryAction.getRules().forEach(ruleAction -> {
                    if (!hasRuleDefined(ruleAction.getRule(), initialRules)) {
                        JsonNode newRule = getJsonNodeFromRuleAction(ruleAction);
                        initialRules.add(newRule);
                    }
                });
                initialRuleCategory.set(RULES_KEY, initialRules);
            }

            // set category
            initialMgt.set(category, initialRuleCategory);
        });
    }

    private boolean hasRuleDefined(final String ruleId, final ArrayNode rules) {
        Iterator<JsonNode> nodes = rules.iterator();
        while (nodes.hasNext()) {
            if (ruleId.equals(nodes.next().get("Rule").textValue())) {
                return true;
            }
        }
        return false;
    }

    private void applyUpdateRuleAction(final List<Map<String, RuleCategoryAction>> ruleActions, final ObjectNode initialMgt) {
        if(ruleActions == null || ruleActions.isEmpty())
            return;

        ruleActions.stream().flatMap(item-> item.entrySet().stream()).forEach((Map.Entry<String, RuleCategoryAction> entry) -> {
            String category = entry.getKey();
            RuleCategoryAction ruleCategoryAction = entry.getValue();
            ObjectNode initialRuleCategory = (ObjectNode) getOrCreateEmptyNodeByName(initialMgt, category, false);

            if (ruleCategoryAction.getRules() != null && !ruleCategoryAction.getRules().isEmpty()) {
                Map<String, RuleAction> rulesToUpdate = ruleCategoryAction.getRules().stream().collect(Collectors.toMap(RuleAction::getOldRule, Function.identity()));
                ArrayNode initialRules = (ArrayNode) getOrCreateEmptyNodeByName(initialRuleCategory, RULES_KEY, true);
                Iterator<JsonNode> it = initialRules.iterator();
                while (it.hasNext()) {
                    ObjectNode node = (ObjectNode) it.next();
                    String actualRule = node.get(RULE_KEY).asText();
                    if (rulesToUpdate.keySet().contains(actualRule)) {
                        updateJsonNodeUsingRuleAction(node, rulesToUpdate.get(actualRule));
                    }
                }
                initialRuleCategory.set(RULES_KEY, initialRules);
            }

            // set category
            initialMgt.set(category, initialRuleCategory);
        });
    }

    private void applyDeleteRuleAction(final List<Map<String, RuleCategoryAction>> ruleActions, final ObjectNode initialMgt) {
        if(ruleActions == null || ruleActions.isEmpty())
            return;

        ruleActions.stream().flatMap(item-> item.entrySet().stream()).forEach((Map.Entry<String, RuleCategoryAction> entry) -> {
            String category = entry.getKey();
            RuleCategoryAction ruleCategoryAction = entry.getValue();
            ObjectNode initialRuleCategory = (ObjectNode) getOrCreateEmptyNodeByName(initialMgt, category, false);

            if (ruleCategoryAction.getRules() != null && !ruleCategoryAction.getRules().isEmpty()) {
                List<String> rulesToDelete = ruleCategoryAction.getRules().stream().map(rule -> rule.getRule()).collect(Collectors.toList());
                ArrayNode initialRules = (ArrayNode) getOrCreateEmptyNodeByName(initialRuleCategory, RULES_KEY, true);
                ArrayNode filteredRules = JsonHandler.createArrayNode();
                initialRules.forEach(node -> {
                    if (!rulesToDelete.contains(node.get(RULE_KEY).asText())) {
                        filteredRules.add(node);
                    }
                });
                initialRuleCategory.set(RULES_KEY, filteredRules);
            }

            // set category
            initialMgt.set(category, initialRuleCategory);
        });
    }

    private JsonNode getOrCreateEmptyNodeByName(JsonNode parent, String fieldName, boolean acceptArray) {
        return parent.hasNonNull(fieldName) ? parent.get(fieldName) : (acceptArray ? JsonHandler.createArrayNode() : JsonHandler.createObjectNode());
    }

    private JsonNode getJsonNodeFromRuleAction(RuleAction ruleAction) {
        ObjectNode newRule = JsonHandler.createObjectNode();
        newRule.put(RULE_KEY, ruleAction.getRule());
        newRule.put(START_DATE_KEY, ruleAction.getStartDate());
        newRule.put(END_DATE_KEY, ruleAction.getEndDate());
        return newRule;
    }

    private void updateJsonNodeUsingRuleAction(ObjectNode node, RuleAction ruleAction) {
        if (ruleAction.getRule() != null) {
            node.put(RULE_KEY, ruleAction.getRule());
        }
        if (ruleAction.getStartDate() != null) {
            node.put(START_DATE_KEY, ruleAction.getStartDate());
            node.put(END_DATE_KEY, ruleAction.getEndDate());
        }
    }

    /**
     * Reset the updatedDocument with the original values
     */
    @VisibleForTesting
    public void resetUpdatedAU() {
        updatedDocument = originalDocument.deepCopy();
        updatedFields.clear();
    }

    private void inc(final BuilderToken.UPDATEACTION req, final JsonNode content)
        throws InvalidParseOperationException {
        final Map.Entry<String, JsonNode> element = JsonHandler.checkUnicity(req.exactToken(), content);
        final String fieldName = element.getKey();
        Double nodeValue = getNumberValue(req.name(), fieldName);

        JsonNode actionValue = element.getValue();
        if (!actionValue.isNumber()) {
            throw new InvalidParseOperationException("[" + "INC" + "]Action argument (" + actionValue +
                ") cannot be converted as number for field " + fieldName);
        }

        String[] fieldNamePath = fieldName.split("[.]");
        String lastNodeName = fieldNamePath[fieldNamePath.length - 1];
        ((ObjectNode) JsonHandler.getParentNodeByPath(updatedDocument, fieldName, false)).put(lastNodeName,
            element.getValue().asLong() + nodeValue);
        updatedFields.add(fieldName);
    }

    private void unset(final JsonNode content) {
        final Iterator<JsonNode> iterator = content.elements();
        while (iterator.hasNext()) {
            final JsonNode element = iterator.next();
            String fieldName = element.asText();
            JsonNode node = JsonHandler.getParentNodeByPath(updatedDocument, fieldName, false);
            if(node != null) {
                String[] fieldNamePath = fieldName.split("[.]");
                String lastNodeName = fieldNamePath[fieldNamePath.length - 1];
                ((ObjectNode) node).remove(lastNodeName);
                updatedFields.add(fieldName);
            }
        }
    }

    private void set(final JsonNode content) throws InvalidParseOperationException {
        final Iterator<Map.Entry<String, JsonNode>> iterator = content.fields();
        while (iterator.hasNext()) {
            final Map.Entry<String, JsonNode> element = iterator.next();
            String fieldName = element.getKey();
            if (ParserTokens.PROJECTIONARGS.isAnArray(fieldName)) {
                ArrayNode arrayNode = GlobalDatasParser.getArray(element.getValue());
                JsonHandler.setNodeInPath((ObjectNode) updatedDocument, fieldName, arrayNode, true);
            } else {
                JsonHandler.setNodeInPath((ObjectNode) updatedDocument, fieldName, element.getValue(), true);
            }
            updatedFields.add(fieldName);
        }
    }

    private void setRegex(final JsonNode content) throws InvalidParseOperationException {
        QueryPattern queryPattern = JsonHandler.getFromJsonNodeLowerCamelCase(content, QueryPattern.class);
        String fieldName = queryPattern.getTarget();
        ObjectNode parentObjectNode = (ObjectNode) JsonHandler.getParentNodeByPath(updatedDocument, fieldName, false);

        String lastFieldName = JsonHandler.getLastFieldName(fieldName);
        if (parentObjectNode == null || !parentObjectNode.has(lastFieldName)) {
            return;
        }
        JsonNode jsonNode = parentObjectNode.get(lastFieldName);


        Pattern pattern = Pattern.compile(queryPattern.getControlPattern());

        if (jsonNode.isTextual()) {

            // Update text field
            String stringToSearch = jsonNode.asText();
            String newString = replaceAll(pattern, stringToSearch, queryPattern.getUpdatePattern());
            if (!stringToSearch.equals(newString)) {
                parentObjectNode.put(lastFieldName, newString);
                updatedFields.add(fieldName);
            }

        } else if (jsonNode.isArray()) {

            // Update array field
            ArrayNode arrayNode = (ArrayNode) jsonNode;
            for (int i = 0; i < arrayNode.size(); i++) {
                JsonNode item = arrayNode.get(i);
                if (item.isTextual()) {
                    String stringToSearch = item.asText();
                    String newString = replaceAll(pattern, stringToSearch, queryPattern.getUpdatePattern());
                    if (!stringToSearch.equals(newString)) {
                        arrayNode.set(i, new TextNode(newString));
                        updatedFields.add(fieldName);
                    }
                }
            }
        }
    }

    private String replaceAll(Pattern pattern, String stringToSearch, String replacement) {
        return pattern.matcher(stringToSearch).replaceAll(replacement);
    }

    private void min(final BuilderToken.UPDATEACTION req, final JsonNode content)
        throws InvalidParseOperationException {
        final Map.Entry<String, JsonNode> element = JsonHandler.checkUnicity(req.exactToken(), content);
        final String fieldName = element.getKey();
        Double nodeValue = getNumberValue(req.name(), fieldName);

        JsonNode actionValue = element.getValue();
        if (!actionValue.isNumber()) {
            throw new InvalidParseOperationException("[" + "MIN" + "]Action argument (" + actionValue +
                ") cannot be converted as number for field " + fieldName);
        }

        String[] fieldNamePath = fieldName.split("[.]");
        String lastNodeName = fieldNamePath[fieldNamePath.length - 1];
        ((ObjectNode) JsonHandler.getParentNodeByPath(updatedDocument, fieldName, false)).put(lastNodeName,
            Math.min(element.getValue().asDouble(), nodeValue));
        updatedFields.add(fieldName);
    }

    private void max(final BuilderToken.UPDATEACTION req, final JsonNode content)
        throws InvalidParseOperationException {
        final Map.Entry<String, JsonNode> element = JsonHandler.checkUnicity(req.exactToken(), content);
        final String fieldName = element.getKey();
        Double nodeValue = getNumberValue(req.name(), fieldName);

        JsonNode actionValue = element.getValue();
        if (!actionValue.isNumber()) {
            throw new InvalidParseOperationException("[" + "MAX" + "]Action argument (" + actionValue +
                ") cannot be converted as number for field " + fieldName);
        }

        String[] fieldNamePath = fieldName.split("[.]");
        String lastNodeName = fieldNamePath[fieldNamePath.length - 1];
        ((ObjectNode) JsonHandler.getParentNodeByPath(updatedDocument, fieldName, false)).put(lastNodeName,
            Math.max(element.getValue().asDouble(), nodeValue));
        updatedFields.add(fieldName);
    }

    private void rename(final BuilderToken.UPDATEACTION req, final JsonNode content)
        throws InvalidParseOperationException {
        final Map.Entry<String, JsonNode> element = JsonHandler.checkUnicity(req.exactToken(), content);
        final String fieldName = element.getKey();
        JsonNode value = JsonHandler.getNodeByPath(updatedDocument, fieldName, false);
        if (value == null) {
            throw new InvalidParseOperationException(
                "[" + "RENAME" + "]Can't rename field " + fieldName + " because it doesn't exist");
        }

        JsonNode parent = JsonHandler.getParentNodeByPath(updatedDocument, fieldName, false);
        String[] fieldNamePath = fieldName.split("[.]");
        String lastNodeName = fieldNamePath[fieldNamePath.length - 1];
        ((ObjectNode) parent).remove(lastNodeName);

        String newFieldName = element.getValue().asText();
        JsonHandler.setNodeInPath((ObjectNode) updatedDocument, newFieldName, value, true);
        updatedFields.add(fieldName);
        updatedFields.add(newFieldName);
    }

    private void push(final BuilderToken.UPDATEACTION req, final JsonNode content)
        throws InvalidParseOperationException {
        final Map.Entry<String, JsonNode> element = JsonHandler.checkUnicity(req.exactToken(), content);
        final String fieldName = element.getKey();
        final ArrayNode array = (ArrayNode) element.getValue().get(BuilderToken.UPDATEACTIONARGS.EACH.exactToken());
        ArrayNode node = (ArrayNode) getArrayValue(req.name(), fieldName);
        final Iterator<JsonNode> iterator = array.elements();
        while (iterator.hasNext()) {
            node.add(iterator.next());
        }
        updatedFields.add(fieldName);
    }

    private void pull(final BuilderToken.UPDATEACTION req, final JsonNode content)
        throws InvalidParseOperationException {
        final Map.Entry<String, JsonNode> element = JsonHandler.checkUnicity(req.exactToken(), content);
        final String fieldName = element.getKey();
        final ArrayNode array = (ArrayNode) element.getValue().get(BuilderToken.UPDATEACTIONARGS.EACH.exactToken());
        ArrayNode node = (ArrayNode) getArrayValue(req.name(), fieldName);
        final List<Integer> indexesToRemove = new ArrayList<>();
        final Iterator<JsonNode> iterator = array.elements();
        // TODO: optimize ! review loop order for the best !
        while (iterator.hasNext()) {
            JsonNode pullValue = iterator.next();
            Iterator<JsonNode> originIt = node.elements();
            int index = 0;
            while (originIt.hasNext()) {
                if (originIt.next().asText().equals(pullValue.asText())) {
                    indexesToRemove.add(index);
                }
                index++;
            }
        }
        Collections.sort(indexesToRemove);
        for (int i = indexesToRemove.size() - 1; i >= 0; i--) {
            node.remove(indexesToRemove.get(i));
        }
        if(!indexesToRemove.isEmpty()) {
            updatedFields.add(fieldName);
        }
    }

    private void add(final BuilderToken.UPDATEACTION req, final JsonNode content)
        throws InvalidParseOperationException {
        final Map.Entry<String, JsonNode> element = JsonHandler.checkUnicity(req.exactToken(), content);
        final String fieldName = element.getKey();
        final ArrayNode array = (ArrayNode) element.getValue().get(BuilderToken.UPDATEACTIONARGS.EACH.exactToken());

        ArrayNode node = (ArrayNode) getArrayValue(req.name(), fieldName);
        final Iterator<JsonNode> iterator = array.elements();

        // TODO: optimize ! review loop order for the best !
        while (iterator.hasNext()) {
            JsonNode newNode = iterator.next();
            Iterator<JsonNode> originIt = node.elements();
            boolean mustAdd = true;
            while (originIt.hasNext()) {
                if (originIt.next().asText().equals(newNode.asText())) {
                    mustAdd = false;
                }
            }
            if (mustAdd) {
                node.add(newNode);
                updatedFields.add(fieldName);
            }
        }
    }

    private void pop(final BuilderToken.UPDATEACTION req, final JsonNode content)
        throws InvalidParseOperationException {
        final Map.Entry<String, JsonNode> element = JsonHandler.checkUnicity(req.exactToken(), content);
        final String fieldName = element.getKey();
        ArrayNode node = (ArrayNode) getArrayValue(req.name(), fieldName);

        JsonNode actionValue = element.getValue();
        if (!actionValue.isNumber()) {
            throw new InvalidParseOperationException("[" + req.name() + "]Action argument (" + actionValue +
                ") cannot be converted as number for field " + fieldName);
        }

        int numberOfPop = Math.abs(actionValue.asInt());

        if(numberOfPop == 0) {
            return;
        }

        if (numberOfPop > node.size()) {
            throw new InvalidParseOperationException(
                "Cannot pop " + numberOfPop + "items from the field '" + fieldName + "' because it has less items");
        }
        if (actionValue.asInt() < 0) {
            for (int i = 0; i < numberOfPop; i++) {
                node.remove(0);
            }
        } else {
            for (int i = 0; i < numberOfPop; i++) {
                node.remove(node.size() - 1);
            }
        }
        updatedFields.add(fieldName);
    }

    private double getNumberValue(final String actionName, final String fieldName)
        throws InvalidParseOperationException {
        JsonNode node = JsonHandler.getNodeByPath(updatedDocument, fieldName, false);
        if (node == null || !node.isNumber()) {
            String message = "This field '" + fieldName + "' is not a number, cannot do '" + actionName +
                "' action: " + node + " or unknow fieldName";
            LOGGER.error(message);
            throw new InvalidParseOperationException(message);
        }
        return node.asDouble();
    }

    private JsonNode getArrayValue(final String actionName, final String fieldName)
        throws InvalidParseOperationException {
        JsonNode node = JsonHandler.getNodeByPath(updatedDocument, fieldName, false);
        if (node == null || node instanceof NullNode) {
            LOGGER.info("Action '" + actionName + "' in item previously null '" + fieldName + "' or unknow");
            ObjectNode updatedDocumentAsObject = (ObjectNode) updatedDocument;
            updatedDocumentAsObject.set(fieldName, JsonHandler.createArrayNode());
            return updatedDocument.get(fieldName);
        }
        if (!node.isArray()) {
            String message =
                "This field '" + fieldName + "' is not an array, cannot do '" + actionName + "' action";
            LOGGER.error(message);
            throw new InvalidParseOperationException(message);
        }
        return node;
    }

    public Set<String> getUpdatedFields() {
        return updatedFields;
    }
}
