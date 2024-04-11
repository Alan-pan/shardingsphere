/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.sharding.route.engine.condition.engine;

import com.google.common.base.Preconditions;
import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.core.rule.ShardingRule;
import org.apache.shardingsphere.core.strategy.route.value.ListRouteValue;
import org.apache.shardingsphere.sharding.route.engine.condition.ExpressionConditionUtils;
import org.apache.shardingsphere.sharding.route.engine.condition.ShardingCondition;
import org.apache.shardingsphere.sql.parser.binder.segment.insert.keygen.GeneratedKeyContext;
import org.apache.shardingsphere.sharding.route.spi.SPITimeService;
import org.apache.shardingsphere.sql.parser.binder.segment.insert.values.InsertValueContext;
import org.apache.shardingsphere.sql.parser.binder.statement.dml.InsertStatementContext;
import org.apache.shardingsphere.sql.parser.sql.segment.dml.expr.ExpressionSegment;
import org.apache.shardingsphere.sql.parser.sql.segment.dml.expr.simple.LiteralExpressionSegment;
import org.apache.shardingsphere.sql.parser.sql.segment.dml.expr.simple.ParameterMarkerExpressionSegment;
import org.apache.shardingsphere.sql.parser.sql.segment.dml.expr.simple.SimpleExpressionSegment;
import org.apache.shardingsphere.underlying.common.exception.ShardingSphereException;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Sharding condition engine for insert clause.
 */
@RequiredArgsConstructor
public final class InsertClauseShardingConditionEngine {
    
    private final ShardingRule shardingRule;
    
    /**
     * Create sharding conditions.
     * 
     * @param insertStatementContext insert statement context
     * @param parameters SQL parameters
     * @return sharding conditions
     */
    public List<ShardingCondition> createShardingConditions(final InsertStatementContext insertStatementContext, final List<Object> parameters) {
        List<ShardingCondition> result = new LinkedList<>();
        String tableName = insertStatementContext.getSqlStatement().getTable().getTableName().getIdentifier().getValue();
        Collection<String> columnNames = getColumnNames(insertStatementContext);
        //生成分片条件
        for (InsertValueContext each : insertStatementContext.getInsertValueContexts()) {
            result.add(createShardingCondition(tableName, columnNames.iterator(), each, parameters));
        }
        //生成自动生成key
        Optional<GeneratedKeyContext> generatedKey = insertStatementContext.getGeneratedKeyContext();
        if (generatedKey.isPresent() && generatedKey.get().isGenerated()) {
            generatedKey.get().getGeneratedValues().addAll(getGeneratedKeys(tableName, insertStatementContext.getSqlStatement().getValueListCount()));
            if (shardingRule.isShardingColumn(generatedKey.get().getColumnName(), tableName)) {
                appendGeneratedKeyCondition(generatedKey.get(), tableName, result);
            }
        }
        return result;
    }
    
    private Collection<String> getColumnNames(final InsertStatementContext insertStatementContext) {
        Optional<GeneratedKeyContext> generatedKey = insertStatementContext.getGeneratedKeyContext();
        if (generatedKey.isPresent() && generatedKey.get().isGenerated()) {
            Collection<String> result = new LinkedList<>(insertStatementContext.getColumnNames());
            result.remove(generatedKey.get().getColumnName());
            return result;
        }
        return insertStatementContext.getColumnNames();
    }
    
    private ShardingCondition createShardingCondition(final String tableName, final Iterator<String> columnNames, final InsertValueContext insertValueContext, final List<Object> parameters) {
        ShardingCondition result = new ShardingCondition();
        SPITimeService timeService = new SPITimeService();
        for (ExpressionSegment each : insertValueContext.getValueExpressions()) {
            String columnName = columnNames.next();
            if (shardingRule.isShardingColumn(columnName, tableName)) {
                if (each instanceof SimpleExpressionSegment) {
                    result.getRouteValues().add(new ListRouteValue<>(columnName, tableName, Collections.singletonList(getRouteValue((SimpleExpressionSegment) each, parameters))));
                } else if (ExpressionConditionUtils.isNowExpression(each)) {
                    result.getRouteValues().add(new ListRouteValue<>(columnName, tableName, Collections.singletonList(timeService.getTime())));
                } else if (ExpressionConditionUtils.isNullExpression(each)) {
                    throw new ShardingSphereException("Insert clause sharding column can't be null.");
                }
            }
        }
        return result;
    }
    
    private Comparable<?> getRouteValue(final SimpleExpressionSegment expressionSegment, final List<Object> parameters) {
        Object result;
        if (expressionSegment instanceof ParameterMarkerExpressionSegment) {
            result = parameters.get(((ParameterMarkerExpressionSegment) expressionSegment).getParameterMarkerIndex());
        } else {
            result = ((LiteralExpressionSegment) expressionSegment).getLiterals();
        }
        Preconditions.checkArgument(result instanceof Comparable, "Sharding value must implements Comparable.");
        return (Comparable) result;
    }
    
    private Collection<Comparable<?>> getGeneratedKeys(final String tableName, final int valueListCount) {
        return IntStream.range(0, valueListCount).mapToObj(i -> shardingRule.generateKey(tableName)).collect(Collectors.toCollection(LinkedList::new));
    }
    
    private void appendGeneratedKeyCondition(final GeneratedKeyContext generatedKey, final String tableName, final List<ShardingCondition> shardingConditions) {
        Iterator<Comparable<?>> generatedValuesIterator = generatedKey.getGeneratedValues().iterator();
        for (ShardingCondition each : shardingConditions) {
            each.getRouteValues().add(new ListRouteValue<>(generatedKey.getColumnName(), tableName, Collections.<Comparable<?>>singletonList(generatedValuesIterator.next())));
        }
    }
}
