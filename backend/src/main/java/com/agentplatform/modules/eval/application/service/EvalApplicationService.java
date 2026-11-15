package com.agentplatform.modules.eval.application.service;

import com.agentplatform.modules.eval.domain.model.EvalRun;
import com.agentplatform.modules.eval.domain.model.EvalSuite;
import com.agentplatform.modules.eval.domain.repository.EvalRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 评测应用服务（application 层）。
 * <p>提供评测集/运行记录查询与“触发一次评测”的演示用例。当前评测执行是模拟的：
 * 分数在 92-97 之间随机生成，不真正调用模型或评测框架；接入真实评测时替换
 * {@link #run} 内部实现即可，对外契约不变。</p>
 */
@Service
public class EvalApplicationService {
    private final EvalRepository repository;

    /**
     * @param repository 评测仓储端口
     */
    public EvalApplicationService(EvalRepository repository) {
        this.repository = repository;
    }

    /**
     * 查询全部评测集。
     *
     * @return 评测集列表
     */
    public List<EvalSuite> suites() { return repository.suites(); }

    /**
     * 查询全部评测运行记录。
     *
     * @return 运行记录列表
     */
    public List<EvalRun> runs() { return repository.runs(); }

    /**
     * 触发一次评测运行（演示实现：随机生成得分并写入运行记录）。
     * <p>评测集按 id 查找，找不到时回退到列表第一个；智能体名称/版本缺省时
     * 使用演示默认值。通过用例数按“用例总数 × 得分”四舍五入估算。</p>
     *
     * @param suiteId   评测集 id，可能为 null/不存在
     * @param agentName 被评测智能体名称，可为 null
     * @param version   被评测版本号，可为 null
     * @return 新建的评测运行记录
     * @throws IndexOutOfBoundsException 当系统中没有任何评测集时（下标 0 越界）
     */
    public EvalRun run(String suiteId, String agentName, String version) {
        EvalSuite suite = repository.suites().stream()
                .filter(s -> s.id().equals(suiteId)).findFirst()
                .orElse(repository.suites().get(0));
        // 模拟评测：生成 92.0-97.0 的一位小数得分，保留一位小数
        double score = Math.round((92 + Math.random() * 5) * 10.0) / 10.0;
        int passed = (int) Math.round(suite.cases() * score / 100);
        EvalRun run = new EvalRun(
                "run_" + UUID.randomUUID().toString().replace("-", "").substring(0, 6),
                suite.name(), agentName == null ? "售后工单助手" : agentName,
                version == null ? "v25" : version, passed + "/" + suite.cases(),
                score, "5m 20s", "刚刚", "success");
        repository.addRun(run);
        return run;
    }
}
