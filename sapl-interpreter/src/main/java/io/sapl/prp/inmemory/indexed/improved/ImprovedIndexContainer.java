/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.prp.inmemory.indexed.improved;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;
import io.sapl.prp.inmemory.indexed.Bitmask;
import io.sapl.prp.inmemory.indexed.DisjunctiveFormula;
import io.sapl.prp.inmemory.indexed.IndexContainer;

//@Slf4j
//@RequiredArgsConstructor
public class ImprovedIndexContainer implements IndexContainer {

	private final boolean abortOnError;

	private final ImmutableMap<DisjunctiveFormula, Set<SAPL>> formulaToDocuments;

	private final ImmutableList<Predicate> predicateOrder;

	private final ImmutableList<Set<DisjunctiveFormula>> relatedFormulas;

	private final ImmutableMap<DisjunctiveFormula, Bitmask> relatedCandidates;

	private final ImmutableMap<Integer, Set<CTuple>> conjunctionsInFormulasReferencingConjunction;

	private final int[] numberOfLiteralsInConjunction;

	private final int[] numberOfFormulasWithConjunction;

	public ImprovedIndexContainer(boolean abortOnError, Map<DisjunctiveFormula, Set<SAPL>> formulaToDocuments,
			List<Predicate> predicateOrder, List<Set<DisjunctiveFormula>> relatedFormulas,
			Map<DisjunctiveFormula, Bitmask> relatedCandidates,
			Map<Integer, Set<CTuple>> conjunctionsInFormulasReferencingConjunction, int[] numberOfLiteralsInConjunction,
			int[] numberOfFormulasWithConjunction) {
		this.abortOnError = abortOnError;
		this.formulaToDocuments = ImmutableMap.copyOf(formulaToDocuments);
		this.predicateOrder = ImmutableList.copyOf(predicateOrder);
		this.relatedFormulas = ImmutableList.copyOf(relatedFormulas);
		this.relatedCandidates = ImmutableMap.copyOf(relatedCandidates);
		this.conjunctionsInFormulasReferencingConjunction = ImmutableMap
				.copyOf(conjunctionsInFormulasReferencingConjunction);
		this.numberOfLiteralsInConjunction = Arrays.stream(numberOfLiteralsInConjunction).toArray();
		this.numberOfFormulasWithConjunction = Arrays.stream(numberOfFormulasWithConjunction).toArray();
	}

	@Override
	public PolicyRetrievalResult match(final FunctionContext functionCtx, final VariableContext variableCtx) {
		Set<DisjunctiveFormula> result = new HashSet<>();
		boolean errorOccurred = false;

		Bitmask clauseCandidates = new Bitmask();
		clauseCandidates.set(0, numberOfLiteralsInConjunction.length);

		Bitmask satisfiedCandidates = new Bitmask();
		clauseCandidates.set(0, numberOfLiteralsInConjunction.length);

		int[] trueLiteralsOfConjunction = new int[numberOfLiteralsInConjunction.length];
		int[] eliminatedFormulasWithConjunction = new int[numberOfLiteralsInConjunction.length];

		for (Predicate predicate : predicateOrder) {
			if (!isReferenced(predicate, clauseCandidates))
				continue;

			Optional<Boolean> outcome = predicate.evaluate(functionCtx, variableCtx);
			if (!outcome.isPresent()) {
				if (abortOnError) {
					return new PolicyRetrievalResult(fetchPolicies(result), true);
				} else {
					removeCandidatesRelatedToPredicate(predicate, clauseCandidates);
					errorOccurred = true;
					continue;
				}
			}
			boolean evaluationResult = outcome.get();

			Bitmask satisfiableCandidates = findSatisfiableCandidates(clauseCandidates, predicate, evaluationResult,
					trueLiteralsOfConjunction);
			satisfiedCandidates.or(satisfiableCandidates);
			// result.addAll(fetchFormulas(satisfiableCandidates));

			Bitmask unsatisfiableCandidates = findUnsatisfiableCandidates(clauseCandidates, predicate,
					evaluationResult);
			Bitmask orphanedCandidates = findOrphanedCandidates(clauseCandidates, satisfiableCandidates,
					eliminatedFormulasWithConjunction);

			eliminateCandidates(clauseCandidates, unsatisfiableCandidates, satisfiableCandidates, orphanedCandidates);
		}

		result.addAll(fetchFormulas(satisfiedCandidates));

		return new PolicyRetrievalResult(fetchPolicies(result), errorOccurred);
	}

	private void removeCandidatesRelatedToPredicate(final Predicate predicate, Bitmask candidates) {
		// Bitmask affectedCandidates = findRelatedCandidates(predicate);
		Bitmask affectedCandidates = predicate.getConjunctions();
		candidates.andNot(affectedCandidates);
	}

	protected Bitmask findRelatedCandidates(final Predicate predicate) {
		Bitmask result = new Bitmask();
		fetchFormulas(predicate.getConjunctions()).parallelStream()
				.forEach(formula -> result.or(relatedCandidates.get(formula)));

		return result;
	}

	private Bitmask findOrphanedCandidates(final Bitmask candidates, final Bitmask satisfiableCandidates,
			int[] eliminatedFormulasWithConjunction) {
		Bitmask result = new Bitmask();

		satisfiableCandidates.forEachSetBit(index -> {
			Set<CTuple> cTuples = conjunctionsInFormulasReferencingConjunction.get(index);
			for (CTuple cTuple : cTuples) {
				if (!candidates.isSet(cTuple.getCI()))
					continue;
				eliminatedFormulasWithConjunction[cTuple.getCI()] += cTuple.getN();

				// if all formular of conjunction have been eliminated
				if (eliminatedFormulasWithConjunction[cTuple.getCI()] == numberOfFormulasWithConjunction[cTuple
						.getCI()])
					result.set(cTuple.getCI());
			}
		});

		return result;
	}

	protected void eliminateCandidates(final Bitmask candidates, final Bitmask unsatisfiableCandidates,
			final Bitmask satisfiableCandidates, final Bitmask orphanedCandidates) {
		candidates.andNot(unsatisfiableCandidates);
		candidates.andNot(satisfiableCandidates);
		candidates.andNot(orphanedCandidates);
	}

	protected Set<DisjunctiveFormula> fetchFormulas(final Bitmask satisfiableCandidates) {
		final Set<DisjunctiveFormula> result = new HashSet<>();
		satisfiableCandidates.forEachSetBit(index -> result.addAll(relatedFormulas.get(index)));
		return result;
	}

	private Bitmask findSatisfiableCandidates(final Bitmask candidates, final Predicate predicate,
			final boolean evaluationResult, final int[] trueLiteralsOfConjunction) {
		Bitmask result = new Bitmask();
		// calling method with negated evaluation result will return satisfied clauses
		Bitmask satisfiableCandidates = findUnsatisfiableCandidates(candidates, predicate, !evaluationResult);

		satisfiableCandidates.forEachSetBit(index -> {
			// increment number of true literals
			trueLiteralsOfConjunction[index] += 1;
			// if all literals in conjunction are true, add conjunction to result
			if (trueLiteralsOfConjunction[index] == numberOfLiteralsInConjunction[index])
				result.set(index);
		});

		return result;
	}

	protected boolean isReferenced(final Predicate predicate, final Bitmask candidates) {
		return predicate.getConjunctions().intersects(candidates);
	}

	protected Set<SAPL> fetchPolicies(final Set<DisjunctiveFormula> formulas) {
		return formulas.parallelStream().map(formulaToDocuments::get).flatMap(Collection::parallelStream)
				.collect(Collectors.toSet());
	}

	protected Bitmask findUnsatisfiableCandidates(final Bitmask candidates, final Predicate predicate,
			final boolean predicateEvaluationResult) {
		Bitmask result = new Bitmask(candidates);
		if (predicateEvaluationResult) {
			result.and(predicate.getFalseForTruePredicate());
		} else {
			result.and(predicate.getFalseForFalsePredicate());
		}
		return result;
	}

}