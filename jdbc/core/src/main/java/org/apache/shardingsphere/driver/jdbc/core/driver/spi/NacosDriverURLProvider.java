package org.apache.shardingsphere.driver.jdbc.core.driver.spi;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import org.apache.commons.lang3.StringUtils;
import org.apache.shardingsphere.driver.jdbc.core.driver.ShardingSphereDriverURLProvider;
import java.util.Properties;
//https://blog.csdn.net/qq_19677511/article/details/135887921
public class NacosDriverURLProvider implements ShardingSphereDriverURLProvider {
    private static final String NACOS_TYPE = "nacos:";

    public NacosDriverURLProvider() {
    }

    public boolean accept(String url) {

        return StringUtils.isNotBlank(url) && url.contains(NACOS_TYPE);
    }

    public byte[] getContent(String url) {
        ConfigService configService = null;
        String resultConfig = "";
        try {
            //https://blog.csdn.net/weixin_40909461/article/details/137606397
            //https://github.com/apache/shardingsphere/issues/25445
            //也可以通过url解析配置
            Properties properties = new Properties();
            properties.put(PropertyKeyConst.SERVER_ADDR,"localhost:8848");
            properties.put(PropertyKeyConst.USERNAME,"nacos");
            properties.put(PropertyKeyConst.PASSWORD,"nacos");
            properties.put(PropertyKeyConst.NAMESPACE,"");
            configService = NacosFactory.createConfigService(properties);
            //nacos测试文件见包内
            resultConfig = configService.getConfig("sharding-databases-tables-range.yaml", "SHARDING_SPHERE_DEFAULT_GROUP", 6000);

        } catch (Exception e){

        }
       return resultConfig.getBytes();
    }

    public static void main(String[] args) {


    }
}