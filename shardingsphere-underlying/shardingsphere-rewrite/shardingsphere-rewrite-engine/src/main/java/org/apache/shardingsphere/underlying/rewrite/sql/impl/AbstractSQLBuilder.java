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

package org.apache.shardingsphere.underlying.rewrite.sql.impl;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.underlying.rewrite.context.SQLRewriteContext;
import org.apache.shardingsphere.underlying.rewrite.sql.SQLBuilder;
import org.apache.shardingsphere.underlying.rewrite.sql.token.pojo.SQLToken;
import org.apache.shardingsphere.underlying.rewrite.sql.token.pojo.Substitutable;

import java.util.Collections;

/**
 * Abstract SQL builder.
 */
@RequiredArgsConstructor
@Getter
public abstract class AbstractSQLBuilder implements SQLBuilder {
    
    private final SQLRewriteContext context;
    
    @Override
    public final String toSQL() {
        if (context.getSqlTokens().isEmpty()) {
            return context.getSql();
        }
        Collections.sort(context.getSqlTokens());
        StringBuilder result = new StringBuilder();
        //截取第一个SQLToken之前的内容  select * from
        result.append(context.getSql().substring(0, context.getSqlTokens().get(0).getStartIndex()));
        for (SQLToken each : context.getSqlTokens()) {
            //重写拼接每个SQLToken对应的内容  t_order ->t_order_0
            result.append(getSQLTokenText(each));
            //拼接SQLToken中间不变的内容 where created_by = '123'
            result.append(getConjunctionText(each));
        }
        return result.toString();
    }
    
    protected abstract String getSQLTokenText(SQLToken sqlToken);
    
    private String getConjunctionText(final SQLToken sqlToken) {
        return context.getSql().substring(getStartIndex(sqlToken), getStopIndex(sqlToken));
    }
    
    private int getStartIndex(final SQLToken sqlToken) {
        int startIndex = sqlToken instanceof Substitutable ? ((Substitutable) sqlToken).getStopIndex() + 1 : sqlToken.getStartIndex();
        return Math.min(startIndex, context.getSql().length());
    }
    
    private int getStopIndex(final SQLToken sqlToken) {
        int currentSQLTokenIndex = context.getSqlTokens().indexOf(sqlToken);
        return context.getSqlTokens().size() - 1 == currentSQLTokenIndex ? context.getSql().length() : context.getSqlTokens().get(currentSQLTokenIndex + 1).getStartIndex();
    }
}
