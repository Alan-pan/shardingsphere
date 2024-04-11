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

package org.apache.shardingsphere.masterslave.route.engine.impl;

import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.api.hint.HintManager;
import org.apache.shardingsphere.core.rule.MasterSlaveRule;
import org.apache.shardingsphere.sql.parser.sql.statement.SQLStatement;
import org.apache.shardingsphere.sql.parser.sql.statement.dml.SelectStatement;

import java.util.ArrayList;

/**
 * Data source router for master-slave.
 */
//主从路由的核心逻辑
@RequiredArgsConstructor
public final class
MasterSlaveDataSourceRouter {
    
    private final MasterSlaveRule masterSlaveRule;
    
    /**
     * Route.
     * 
     * @param sqlStatement SQL statement
     * @return data source name
     */
    public String route(final SQLStatement sqlStatement) {
        if (isMasterRoute(sqlStatement)) {
            //ThreadLocal设置Master访问过
            MasterVisitedManager.setMasterVisited();
            return masterSlaveRule.getMasterDataSourceName();
        }
        //MasterSlaveLoadBalanceAlgorithm包含轮询和随机算法
        //轮询RoundRobinMasterSlaveLoadBalanceAlgorithm
        //随机RandomMasterSlaveLoadBalanceAlgorithm
        return masterSlaveRule.getLoadBalanceAlgorithm().getDataSource(
                masterSlaveRule.getName(), masterSlaveRule.getMasterDataSourceName(), new ArrayList<>(masterSlaveRule.getSlaveDataSourceNames()));
    }
    
    private boolean isMasterRoute(final SQLStatement sqlStatement) {
        //如果 SQL 语句包含锁（例如 SELECT ... FOR UPDATE），则判定为主库路由。
        //如果 SQL 语句不是 SELECT 类型的语句，则判定为主库路由。
        //如果主库已经访问过（可能是之前的操作已经在主库上执行过了），则判定为主库路由。
        //如果通过 HintManager 指定了只使用主库路由，则判定为主库路由。
        return containsLockSegment(sqlStatement) || !(sqlStatement instanceof SelectStatement) || MasterVisitedManager.isMasterVisited() || HintManager.isMasterRouteOnly();
    }
    
    private boolean containsLockSegment(final SQLStatement sqlStatement) {
        return sqlStatement instanceof SelectStatement && ((SelectStatement) sqlStatement).getLock().isPresent();
    }
}
