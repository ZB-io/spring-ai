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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.*;
import org.springframework.ai.vectorstore.filter.converter.AbstractFilterExpressionConverter;

@ExtendWith(MockitoExtension.class)
public class Neo4JVectorFilterExpressionConverterDoExpressionTest {

	@Spy
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

	private StringBuilder context;

	@BeforeEach
	void setUp() {
		context = new StringBuilder();
		doNothing().when(converter).convertOperand(any(), any());
		when(converter.getOperationSymbol(any())).thenReturn(" = ");
		doNothing().when(converter).doNot(any(), any());
	}

	@Test
	@Tag("valid")
	void expressionWithNinTypeTriggersNotInConversion() {
		// Arrange
		Expression ninExpression = new Expression(Filter.ExpressionType.NIN, leftOperand, rightOperand);
		// Act
		converter.doExpression(ninExpression, context);
		// Assert
		verify(converter, times(1)).doNot(any(Expression.class), eq(context));
		verify(converter, never()).convertOperand(any(), any());
		verify(converter, never()).getOperationSymbol(any());
	}

	@Test
	@Tag("valid")
	void expressionWithEqTypeProcessesOperandsAndSymbolDirectly() {
		// Arrange
		Expression eqExpression = new Expression(Filter.ExpressionType.EQ, leftOperand, rightOperand);
		when(converter.getOperationSymbol(eqExpression)).thenReturn(" = ");
		// Act
		converter.doExpression(eqExpression, context);
		// Assert
		verify(converter, times(1)).convertOperand(leftOperand, context);
		verify(converter, times(1)).convertOperand(rightOperand, context);
		verify(converter, times(1)).getOperationSymbol(eqExpression);
		verify(converter, never()).doNot(any(), any());
		assertEquals(" = ", context.toString());
	}

	@Test
	@Tag("valid")
	void expressionWithGtTypeAppendsCorrectOperationSymbol() {
		// Arrange
		Expression gtExpression = new Expression(Filter.ExpressionType.GT, keyOperand, valueOperand);
		when(converter.getOperationSymbol(gtExpression)).thenReturn(" > ");
		// Act
		converter.doExpression(gtExpression, context);
		// Assert
		verify(converter, times(1)).convertOperand(keyOperand, context);
		verify(converter, times(1)).convertOperand(valueOperand, context);
		verify(converter, times(1)).getOperationSymbol(gtExpression);
		assertEquals(" > ", context.toString());
	}

	@Test
	@Tag("valid")
	void expressionWithComplexNestedOperandsProcessesCorrectly() {
		// Arrange
		Expression nestedExpression = new Expression(Filter.ExpressionType.LT, leftOperand, rightOperand);
		Expression ltExpression = new Expression(Filter.ExpressionType.LT, groupOperand, nestedExpression);
		when(converter.getOperationSymbol(ltExpression)).thenReturn(" < ");
		// Act
		assertDoesNotThrow(() -> converter.doExpression(ltExpression, context));
		// Assert
		verify(converter, times(1)).convertOperand(groupOperand, context);
		verify(converter, times(1)).convertOperand(nestedExpression, context);
		verify(converter, times(1)).getOperationSymbol(ltExpression);
		assertEquals(" < ", context.toString());
	}

	@Test
	@Tag("boundary")
	void ninExpressionWithNullOperandsHandlesGracefully() {
		// Arrange
		Expression ninExpression = new Expression(Filter.ExpressionType.NIN, null, rightOperand);
		// Act
		assertDoesNotThrow(() -> converter.doExpression(ninExpression, context));
		// Assert
		verify(converter, times(1)).doNot(any(Expression.class), eq(context));
		verify(converter, never()).convertOperand(any(), any());
		verify(converter, never()).getOperationSymbol(any());
	}

	@Test
	@Tag("valid")
	void expressionWithInTypeProcessesThroughStandardPath() {
		// Arrange
		Expression inExpression = new Expression(Filter.ExpressionType.IN, leftOperand, rightOperand);
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
		Expression ltExpression = new Expression(Filter.ExpressionType.LT, groupOperand, leftOperand);
		when(converter.getOperationSymbol(eqExpression)).thenReturn(" = ");
		when(converter.getOperationSymbol(gtExpression)).thenReturn(" > ");
		when(converter.getOperationSymbol(ltExpression)).thenReturn(" < ");
		// Act
		converter.doExpression(eqExpression, context);
		converter.doExpression(gtExpression, context);
		converter.doExpression(ltExpression, context);
		// Assert
		verify(converter, times(6)).convertOperand(any(), eq(context));
		verify(converter, times(3)).getOperationSymbol(any());
		assertEquals(" =  >  < ", context.toString());
	}

	@Test
	@Tag("valid")
	void emptyStringBuilderContextReceivesExpressionContent() {
		// Arrange
		Expression eqExpression = new Expression(Filter.ExpressionType.EQ, leftOperand, rightOperand);
		when(converter.getOperationSymbol(eqExpression)).thenReturn(" = ");
		assertTrue(context.length() == 0);
		// Act
		converter.doExpression(eqExpression, context);
		// Assert
		verify(converter, times(1)).convertOperand(leftOperand, context);
		verify(converter, times(1)).convertOperand(rightOperand, context);
		verify(converter, times(1)).getOperationSymbol(eqExpression);
		assertEquals(" = ", context.toString());
		assertTrue(context.length() > 0);
	}

	@Test
	@Tag("boundary")
	void ninExpressionWithBothNullOperandsHandlesGracefully() {
		// Arrange
		Expression ninExpression = new Expression(Filter.ExpressionType.NIN, null, null);
		// Act
		assertDoesNotThrow(() -> converter.doExpression(ninExpression, context));
		// Assert
		verify(converter, times(1)).doNot(any(Expression.class), eq(context));
		verify(converter, never()).convertOperand(any(), any());
		verify(converter, never()).getOperationSymbol(any());
	}

	@Test
	@Tag("valid")
	void expressionWithNeTypeProcessesThroughStandardPath() {
		// Arrange
		Expression neExpression = new Expression(Filter.ExpressionType.NE, leftOperand, rightOperand);
		when(converter.getOperationSymbol(neExpression)).thenReturn(" <> ");
		// Act
		converter.doExpression(neExpression, context);
		// Assert
		verify(converter, never()).doNot(any(), any());
		verify(converter, times(1)).convertOperand(leftOperand, context);
		verify(converter, times(1)).convertOperand(rightOperand, context);
		verify(converter, times(1)).getOperationSymbol(neExpression);
		assertEquals(" <> ", context.toString());
	}

	@Test
	@Tag("valid")
	void expressionWithGteTypeProcessesThroughStandardPath() {
		// Arrange
		Expression gteExpression = new Expression(Filter.ExpressionType.GTE, keyOperand, valueOperand);
		when(converter.getOperationSymbol(gteExpression)).thenReturn(" >= ");
		// Act
		converter.doExpression(gteExpression, context);
		// Assert
		verify(converter, never()).doNot(any(), any());
		verify(converter, times(1)).convertOperand(keyOperand, context);
		verify(converter, times(1)).convertOperand(valueOperand, context);
		verify(converter, times(1)).getOperationSymbol(gteExpression);
		assertEquals(" >= ", context.toString());
	}

	@Test
	@Tag("valid")
	void expressionWithLteTypeProcessesThroughStandardPath() {
		// Arrange
		Expression lteExpression = new Expression(Filter.ExpressionType.LTE, leftOperand, rightOperand);
		when(converter.getOperationSymbol(lteExpression)).thenReturn(" <= ");
		// Act
		converter.doExpression(lteExpression, context);
		// Assert
		verify(converter, never()).doNot(any(), any());
		verify(converter, times(1)).convertOperand(leftOperand, context);
		verify(converter, times(1)).convertOperand(rightOperand, context);
		verify(converter, times(1)).getOperationSymbol(lteExpression);
		assertEquals(" <= ", context.toString());
	}

}