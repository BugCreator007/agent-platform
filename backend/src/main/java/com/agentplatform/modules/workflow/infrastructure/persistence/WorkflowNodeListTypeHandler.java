package com.agentplatform.modules.workflow.infrastructure.persistence;

import com.agentplatform.modules.workflow.domain.model.WorkflowNode;
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
 * 流程节点列表的 MyBatis 类型处理器。
 *
 * <p>所属层：infrastructure 基础设施层。把 {@code WorkflowNode} 列表序列化为 JSON 字符串
 * 写入 {@code workflows.nodes_json} 列，读取时反序列化回列表；由实体字段的
 * {@code @TableField(typeHandler = ...)} 引用。</p>
 *
 * <p>约束：使用独立的静态 {@link ObjectMapper}，不依赖 Spring 容器；反序列化失败时
 * 降级为空列表，避免单条脏数据导致整表查询失败。</p>
 */
public class WorkflowNodeListTypeHandler extends BaseTypeHandler<List<WorkflowNode>> {
    /** JSON 序列化器，仅处理节点列表结构。 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 将节点列表写入预编译语句参数。
     *
     * @param ps        预编译语句
     * @param i         参数下标
     * @param parameter 节点列表
     * @param jdbcType  JDBC 类型（本处理器忽略）
     * @throws SQLException 序列化失败时抛出
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<WorkflowNode> parameter, JdbcType jdbcType)
            throws SQLException {
        try {
            ps.setString(i, MAPPER.writeValueAsString(parameter));
        } catch (Exception e) {
            throw new SQLException("nodes serialize failed", e);
        }
    }

    /**
     * 按列名读取节点列表。
     *
     * @param rs         结果集
     * @param columnName 列名
     * @return 节点列表，空值或解析失败时为空列表
     * @throws SQLException 读取列失败时抛出
     */
    @Override
    public List<WorkflowNode> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    /**
     * 按列下标读取节点列表。
     *
     * @param rs          结果集
     * @param columnIndex 列下标
     * @return 节点列表，空值或解析失败时为空列表
     * @throws SQLException 读取列失败时抛出
     */
    @Override
    public List<WorkflowNode> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    /**
     * 从存储过程结果读取节点列表。
     *
     * @param cs          可调用语句
     * @param columnIndex 列下标
     * @return 节点列表，空值或解析失败时为空列表
     * @throws SQLException 读取列失败时抛出
     */
    @Override
    public List<WorkflowNode> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    /**
     * 解析 JSON 为节点列表；空白或格式错误时返回空列表。
     */
    private List<WorkflowNode> parse(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return MAPPER.readValue(value, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
