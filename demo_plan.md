# 企业级多租户 RAG 知识库系统 - Demo 实施计划

## 1. Demo 核心目标

**一句话描述：** 一个可以本地运行、展示“多公司数据隔离 + 内部文档问答”能力的 Web 应用。

**用户验收标准：**
1.  **多租户隔离**：A 公司上传的文档，B 公司用户完全看不到、问不到。
2.  **文档问答**：上传 PDF/Word 后，能基于文档内容回答问题，并**引用来源**。
3.  **简单易展**：打开浏览器，通过下拉框切换“公司”，即可演示隔离效果。
4.  **数据安全示意**：页面有明显标识，表明数据逻辑隔离且处理过程合规。

## 2. 技术范围

| 组件 | 本地开发环境 | AWS生产环境 |
|:---|:---|:---|
| 后端框架 | Spring Boot 3.5.0, Java 21 | 同左 |
| LLM模型 | OpenAI GPT-3.5-Turbo | AWS Bedrock Claude 3 Haiku |
| Embedding模型 | OpenAI text-embedding-ada-002 | AWS Bedrock Titan Embedding |
| 向量数据库 | PostgreSQL + pgvector | 同左 |
| 文档解析 | Apache PDFBox + Apache POI | 同左 |
| 多租户 | 数据库字段隔离 + ThreadLocal | 同左 |

## 3. 数据模型 (核心表)

### 3.1 租户表 (tenant)
| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| id | BIGSERIAL | 主键 |
| tenant_id | VARCHAR(50) | 唯一业务标识，如 `acme_corp` |
| name | VARCHAR(100) | 公司名称 |
| config | JSONB | 扩展配置（模型偏好等） |

### 3.2 文档表 (enterprise_document)
| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| id | BIGSERIAL | 主键 |
| tenant_id | VARCHAR(50) | **租户隔离关键字段** |
| file_name | VARCHAR(255) | 原始文件名 |
| file_type | VARCHAR(20) | PDF, DOCX |
| chunk_text | TEXT | 切分后的文本块 |
| chunk_metadata | JSONB | 存储页码、原始文件名 |
| upload_time | TIMESTAMP | 上传时间 |

### 3.3 对话审计表 (audit_log) - 简化版
| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| id | BIGSERIAL | 主键 |
| tenant_id | VARCHAR(50) | 租户隔离 |
| user_id | VARCHAR(50) | 用户标识（演示时固定为 `demo_user`） |
| question | TEXT | 用户问题 |
| answer | TEXT | 模型回答 |
| timestamp | TIMESTAMP | 时间戳 |

## 4. 后端模块实现清单 (按顺序)

你需要指导 Claude Code 按以下顺序生成代码，确保依赖关系清晰。

### 阶段一：项目基础与多租户核心 (1天)

**任务 1.1：多租户上下文管理**
-   **文件**：`TenantContext.java`, `TenantInterceptor.java`, `WebConfig.java`
-   **功能**：
    -   `TenantContext`：使用 `ThreadLocal` 存储当前请求的 `tenantId`。
    -   `TenantInterceptor`：从请求 Header `X-Tenant-ID` 中解析租户信息并设置到上下文。
    -   `WebConfig`：注册拦截器，对所有 API 路径生效。
-   **验收**：能在 Controller 中通过 `TenantContext.getCurrentTenantId()` 获取到租户 ID。

**任务 1.2：数据库自动注入租户 ID**
-   **文件**：`TenantAwareEntity.java` (抽象基类), 修改现有 Entity
-   **功能**：所有需要隔离的实体继承 `TenantAwareEntity`，利用 JPA 的 `@PrePersist` 注解，在插入数据库前自动从 `TenantContext` 获取并设置 `tenantId`。
-   **验收**：保存 `EnterpriseDocument` 时，不需要手动设置 `tenantId`，数据库记录自动填充正确值。

**任务 1.3：查询自动过滤租户**
-   **文件**：`TenantFilterConfig.java`, 修改 Repository 层
-   **功能**：配置 Hibernate Filter，在所有查询上自动附加 `tenant_id = :currentTenantId` 条件。
-   **验收**：使用不同的租户 ID 查询文档列表，只能看到各自租户的数据。

### 阶段二：文档处理与 RAG 核心 (1.5天)

**任务 2.1：文档解析服务**
-   **文件**：`DocumentParserService.java`
-   **功能**：
    -   提供 `parseDocument(MultipartFile file)` 方法。
    -   根据文件扩展名 (`.pdf` 或 `.docx`) 调用 PDFBox 或 POI 提取纯文本。
    -   返回提取的文本字符串。
-   **依赖**：pom.xml 中加入 `pdfbox`, `poi`, `poi-ooxml`。
-   **验收**：单元测试能成功从测试 PDF/Word 文件中提取出文本。

**任务 2.2：文档切分策略**
-   **文件**：`TextChunkingStrategy.java`
-   **功能**：将长文本切分为适合 Embedding 的块。
    -   **简单策略**：按固定长度 500 字符切分，重叠 50 字符。
    -   **输出**：`List<TextSegment>`，每个 segment 包含文本内容和元数据（如原始文件名、块序号）。
-   **验收**：能正确处理一段 1500 字的文本，输出 3 个有适当重叠的块。

**任务 2.3：集成 AWS Bedrock 与 Spring AI**
-   **文件**：`application.yml`, `BedrockConfig.java`
-   **功能**：
    -   配置 Spring AI 的 `BedrockChatModel` (Claude 3 Haiku) 和 `BedrockEmbeddingModel` (Titan Embedding)。
    -   **注意**：确保配置指向 `ap-southeast-1` (新加坡) 区域。
-   **依赖**：pom.xml 中加入 `spring-ai-bedrock-ai-spring-boot-starter`。
-   **验收**：应用启动成功，能通过 `@Autowired ChatModel` 调用 Bedrock 并得到回复。

**任务 2.4：文档入库与检索服务**
-   **文件**：`RagDocumentService.java`
-   **功能**：
    -   `indexDocument(String tenantId, String fileName, String fileContent)`：调用切分策略 → 调用 EmbeddingModel → 存入 pgvector。
    -   `searchSimilarChunks(String tenantId, String query)`：将查询语句向量化，从 pgvector 中检索最相似的 Top-K 个块，**并确保只检索当前 tenantId 的数据**。
-   **验收**：上传一份文档后，能通过检索服务找到与问题相关的文本块。

### 阶段三：API 接口与业务逻辑 (1天)

**任务 3.1：文档上传接口**
-   **文件**：`DocumentController.java`
-   **端点**：`POST /api/documents/upload`
-   **逻辑**：
    1.  接收文件和当前租户 ID (从 Header 获取)。
    2.  调用 `DocumentParserService` 解析文本。
    3.  调用 `RagDocumentService.indexDocument()` 将文本块向量化并存入数据库。
    4.  返回成功消息和处理的文本块数量。
-   **验收**：使用 Postman 调用接口，数据库的 `enterprise_document` 表中能看到正确的租户隔离数据。

**任务 3.2：聊天问答接口**
-   **文件**：`ChatController.java`
-   **端点**：`POST /api/chat/completions`
-   **请求体**：`{ "message": "用户问题" }`
-   **逻辑**：
    1.  从 Header 获取租户 ID。
    2.  调用 `RagDocumentService.searchSimilarChunks()` 检索相关文档片段。
    3.  构建 Prompt：`基于以下内容回答用户问题，如果无法从内容中找到答案，请说“根据现有资料无法回答”。\n\n【参考资料】\n{chunks}\n\n【用户问题】\n{question}`
    4.  调用 `BedrockChatModel` 生成最终回答。
    5.  将问题和回答异步存入 `audit_log` 表。
    6.  返回回答和引用的文档来源。
-   **验收**：提问文档相关的问题，能得到基于文档内容的准确回答。

### 阶段四：前端演示界面 (0.5天)

**任务 4.1：简单聊天界面 (HTML/JS)**
-   **文件**：`index.html` (放在 `src/main/resources/static/`)
-   **功能**：
    -   页面顶部有一个**公司选择下拉框** (选项：“ACME Corp” 对应 `acme_corp`；“Globex Inc” 对应 `globex_inc`)。
    -   下拉框切换时，所有后续 API 请求的 Header `X-Tenant-ID` 随之改变。
    -   聊天区域：显示对话历史，包含用户问题和机器人回答（回答下方以小字显示引用的文档来源）。
    -   一个简单的文件上传按钮，显示上传状态。
-   **验收**：可以通过 UI 切换公司，上传文件，并进行多轮隔离问答。

## 5. Demo 演示流程 (与客户沟通话术)

1.  **切换公司**：“现在我们在 ‘ACME Corp’ 视角，我们上传一份‘ACME 价格表’。”（上传 `acme_price.pdf`）。
2.  **验证隔离**：“现在切换到 ‘Globex Inc’，问他‘我们的价格是多少？’”
3.  **见证结果**：系统回答“无法回答”。再问“Globex 的员工手册”，回答基于正确文档。
4.  **展示价值**：“系统原生支持多租户，数据逻辑隔离，绝对安全。所有问答都有记录。”（展示审计日志查询接口，需预先实现一个简单的 `GET /api/admin/logs` 接口）。

