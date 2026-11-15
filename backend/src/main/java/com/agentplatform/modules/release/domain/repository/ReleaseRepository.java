package com.agentplatform.modules.release.domain.repository;

import com.agentplatform.modules.release.domain.model.Release;

import java.util.List;
import java.util.Optional;

/**
 * 发布记录仓储端口（domain 层）。
 * <p>只声明持久化抽象，实现位于 infrastructure 层；应用层依赖该接口，
 * 不感知 MyBatis-Plus 细节。</p>
 */
public interface ReleaseRepository {
    /**
     * 查询全部发布单（演示规模，无分页）。
     *
     * @return 发布记录列表
     */
    List<Release> findAll();

    /**
     * 按主键查询发布单。
     *
     * @param id 发布单唯一标识
     * @return 存在时返回聚合，否则返回空 Optional
     */
    Optional<Release> findById(String id);

    /**
     * 新增或更新发布单。
     *
     * @param release 待持久化的聚合
     */
    void save(Release release);
}
