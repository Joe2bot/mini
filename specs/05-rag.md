# RAG 规格

## 1. 目的

允许 Agent / Workflow 从上传的文档中检索相关信息。

## 2. V1 支持文档

- TXT
- Markdown
- PDF

V1 的 PDF 仅支持可提取文本的 PDF：使用单一文本提取器，不支持 OCR、扫描件、表格/复杂版式还原。无法提取文本的文件标记为 ingestion failed。

## 3. Ingestion Pipeline

Document
 -> Parsing
 -> Chunking
 -> Embedding
 -> Qdrant

每个 Chunk 至少包含：
- document_id
- filename
- chunk_id
- 可选 page number
- text

## 4. Retrieval Pipeline

User Query
 -> Embedding
 -> Qdrant Similarity Search
 -> Top-K Documents
 -> Context Construction
 -> LLM

默认 Top-K = 5。

## 5. Knowledge Base

Knowledge Base：
- id
- name
- description
- embedding configuration
- document list

Document：
- id
- filename
- status
- chunk_count

## 6. Agent 集成

Agent 必须显式选择 Knowledge Base。Agent 只能通过 `knowledge_search` Tool 对所绑定的 Knowledge Base 检索，检索结果作为该 Tool Result 返回给 LLM。

Workflow 中的 RAG Node 必须引用 Knowledge Base。

## 7. 验收标准

- [ ] 创建 Knowledge Base
- [ ] 上传文档
- [ ] 解析文档
- [ ] Chunk
- [ ] 生成 Embedding
- [ ] 保存到 Qdrant
- [ ] 检索 Top-K
- [ ] Agent 通过 Knowledge Search Tool 获取检索结果
- [ ] Workflow RAG Node 将 Context 注入后续 LLM Node
- [ ] RAG 出现在 Trace
