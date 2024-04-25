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

package org.apache.shardingsphere.underlying.common.properties;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.underlying.common.config.exception.ShardingSphereConfigurationException;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;

/**
 * Typed properties with a specified enum.
 */
@Slf4j
public abstract class TypedProperties<E extends Enum & TypedPropertyKey> {
    
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");
    
    @Getter
    private final Properties props;
    
    private final Map<E, TypedPropertyValue> cache;
    
    public TypedProperties(final Class<E> keyClass, final Properties props) {
        this.props = props;
        cache = preload(keyClass);
    }
    
    private Map<E, TypedPropertyValue> preload(final Class<E> keyClass) {
        E[] enumConstants = keyClass.getEnumConstants();
        Map<E, TypedPropertyValue> result = new HashMap<>(enumConstants.length, 1);
        Collection<String> errorMessages = new LinkedList<>();
        for (E each : enumConstants) {
            TypedPropertyValue value = null;
            try {
                value = new TypedPropertyValue(each, props.getOrDefault(each.getKey(), each.getDefaultValue()).toString());
            } catch (final TypedPropertyValueException ex) {
                errorMessages.add(ex.getMessage());
            }
            result.put(each, value);
        }
        if (!errorMessages.isEmpty()) {
            throw new ShardingSphereConfigurationException(Joiner.on(LINE_SEPARATOR).join(errorMessages));
        }
        return result;
    }
    
    /**
     * Get property value.
     *
     * @param key property key
     * @param <T> class type of return value
     * @return property value
     */
    @SuppressWarnings("unchecked")
    public <T> T getValue(final E key) {
        return (T) cache.get(key).getValue();
    }

    //4.3 配置动态生效
    /**
     * vivo定制改造方法 refresh property value.
     * @param key property key
     * @param value property value
     * @return 更新配置是否成功
     */
    public boolean refreshValue(final Class<E> keyClass,String key, String value){
        //获取配置类支持的配置项
        E[] enumConstants = keyClass.getEnumConstants();
        for (E each : enumConstants) {
            //遍历新的值
            if(each.getKey().equals(key)){
                try {
                    //空白value认为无效,取默认值
                    if(Strings.isNullOrEmpty(value)){
                        value = each.getDefaultValue();
                    }
                    //构造新属性
                    TypedPropertyValue typedPropertyValue = new TypedPropertyValue(each, value);
                    //替换缓存
                    cache.put(each, typedPropertyValue);
                    //原始属性也替换下,有可能会通过RuntimeContext直接获取Properties
                    props.put(key,value);
                    return true;
                } catch (final TypedPropertyValueException ex) {
                    log.error("refreshValue error. key={} , value={}", key, value, ex);
                }
            }
        }
        return false;
    }

}
