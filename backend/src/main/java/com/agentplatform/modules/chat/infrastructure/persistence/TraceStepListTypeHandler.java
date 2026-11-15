package com.agentplatform.modules.chat.infrastructure.persistence;

import com.agentplatform.modules.chat.domain.model.TraceStep;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * {@link TraceStep} 列表与 JSON 列互转的 MyBatis TypeHandler（infrastructure 层）。
 *
 * <p>会话 trace 以 JSON 数组存入 {@code chat_sessions.trace_json}；读取到非法 JSON 时
 * 降级为空列表，避免历史脏数据导致会话查询整体失败。</p>
 */
public class TraceStepListTypeHandler extends BaseTypeHandler<List<TraceStep>> {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 将 trace 列表序列化为 JSON 写入 PreparedStatement。
     *
     * @param ps         PreparedStatement
     * @param i          参数下标
     * @param parameter 待写入的 trace 列表
     * @param jdbcType  JDBC 类型
     * @throws SQLException 序列化失败时抛出
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<TraceStep> parameter, JdbcType jdbcType)
            throws SQLException {
        try {
            ps.setString(i, MAPPER.writeValueAsString(parameter));
        } catch (Exception e) {
            throw new SQLException("trace serialize failed", e);
        }
    }

    /**
     * 按列名读取 trace JSON。
     *
     * @param rs         结果集
     * @param columnName 列名
     * @return trace 列表；空值或解析失败时为空列表
     * @throws SQLException 读取列失败时抛出
     */
    @Override
    public List<TraceStep> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    /**
     * 按列下标读取 trace JSON。
     *
     * @param rs          结果集
     * @param columnIndex 列下标
     * @return trace 列表；空值或解析失败时为空列表
     * @throws SQLException 读取列失败时抛出
     */
    @Override
    public List<TraceStep> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    /**
     * 从 CallableStatement 按列下标读取 trace JSON。
     *
     * @param cs          CallableStatement
     * @param columnIndex 列下标
     * @return trace 列表；空值或解析失败时为空列表
     * @throws SQLException 读取列失败时抛出
     */
    @Override
    public List<TraceStep> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    // 容错解析：历史数据可能为空串或非法 JSON，统一降级为空列表。
    private List<TraceStep> parse(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return MAPPER.readValue(value, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
