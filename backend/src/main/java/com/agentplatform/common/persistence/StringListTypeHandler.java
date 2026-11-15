package com.agentplatform.common.persistence;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

/**
 * 字符串列表与 TEXT 列的映射，使用单元分隔符存储。
 *
 * <p>位于共享内核的持久化层，供 MyBatis-Plus 实体通过
 * {@code @TableField(typeHandler = StringListTypeHandler.class)} 引用，
 * 把 {@code List<String>}（如用户角色）序列化为单个 TEXT 列，避免额外的关联表。
 * 选用 ASCII 单元分隔符（U+001F）而非逗号，避免元素本身含逗号时产生歧义。</p>
 */
public class StringListTypeHandler extends BaseTypeHandler<List<String>> {
    /** 单元分隔符，几乎不会出现在业务字符串中 */
    private static final String SEP = "\u001F";

    /**
     * 写入参数：将列表拼接为分隔符分隔的字符串。
     *
     * @param ps        预编译语句
     * @param i         参数下标
     * @param parameter 非空列表
     * @param jdbcType  JDBC 类型（未使用）
     * @throws SQLException 设置参数失败时抛出
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, String.join(SEP, parameter));
    }

    /**
     * 按列名读取并反序列化。
     *
     * @param rs         结果集
     * @param columnName 列名
     * @return 字符串列表，空列返回空列表
     * @throws SQLException 读取失败时抛出
     */
    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    /**
     * 按列下标读取并反序列化。
     *
     * @param rs          结果集
     * @param columnIndex 列下标
     * @return 字符串列表，空列返回空列表
     * @throws SQLException 读取失败时抛出
     */
    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    /**
     * 存储过程结果读取并反序列化。
     *
     * @param cs          可调用语句
     * @param columnIndex 列下标
     * @return 字符串列表，空列返回空列表
     * @throws SQLException 读取失败时抛出
     */
    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    /**
     * 反序列化：空值返回空列表；-1 保留尾部空串以便定位，但空元素会被过滤。
     *
     * @param value 数据库中的原始字符串
     * @return 非空字符串列表
     */
    private List<String> parse(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(SEP, -1)).filter(item -> !item.isBlank()).toList();
    }
}
