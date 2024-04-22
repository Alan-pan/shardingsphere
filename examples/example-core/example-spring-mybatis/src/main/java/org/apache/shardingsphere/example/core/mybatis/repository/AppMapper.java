package org.apache.shardingsphere.example.core.mybatis.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.shardingsphere.example.core.api.entity.App;
import org.apache.shardingsphere.example.core.api.repository.CommonRepository;

/**
 * @author 余攀
 * @description
 * @since 2024-04-19
 */
@Mapper
public interface AppMapper extends CommonRepository<App,Long> {
    void insertBySelect(String appCode);

    App select(Integer appCode);
}
