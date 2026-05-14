# Smart Wealth AI

基于 `Spring Boot + Spring AI + pgvector` 的财富管理 RAG AI 顾问，提供后端接口和可直接验收的 demo 页面。

## 功能

- 分析用户上个月和本月交易数据，输出收入、支出、净结余、储蓄率和消费结构。
- 通过 Spring AI `VectorStore` 检索用户风险等级、储蓄目标和理财产品私有知识。
- 先基于风险等级和目标期限生成候选理财产品。
- 再把候选产品、RAG 片段和财富分析结果交给 LLM，输出最终推荐产品和自然语言回答。
- 暴露前端可调用接口，并提供浏览器 demo 页面用于验收。

## 技术栈

- Java 21
- Spring Boot 3.5.0
- Spring AI 1.1.6
- Spring Web / Spring Data JPA / Validation / Actuator
- PostgreSQL + pgvector
- OpenAI Chat + Embedding

## 启动方式

### 1. 启动数据库

```bash
docker compose up -d
```

默认配置：

- `jdbc:postgresql://localhost:5432/smart_wealth_ai`
- 用户名：`postgres`
- 密码：`postgres`

### 2. 配置 OpenAI Key

```bash
export OPENAI_API_KEY=your_key_here
```

### 3. 启动应用

```bash
mvn spring-boot:run
```

应用启动后会自动：

- 执行 `schema.sql` 和 `data.sql`
- 初始化演示用户、目标、产品和交易数据
- 通过 [RagKnowledgeService.java](src/main/java/com/smartwealth/ai/service/RagKnowledgeService.java) 将私有知识写入 Spring AI `PgVectorStore`

## Demo 页面

启动后直接打开：

- `http://localhost:8080/`

该页面会调用后端接口完成：

- 用户列表加载
- 财富概览展示
- AI 聊天问答
- 候选产品和 LLM 最终推荐结果展示

Demo 页面文件：

- [index.html](src/main/resources/static/index.html)

## 后端接口

### 1. 查询用户列表

```bash
curl http://localhost:8080/api/v1/wealth-advisor/users
```

### 2. 查询用户财富概览

```bash
curl http://localhost:8080/api/v1/wealth-advisor/users/1/overview
```

### 3. AI 财富顾问聊天

```bash
curl -X POST http://localhost:8080/api/v1/wealth-advisor/chat \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "message": "我想在目标期限内完成储蓄目标，请结合我的风险等级和近期收支情况推荐理财产品。"
  }'
```

## 响应重点字段

- `candidateProducts`：规则筛选后的候选产品
- `finalRecommendedProducts`：LLM 最终选出的推荐产品
- `llmSelectionSummary`：LLM 对最终筛选逻辑的摘要
- `answer`：最终自然语言回答

## 关键代码

- 启动类：[SmartWealthAiApplication.java](src/main/java/com/smartwealth/ai/SmartWealthAiApplication.java)
- 接口层：[WealthAdvisorController.java](src/main/java/com/smartwealth/ai/api/WealthAdvisorController.java)
- 财富分析：[TransactionAnalysisService.java](src/main/java/com/smartwealth/ai/service/TransactionAnalysisService.java)
- 储蓄目标测算：[GoalProjectionService.java](src/main/java/com/smartwealth/ai/service/GoalProjectionService.java)
- 候选产品筛选：[ProductRecommendationService.java](src/main/java/com/smartwealth/ai/service/ProductRecommendationService.java)
- Spring AI RAG 写入与检索：[RagKnowledgeService.java](src/main/java/com/smartwealth/ai/service/RagKnowledgeService.java)
- Spring AI 对话编排：[LlmAdvisoryService.java](src/main/java/com/smartwealth/ai/service/LlmAdvisoryService.java)
- Demo 页面：[index.html](src/main/resources/static/index.html)

## 验证

已执行：

```bash
mvn -Dmaven.repo.local=/Users/lijinze/.m2/repository test
```

结果：`BUILD SUCCESS`
