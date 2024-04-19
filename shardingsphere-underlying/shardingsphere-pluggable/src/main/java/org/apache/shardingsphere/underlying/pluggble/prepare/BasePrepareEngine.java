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

package org.apache.shardingsphere.underlying.pluggble.prepare;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.shardingsphere.spi.order.OrderedRegistry;
import org.apache.shardingsphere.sql.parser.SQLParserEngine;
import org.apache.shardingsphere.underlying.common.config.properties.ConfigurationProperties;
import org.apache.shardingsphere.underlying.common.config.properties.ConfigurationPropertyKey;
import org.apache.shardingsphere.underlying.common.exception.ShardingSphereException;
import org.apache.shardingsphere.underlying.common.metadata.ShardingSphereMetaData;
import org.apache.shardingsphere.underlying.common.rule.BaseRule;
import org.apache.shardingsphere.underlying.executor.context.ExecutionContext;
import org.apache.shardingsphere.underlying.executor.context.ExecutionUnit;
import org.apache.shardingsphere.underlying.executor.context.SQLUnit;
import org.apache.shardingsphere.underlying.executor.log.SQLLogger;
import org.apache.shardingsphere.underlying.rewrite.SQLRewriteEntry;
import org.apache.shardingsphere.underlying.rewrite.context.SQLRewriteContext;
import org.apache.shardingsphere.underlying.rewrite.context.SQLRewriteContextDecorator;
import org.apache.shardingsphere.underlying.rewrite.engine.SQLRewriteEngine;
import org.apache.shardingsphere.underlying.rewrite.engine.SQLRewriteResult;
import org.apache.shardingsphere.underlying.rewrite.engine.SQLRouteRewriteEngine;
import org.apache.shardingsphere.underlying.route.DataNodeRouter;
import org.apache.shardingsphere.underlying.route.context.RouteContext;
import org.apache.shardingsphere.underlying.route.context.RouteUnit;
import org.apache.shardingsphere.sql.parser.RuleContextManager;
import org.apache.shardingsphere.underlying.route.decorator.RouteDecorator;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * Base prepare engine.
 */
@Slf4j(topic = "ShardingSphere-BasePrepareEngine-UserDefined")
@RequiredArgsConstructor
public abstract class BasePrepareEngine {
    
    private final Collection<BaseRule> rules;
    
    private final ConfigurationProperties properties;
    
    private final ShardingSphereMetaData metaData;
    
    private final DataNodeRouter router;
    
    private final SQLRewriteEntry rewriter;
    
    public BasePrepareEngine(final Collection<BaseRule> rules, final ConfigurationProperties properties, final ShardingSphereMetaData metaData, final SQLParserEngine parser) {
        this.rules = rules;
        this.properties = properties;
        this.metaData = metaData;
        router = new DataNodeRouter(metaData, properties, parser);
        rewriter = new SQLRewriteEntry(metaData.getSchema(), properties);
    }
    
    /**
     * Prepare to execute.
     *
     * @param sql SQL
     * @param parameters SQL parameters
     * @return execution context
     */
    public ExecutionContext prepare(final String sql, final List<Object> parameters) {
        List<Object> clonedParameters = cloneParameters(parameters);
        //跳过Sharding语法限制-判断是否可以跳过sharding,构造RuleContextManager的值
        buildSkipContext(sql);
        //解析+路由(executeRoute内部先进行解析再执行路由)
        RouteContext routeContext = executeRoute(sql, clonedParameters);
        ExecutionContext result = new ExecutionContext(routeContext.getSqlStatementContext());
        //跳过Sharding语法限制-sql最后的路由结果一定只有一个库
        if(RuleContextManager.isSkipSharding()) {
            log.info("可以跳过sharding的场景 {}", sql);
            if (!Objects.isNull(routeContext.getRouteResult())) {
                //getAllInstanceDataSourceNames随机获取一个数据源返回
                Collection<String> allInstanceDataSourceNames = this.metaData.getDataSources().getAllInstanceDataSourceNames();
                int routeUnitsSize = routeContext.getRouteResult().getRouteUnits().size();
                /*
                 * 1. 没有读写分离的情况下  跳过sharding路由会导致routeUnitsSize为0 此时需要判断数据源数量是否为1
                 * 2. 读写分离情况下 只会路由至具体的主库或从库 routeUnitsSize数量应该为1
                 */
                if (!(routeUnitsSize == 0 && allInstanceDataSourceNames.size() == 1) || routeUnitsSize > 1) {
                    throw new ShardingSphereException("可以跳过sharding,但是路由结果不唯一,SQL= %s ,routeUnits= %s ", sql, routeContext.getRouteResult().getRouteUnits());
                }
                Collection<String> actualDataSourceNames = routeContext.getRouteResult().getActualDataSourceNames();
                // 手动创建执行单元
                String datasourceName = CollectionUtils.isEmpty(actualDataSourceNames) ? allInstanceDataSourceNames.iterator().next() : actualDataSourceNames.iterator().next();
                ExecutionUnit executionUnit = new ExecutionUnit(datasourceName, new SQLUnit(sql, clonedParameters));
                result.getExecutionUnits().add(executionUnit);
                //标记该结果需要跳过
                result.setSkipShardingScenarioFlag(true);
            }
        } else {
            //改写
            result.getExecutionUnits().addAll(executeRewrite(sql, clonedParameters, routeContext));
        }
        if (properties.<Boolean>getValue(ConfigurationPropertyKey.SQL_SHOW)) {
            SQLLogger.logSQL(sql, properties.<Boolean>getValue(ConfigurationPropertyKey.SQL_SIMPLE), result.getSqlStatementContext(), result.getExecutionUnits());
        }
        return result;
    }

    //跳过Sharding语法限制-判断是否可以跳过sharding,构造RuleContextManager的值
    private void buildSkipContext(final String sql){
        Set<String> sqlTokenSet = new HashSet<>(Arrays.asList(sql.split("[\\s]")));
        if (CollectionUtils.isNotEmpty(rules)) {
            for (BaseRule baseRule : rules) {
                //定制方法，ShardingRule实现，判断sqlTokenSet是否包含逻辑表即可
                //主从表影响结果,先执行ShardingRule,再执行MasterSlaveRule
                if(baseRule.hasContainShardingTable(sqlTokenSet)){
                    RuleContextManager.setSkipSharding(false);
                    break;
                }else {
                    RuleContextManager.setSkipSharding(true);
                }
            }
        }
    }
    
    protected abstract List<Object> cloneParameters(List<Object> parameters);
    
    private RouteContext executeRoute(final String sql, final List<Object> clonedParameters) {
        registerRouteDecorator();
        return route(router, sql, clonedParameters);
    }
    
    private void registerRouteDecorator() {
        for (Class<? extends RouteDecorator> each : OrderedRegistry.getRegisteredClasses(RouteDecorator.class)) {
            RouteDecorator routeDecorator = createRouteDecorator(each);
            Class<?> ruleClass = (Class<?>) routeDecorator.getType();
            // FIXME rule.getClass().getSuperclass() == ruleClass for orchestration, should decouple extend between orchestration rule and sharding rule
            rules.stream().filter(rule -> rule.getClass() == ruleClass || rule.getClass().getSuperclass() == ruleClass).collect(Collectors.toList())
                    .forEach(rule -> router.registerDecorator(rule, routeDecorator));
        }
    }
    
    private RouteDecorator createRouteDecorator(final Class<? extends RouteDecorator> decorator) {
        try {
            return decorator.newInstance();
        } catch (final InstantiationException | IllegalAccessException ex) {
            throw new ShardingSphereException(String.format("Can not find public default constructor for route decorator `%s`", decorator), ex);
        }
    }
    
    protected abstract RouteContext route(DataNodeRouter dataNodeRouter, String sql, List<Object> parameters);
    //改写SQL从逻辑表到物理表
    private Collection<ExecutionUnit> executeRewrite(final String sql, final List<Object> parameters, final RouteContext routeContext) {
        //注册重写装饰器
        registerRewriteDecorator();
        //创建 SQLRewriteContext
        //完成了两件事，一个是对SQL参数进行了重写，一个是生成了SQLToken
        SQLRewriteContext sqlRewriteContext = rewriter.createSQLRewriteContext(sql, parameters, routeContext.getSqlStatementContext(), routeContext);
        //重写
        //创建完SQLRewriteContext后就对整条SQL进行重写和组装参数，可以看出每个RouteUnit都会重写SQL并获取自己对应的参数。
        //是否有路由重写?
        return routeContext.getRouteResult().getRouteUnits().isEmpty() ? rewrite(sqlRewriteContext) : rewrite(routeContext, sqlRewriteContext);
    }
    
    private void registerRewriteDecorator() {
        for (Class<? extends SQLRewriteContextDecorator> each : OrderedRegistry.getRegisteredClasses(SQLRewriteContextDecorator.class)) {
            //反射实例化
            SQLRewriteContextDecorator rewriteContextDecorator = createRewriteDecorator(each);
            Class<?> ruleClass = (Class<?>) rewriteContextDecorator.getType();
            // FIXME rule.getClass().getSuperclass() == ruleClass for orchestration, should decouple extend between orchestration rule and sharding rule
            //Collection<BaseRule> rules,其中ShardingRule implements BaseRule
            //SQLRewriteContextDecorator三个子类getType方法返回ShardingRule.class
            //如果匹配rewriter.registerDecorator,在SQLRewriteEntry.decorators放入对应的实例对象
            rules.stream().filter(rule -> rule.getClass() == ruleClass || rule.getClass().getSuperclass() == ruleClass).collect(Collectors.toList())
                    .forEach(rule -> rewriter.registerDecorator(rule, rewriteContextDecorator));
        }
    }
    
    private SQLRewriteContextDecorator createRewriteDecorator(final Class<? extends SQLRewriteContextDecorator> decorator) {
        try {
            return decorator.newInstance();
        } catch (final InstantiationException | IllegalAccessException ex) {
            throw new ShardingSphereException(String.format("Can not find public default constructor for rewrite decorator `%s`", decorator), ex);
        }
    }
    
    private Collection<ExecutionUnit> rewrite(final SQLRewriteContext sqlRewriteContext) {
        SQLRewriteResult sqlRewriteResult = new SQLRewriteEngine().rewrite(sqlRewriteContext);
        String dataSourceName = metaData.getDataSources().getAllInstanceDataSourceNames().iterator().next();
        return Collections.singletonList(new ExecutionUnit(dataSourceName, new SQLUnit(sqlRewriteResult.getSql(), sqlRewriteResult.getParameters())));
    }
    
    private Collection<ExecutionUnit> rewrite(final RouteContext routeContext, final SQLRewriteContext sqlRewriteContext) {
        Collection<ExecutionUnit> result = new LinkedHashSet<>();
        //创建完SQLRewriteContext后就对整条SQL进行重写和组装参数，可以看出每个RouteUnit都会重写SQL并获取自己对应的参数。
        for (Entry<RouteUnit, SQLRewriteResult> entry : new SQLRouteRewriteEngine().rewrite(sqlRewriteContext, routeContext.getRouteResult()).entrySet()) {
            result.add(new ExecutionUnit(entry.getKey().getDataSourceMapper().getActualName(), new SQLUnit(entry.getValue().getSql(), entry.getValue().getParameters())));
        }
        return result;
    }
}
