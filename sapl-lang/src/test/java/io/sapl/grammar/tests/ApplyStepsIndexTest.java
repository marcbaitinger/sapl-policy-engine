/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.tests;

import static io.sapl.grammar.tests.ArrayUtil.numberArrayRange;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;

import org.junit.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.interpreter.EvaluationContext;
import reactor.test.StepVerifier;

public class ApplyStepsIndexTest {

	private static SaplFactory FACTORY = SaplFactoryImpl.eINSTANCE;
	private static EvaluationContext CTX = mock(EvaluationContext.class);

	@Test
	public void indexStepPropagatesErrors() {
		var step = FACTORY.createIndexStep();
		StepVerifier.create(step.apply(Val.error("TEST"), CTX, Val.UNDEFINED)).expectNext(Val.error("TEST"))
				.verifyComplete();
	}

	@Test
	public void applyIndexStepToNonArrayFails() {
		// test case : undefined[0] -> fails
		var step = FACTORY.createIndexStep();
		StepVerifier.create(step.apply(Val.UNDEFINED, CTX, Val.UNDEFINED)).expectNextMatches(Val::isError)
				.verifyComplete();
	}

	@Test
	public void applyPositiveExistingToArrayNode() {
		var parentValue = numberArrayRange(0, 9);
		var step = FACTORY.createIndexStep();
		step.setIndex(BigDecimal.valueOf(5));
		StepVerifier.create(step.apply(parentValue, CTX, Val.UNDEFINED)).expectNext(Val.of(5)).verifyComplete();
	}

	@Test
	public void applyPositiveOutOfBoundsToArrayNode() {
		var parentValue = numberArrayRange(0, 9);
		var step = FACTORY.createIndexStep();
		step.setIndex(BigDecimal.valueOf(100));
		StepVerifier.create(step.apply(parentValue, CTX, Val.UNDEFINED)).expectNextMatches(Val::isError)
				.verifyComplete();
	}

	@Test
	public void applyNegativeExistingToArrayNode() {
		var parentValue = numberArrayRange(0, 9);
		var step = FACTORY.createIndexStep();
		step.setIndex(BigDecimal.valueOf(-2));
		StepVerifier.create(step.apply(parentValue, CTX, Val.UNDEFINED)).expectNext(Val.of(8)).verifyComplete();
	}

	@Test
	public void applyNegativeOutOfBoundsToArrayNode() {
		var parentValue = numberArrayRange(0, 9);
		var step = FACTORY.createIndexStep();
		step.setIndex(BigDecimal.valueOf(-12));
		StepVerifier.create(step.apply(parentValue, CTX, Val.UNDEFINED)).expectNextMatches(Val::isError)
				.verifyComplete();
	}

}
