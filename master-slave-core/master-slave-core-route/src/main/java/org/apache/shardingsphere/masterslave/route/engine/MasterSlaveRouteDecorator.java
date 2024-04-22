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

package org.apache.shardingsphere.masterslave.route.engine;

import org.apache.shardingsphere.api.hint.HintManager;
import org.apache.shardingsphere.core.rule.MasterSlaveRule;
import org.apache.shardingsphere.masterslave.route.engine.impl.MasterSlaveDataSourceRouter;
import org.apache.shardingsphere.masterslave.route.engine.impl.MasterVisitedManager;
import org.apache.shardingsphere.sql.parser.RuleContextManager;
import org.apache.shardingsphere.sql.parser.sql.statement.SQLStatement;
import org.apache.shardingsphere.sql.parser.sql.statement.SkipShardingStatement;
import org.apache.shardingsphere.sql.parser.sql.statement.dml.SelectStatement;
import org.apache.shardingsphere.underlying.common.config.properties.ConfigurationProperties;
import org.apache.shardingsphere.underlying.common.config.properties.ConfigurationPropertyKey;
import org.apache.shardingsphere.underlying.common.metadata.ShardingSphereMetaData;
import org.apache.shardingsphere.underlying.route.context.RouteContext;
import org.apache.shardingsphere.underlying.route.context.RouteMapper;
import org.apache.shardingsphere.underlying.route.context.RouteResult;
import org.apache.shardingsphere.underlying.route.context.RouteUnit;
import org.apache.shardingsphere.underlying.route.decorator.RouteDecorator;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

/**
 * Route decorator for master-slave.
 */
public final class MasterSlaveRouteDecorator implements RouteDecorator<MasterSlaveRule> {
    //主从路由decorate
    @Override
    public RouteContext decorate(final RouteContext routeContext, final ShardingSphereMetaData metaData, final MasterSlaveRule masterSlaveRule, final ConfigurationProperties properties) {
        /**
         * 如果配置了强制主库 MasterVisitedManager设置为true
         * 后续isMasterRoute中会保证路由至主库
         */
        if(properties.<Boolean>getValue(ConfigurationPropertyKey.MASTER_ROUTE_ONLY)){
            MasterVisitedManager.setMasterVisited();
        }

        //为空证明没有经过分片路由处理
        if (routeContext.getRouteResult().getRouteUnits().isEmpty()) {
            //判断走主从库,返回数据库名
            String dataSourceName = new MasterSlaveDataSourceRouter(masterSlaveRule).route(routeContext.getSqlStatementContext().getSqlStatement());
            RouteResult routeResult = new RouteResult();
            //根据具体的主库/从库名创建路由单元
            routeResult.getRouteUnits().add(new RouteUnit(new RouteMapper(dataSourceName, dataSourceName), Collections.emptyList()));
            return new RouteContext(routeContext.getSqlStatementContext(), Collections.emptyList(), routeResult);
        }
        Collection<RouteUnit> toBeRemoved = new LinkedList<>();
        Collection<RouteUnit> toBeAdded = new LinkedList<>();
        //不为空证明已经被分片路由处理了
        for (RouteUnit each : routeContext.getRouteResult().getRouteUnits()) {
            //如果某个路由单元对应的数据源名称与主从规则中的主库名称相同
            //RouteUnit(dataSourceMapper=RouteMapper(logicName=ds_0, actualName=ds_0), tableMappers=[RouteMapper(logicName=t_order, actualName=t_order_0)])
            //=>
            //RouteUnit(dataSourceMapper=RouteMapper(logicName=ds_0, actualName=ds_master_0), tableMappers=[RouteMapper(logicName=t_order, actualName=t_order_0)])
            if (masterSlaveRule.getName().equalsIgnoreCase(each.getDataSourceMapper().getActualName())) {
                //移除之前的路由规则
                toBeRemoved.add(each);
                //重新生成路由规则
                String actualDataSourceName = new MasterSlaveDataSourceRouter(masterSlaveRule).route(routeContext.getSqlStatementContext().getSqlStatement());
                toBeAdded.add(new RouteUnit(new RouteMapper(each.getDataSourceMapper().getLogicName(), actualDataSourceName), each.getTableMappers()));
            }
        }
        routeContext.getRouteResult().getRouteUnits().removeAll(toBeRemoved);
        routeContext.getRouteResult().getRouteUnits().addAll(toBeAdded);
        return routeContext;
    }
    
    @Override
    public int getOrder() {
        return 10;
    }
    
    @Override
    public Class<MasterSlaveRule> getType() {
        return MasterSlaveRule.class;
    }

}
