/*
 * Copyright 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.vectorstore.neo4j.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.*;
import org.springframework.ai.vectorstore.filter.converter.AbstractFilterExpressionConverter;

class Neo4JVectorFilterExpressionConverterDoExpressionTest {

	private Neo4jVectorFilterExpressionConverter converter;

	@Mock
	private Filter.Operand leftOperand;

	@Mock
	private Filter.Operand rightOperand;

	@Mock
	private Key keyOperand;

	@Mock
	private Filter.Value valueOperand;

	@Mock
	private Group groupOperand;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		converter = spy(new TestableNeo4jVectorFilterExpressionConverter());
	}

	@Test
	@Tag("valid")
	void expressionWithNinTypeTriggersNotInConversion() {
		// Arrange
		Expression ninExpression = new Expression(Filter.ExpressionType.NIN, leftOperand, rightOperand);
		StringBuilder context = new StringBuilder();

		doNothing().when(converter).doNot(any(Expression.class), eq(context));

		// Act
		converter.doExpression(ninExpression, context);

		// Assert
		ArgumentCaptor<Expression> expressionCaptor = ArgumentCaptor.forClass(Expression.class);
		verify(converter, times(1)).doNot(expressionCaptor.capture(), eq(context));

		Expression capturedExpression = expressionCaptor.getValue();
		assertEquals(Filter.ExpressionType.NOT, capturedExpression.type());

		Expression innerExpression = (Expression) capturedExpression.left();
		assertEquals(Filter.ExpressionType.IN, innerExpression.type());
		assertEquals(leftOperand, innerExpression.left());
		assertEquals(rightOperand, innerExpression.right());
	}

	@Test
	@Tag("valid")
	void expressionWithEqTypeProcessesOperandsAndSymbolDirectly() {
		// Arrange
		Expression eqExpression = new Expression(Filter.ExpressionType.EQ, leftOperand, rightOperand);
		StringBuilder context = new StringBuilder();

		doNothing().when(converter).convertOperand(any(), any());
		when(converter.getOperationSymbol(eqExpression)).thenReturn(" = ");

		// Act
		converter.doExpression(eqExpression, context);

		// Assert
		verify(converter, times(1)).convertOperand(leftOperand, context);
		verify(converter, times(1)).convertOperand(rightOperand, context);
		verify(converter, times(1)).getOperationSymbol(eqExpression);
		assertEquals(" = ", context.toString());
	}

	@Test
	@Tag("valid")
	void expressionWithGtTypeAppendsCorrectOperationSymbol() {
		// Arrange
		Expression gtExpression = new Expression(Filter.ExpressionType.GT, keyOperand, valueOperand);
		StringBuilder context = new StringBuilder();

		doAnswer(invocation -> {
			StringBuilder sb = invocation.getArgument(1);
			sb.append("key");
			return null;
		}).when(converter).convertOperand(eq(keyOperand), eq(context));

		doAnswer(invocation -> {
			StringBuilder sb = invocation.getArgument(1);
			sb.append("value");
			return null;
		}).when(converter).convertOperand(eq(valueOperand), eq(context));

		when(converter.getOperationSymbol(gtExpression)).thenReturn(" > ");

		// Act
		converter.doExpression(gtExpression, context);

		// Assert
		assertEquals("key > value", context.toString());
		verify(converter, times(1)).convertOperand(keyOperand, context);
		verify(converter, times(1)).getOperationSymbol(gtExpression);
		verify(converter, times(1)).convertOperand(valueOperand, context);
	}

	@Test
	@Tag("valid")
	void expressionWithComplexNestedOperandsProcessesCorrectly() {
		// Arrange
		Expression ltExpression = new Expression(Filter.ExpressionType.LT, groupOperand, leftOperand);
		StringBuilder context = new StringBuilder();

		doAnswer(invocation -> {
			StringBuilder sb = invocation.getArgument(1);
			sb.append("(nested_group)");
			return null;
		}).when(converter).convertOperand(eq(groupOperand), eq(context));

		doAnswer(invocation -> {
			StringBuilder sb = invocation.getArgument(1);
			sb.append("operand");
			return null;
		}).when(converter).convertOperand(eq(leftOperand), eq(context));

		when(converter.getOperationSymbol(ltExpression)).thenReturn(" < ");

		// Act
		assertDoesNotThrow(() -> converter.doExpression(ltExpression, context));

		// Assert
		verify(converter, times(1)).convertOperand(groupOperand, context);
		verify(converter, times(1)).convertOperand(leftOperand, context);
		verify(converter, times(1)).getOperationSymbol(ltExpression);
		assertEquals("(nested_group) < operand", context.toString());
	}

	@Test
	@Tag("boundary")
	void ninExpressionWithNullOperandsHandlesGracefully() {
		// Arrange
		Expression ninExpression = new Expression(Filter.ExpressionType.NIN, null, rightOperand);
		StringBuilder context = new StringBuilder();

		doNothing().when(converter).doNot(any(Expression.class), eq(context));

		// Act
		assertDoesNotThrow(() -> converter.doExpression(ninExpression, context));

		// Assert
		ArgumentCaptor<Expression> expressionCaptor = ArgumentCaptor.forClass(Expression.class);
		verify(converter, times(1)).doNot(expressionCaptor.capture(), eq(context));

		Expression capturedExpression = expressionCaptor.getValue();
		assertEquals(Filter.ExpressionType.NOT, capturedExpression.type());

		Expression innerExpression = (Expression) capturedExpression.left();
		assertEquals(Filter.ExpressionType.IN, innerExpression.type());
		assertNull(innerExpression.left());
		assertEquals(rightOperand, innerExpression.right());
	}

	@Test
	@Tag("valid")
	void expressionWithInTypeProcessesThroughStandardPath() {
		// Arrange
		Expression inExpression = new Expression(Filter.ExpressionType.IN, leftOperand, rightOperand);
		StringBuilder context = new StringBuilder();

		doNothing().when(converter).convertOperand(any(), any());
		when(converter.getOperationSymbol(inExpression)).thenReturn(" IN ");

		// Act
		converter.doExpression(inExpression, context);

		// Assert
		verify(converter, never()).doNot(any(), any());
		verify(converter, times(1)).convertOperand(leftOperand, context);
		verify(converter, times(1)).convertOperand(rightOperand, context);
		verify(converter, times(1)).getOperationSymbol(inExpression);
		assertEquals(" IN ", context.toString());
	}

	@Test
	@Tag("integration")
	void multipleSequentialExpressionsModifyContextCorrectly() {
		// Arrange
		Expression eqExpression = new Expression(Filter.ExpressionType.EQ, leftOperand, rightOperand);
		Expression gtExpression = new Expression(Filter.ExpressionType.GT, keyOperand, valueOperand);
		Expression ltExpression = new Expression(Filter.ExpressionType.LT, leftOperand, rightOperand);
		StringBuilder context = new StringBuilder();

		doAnswer(invocation -> {
			StringBuilder sb = invocation.getArgument(1);
			sb.append("left");
			return null;
		}).when(converter).convertOperand(eq(leftOperand), eq(context));

		doAnswer(invocation -> {
			StringBuilder sb = invocation.getArgument(1);
			sb.append("right");
			return null;
		}).when(converter).convertOperand(eq(rightOperand), eq(context));

		doAnswer(invocation -> {
			StringBuilder sb = invocation.getArgument(1);
			sb.append("key");
			return null;
		}).when(converter).convertOperand(eq(keyOperand), eq(context));

		doAnswer(invocation -> {
			StringBuilder sb = invocation.getArgument(1);
			sb.append("value");
			return null;
		}).when(converter).convertOperand(eq(valueOperand), eq(context));

		when(converter.getOperationSymbol(eqExpression)).thenReturn("=");
		when(converter.getOperationSymbol(gtExpression)).thenReturn(">");
		when(converter.getOperationSymbol(ltExpression)).thenReturn("<");

		// Act
		converter.doExpression(eqExpression, context);
		converter.doExpression(gtExpression, context);
		converter.doExpression(ltExpression, context);

		// Assert
		assertEquals("left=rightkeyvalue>leftvalue", context.toString());
		verify(converter, times(4)).convertOperand(eq(leftOperand), eq(context));
		verify(converter, times(2)).convertOperand(eq(rightOperand), eq(context));
		verify(converter, times(1)).convertOperand(eq(keyOperand), eq(context));
		verify(converter, times(1)).convertOperand(eq(valueOperand), eq(context));
	}

	@Test
	@Tag("valid")
	void emptyStringBuilderContextReceivesExpressionContent() {
		// Arrange
		Expression eqExpression = new Expression(Filter.ExpressionType.EQ, leftOperand, rightOperand);
		StringBuilder context = new StringBuilder();
		assertTrue(context.length() == 0);

		doAnswer(invocation -> {
			StringBuilder sb = invocation.getArgument(1);
			sb.append("field");
			return null;
		}).when(converter).convertOperand(eq(leftOperand), eq(context));

		doAnswer(invocation -> {
			StringBuilder sb = invocation.getArgument(1);
			sb.append("'value'");
			return null;
		}).when(converter).convertOperand(eq(rightOperand), eq(context));

		when(converter.getOperationSymbol(eqExpression)).thenReturn(" = ");

		// Act
		converter.doExpression(eqExpression, context);

		// Assert
		assertFalse(context.length() == 0);
		assertEquals("field = 'value'", context.toString());
		verify(converter, times(1)).convertOperand(leftOperand, context);
		verify(converter, times(1)).getOperationSymbol(eqExpression);
		verify(converter, times(1)).convertOperand(rightOperand, context);
	}

	private static class TestableNeo4jVectorFilterExpressionConverter extends Neo4jVectorFilterExpressionConverter {

		@Override
		protected void doNot(Expression expression, StringBuilder context) {
			// Mock implementation for testing
		}

		@Override
		protected void convertOperand(Filter.Operand operand, StringBuilder context) {
			// Mock implementation for testing
		}

		@Override
		protected String getOperationSymbol(Expression expression) {
			// Mock implementation for testing
			return "";
		}

	}

}