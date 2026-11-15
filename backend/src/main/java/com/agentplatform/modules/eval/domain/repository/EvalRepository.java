package com.agentplatform.modules.eval.domain.repository;

import com.agentplatform.modules.eval.domain.model.EvalRun;
import com.agentplatform.modules.eval.domain.model.EvalSuite;

import java.util.List;

/**
 * 评测仓储端口（domain 层）。
 * <p>聚合评测集与评测运行两类数据的读写抽象，实现位于 infrastructure 层。</p>
 */
public interface EvalRepository {
    /**
     * 查询全部评测集。
     *
     * @return 评测集列表
     */
    List<EvalSuite> suites();

    /**
     * 查询全部评测运行记录。
     *
     * @return 运行记录列表
     */
    List<EvalRun> runs();

    /**
     * 追加一条评测运行记录。
     *
     * @param run 运行记录
     */
    void addRun(EvalRun run);
}
