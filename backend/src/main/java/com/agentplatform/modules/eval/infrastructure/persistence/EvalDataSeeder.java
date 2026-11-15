package com.agentplatform.modules.eval.infrastructure.persistence;

import com.agentplatform.modules.eval.infrastructure.persistence.entity.EvalRunEntity;
import com.agentplatform.modules.eval.infrastructure.persistence.repository.EvalSuiteRepository;
import com.agentplatform.modules.eval.infrastructure.persistence.repository.EvalRunRepository;
import com.agentplatform.modules.eval.infrastructure.persistence.entity.EvalSuiteEntity;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 评测演示数据初始化器（infrastructure 层）。
 * <p>仅在 {@code app.demo.seed=true}（缺省开启）且 eval_suites 表为空时写入
 * 评测集与运行记录；以评测集数量作为整体是否已初始化的判断依据。</p>
 */
@Component
@ConditionalOnProperty(name = "app.demo.seed", matchIfMissing = true)
public class EvalDataSeeder implements ApplicationRunner {
    private final EvalSuiteRepository suites;
    private final EvalRunRepository runs;

    /**
     * @param suites 评测集表仓储
     * @param runs   评测运行表仓储
     */
    public EvalDataSeeder(EvalSuiteRepository suites, EvalRunRepository runs) { this.suites = suites; this.runs = runs; }

    /**
     * 应用启动后检查评测集是否为空，为空则写入评测集与运行记录种子。
     *
     * @param args 启动参数（未使用）
     */
    @Override public void run(ApplicationArguments args) {
        if (suites.count() > 0) return;
        EvalSeedData seed = new EvalSeedData();
        seed.suites().forEach(s -> suites.save(EvalSuiteEntity.from(s)));
        seed.runs().forEach(r -> runs.save(EvalRunEntity.from(r)));
    }
}
