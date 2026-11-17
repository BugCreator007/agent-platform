#!/usr/bin/env python3
"""
知识库检索评估脚本（无第三方依赖）。

用法：
  python3 scripts/retrieval_eval.py \
    --base-url http://localhost:8088 \
    --username admin --password '******' \
    --dataset kb-refund \
    --golden docs/retrieval-golden.example.json \
    --k 10

金标文件格式：
  {
    "queries": [
      {"query": "用户问题", "relevant": [{"source": "文档名.md", "chunk": 0}]}
    ]
  }
  - relevant 可用 source（文档展示名）或 documentId 匹配；chunk 可选，省略表示该文档任意分块。
  - 指标：Recall@k、MRR、nDCG@k（二值相关性）。
"""
import argparse
import json
import math
import sys
import urllib.error
import urllib.request


def request_json(url, method="GET", body=None, token=None):
    data = None if body is None else json.dumps(body).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", "replace")[:300]
        raise SystemExit(f"HTTP {exc.code} {url}: {detail}")
    except Exception as exc:  # noqa: BLE001
        raise SystemExit(f"请求失败 {url}: {exc}")


def login(base, username, password):
    payload = request_json(base + "/api/v1/auth/login", "POST",
                           {"username": username, "password": password})
    return payload["data"]["accessToken"]


def matches(hit, expected):
    if expected.get("documentId"):
        if hit.get("documentId") != expected["documentId"]:
            return False
    elif expected.get("source") and hit.get("source") != expected["source"]:
        return False
    if "chunk" in expected and hit.get("chunk") != expected["chunk"]:
        return False
    return True


def evaluate_query(base, token, dataset, item, k):
    query = item["query"]
    relevant = item.get("relevant", [])
    payload = request_json(f"{base}/api/v1/datasets/{dataset}/retrieval-test",
                           "POST", {"query": query}, token)
    hits = payload["data"]["hits"][:k]
    # 每个金标条目是否在前 k 条中被命中
    matched = [any(matches(hit, exp) for hit in hits) for exp in relevant]
    recall = sum(1 for m in matched if m) / len(relevant) if relevant else 0.0
    first_rank = 0
    for rank, hit in enumerate(hits, start=1):
        if any(matches(hit, exp) for exp in relevant):
            first_rank = rank
            break
    mrr = 1.0 / first_rank if first_rank else 0.0
    dcg = sum(1.0 / math.log2(rank + 1) for rank, hit in enumerate(hits, start=1)
              if any(matches(hit, exp) for exp in relevant))
    ideal_hits = min(len(relevant), k)
    idcg = sum(1.0 / math.log2(rank + 1) for rank in range(1, ideal_hits + 1))
    ndcg = dcg / idcg if idcg else 0.0
    return query, recall, mrr, ndcg, [h.get("source") for h in hits]


def main():
    parser = argparse.ArgumentParser(description="知识库检索指标评估")
    parser.add_argument("--base-url", default="http://localhost:8088")
    parser.add_argument("--token", help="已有 JWT；不传则用用户名密码登录")
    parser.add_argument("--username")
    parser.add_argument("--password")
    parser.add_argument("--dataset", required=True)
    parser.add_argument("--golden", required=True)
    parser.add_argument("--k", type=int, default=10)
    args = parser.parse_args()

    base = args.base_url.rstrip("/")
    token = args.token or login(base, args.username, args.password)
    with open(args.golden, encoding="utf-8") as fh:
        golden = json.load(fh)
    items = golden.get("queries", golden if isinstance(golden, list) else [])
    if not items:
        raise SystemExit("金标文件没有 queries")

    rows = []
    for item in items:
        query, recall, mrr, ndcg, sources = evaluate_query(
            base, token, args.dataset, item, args.k)
        rows.append((query, recall, mrr, ndcg))
        print(f"[{recall:.2f} {mrr:.2f} {ndcg:.2f}] {query}  top={sources[:5]}")

    n = len(rows)
    print("-" * 72)
    print(f"queries={n}  Recall@{args.k}={sum(r[1] for r in rows)/n:.3f}  "
          f"MRR={sum(r[2] for r in rows)/n:.3f}  nDCG@{args.k}={sum(r[3] for r in rows)/n:.3f}")
    sys.exit(0)


if __name__ == "__main__":
    main()
