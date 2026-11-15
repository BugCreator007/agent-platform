package com.agentplatform.modules.workflow.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流程条件表达式求值服务。
 *
 * <p>所属层：application 应用层。为 CONDITION 节点与通知模板提供变量，并使用 Spring SpEL
 * 求值布尔表达式（如 {@code risk_score < 80}、{@code address.contains('上海')}）。</p>
 *
 * <p>关键设计：</p>
 * <ul>
 *   <li>使用 {@link SimpleEvaluationContext} 而非 {@code StandardEvaluationContext}，
 *       只开放 Map 属性访问与实例方法，禁止类型加载、构造器调用等；</li>
 *   <li>变量同时提供扁平别名（如 {@code address}）与带节点前缀的限定名（如 {@code route.address}）；</li>
 *   <li>表达式缺失、变量缺失或语法错误一律按 false 分支处理，避免单个条件错误拖垮整个流程。</li>
 * </ul>
 */
@Service
public class WorkflowExpressionService {
    /** 用于解析输入/节点输出 JSON。 */
    private final ObjectMapper mapper;
    /** SpEL 解析器，线程安全，可复用。 */
    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * 构造表达式服务。
     *
     * @param mapper JSON 序列化/反序列化器
     */
    public WorkflowExpressionService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 求值条件表达式。
     *
     * @param expression 待求值的 SpEL 表达式；为空白时视为 true（无条件约束）
     * @param inputJson  流程启动输入 JSON，会被展开为表达式变量
     * @param outputs    已完成节点的输出（nodeKey → JSON 字符串），按节点 key 限定访问
     * @return 表达式结果；非布尔值按字符串转布尔处理，求值异常或变量缺失时返回 false
     */
    public boolean evaluate(String expression, String inputJson, Map<String, String> outputs) {
        if (expression == null || expression.isBlank()) return true;
        Map<String, Object> variables = variables(inputJson, outputs);
        // SimpleEvaluationContext 只允许 MapAccessor 与实例方法，属于受限上下文，避免任意代码执行。
        SimpleEvaluationContext context = SimpleEvaluationContext
                .forPropertyAccessors(new MapAccessor())
                .withInstanceMethods()
                .build();
        try {
            Expression parsed = parser.parseExpression(expression);
            Object value = parsed.getValue(context, variables);
            if (value instanceof Boolean b) return b;
            return value != null && Boolean.parseBoolean(String.valueOf(value));
        } catch (Exception e) {
            // Missing variables and malformed expressions are treated as a false branch,
            // instead of turning the whole workflow into a failed run.
            return false;
        }
    }

    /**
     * 构建表达式/通知模板可用的变量表。
     *
     * <p>变量来源：</p>
     * <ul>
     *   <li>{@code input}、{@code text}：原始输入对象与原始 JSON 文本；</li>
     *   <li>输入字段的扁平别名与 snake_case 别名（如 {@code riskScore} 同时映射 {@code risk_score}）；</li>
     *   <li>{@code nodes}/{@code outputs}：按节点 key 组织的输出 Map；</li>
     *   <li>每个节点输出的字段以 {@code nodeKey.field} 形式展开。</li>
     * </ul>
     *
     * @param inputJson 流程启动输入 JSON
     * @param outputs   已完成节点输出（nodeKey → JSON 字符串），可为 {@code null}
     * @return 有序变量 Map
     */
    public Map<String, Object> variables(String inputJson, Map<String, String> outputs) {
        Map<String, Object> variables = new LinkedHashMap<>();
        Map<String, Object> input = parse(inputJson);
        // 先展开字段别名，使表达式可直接写 address、risk_score 等短变量名。
        flatten(variables, input, null);
        variables.put("input", input);
        variables.put("text", inputJson == null ? "" : inputJson);
        Map<String, Object> nodeOutputs = new LinkedHashMap<>();
        if (outputs != null) {
            for (Map.Entry<String, String> entry : outputs.entrySet()) {
                Map<String, Object> parsed = parse(entry.getValue());
                nodeOutputs.put(entry.getKey(), parsed);
                // 同时提供带节点前缀的变量（如 judge.output），避免不同节点同名字段互相覆盖。
                flatten(variables, parsed, entry.getKey());
            }
        }
        variables.put("nodes", nodeOutputs);
        variables.put("outputs", nodeOutputs);
        return variables;
    }

    /**
     * 解析 JSON 为 Map；非 Map 或解析失败时包装为 {@code value} 字段，保证表达式始终有 Map 可访问。
     */
    private Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Object value = mapper.readValue(json, Object.class);
            return value instanceof Map<?, ?> map ? toStringMap(map) : Map.of("value", value);
        } catch (Exception e) {
            return Map.of("value", json);
        }
    }

    /** 将任意键 Map 转为字符串键 Map，避免 SpEL 以非字符串键访问时失败。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toStringMap(Map<?, ?> value) {
        Map<String, Object> result = new LinkedHashMap<>();
        value.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }

    /**
     * 递归展开 Map 字段：同时写入全限定名与短名，并对嵌套 Map 继续展开。
     */
    private void flatten(Map<String, Object> target, Map<String, Object> source, String prefix) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = prefix == null ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = normalize(entry.getValue());
            put(target, key, value);
            // 同时提供短名，方便表达式直接引用字段；put 使用 putIfAbsent，短名冲突时保留先出现的值。
            put(target, entry.getKey(), value);
            if (value instanceof Map<?, ?> nested) {
                flatten(target, toStringMap(nested), key);
            }
        }
    }

    /**
     * 字符串值归一：能解析为数字/布尔/JSON 的字符串转换为对应类型，便于 SpEL 做数值比较。
     */
    private Object normalize(Object value) {
        if (value instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.matches("-?\\d+(\\.\\d+)?")) {
                try { return Double.parseDouble(trimmed); } catch (NumberFormatException ignored) { }
            }
            if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
                return Boolean.parseBoolean(trimmed);
            }
            if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                try {
                    Object nested = mapper.readValue(trimmed, Object.class);
                    return nested instanceof Map<?, ?> map ? toStringMap(map) : nested;
                } catch (Exception ignored) { }
            }
        }
        return value;
    }

    /** 写入原键与 snake_case 键；使用 putIfAbsent 保证先出现的变量优先。 */
    private void put(Map<String, Object> target, String key, Object value) {
        target.putIfAbsent(key, value);
        target.putIfAbsent(toSnakeCase(key), value);
    }

    /** 驼峰转 snake_case，例如 {@code riskScore → risk_score}。 */
    private String toSnakeCase(String key) {
        return key.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
