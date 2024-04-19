package org.apache.shardingsphere.sql.parser;
//跳过部分分片执行
public final class RuleContextManager {
 
    private static final ThreadLocal<RuleContextManager> SKIP_CONTEXT_HOLDER = ThreadLocal.withInitial(RuleContextManager::new);
 
    /**
     * 是否跳过sharding
     */
    private boolean skipSharding;
 
    /**
     * 是否路由至主库
     */
    private boolean masterRoute;
 
    public static boolean isSkipSharding() {
        return SKIP_CONTEXT_HOLDER.get().skipSharding;
    }
 
    public static void setSkipSharding(boolean skipSharding) {
        SKIP_CONTEXT_HOLDER.get().skipSharding = skipSharding;
    }
 
    public static boolean isMasterRoute() {
 
        return SKIP_CONTEXT_HOLDER.get().masterRoute;
    }
 
    public static void setMasterRoute(boolean masterRoute) {
        SKIP_CONTEXT_HOLDER.get().masterRoute = masterRoute;
    }
 
    public static void clear(){
        SKIP_CONTEXT_HOLDER.remove();
    }
 
}