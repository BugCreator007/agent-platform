package com.agentplatform.modules.insight.application.service;

import com.agentplatform.modules.insight.domain.model.ProductEvent;
import com.agentplatform.modules.insight.domain.repository.ProductEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 产品事件写入器（application 层）。
 *
 * <p>使用独立事务写入，避免事件失败影响主业务事务；
 * 重复 eventId 抛出的 DuplicateKeyException 由调用方按幂等处理。</p>
 */
@Service
public class ProductEventWriter {
    private final ProductEventRepository repository;

    /**
     * @param repository 事件仓储
     */
    public ProductEventWriter(ProductEventRepository repository) {
        this.repository = repository;
    }

    /**
     * 在独立事务中写入一条事件。
     *
     * @param event 事件
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(ProductEvent event) {
        repository.save(event);
    }
}
