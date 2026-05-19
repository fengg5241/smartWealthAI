# Smart Wealth AI

基于 `Spring Boot + Spring AI + pgvector` 的财富管理 RAG AI 顾问，提供后端接口和可直接验收的 demo 页面。

当前版本已经从“统一问答链路”升级为“语言识别 + 轻量意图分类 + 回复策略分流 + 专属业务处理/通用理财回答”多层架构，尤其补强了英文财富管理问题的处理。

## 功能

- 分析用户上个月和本月交易数据，输出收入、支出、净结余、储蓄率和消费结构。
- 通过 Spring AI `VectorStore` 检索用户风险等级、储蓄目标和理财产品私有知识。
- 先做语言识别和轻量 LLM 意图分类，再按固定意图编码和 `responsePolicy` 路由到不同回复通道。
- 对通用财富管理问题，继续基于风险等级、目标期限和产品池生成候选理财产品，再交给 LLM 输出最终回答。
- 对“不是专属场景，但仍然是理财问题”的问题，新增 `GENERIC_WEALTH_GUIDANCE` 通道，不再默认拒答，也不默认强推产品。
- 对缺少关键数据的问题，新增 `ASK_CLARIFY` 通道，优先要求补充必要信息。
- 对保险、税务、市场择时等当前能力不足或不宜直接回答的问题，新增 `SAFE_DECLINE` 通道。
- 对三个专属场景，优先走“专属处理器先计算，再由 LLM 只做润色”的半结构化链路：
  - `FUND_SELECTION`：`What is the best funds for $50,000?`
  - `PRODUCT_COMPARISON`：`Fixed deposits or bonds is better for me?`
  - `PORTFOLIO_REBALANCING`：`The market is so volatile now, how should I adjust my portfolio?`
- 暴露前端可调用接口，并提供浏览器 demo 页面用于验收。

## 当前问答链路

聊天主链路分为四段：

1. `语言识别`
2. `轻量 LLM 意图分类`
3. `按意图和 responsePolicy 选择回复通道`
4. `对专属英文场景，将结构化结果交给 LLM 做受限润色`

## 回复策略

除 `intentCode` 外，当前还会输出一个内部回复策略 `responsePolicy`：

- `SPECIALIZED_EXECUTE`
  - 命中专属 handler，先计算结构化结果，再交给 LLM 做受限润色
- `GENERIC_WEALTH_GUIDANCE`
  - 仍然是理财问题，但不需要专属处理器
  - 先给判断，再解释因素，再结合当前已知用户数据给出一般性建议
- `ASK_CLARIFY`
  - 问题仍在理财范围内，但缺少关键数据
  - 先要求补充金额、期限、持仓或负债信息
- `SAFE_DECLINE`
  - 问题属于理财相关，但当前产品不具备安全回答所需的数据或规则支持

典型示例：

- `给我一些投资建议`
  - `WEALTH_OVERVIEW + GENERIC_WEALTH_GUIDANCE`
- `Can I afford a $800k condo`
  - `GOAL_FEASIBILITY + GENERIC_WEALTH_GUIDANCE`

当前已定义的主要意图包括：

- `WEALTH_OVERVIEW`
- `CASHFLOW_ANALYSIS`
- `GOAL_PROGRESS`
- `GOAL_FEASIBILITY`
- `PRODUCT_RECOMMENDATION`
- `RISK_REBALANCING`
- `FUND_SELECTION`
- `PRODUCT_COMPARISON`
- `PORTFOLIO_REBALANCING`
- `OUT_OF_SCOPE`

几个典型映射：

- `What is the best funds for $50,000?`
  - `FUND_SELECTION + SPECIALIZED_EXECUTE`
- `Should I diversify my portfolio?`
  - `WEALTH_OVERVIEW + GENERIC_WEALTH_GUIDANCE`
- `Save for retirement or pay off debt?`
  - `WEALTH_OVERVIEW + ASK_CLARIFY`
- `What are the tax-efficient investment options?`
  - `WEALTH_OVERVIEW + SAFE_DECLINE`

## 技术栈

- Java 21
- Spring Boot 3.5.0
- Spring AI 1.1.6
- Spring Web / Spring Data JPA / Validation / Actuator
- PostgreSQL + pgvector
- OpenAI Chat + Embedding

## 数据模型

除原有用户画像、储蓄目标、交易流水、理财产品外，当前版本还补充了两块关键数据：

- `financial_product.product_category`
- `financial_product.currency`
- `financial_product.minimum_investment_amount`
- `user_portfolio_holding`

这样 demo 用户不仅有风险等级和收支目标，还有实际持仓数据，可以支撑组合再平衡类问题。

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
- 初始化演示用户、目标、产品、持仓和交易数据
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

专属场景示例：

```bash
curl -X POST http://localhost:8080/api/v1/wealth-advisor/chat \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "message": "What is the best funds for $50,000?"
  }'
```

```bash
curl -X POST http://localhost:8080/api/v1/wealth-advisor/chat \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 2,
    "message": "Fixed deposits or bonds is better for me?"
  }'
```

```bash
curl -X POST http://localhost:8080/api/v1/wealth-advisor/chat \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 3,
    "message": "The market is so volatile now, how should I adjust my portfolio?"
  }'
```

## 响应重点字段

- `candidateProducts`：规则筛选后的候选产品
- `finalRecommendedProducts`：LLM 最终选出的推荐产品
- `llmSelectionSummary`：LLM 对最终筛选逻辑的摘要
- `answer`：最终自然语言回答

对于专属英文场景：

- `candidateProducts`：专属处理器筛出的基金/比较产品/防御型候选资产
- `finalRecommendedProducts`：专属处理器保留的最终候选
- `advisoryHighlights`：会包含预算分配、比较结论或再平衡提示
- `answer`：先由专属处理器生成结构化结论，再交给 LLM 做受限润色

对于通用理财回答和专属场景，`investmentPlanSummaries` 会单独返回给前端，页面不再需要从 `answer` 文本里拆计划摘要。

## 关键代码

- 启动类：[SmartWealthAiApplication.java](src/main/java/com/smartwealth/ai/SmartWealthAiApplication.java)
- 接口层：[WealthAdvisorController.java](src/main/java/com/smartwealth/ai/api/WealthAdvisorController.java)
- 财富分析：[TransactionAnalysisService.java](src/main/java/com/smartwealth/ai/service/TransactionAnalysisService.java)
- 储蓄目标测算：[GoalProjectionService.java](src/main/java/com/smartwealth/ai/service/GoalProjectionService.java)
- 候选产品筛选：[ProductRecommendationService.java](src/main/java/com/smartwealth/ai/service/ProductRecommendationService.java)
- 持仓查询：[PortfolioQueryService.java](src/main/java/com/smartwealth/ai/service/PortfolioQueryService.java)
- 专属英文场景处理：[SpecializedAdvisoryService.java](src/main/java/com/smartwealth/ai/service/SpecializedAdvisoryService.java)
- Spring AI RAG 写入与检索：[RagKnowledgeService.java](src/main/java/com/smartwealth/ai/service/RagKnowledgeService.java)
- 轻量意图分类：[IntentClassificationService.java](src/main/java/com/smartwealth/ai/service/IntentClassificationService.java)
- 主聊天编排：[AiWealthChatService.java](src/main/java/com/smartwealth/ai/service/AiWealthChatService.java)
- 通用理财回答生成与专属结果润色：[LlmAdvisoryService.java](src/main/java/com/smartwealth/ai/service/LlmAdvisoryService.java)
- Demo 页面：[index.html](src/main/resources/static/index.html)

## 验证

已执行：

```bash
mvn test -Dtest=SpecializedAdvisoryServiceTest,IntentRoutingServiceTest,TransactionAnalysisServiceTest
```

结果：`BUILD SUCCESS`
