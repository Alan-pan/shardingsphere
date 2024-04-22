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

package org.apache.shardingsphere.example.core.mybatis.service;

import org.apache.shardingsphere.example.core.api.entity.Address;
import org.apache.shardingsphere.example.core.api.entity.App;
import org.apache.shardingsphere.example.core.api.entity.Order;
import org.apache.shardingsphere.example.core.api.entity.OrderItem;
import org.apache.shardingsphere.example.core.api.repository.AddressRepository;
import org.apache.shardingsphere.example.core.api.repository.OrderItemRepository;
import org.apache.shardingsphere.example.core.api.repository.OrderRepository;
import org.apache.shardingsphere.example.core.api.service.ExampleService;
import org.apache.shardingsphere.example.core.mybatis.repository.AppMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Service
@Primary
public class OrderServiceImpl implements ExampleService {
    
    @Resource
    private OrderRepository orderRepository;
    
    @Resource
    private OrderItemRepository orderItemRepository;
    
    @Resource
    private AddressRepository addressRepository;

    @Resource
    private AppMapper appMapper;

    @Override
    public void initEnvironment() throws SQLException {
//      测试insertbyselect
//        4.1 跳过Sharding语法限制
//        t_app广播表
//        appMapper.createTableIfNotExists();
//        appMapper.selectAll();
//        appMapper.insert(new App("1","1"));

//        4.2 强制路由主库,由于只配置了分表,没有配置分库,所以路由结果是两个库的从库
//[INFO ] 2024-04-22 17:00:07,611 --main-- [ShardingSphere-SQL] Actual SQL: ds_master_0_slave_0 ::: SELECT * FROM t_app_0 WHERE app_code = ?; ::: [2]
//[INFO ] 2024-04-22 17:00:07,611 --main-- [ShardingSphere-SQL] Actual SQL: ds_master_1_slave_0 ::: SELECT * FROM t_app_0 WHERE app_code = ?; ::: [2]
//=>
//        配置
//        spring.shardingsphere.props.master.route.only=true
//[INFO ] 2024-04-22 17:05:55,060 --main-- [ShardingSphere-SQL] Actual SQL: ds_master_0 ::: SELECT * FROM t_app_0 WHERE app_code = ?; ::: [2]
//[INFO ] 2024-04-22 17:05:55,060 --main-- [ShardingSphere-SQL] Actual SQL: ds_master_1 ::: SELECT * FROM t_app_0 WHERE app_code = ?; ::: [2]
        appMapper.select(2);
//        appMapper.dropTable();

//        4.2 强制路由主库
//        t_app配置主从库+配置开启主库路由配置项ConfigurationPropertyKey.java=>master.route.only
//        appMapper.selectAll();

//        orderRepository.createTableIfNotExists();
//        orderItemRepository.createTableIfNotExists();
//        orderRepository.truncateTable();
//        orderItemRepository.truncateTable();
//        initAddressTable();
    }
    
    private void initAddressTable() throws SQLException {
        addressRepository.createTableIfNotExists();
        addressRepository.truncateTable();
        for (int i = 1; i <= 10; i++) {
            Address entity = new Address();
            entity.setAddressId((long) i);
            entity.setAddressName("address_" + String.valueOf(i));
            addressRepository.insert(entity);
        }
    }

    @Override
    public void cleanEnvironment() throws SQLException {
        orderRepository.dropTable();
        orderItemRepository.dropTable();
    }
    
    @Override
    @Transactional
    public void processSuccess() throws SQLException {
        System.out.println("-------------- Process Success Begin ---------------");
        List<Long> orderIds = insertData();
        printData();
        deleteData(orderIds);
        printData();
        System.out.println("-------------- Process Success Finish --------------");
    }
    
    @Override
    @Transactional
    public void processFailure() throws SQLException {
        System.out.println("-------------- Process Failure Begin ---------------");
        insertData();
        System.out.println("-------------- Process Failure Finish --------------");
        throw new RuntimeException("Exception occur for transaction test.");
    }

    private List<Long> insertData() throws SQLException {
        System.out.println("---------------------------- Insert Data ----------------------------");
        List<Long> result = new ArrayList<>(10);
        for (int i = 1; i <= 10; i++) {
            Order order = new Order();
            order.setUserId(i);
            order.setAddressId(i);
            order.setStatus("INSERT_TEST");
            orderRepository.insert(order);
            OrderItem item = new OrderItem();
            item.setOrderId(order.getOrderId());
            item.setUserId(i);
            item.setStatus("INSERT_TEST");
            orderItemRepository.insert(item);
            result.add(order.getOrderId());
        }
        return result;
    }

    private void deleteData(final List<Long> orderIds) throws SQLException {
        System.out.println("---------------------------- Delete Data ----------------------------");
        for (Long each : orderIds) {
            orderRepository.delete(each);
            orderItemRepository.delete(each);
        }
    }
    
    @Override
    public void printData() throws SQLException {
        System.out.println("---------------------------- Print Order Data -----------------------");
        for (Object each : orderRepository.selectAll()) {
            System.out.println(each);
        }
        System.out.println("---------------------------- Print OrderItem Data -------------------");
        for (Object each : orderItemRepository.selectAll()) {
            System.out.println(each);
        }
    }
}
