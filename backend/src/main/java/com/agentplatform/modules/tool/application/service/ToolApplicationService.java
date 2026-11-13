package com.agentplatform.modules.tool.application.service;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import com.agentplatform.modules.tool.domain.model.Tool;
import com.agentplatform.modules.tool.domain.repository.ToolRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ToolApplicationService {
    private final ToolRepository repository;

    public ToolApplicationService(ToolRepository repository) {
        this.repository = repository;
    }

    public List<Tool> list() { return repository.findAll(); }

    public Tool toggle(String name) {
        Tool tool = require(name);
        tool.toggle();
        repository.save(tool);
        return tool;
    }

    public Tool create(String name, String type, String owner, String description) {
        return create(name, type, owner, description, null);
    }

    public Tool create(String name, String type, String owner, String description, String endpoint) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "工具名称不能为空");
        }
        if (repository.findByName(name).isPresent()) {
            throw new BusinessException(ErrorCode.NAME_CONFLICT, "工具已存在: " + name);
        }
        Tool tool = new Tool(name, type == null || type.isBlank() ? "HTTP" : type,
                owner == null || owner.isBlank() ? "平台团队" : owner,
                0, 0, "healthy", "刚刚", true,
                description == null || description.isBlank() ? "自定义工具" : description,
                endpoint);
        repository.save(tool);
        return tool;
    }

    public List<Tool> checkAll() {
        repository.findAll().forEach(Tool::check);
        return repository.findAll();
    }

    private Tool require(String name) {
        return repository.findByName(name)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "工具不存在: " + name));
    }
}
