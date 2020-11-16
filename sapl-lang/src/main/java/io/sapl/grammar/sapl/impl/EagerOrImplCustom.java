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
package io.sapl.grammar.sapl.impl;

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.EvaluationContext;
import lombok.NonNull;
import reactor.core.publisher.Flux;

/**
 * Implements the eager logical OR operation, noted as '|' in the grammar.
 *
 * Grammar: Addition returns Expression: Multiplication (('|'
 * {EagerOr.left=current}) right=Multiplication)* ;
 */

public class EagerOrImplCustom extends EagerOrImpl {

	@Override
	public Flux<Val> evaluate(@NonNull EvaluationContext ctx, @NonNull Val relativeNode) {
		var leftFlux = getLeft().evaluate(ctx, relativeNode).map(Val::requireBoolean);
		var rightFlux = getRight().evaluate(ctx, relativeNode).map(Val::requireBoolean);
		return Flux.combineLatest(leftFlux, rightFlux, (left, right) -> {
			if (left.isError())
				return left;
			if (right.isError())
				return right;
			return Val.of(left.getBoolean() || right.getBoolean());
		});

	}

}
