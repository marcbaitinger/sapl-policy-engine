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
package io.sapl.test.mocking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;

class MockCallTests {

	@Test
	void test() {
		var call = new MockCall(Val.of("foo"));

		assertThat(call.getNumberOfArguments()).isEqualTo(1);
		assertThat(call.getArgument(0)).isEqualTo(Val.of("foo"));
		assertThat(call.getListOfArguments().size()).isEqualTo(1);
	}

	@Test
	void test_invalidIndex() {
		var call = new MockCall(Val.of("foo"));

		assertThat(call.getNumberOfArguments()).isEqualTo(1);
		assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> call.getArgument(1));
	}

	@Test
	void test_modifyParameterList() {
		var call = new MockCall(Val.of("foo"));

		assertThatExceptionOfType(UnsupportedOperationException.class)
				.isThrownBy(() -> call.getListOfArguments().add(Val.of("barr")));
	}

}
