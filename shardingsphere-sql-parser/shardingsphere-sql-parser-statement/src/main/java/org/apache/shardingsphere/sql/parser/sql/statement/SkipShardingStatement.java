package org.apache.shardingsphere.sql.parser.sql.statement;

public class SkipShardingStatement implements SQLStatement{
    @Override
    public int getParameterCount() {
        return 0;
    }
}