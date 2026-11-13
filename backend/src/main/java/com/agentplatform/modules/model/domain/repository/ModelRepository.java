package com.agentplatform.modules.model.domain.repository;

import com.agentplatform.modules.model.domain.model.ModelInfo;
import com.agentplatform.modules.model.domain.model.Provider;
import com.agentplatform.modules.model.domain.model.RoutingPolicy;

import java.util.List;
import java.util.Optional;

public interface ModelRepository {
    List<Provider> findProviders();
    Optional<Provider> findProvider(String id);
    List<ModelInfo> findModels();
    Optional<ModelInfo> findModel(String id);
    void saveProvider(Provider provider);
    void saveModel(ModelInfo model);
    void deleteModel(String id);
    RoutingPolicy routing();
    void saveRouting(RoutingPolicy routing);
    void deleteProvider(String id);
    void deleteModelsByProvider(String providerId);
}
