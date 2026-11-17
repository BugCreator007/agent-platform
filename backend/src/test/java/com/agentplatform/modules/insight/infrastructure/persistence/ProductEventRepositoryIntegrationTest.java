package com.agentplatform.modules.insight.infrastructure.persistence;

import com.agentplatform.modules.insight.domain.model.ProductEvent;
import com.agentplatform.modules.insight.domain.repository.ProductEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 产品事件持久化集成测试：验证 V12 表、属性 JSON 与按事件名统计。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductEventRepositoryIntegrationTest {
    @Autowired private ProductEventRepository events;

    /** 两条同名事件应聚合为数量 2。使用测试专用事件名，避免与其他用例共享 H2 数据库时互相干扰。 */
    @Test
    void countsByName() {
        Instant now = Instant.now();
        events.save(new ProductEvent("evt_test_1", "it_event_started", "alice", "s1", null, "a1",
                Map.of("source", "user_portal"), now));
        events.save(new ProductEvent("evt_test_2", "it_event_started", "alice", "s2", null, "a1",
                Map.of("source", "user_portal"), now));
        events.save(new ProductEvent("evt_test_3", "it_event_copied", "alice", "s1", "m1", "a1",
                Map.of("length", 12), now));

        Map<String, Long> counts = events.countByName(now.minusSeconds(60), now.plusSeconds(60));
        assertEquals(2L, counts.get("it_event_started"));
        assertEquals(1L, counts.get("it_event_copied"));
    }
}
