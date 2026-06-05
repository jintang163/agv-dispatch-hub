# AGV 机器人任务调度中心

基于 Spring Boot 3 + Vue 3 的仓储 AGV 任务调度系统，解决多机器人路径冲突和任务优先级抢占问题。

## 技术栈

### 后端
- **核心框架**: Spring Boot 3.2.5
- **数据库**: PostgreSQL 16
- **缓存**: Redis 7
- **消息队列**: MQTT (EMQX 5.7.0)
- **ORM**: Spring Data JPA + MyBatis Plus
- **实时推送**: WebSocket (STOMP)
- **API文档**: Knife4j (OpenAPI 3)

### 前端
- **核心框架**: Vue 3.4.21 (Composition API)
- **UI组件库**: Element Plus 2.6.3
- **图表库**: ECharts 5.5.0
- **构建工具**: Vite 5
- **实时通信**: SockJS + STOMP

### 部署
- **容器化**: Docker + Docker Compose
- **反向代理**: Nginx

## 系统架构

### 项目结构
```
agv-dispatch-hub/
├── agv-dispatch-common/        # 公共模块
│   ├── entity/                 # JPA实体类
│   ├── dto/                    # 数据传输对象
│   ├── enums/                  # 枚举类
│   ├── util/                   # 工具类
│   └── constant/               # 常量定义
├── agv-dispatch-core/          # 核心调度模块
│   ├── repository/             # 数据访问层
│   └── service/                # 业务逻辑层
│       ├── TaskDispatchService       # 任务调度服务
│       ├── PathPlanningService       # 路径规划服务
│       └── ConflictDetectionService  # 冲突检测服务
├── agv-dispatch-mqtt/          # MQTT通信模块
│   ├── config/                 # MQTT配置
│   ├── handler/                # 消息处理器
│   ├── gateway/                # 消息发送网关
│   └── scheduler/              # 定时任务
├── agv-dispatch-api/           # API接口模块
│   ├── controller/             # REST控制器
│   ├── config/                 # 配置类
│   ├── service/                # WebSocket推送服务
│   └── exception/              # 全局异常处理
├── frontend/                   # 前端项目
│   ├── src/
│   │   ├── views/              # 页面组件
│   │   ├── api/                # API封装
│   │   ├── router/             # 路由配置
│   │   └── assets/             # 静态资源
│   ├── Dockerfile              # 前端Dockerfile
│   └── nginx.conf              # Nginx配置
├── docker/                     # Docker相关
│   └── postgres/               # PostgreSQL初始化脚本
├── docker-compose.yml          # Docker Compose编排
├── Dockerfile                  # 后端Dockerfile
└── pom.xml                     # 父POM
```

### 核心功能架构

#### 任务调度流程
```
WMS系统 → MQTT/REST API → 任务创建 → Redis优先级队列 → 自动调度(5s)
                                                          ↓
                                                路径规划(Dijkstra)
                                                          ↓
                                                冲突检测(前瞻3步)
                                                          ↓
                                                任务分配给AGV
                                                          ↓
                                                MQTT下发任务 → AGV执行
                                                          ↓
                                                AGV状态反馈 → 更新进度
                                                          ↓
                                                任务完成/异常/取消
```

#### 冲突检测与解决
1. **检测类型**:
   - 对向冲突: 两AGV相向行驶
   - 交叉冲突: 路径在节点交叉
   - 跟车冲突: 后车速度快于前车
   - 资源冲突: 多AGV抢占同一节点

2. **解决策略**:
   - 优先级比较: 高优先级任务优先
   - 截止时间比较: 截止时间早的优先
   - 任务进度比较: 进度快的优先
   - 低优先级AGV暂停让行

## 数据库设计

### 核心表结构

1. **agv_map_node** - 地图节点表
2. **agv_robot** - AGV机器人表
3. **agv_task** - 任务表
4. **agv_task_log** - 任务操作日志表
5. **agv_conflict_record** - 冲突记录表

## 部署说明

### 方式一：Docker Compose 一键部署

```bash
# 克隆项目
git clone <repository-url>
cd agv-dispatch-hub

# 启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f agv-dispatch-api
```

**访问地址**:
- 前端: http://localhost
- API文档: http://localhost:8080/doc.html
- EMQX控制台: http://localhost:18083 (admin/admin123)

### 方式二：本地开发调试

#### 后端
```bash
# 进入项目根目录
cd agv-dispatch-hub

# 编译项目
mvn clean install -DskipTests

# 启动API模块
cd agv-dispatch-api
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

#### 前端
```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

### 服务端口说明

| 服务 | 端口 | 说明 |
|------|------|------|
| PostgreSQL | 5432 | 数据库 |
| Redis | 6379 | 缓存 |
| EMQX MQTT | 1883 | MQTT协议 |
| EMQX WebSocket | 8083 | MQTT over WebSocket |
| EMQX Dashboard | 18083 | EMQX管理控制台 |
| Spring Boot API | 8080 | 后端API |
| Nginx | 80 | 前端页面 |

## API 接口说明

### 任务管理接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/tasks | 创建任务 |
| GET | /api/v1/tasks/{id} | 查询任务详情 |
| POST | /api/v1/tasks/query | 分页查询任务 |
| POST | /api/v1/tasks/assign | 手动分配任务 |
| POST | /api/v1/tasks/{id}/auto-assign | 自动分配任务 |
| POST | /api/v1/tasks/cancel | 取消任务 |
| POST | /api/v1/tasks/reassign | 重分配任务 |
| POST | /api/v1/tasks/priority | 更新任务优先级 |
| GET | /api/v1/tasks/queue | 获取任务队列 |
| POST | /api/v1/tasks/queue/refresh | 刷新任务队列 |
| GET | /api/v1/tasks/{id}/logs | 获取任务日志 |
| GET | /api/v1/tasks/statistics | 获取任务统计 |

### AGV管理接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/v1/agvs | 查询AGV列表 |
| GET | /api/v1/agvs/{id} | 查询AGV详情 |
| GET | /api/v1/agvs/available | 获取可用AGV |
| POST | /api/v1/agvs/{id}/pause | 暂停AGV |
| POST | /api/v1/agvs/{id}/resume | 恢复AGV |
| POST | /api/v1/agvs/{id}/stop | 紧急停车 |
| POST | /api/v1/agvs/{id}/charge | 呼叫充电 |

### 调度控制接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/v1/dispatch/conflicts | 检测冲突 |
| GET | /api/v1/dispatch/conflicts/unresolved | 获取未解决冲突 |
| POST | /api/v1/dispatch/conflicts/{id}/resolve | 解决单个冲突 |
| POST | /api/v1/dispatch/conflicts/resolve-all | 解决所有冲突 |
| GET | /api/v1/dispatch/path/plan | 路径规划 |
| GET | /api/v1/dispatch/path/occupied | 获取占用路径 |
| POST | /api/v1/dispatch/path/init | 初始化地图 |

## MQTT 主题说明

### 订阅主题

| 主题 | 说明 |
|------|------|
| agv/+/status | AGV状态上报 |
| agv/+/task/feedback | 任务执行反馈 |
| agv/+/heartbeat | AGV心跳 |
| agv/+/fault | AGV故障告警 |
| wms/task/create | WMS创建任务 |

### 发布主题

| 主题 | 说明 |
|------|------|
| agv/{agvId}/task/assign | 下发任务 |
| agv/{agvId}/task/cancel | 取消任务 |
| agv/{agvId}/control/pause | 暂停控制 |
| agv/{agvId}/control/resume | 恢复控制 |
| agv/{agvId}/control/stop | 紧急停车 |

## 前端页面说明

1. **实时监控大屏** (`/dashboard`)
   - 任务统计卡片
   - 任务状态/AGV状态饼图
   - 今日任务趋势折线图
   - AGV位置散点图
   - 活跃任务/AGV列表

2. **任务管理** (`/tasks`)
   - 任务列表查询
   - 创建任务
   - 任务详情/日志
   - 任务分配/取消/重分配
   - 优先级调整

3. **任务队列** (`/queue`)
   - 优先级队列可视化
   - 优先级/截止时间分布图
   - 拖拽调整优先级
   - 高优先级任务插队

4. **AGV管理** (`/agvs`)
   - AGV列表/状态筛选
   - AGV详情查看
   - 暂停/恢复/紧急停车
   - 呼叫充电
   - 状态/电量分布图

5. **调度控制** (`/dispatch`)
   - 仓库地图可视化
   - 冲突检测与解决
   - 路径规划测试
   - 占用路径查看

## 核心算法说明

### 任务队列排序算法
综合优先级(40%)和截止时间(60%)的加权评分：
```
score = priorityWeight * 0.4 + deadlineWeight * 0.6

priorityWeight: HIGH=3, MEDIUM=2, LOW=1
deadlineWeight: 时间越近权重越高
```

### 路径规划算法
基于Dijkstra算法，考虑路径占用惩罚：
```
总代价 = 距离代价 + 占用惩罚系数 * 路径占用时间
```

### 冲突解决策略
三级比较策略：
1. 优先级比较（高优先级优先）
2. 截止时间比较（截止时间早优先）
3. 任务进度比较（进度快优先）

## 注意事项

1. **生产环境**请修改默认密码和敏感配置
2. AGV心跳超时设置为2分钟，超时标记为离线
3. 路径前瞻检测步数为3步，可根据实际场景调整
4. Redis分布式锁确保调度任务不并发执行
5. 任务状态机严格校验状态转换合法性

## 开发规范

- 后端代码遵循 Alibaba Java Coding Guidelines
- 前端代码遵循 Vue 3 官方风格指南
- 所有接口返回统一格式 `Result<T>`
- 异常统一处理，返回友好错误信息

## License

MIT License
