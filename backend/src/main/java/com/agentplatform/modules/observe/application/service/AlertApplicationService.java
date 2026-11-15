package com.agentplatform.modules.observe.application.service;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import com.agentplatform.modules.observe.domain.model.Alert;
import com.agentplatform.modules.observe.domain.repository.AlertRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 告警应用服务（应用层）。
 *
 * <p>提供告警查询与状态流转（确认 ack / 恢复 resolved）。状态变更委托给
 * 领域对象 {@link Alert}，本服务只负责加载、调用领域方法并持久化。</p>
 */
@Service
public class AlertApplicationService {
    private final AlertRepository repository;

    /**
     * 构造告警应用服务。
     *
     * @param repository 告警仓储端口
     */
    public AlertApplicationService(AlertRepository repository) {
        this.repository = repository;
    }

    /**
     * 按状态过滤查询告警。
     *
     * @param status 状态过滤（active/ack/resolved），为空时返回全部
     * @return 告警列表
     */
    public List<Alert> list(String status) {
        return repository.findAll().stream()
                .filter(a -> status == null || status.isBlank() || a.status().equals(status)).toList();
    }

    /**
     * 确认告警（active → ack），表示已有人接手处理。
     *
     * @param id 告警 ID
     * @return 确认后的告警
     * @throws BusinessException 告警不存在时抛出 NOT_FOUND
     */
    public Alert ack(String id) {
        Alert alert = require(id);
        alert.ack();
        repository.save(alert);
        return alert;
    }

    /**
     * 恢复告警（→ resolved），表示问题已消除。
     *
     * @param id 告警 ID
     * @return 恢复后的告警
     * @throws BusinessException 告警不存在时抛出 NOT_FOUND
     */
    public Alert resolve(String id) {
        Alert alert = require(id);
        alert.resolve();
        repository.save(alert);
        return alert;
    }

    /**
     * 按 ID 获取告警，不存在时抛出业务异常。
     *
     * @param id 告警 ID
     * @return 告警
     * @throws BusinessException 告警不存在时抛出 NOT_FOUND
     */
    private Alert require(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "告警不存在: " + id));
    }
}
