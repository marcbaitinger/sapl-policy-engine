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
package io.sapl.interpreter.variables;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;

public class VariableContextTest {

	private static final Val SUBJECT_NODE = Val.of("subject");

	private static final Val ACTION_NODE = Val.of("action");

	private static final Val RESOURCE_NODE = Val.of("resource");

	private static final Val ENVIRONMENT_NODE = Val.of("environment");

	private static final Val VAR_NODE = Val.of("var");

	private static final Val VAR_NODE_NEW = Val.of("var_new");

	private static final String VAR_ID = "var";

	private static final AuthorizationSubscription AUTH_SUBSCRIPTION = new AuthorizationSubscription(SUBJECT_NODE.get(),
			ACTION_NODE.get(), RESOURCE_NODE.get(), ENVIRONMENT_NODE.get());

	private static final AuthorizationSubscription EMPTY_AUTH_SUBSCRIPTION = new AuthorizationSubscription(null, null,
			null, null);

	@Test
	public void emtpyInitializationTest() {
		VariableContext ctx = new VariableContext();
		assertThat("context was not created and is null", ctx, not(nullValue()));
	}

	@Test
	public void authzSubscriptionInitializationTest() throws PolicyEvaluationException {
		VariableContext ctx = new VariableContext(AUTH_SUBSCRIPTION);
		assertTrue("context was not created or did not remember values",
				ctx != null && ctx.get("subject").equals(SUBJECT_NODE) && ctx.get("action").equals(ACTION_NODE)
						&& ctx.get("resource").equals(RESOURCE_NODE)
						&& ctx.get("environment").equals(ENVIRONMENT_NODE));
	}

	@Test
	public void emptyauthzSubscriptionInitializationTest() throws PolicyEvaluationException {
		VariableContext ctx = new VariableContext(EMPTY_AUTH_SUBSCRIPTION);
		assertTrue("context was not created or did not remember values",
				ctx != null && ctx.get("subject").equals(Val.ofNull()) && ctx.get("action").equals(Val.ofNull())
						&& ctx.get("resource").equals(Val.ofNull()) && ctx.get("environment").equals(Val.ofNull()));
	}

	@Test
	public void notExistsTest() throws PolicyEvaluationException {
		VariableContext ctx = new VariableContext(AUTH_SUBSCRIPTION);
		assertFalse("var should not be existing in freshly created context", ctx.exists(VAR_ID));
	}

	@Test
	public void existsTest() throws PolicyEvaluationException {
		VariableContext ctx = new VariableContext(AUTH_SUBSCRIPTION);
		ctx.put(VAR_ID, VAR_NODE.get());
		assertTrue("var should be existing in freshly created context", ctx.get(VAR_ID).equals(VAR_NODE));
	}

	@Test
	public void doubleRegistrationOverwrite() throws PolicyEvaluationException {
		VariableContext ctx = new VariableContext(AUTH_SUBSCRIPTION);
		ctx.put(VAR_ID, VAR_NODE.get());
		ctx.put(VAR_ID, VAR_NODE_NEW.get());
		assertTrue("", ctx.get(VAR_ID).equals(VAR_NODE_NEW));
	}

	@Test
	public void failGetUndefined() throws PolicyEvaluationException {
		VariableContext ctx = new VariableContext(AUTH_SUBSCRIPTION);
		assertThat("returns undefined", ctx.get(VAR_ID), is(Val.undefined()));
	}

}