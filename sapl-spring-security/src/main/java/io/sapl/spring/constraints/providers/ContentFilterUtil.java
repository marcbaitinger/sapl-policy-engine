/*
 * Copyright © 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.spring.constraints.providers;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.reactivestreams.Publisher;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.MapFunction;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@UtilityClass
public class ContentFilterUtil {

	private static final String NOT_A_VALID_PREDICATE_CONDITION = "Not a valid predicate condition: ";
	private static final String DISCLOSE_LEFT                   = "discloseLeft";
	private static final String DISCLOSE_RIGHT                  = "discloseRight";
	private static final String REPLACEMENT                     = "replacement";
	private static final String REPLACE                         = "replace";
	private static final String BLACKEN                         = "blacken";
	private static final String DELETE                          = "delete";
	private static final String PATH                            = "path";
	private static final String ACTIONS                         = "actions";
	private static final String CONDITIONS                      = "conditions";
	private static final String VALUE                           = "value";
	private static final String EQUALS                          = "==";
	private static final String NEQ                             = "!=";
	private static final String GEQ                             = ">=";
	private static final String LEQ                             = "<=";
	private static final String GT                              = ">";
	private static final String LT                              = "<";
	private static final String REGEX                           = "=~";
	private static final String TYPE                            = "type";
	private static final String BLACK_SQUARE                    = "█";
	private static final String UNDEFINED_KEY_S                 = "An action does not declare '%s'.";
	private static final String VALUE_NOT_INTEGER_S             = "An action's '%s' is not an integer.";
	private static final String VALUE_NOT_TEXTUAL_S             = "An action's '%s' is not textual.";
	private static final String PATH_NOT_TEXTUAL                = "The constraint indicates a text node to be blackened. However, the node identified by the path is not a text note.";
	private static final String NO_REPLACEMENT_SPECIFIED        = "The constraint indicates a text node to be replaced. However, the action does not specify a 'replacement'.";
	private static final String REPLACEMENT_NOT_TEXTUAL         = "'replacement' of 'blacken' action is not textual.";
	private static final String UNKNOWN_ACTION_S                = "Unknown action type: '%s'.";
	private static final String ACTION_NOT_AN_OBJECT            = "An action in 'actions' is not an object.";
	private static final String ACTIONS_NOT_AN_ARRAY            = "'actions' is not an array.";

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	public static UnaryOperator<Object> getHandler(JsonNode constraint, ObjectMapper objectMapper) {
		var predicate      = predicateFromConditions(constraint, objectMapper);
		var transformation = getTransformationHandler(constraint, objectMapper);

		return payload -> {
			if (payload == null)
				return null;
			if (payload instanceof Optional<?> optional)
				return optional.map(x -> mapElement(x, transformation, predicate));
			if (payload instanceof List<?> list)
				return mapListContents(list, transformation, predicate);
			if (payload instanceof Set<?> set)
				return mapSetContents(set, transformation, predicate);
			if (payload instanceof Publisher<?> publisher)
				return mapPublisherContents(publisher, transformation, predicate);
			if (payload instanceof Object[] array) {
				var filteredAsList = mapListContents(Arrays.asList(array), transformation, predicate);
				var resultArray    = Array.newInstance(payload.getClass().getComponentType(), filteredAsList.size());

				var i = 0;
				for (var x : filteredAsList) {
					Array.set(resultArray, i++, x);
				}
				return resultArray;
			}

			return mapElement(payload, transformation, predicate);
		};
	}

	private static Object mapPublisherContents(Publisher<?> payload, UnaryOperator<Object> transformation,
			Predicate<Object> predicate) {
		if (payload instanceof Mono<?> mono) {
			return mono.map(element -> mapElement(element, transformation, predicate));
		}
		return ((Flux<?>) payload).map(element -> mapElement(element, transformation, predicate));
	}

	private static Object mapElement(Object payload, UnaryOperator<Object> transformation,
			Predicate<Object> predicate) {
		if (predicate.test(payload))
			return transformation.apply(payload);

		return payload;
	}

	private static List<?> mapListContents(Collection<?> payload, UnaryOperator<Object> transformation,
			Predicate<Object> predicate) {
		return payload.stream().map(o -> mapElement(o, transformation, predicate)).toList();
	}

	private static Set<?> mapSetContents(Collection<?> payload, UnaryOperator<Object> transformation,
			Predicate<Object> predicate) {
		return payload.stream().map(o -> mapElement(o, transformation, predicate)).collect(Collectors.toSet());
	}

	public static Predicate<Object> predicateFromConditions(JsonNode constraint, ObjectMapper objectMapper) {
		assertConstraintIsAnObjectNode(constraint);
		Predicate<Object> predicate = anything -> true;
		if (noConditionsPresent(constraint))
			return predicate;

		assertConditionsIsAnArrayNode(constraint);

		var conditions = (ArrayNode) constraint.get(CONDITIONS);
		for (var condition : conditions) {
			var newPredicate      = conditionToPredicate(condition, objectMapper);
			var previousPredicate = predicate;
			predicate = x -> previousPredicate.test(x) && newPredicate.test(x);
		}
		return mapPathNotFoundToAccessDeniedException(predicate);
	}

	private static void assertConstraintIsAnObjectNode(JsonNode constraint) {
		if (constraint == null || !constraint.isObject())
			throw new IllegalArgumentException("Not a valid constraint. Expected a JSON Object");

	}

	private static Predicate<Object> mapPathNotFoundToAccessDeniedException(Predicate<Object> predicate) {
		return x -> {
			try {
				return predicate.test(x);
			} catch (PathNotFoundException e) {
				log.warn(
						"Error evaluating a constraint predicate. The path defined in the constraint is not present in the data.",
						e);
				throw new AccessDeniedException("Constraint enforcement failed.");
			}
		};
	}

	private static Predicate<Object> conditionToPredicate(JsonNode condition, ObjectMapper objectMapper) {
		if (!condition.isObject())
			throw new IllegalArgumentException(NOT_A_VALID_PREDICATE_CONDITION + condition.toString());

		if (!condition.has(PATH) || !condition.get(PATH).isTextual())
			throw new IllegalArgumentException(NOT_A_VALID_PREDICATE_CONDITION + condition.toString());

		var path = condition.get(PATH).textValue();

		if (!condition.has(TYPE) ||
				!condition.get(TYPE).isTextual())
			throw new IllegalArgumentException(NOT_A_VALID_PREDICATE_CONDITION + condition.toString());

		var type = condition.get(TYPE).textValue();

		if (!condition.has(VALUE))
			throw new IllegalArgumentException(NOT_A_VALID_PREDICATE_CONDITION + condition.toString());

		var jsonPathConfiguration = Configuration.builder().jsonProvider(new JacksonJsonNodeJsonProvider(objectMapper))
				.build();

		if (EQUALS.equals(type))
			return equalsCondition(condition, path, jsonPathConfiguration, objectMapper);

		if (NEQ.equals(type))
			return Predicate.not(equalsCondition(condition, path, jsonPathConfiguration, objectMapper));

		if (GEQ.equals(type))
			return geqCondition(condition, path, jsonPathConfiguration, objectMapper);

		if (LEQ.equals(type))
			return leqCondition(condition, path, jsonPathConfiguration, objectMapper);

		if (LT.equals(type))
			return ltCondition(condition, path, jsonPathConfiguration, objectMapper);

		if (GT.equals(type))
			return gtCondition(condition, path, jsonPathConfiguration, objectMapper);

		if (REGEX.equals(type))
			return regexCondition(condition, path, jsonPathConfiguration, objectMapper);

		throw new IllegalArgumentException(NOT_A_VALID_PREDICATE_CONDITION + condition);
	}

	private static Predicate<Object> regexCondition(JsonNode condition, String path,
			Configuration jsonPathConfiguration, ObjectMapper objectMapper) {

		if (!condition.get(VALUE).isTextual())
			throw new IllegalArgumentException(NOT_A_VALID_PREDICATE_CONDITION + condition.toString());

		var regex = Pattern.compile(condition.get(VALUE).textValue());

		return original -> {
			var node = getNodeAtPath(original, path, jsonPathConfiguration, objectMapper);
			if (!node.isTextual())
				return false;
			return regex.asMatchPredicate().test(node.textValue());
		};
	}

	private static Predicate<Object> leqCondition(JsonNode condition, String path, Configuration jsonPathConfiguration,
			ObjectMapper objectMapper) {
		if (!condition.get(VALUE).isNumber())
			throw new IllegalArgumentException(NOT_A_VALID_PREDICATE_CONDITION + condition.toString());

		var value = condition.get(VALUE).asDouble();

		return original -> {
			var node = getNodeAtPath(original, path, jsonPathConfiguration, objectMapper);
			if (!node.isNumber())
				return false;
			return node.asDouble() <= value;
		};
	}

	private static Predicate<Object> geqCondition(JsonNode condition, String path, Configuration jsonPathConfiguration,
			ObjectMapper objectMapper) {
		if (!condition.get(VALUE).isNumber())
			throw new IllegalArgumentException(NOT_A_VALID_PREDICATE_CONDITION + condition.toString());

		var value = condition.get(VALUE).asDouble();

		return original -> {
			var node = getNodeAtPath(original, path, jsonPathConfiguration, objectMapper);
			if (!node.isNumber())
				return false;
			return node.asDouble() >= value;
		};
	}

	private static Predicate<Object> ltCondition(JsonNode condition, String path, Configuration jsonPathConfiguration,
			ObjectMapper objectMapper) {
		if (!condition.get(VALUE).isNumber())
			throw new IllegalArgumentException(NOT_A_VALID_PREDICATE_CONDITION + condition.toString());

		var value = condition.get(VALUE).asDouble();

		return original -> {
			var node = getNodeAtPath(original, path, jsonPathConfiguration, objectMapper);
			if (!node.isNumber())
				return false;
			return node.asDouble() < value;
		};
	}


	private static Predicate<Object> gtCondition(JsonNode condition, String path, Configuration jsonPathConfiguration,
			ObjectMapper objectMapper) {
		if (!condition.get(VALUE).isNumber())
			throw new IllegalArgumentException(NOT_A_VALID_PREDICATE_CONDITION + condition.toString());

		var value = condition.get(VALUE).asDouble();

		return original -> {
			var node = getNodeAtPath(original, path, jsonPathConfiguration, objectMapper);
			if (!node.isNumber())
				return false;
			return node.asDouble() > value;
		};
	}
	
	private static Predicate<Object> numberEqCondition(JsonNode condition, String path,
			Configuration jsonPathConfiguration, ObjectMapper objectMapper) {
		var value = condition.get(VALUE).asDouble();

		return original -> {
			var node = getNodeAtPath(original, path, jsonPathConfiguration, objectMapper);
			if (!node.isNumber())
				return false;
			return value == node.asDouble();
		};
	}

	private static Predicate<Object> equalsCondition(JsonNode condition, String path,
			Configuration jsonPathConfiguration, ObjectMapper objectMapper) {
		var valueNode = condition.get(VALUE);
		if (valueNode.isNumber())
			return numberEqCondition(condition, path, jsonPathConfiguration, objectMapper);

		if (!valueNode.isTextual())
			throw new IllegalArgumentException(NOT_A_VALID_PREDICATE_CONDITION + condition.toString());

		var value = valueNode.textValue();

		return original -> {
			var node = getNodeAtPath(original, path, jsonPathConfiguration, objectMapper);
			if (!node.isTextual())
				return false;
			return value.equals(node.textValue());
		};
	}

	private static JsonNode getNodeAtPath(Object original, String path, Configuration jsonPathConfiguration,
			ObjectMapper objectMapper) {
		var originalJsonNode = objectMapper.valueToTree(original);
		var jsonContext      = JsonPath.using(jsonPathConfiguration).parse(originalJsonNode);
		return (JsonNode) jsonContext.read(path);
	}

	private static boolean noConditionsPresent(JsonNode constraint) {
		return !constraint.has(CONDITIONS);
	}

	private static void assertConditionsIsAnArrayNode(JsonNode constraint) {
		var conditions = constraint.get(CONDITIONS);
		if (!conditions.isArray())
			throw new IllegalArgumentException("'conditions' not an array: " + conditions.toString());
	}

	public static UnaryOperator<Object> getTransformationHandler(JsonNode constraint, ObjectMapper objectMapper) {
		return original -> {
			var actions               = constraint.get(ACTIONS);
			var jsonPathConfiguration = Configuration.builder()
					.jsonProvider(new JacksonJsonNodeJsonProvider(objectMapper)).build();
			if (actions == null)
				return original;

			if (!actions.isArray())
				throw new IllegalArgumentException(ACTIONS_NOT_AN_ARRAY);

			var originalJsonNode = objectMapper.valueToTree(original);

			var jsonContext = JsonPath.using(jsonPathConfiguration).parse(originalJsonNode);

			for (var action : actions)
				applyAction(jsonContext, action);

			JsonNode modifiedJsonNode = jsonContext.json();

			try {
				return objectMapper.treeToValue(modifiedJsonNode, original.getClass());
			} catch (JsonProcessingException e) {
				throw (new RuntimeException("Error converting modified object to original class type.", e));
			}
		};
	}

	private static void applyAction(DocumentContext jsonContext, JsonNode action) {
		if (!action.isObject())
			throw new IllegalArgumentException(ACTION_NOT_AN_OBJECT);

		var path       = getTextualValueOfActionKey(action, PATH);
		var actionType = getTextualValueOfActionKey(action, TYPE).trim().toLowerCase();

		try {
			jsonContext.read(path);
		} catch (PathNotFoundException e) {
			log.warn(
					"Error evaluating a constraint predicate. The path defined in the constraint is not present in the data.",
					e);
			throw new AccessDeniedException("Constraint enforcement failed.");
		}

		if (DELETE.equals(actionType)) {
			jsonContext.delete(path);
			return;
		}

		if (BLACKEN.equals(actionType)) {
			blacken(jsonContext, path, action);
			return;
		}

		if (REPLACE.equals(actionType)) {
			replace(jsonContext, path, action);
			return;
		}

		throw new IllegalArgumentException(String.format(UNKNOWN_ACTION_S, actionType));

	}

	private static void replace(DocumentContext jsonContext, String path, JsonNode action) {
		jsonContext.map(path, replaceNode(action));
	}

	private static MapFunction replaceNode(JsonNode action) {
		return (original, configuration) -> {
			if (!action.has(REPLACEMENT))
				throw new IllegalArgumentException(NO_REPLACEMENT_SPECIFIED);

			return action.get(REPLACEMENT);
		};
	}

	private static void blacken(DocumentContext jsonContext, String path, JsonNode action) {
		jsonContext.map(path, blackenNode(action));
	}

	private static MapFunction blackenNode(JsonNode action) {
		return (original, configuration) -> {

			if (!(original instanceof String))
				throw new IllegalArgumentException(PATH_NOT_TEXTUAL);

			var replacementString = determineReplacementString(action);
			var discloseRight     = getIntegerValueOfActionKeyOrDefaultToZero(action, DISCLOSE_RIGHT);
			var discloseLeft      = getIntegerValueOfActionKeyOrDefaultToZero(action, DISCLOSE_LEFT);

			return JSON.textNode(blacken((String) original, replacementString, discloseRight, discloseLeft));
		};
	}

	private static String determineReplacementString(JsonNode action) {
		var replacementNode = action.get(REPLACEMENT);

		if (replacementNode == null)
			return BLACK_SQUARE;

		if (replacementNode.isTextual())
			return replacementNode.textValue();

		throw new IllegalArgumentException(REPLACEMENT_NOT_TEXTUAL);
	}

	private static String blacken(String originalString, String replacement, int discloseRight, int discloseLeft) {
		if (discloseLeft + discloseRight >= originalString.length())
			return originalString;

		var result = new StringBuilder();
		if (discloseLeft > 0)
			result.append(originalString, 0, discloseLeft);

		var numberOfReplacedChars = originalString.length() - discloseLeft - discloseRight;
		result.append(String.valueOf(replacement).repeat(Math.max(0, numberOfReplacedChars)));

		if (discloseRight > 0)
			result.append(originalString.substring(discloseLeft + numberOfReplacedChars));

		return result.toString();
	}

	private static String getTextualValueOfActionKey(JsonNode action, String key) {
		var value = getValueOfActionKey(action, key);

		if (!value.isTextual())
			throw new IllegalArgumentException(String.format(VALUE_NOT_TEXTUAL_S, key));

		return value.textValue();
	}

	private static int getIntegerValueOfActionKeyOrDefaultToZero(JsonNode action, String key) {
		if (!action.has(key))
			return 0;

		var value = action.get(key);

		if (!value.canConvertToInt())
			throw new IllegalArgumentException(String.format(VALUE_NOT_INTEGER_S, key));

		return value.intValue();
	}

	private static JsonNode getValueOfActionKey(JsonNode action, String key) {
		if (!action.hasNonNull(key))
			throw new IllegalArgumentException(String.format(UNDEFINED_KEY_S, key));

		return action.get(key);
	}

}
