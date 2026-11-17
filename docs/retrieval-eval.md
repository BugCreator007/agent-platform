# 检索评估指南

用金标查询集衡量知识库检索质量，避免“凭感觉调分块/阈值/模型”。

## 1. 准备金标集

复制 [retrieval-golden.example.json](retrieval-golden.example.json)，按数据集补充真实问题：

```json
{
  "dataset": "kb-refund",
  "queries": [
    {
      "query": "已签收商品还能无理由退款吗",
      "relevant": [{ "source": "七天无理由退款适用范围.md" }]
    }
  ]
}
```

- `source` 与检索结果中的文档展示名一致；也可以用 `documentId`。
- `chunk` 可选，填写后会精确匹配分块下标。
- 建议 100–300 条，覆盖同义改写、专有名词/单号、跨段多跳、无答案问题。

## 2. 执行评估

```bash
python3 scripts/retrieval_eval.py \
  --base-url https://admin.xianlu.site \
  --username admin --password '******' \
  --dataset kb-refund \
  --golden docs/retrieval-golden.example.json \
  --k 10
```

输出每条查询的 Recall/MRR/nDCG 和整体均值。

## 3. 指标口径

- **Recall@k**：前 k 条中命中的金标条目占全部金标条目的比例；
- **MRR**：第一个相关结果排名的倒数；
- **nDCG@k**：二值相关性折扣累计增益，除以理想排序的 IDCG。

## 4. 使用建议

- 先固定分块与索引，再比较 Embedding 模型；
- 每次只改一个变量（分块大小、阈值、候选数、rerank），对比同一份金标集；
- 除了离线指标，还要看端到端答案正确率、引用准确率、拒答率、P95 延迟和成本；
- 无答案问题应验证系统能返回“未找到依据”，而不是强行生成。

## 5. 当前默认值与建议

- 召回候选：`max(topK*5, 30)`；最终返回 `dataset.topK`；
- MMR 去重：默认 λ=0.7，使用分块向量计算多样性；
- Rerank：数据集开启且配置 `RERANK_ENABLED/BASE_URL/API_KEY/MODEL` 时生效，失败自动回退融合排序；
- 阈值与 Embedding 选型仍需用你的真实语料校准，参见 [backlog.md](backlog.md) 的 P1-1/P1-3。
