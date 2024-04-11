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

package org.apache.shardingsphere.underlying.rewrite.engine;

import org.apache.shardingsphere.underlying.common.rule.DataNode;
import org.apache.shardingsphere.underlying.rewrite.context.SQLRewriteContext;
import org.apache.shardingsphere.underlying.rewrite.parameter.builder.ParameterBuilder;
import org.apache.shardingsphere.underlying.rewrite.parameter.builder.impl.GroupedParameterBuilder;
import org.apache.shardingsphere.underlying.rewrite.parameter.builder.impl.StandardParameterBuilder;
import org.apache.shardingsphere.underlying.rewrite.sql.impl.RouteSQLBuilder;
import org.apache.shardingsphere.underlying.route.context.RouteResult;
import org.apache.shardingsphere.underlying.route.context.RouteUnit;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * SQL rewrite engine with route.
 */
public final class SQLRouteRewriteEngine {
    
    /**
     * Rewrite SQL and parameters.
     *
     * @param sqlRewriteContext SQL rewrite context
     * @param routeResult route result
     * @return SQL map of route unit and rewrite result
     */
    public Map<RouteUnit, SQLRewriteResult> rewrite(final SQLRewriteContext sqlRewriteContext, final RouteResult routeResult) {
        Map<RouteUnit, SQLRewriteResult> result = new LinkedHashMap<>(routeResult.getRouteUnits().size(), 1);
        for (RouteUnit each : routeResult.getRouteUnits()) {
            //重写SQL+组装参数
            //toSQL核心就是根据SQLToken将SQL拆分改写再拼装，
            //比如select * from t_order where created_by = '123' 就会被拆分为select * from | t_order | where created_by = '123'三部分进行改写拼装。

            //构造器配置parameterBuilder,判断是否是插入语句
            //ParameterBuilder有StandardParameterBuilder和GroupedParameterBuilder两个实现。
            //StandardParameterBuilder： 适用于非insert语句，getParameters无需分组处理直接返回即可
            //GroupedParameterBuilder： 适用于insert语句，需要根据路由情况对参数进行分组。
            result.put(each, new SQLRewriteResult(new RouteSQLBuilder(sqlRewriteContext, each).toSQL(), getParameters(sqlRewriteContext.getParameterBuilder(), routeResult, each)));
        }
        return result;
    }
    
    private List<Object> getParameters(final ParameterBuilder parameterBuilder, final RouteResult routeResult, final RouteUnit routeUnit) {
        if (parameterBuilder instanceof StandardParameterBuilder || routeResult.getOriginalDataNodes().isEmpty() || parameterBuilder.getParameters().isEmpty()) {
            //非插入语句直接返回
            return parameterBuilder.getParameters();
        }
        List<Object> result = new LinkedList<>();
        int count = 0;
        for (Collection<DataNode> each : routeResult.getOriginalDataNodes()) {
            if (isInSameDataNode(each, routeUnit)) {
                //插入语句参数分组构造
                result.addAll(((GroupedParameterBuilder) parameterBuilder).getParameters(count));
            }
            count++;
        }
        return result;
    }
    
    private boolean isInSameDataNode(final Collection<DataNode> dataNodes, final RouteUnit routeUnit) {
        if (dataNodes.isEmpty()) {
            return true;
        }
        for (DataNode each : dataNodes) {
            if (routeUnit.findTableMapper(each.getDataSourceName(), each.getTableName()).isPresent()) {
                return true;
            }
        }
        return false;
    }
}
