package org.apache.shardingsphere.example.core.api.entity;

import java.io.Serializable;

/**
 * @author 余攀
 * @description
 * @since 2024-04-19
 */
public class App implements Serializable {

    private static final long serialVersionUID = 661434701950670671L;
    String appCode;
    String appName;

    public App(String appCode, String appName) {
        this.appCode = appCode;
        this.appName = appName;
    }

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }
}
