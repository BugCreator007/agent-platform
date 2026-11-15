package com.agentplatform.modules.release.application.service;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import com.agentplatform.modules.release.domain.model.Release;
import com.agentplatform.modules.release.domain.repository.ReleaseRepository;
import com.agentplatform.modules.eval.application.service.EvalGateService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 发布应用服务（application 层）。
 * <p>编排发布状态流转：审批前调用评测中心 {@link EvalGateService} 做发布门禁校验，
 * 再把领域异常 {@link IllegalStateException} 翻译为统一的
 * {@link BusinessException}（STATE_CONFLICT），避免领域异常泄漏到接口层。</p>
 * <p>跨模块依赖仅限 eval 模块 {@code application.service} 的公开 API。</p>
 */
@Service
public class ReleaseApplicationService {
    private final ReleaseRepository repository;
    private final EvalGateService evalGate;

    /**
     * @param repository 发布记录仓储端口
     * @param evalGate   评测发布门禁服务
     */
    public ReleaseApplicationService(ReleaseRepository repository, EvalGateService evalGate) {
        this.repository = repository;
        this.evalGate = evalGate;
    }

    /**
     * 查询全部发布单。
     *
     * @return 发布记录列表
     */
    public List<Release> list() { return repository.findAll(); }

    /**
     * 审批发布单。
     * <p>先执行评测门禁（得分/最近评测记录），通过后才允许从 approve 阶段进入灰度；
     * 门禁失败不会修改发布记录。</p>
     *
     * @param id 发布单唯一标识
     * @return 审批后的发布记录
     * @throws BusinessException id 不存在（NOT_FOUND）、门禁未通过或状态不允许（STATE_CONFLICT）
     */
    public Release approve(String id) {
        Release release = require(id);
        // 门禁在状态变更之前执行，保证校验失败时聚合保持原状
        evalGate.check(release.agentName(), release.version(), release.evalScore());
        try { release.approve(); } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, e.getMessage());
        }
        repository.save(release);
        return release;
    }

    /**
     * 推进发布阶段（draft 提交审批 / canary 放量至全量）。
     *
     * @param id 发布单唯一标识
     * @return 推进后的发布记录
     * @throws BusinessException id 不存在（NOT_FOUND）或当前状态不允许推进（STATE_CONFLICT）
     */
    public Release promote(String id) {
        Release release = require(id);
        try { release.promote(); } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, e.getMessage());
        }
        repository.save(release);
        return release;
    }

    /**
     * 调整灰度/全量流量比例；越界值由聚合夹紧到 0-100。
     *
     * @param id      发布单唯一标识
     * @param traffic 期望流量百分比
     * @return 更新后的发布记录
     * @throws BusinessException id 不存在时抛出 404
     */
    public Release updateTraffic(String id, int traffic) {
        Release release = require(id);
        release.updateTraffic(traffic);
        repository.save(release);
        return release;
    }

    /**
     * 回滚发布单（阶段置为 rollback、流量归零）。
     *
     * @param id 发布单唯一标识
     * @return 回滚后的发布记录
     * @throws BusinessException id 不存在时抛出 404
     */
    public Release rollback(String id) {
        Release release = require(id);
        release.rollback();
        repository.save(release);
        return release;
    }

    /**
     * 按 id 加载发布单，不存在时抛出 404 业务异常。
     *
     * @param id 发布单唯一标识
     * @return 发布聚合
     * @throws BusinessException id 不存在时抛出 404
     */
    private Release require(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "发布记录不存在: " + id));
    }
}
