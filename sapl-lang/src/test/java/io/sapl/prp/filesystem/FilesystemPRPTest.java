/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.prp.filesystem;

import java.util.HashMap;
import java.util.logging.Level;

import org.junit.Test;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.prp.GenericInMemoryIndexedPolicyRetrievalPoint;
import io.sapl.prp.index.naive.NaiveImmutableParsedDocumentIndex;
import reactor.core.publisher.SignalType;

public class FilesystemPRPTest {

	@Test
	public void doTest() throws Exception {
		var interpreter = new DefaultSAPLInterpreter();
		var source = new FileSystemPrpUpdateEventSource("src/test/resources/policies", interpreter);
		var prp = new GenericInMemoryIndexedPolicyRetrievalPoint(new NaiveImmutableParsedDocumentIndex(), source);
//		var prp = new GenericInMemoryIndexedPolicyRetrievalPoint(new CanonicalImmutableParsedDocumentIndex(), source);
		var authzSubscription = AuthorizationSubscription.of("Willi", "eat", "icecream");
		var evaluationCtx = new EvaluationContext(new AnnotationAttributeContext(), new AnnotationFunctionContext(),
				new HashMap<>());
		evaluationCtx = evaluationCtx.forAuthorizationSubscription(authzSubscription);
		prp.retrievePolicies(evaluationCtx).log(null, Level.INFO, SignalType.ON_NEXT).blockFirst();
		prp.dispose();
	}
}
