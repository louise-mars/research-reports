# 分布式任务调度系统 — 核心架构设计文档

> 版本：v1.0 | 日期：2026-05-12 | 状态：**鲁班出品**

---

## 一、系统架构设计

### 1.1 整体架构图

```
                                    ┌─────────────────────────────────────────────┐
                                    │              外层接入层                     │
                                    │  (HTTP/gRPC API · 任务提交 · 管理控制台)      │
                                    └──────────────────┬──────────────────────────┘
                                                       │
                                    ┌──────────────────▼──────────────────────────┐
                                    │              API Gateway                    │
                                    │        (认证鉴权 · 路由分发 · 限流熔断)        │
                                    └──────────────────┬──────────────────────────┘
                                                       │
                          ┌────────────────────────────┼────────────────────────────┐
                          │                            │                            │
          ┌───────────────▼───────────────┐  ┌────────▼─────────────────────────┐  │
          │         调度引擎              │  │         调度引擎                  │  │
          │   ┌──────────────────────┐   │  │   ┌──────────────────────────┐   │  │
          │   │   Priority Scheduler │   │  │   │   Priority Scheduler     │   │  │
          │   │   (优先级调度器)       │   │  │   │   (优先级调度器)          │   │  │
          │   └──────────┬───────────┘   │  │   └──────────┬───────────────┘   │  │
          │              │               │  │              │                   │  │
          │   ┌──────────▼───────────┐   │  │   ┌──────────▼───────────────┐   │  │
          │   │   Retry Manager     │   │  │   │   Retry Manager         │   │  │
          │   │   (重试管理器)        │   │  │   │   (重试管理器)            │   │  │
          │   └──────────┬───────────┘   │  │   └──────────┬───────────────┘   │  │
          │              │               │  │              │                   │  │
          │   ┌──────────▼───────────┐   │  │   ┌──────────▼───────────────┐   │  │
          │   │   Worker Registry    │   │  │   │   Worker Registry       │   │  │
          │   │   (Worker注册中心)    │   │  │   │   (Worker注册中心)        │   │  │
          │   └──────────────────────┘   │  │   └──────────────────────────┘   │  │
          │        Node A                │  │        Node B                     │  │
          └──────────────────────────────┘  └───────────────────────────────────┘
                               │                            │
                               │      ┌─────────────────┐   │
                               │      │   Redis Cluster │   │
                               │      │  (优先级队列 ·   │   │
                               │      │   分布式锁 ·     │   │
                               └─────►│   发布订阅)      │◄──┘
                                      └────────┬────────┘
                                               │
                          ┌────────────────────┼────────────────────┐
                          │                    │                    │
              ┌───────────▼─────────┐  ┌──────▼──────┐  ┌─────────▼──────────┐
              │  PostgreSQL          │  │  PostgreSQL  │  │  Object Storage    │
              │  (任务持久化)          │  │  (从库)       │  │  (任务执行日志/结果) │
              └───────────────────────┘  └─────────────┘  └───────────────────┘
```

### 1.2 核心组件划分

| 组件 | 职责 | 关键技术 |
|------|------|---------|
| **API Gateway** | 任务接入、认证鉴权、限流熔断 | Kong / APISIX / 自研 |
| **调度引擎（Scheduler）** | 优先级排序、任务分发、调度决策 | 多线程/协程 + 时间轮 |
| **优先级队列（Priority Queue）** | 任务按优先级排序存储，支持按时间戳二次排序 | Redis ZSET / 自定义跳表 |
| **重试管理器（Retry Manager）** | 指数退避重试、死信队列管理、任务状态机 | 状态机 + 延迟队列 |
| **执行器（Executor）** | 拉取任务、执行回调、结果上报 | Worker Pool / 线程池 |
| **Worker注册中心** | 心跳维持、健康检查、负载上报 | Redis SET + TTL |
| **状态存储（State Store）** | 任务状态持久化、事务支持 | PostgreSQL / MySQL |
| **分布式锁（Dist Lock）** | 任务抢占、防止重复执行 | Redis Redlock / ZK |
| **消息总线（Event Bus）** | 任务状态变更通知、事件驱动 | Redis Pub/Sub / Kafka |

### 1.3 任务提交流程

```
客户端提交任务
     │
     ▼
API Gateway (限流/鉴权)
     │
     ▼
任务ID生成 (Snowflake / ULID)
     │
     ▼
任务元数据写入 PostgreSQL (状态: PENDING)
     │
     ├───► 任务入队: ZADD priority_queue:{priority} {score=timestamp} {task_id}
     │
     ▼
返回任务ID给客户端
```

### 1.4 任务调度流程

```
Scheduler 主循环 (每 100ms 触发)
     │
     ▼
从 Redis 读取各优先级队列的待调度任务
  ZRANGEBYSCORE high_priority_queue -inf +inf LIMIT 0 100
  ZRANGEBYSCORE medium_priority_queue -inf +inf LIMIT 0 100
  ZRANGEBYSCORE low_priority_queue -inf +inf LIMIT 0 100
     │
     ▼
按优先级合并 (HIGH → MEDIUM → LOW)
     │
     ▼
对每个任务:
  1. 尝试获取分布式锁 SET task_lock:{task_id} {owner_id} NX PX 30000
  2. 锁获取成功 → 推送至执行器待执行队列
  3. 锁获取失败 → 跳过 (其他节点在执行)
     │
     ▼
更新任务状态为 ASSIGNED (乐观锁)
     │
     ▼
执行器执行任务 → 结果回写 PostgreSQL
```

---

## 二、优先级队列设计

### 2.1 队列数据结构选型

**推荐方案：Redis Sorted Set（ZSET）**

| 方案 | 优点 | 缺点 | 适用场景 |
|------|------|------|---------|
| **Redis ZSET** ✅ | O(log N) 读写、天然支持分数排序、内置 ZPOP 系列命令 | 数据量受 Redis 内存限制 | 中小规模（< 1亿任务）、多节点共享 |
| 自定义跳表 | 内存可控、支持持久化 | 实现复杂、需自行做集群分片 | 超大规模、自研能力强 |
| RabbitMQ 优先级队列 | 消息中间件原生支持 | 队列内消息不宜过多（内存限制）、多节点共享需镜像队列 | 小规模、已有 RabbitMQ |
| Kafka 按 Partition 分组 | 高吞吐、持久化 | 优先级需多 Partition 实现，运维复杂 | 超大规模流处理场景 |

**最终选型理由：** Redis ZSET 以 `score = timestamp_ms * 10000 + priority_rank` 实现**时间+优先级二维排序**，兼顾公平性和优先级。

### 2.2 Score 设计

```
score = timestamp_ms * 10000 + priority_rank

其中 priority_rank:
  HIGH   = 3
  MEDIUM = 2
  LOW    = 1

示例：
  任务A (HIGH,    2026-05-12 10:00:00) → score = 17154852000000003
  任务B (MEDIUM,  2026-05-12 10:00:00) → score = 17154852000000002
  任务C (LOW,     2026-05-12 10:00:00) → score = 17154852000000001
  任务D (HIGH,    2026-05-12 10:00:01) → score = 17154852010000003

ZRANGEBYSCORE 永远先返回高优先级任务，同优先级内按 FIFO 排序
```

### 2.3 优先级调度算法

```python
# 伪代码：优先级调度器

class PriorityScheduler:
    def __init__(self, redis_client, worker_registry):
        self.redis = redis_client
        self.workers = worker_registry
        self.batch_size = 100

    def schedule_batch(self):
        """
        每调度周期执行一次：
        1. 从高→中→低依次拉取任务
        2. 按 Worker 可用负载分配
        """
        candidates = []
        for priority in [HIGH, MEDIUM, LOW]:
            queue_key = f"pq:{priority}"
            # 取出最早加入的任务（分数最小）
            tasks = self.redis.zrange(queue_key, 0, self.batch_size - 1)
            for task_id in tasks:
                candidates.append((priority, task_id))

        # 按优先级排序（高在前），同优先级按分数（时间）排序
        candidates.sort(key=lambda x: (-x[0], x[1]))

        scheduled = []
        for priority, task_id in candidates:
            # 尝试获取分布式锁
            lock_key = f"lock:task:{task_id}"
            lock_acquired = self.redis.set(lock_key, self.worker_id,
                                          nx=True, px=30000)
            if not lock_acquired:
                continue  # 已被其他节点锁住

            # 分配给负载最低的 Worker
            worker = self.select_least_loaded_worker()
            if worker is None:
                self.redis.delete(lock_key)
                break  # 无可用 Worker，暂停调度

            # 推送至 Worker 执行队列
            self.redis.lpush(f"exec:queue:{worker.id}", task_id)
            # 从就绪队列移除（延迟删除，Worker 确认后再删）
            scheduled.append((priority, task_id, worker.id))

        return scheduled

    def select_least_loaded_worker(self):
        """负载均衡：选择当前执行任务数最少的 Worker"""
        workers = self.workers.get_available()
        if not workers:
            return None
        return min(workers, key=lambda w: w.current_load)
```

### 2.4 防止优先级反转策略

**问题：** 低优先级任务持有锁，导致高优先级任务无法推进。

**解决方案：锁优先级继承**

```
实现方式：
1. 当高优先级任务尝试获取锁失败时，检查锁持有者的任务优先级
2. 若持有者是低优先级任务 → 发送"优先级提升"信号给持有者
3. 持有者主动释放锁或降速，让高优先级任务先执行

简化实现（推荐）：
→ 不在调度层处理锁继承，而是：
  ① 每个任务的锁超时设置足够短（30s），避免长期阻塞
  ② 调度器侧维护"等待链"，高优先级任务等待超时后自动重调度
  ③ 使用分段锁：调度锁（粗粒度） + 任务锁（细粒度）
```

---

## 三、故障重试机制

### 3.1 重试策略

```python
from enum import Enum
from dataclasses import dataclass
import time

class RetryStrategy(Enum):
    FIXED_INTERVAL = "fixed"      # 固定间隔
    EXPONENTIAL_BACKOFF = "exp"   # 指数退避
    LINEAR_BACKOFF = "linear"     # 线性退避

@dataclass
class RetryConfig:
    max_attempts: int = 3
    strategy: RetryStrategy = RetryStrategy.EXPONENTIAL_BACKOFF
    base_delay_ms: int = 1000     # 基础延迟
    max_delay_ms: int = 60000     # 最大延迟
    jitter: bool = True           # 是否加随机抖动

def compute_retry_delay(attempt: int, config: RetryConfig) -> int:
    if config.strategy == RetryStrategy.FIXED_INTERVAL:
        delay = config.base_delay_ms
    elif config.strategy == RetryStrategy.EXPONENTIAL_BACKOFF:
        delay = config.base_delay_ms * (2 ** (attempt - 1))
    else:  # LINEAR
        delay = config.base_delay_ms * attempt

    delay = min(delay, config.max_delay_ms)

    if config.jitter:
        import random
        # ±25% 随机抖动，打散重试峰值
        jitter_range = delay * 0.25
        delay = delay + random.uniform(-jitter_range, jitter_range)

    return int(delay)
```

### 3.2 任务状态机

```
                                    ┌──────────┐
                                    │ PENDING  │ ←── 初始状态（任务入队）
                                    └────┬─────┘
                                         │ 调度器分配
                                         ▼
                                   ┌──────────┐
                                   │ ASSIGNED │ ←── Worker 已认领
                                   └────┬─────┘
                                        │ Worker 开始执行
                                        ▼
                               ┌──────────────────┐
                               │    RUNNING       │
                               └────────┬─────────┘
                                        │
                  ┌─────────────────────┼─────────────────────┐
                  │                     │                     │
                  ▼                     ▼                     ▼
           ┌──────────┐          ┌─────────────┐        ┌──────────┐
           │  SUCCESS │          │   RETRYING  │        │  FAILED  │
           └──────────┘          └──────┬──────┘        └────┬─────┘
                                        │                     │
                                        │ 达到最大重试次数     │
                                        ▼                     ▼
                                   ┌──────────┐        ┌─────────────┐
                                   │ DEAD_LETTER       │  DEAD_LETTER │
                                   │ (可重试耗尽)       │  (永久失败)   │
                                   └──────────┘        └─────────────┘
```

**状态转换规则：**

| 当前状态 | 触发事件 | 下一状态 | 动作 |
|---------|---------|---------|------|
| PENDING | 调度器分配 | ASSIGNED | 记录分配时间、Worker ID |
| ASSIGNED | Worker 确认执行 | RUNNING | 记录开始时间 |
| RUNNING | 执行成功 | SUCCESS | 写结果、清理锁、释放 Worker |
| RUNNING | 执行失败（可重试）| RETRYING | 计算下次重试时间，入延迟队列 |
| RUNNING | 执行失败（不可重试）| FAILED | 写错误日志，进死信队列 |
| RETRYING | 等待时间到达 | PENDING | 重新入优先级队列 |
| RETRYING | 达到最大重试 | DEAD_LETTER | 标记为不可重试，进死信队列 |

### 3.3 延迟重试队列实现

```python
class RetryManager:
    """
    利用 Redis ZSET 实现延迟重试：
    score = 下次可执行时间戳
    """
    def __init__(self, redis_client):
        self.redis = redis_client
        self.retry_queue = "retry:queue"

    def schedule_retry(self, task: Task, attempt: int, config: RetryConfig):
        delay_ms = compute_retry_delay(attempt, config)
        next_execute_at = time.time() * 1000 + delay_ms

        # 记录重试元数据
        retry_key = f"retry:meta:{task.id}"
        self.redis.hset(retry_key, mapping={
            "attempt": attempt + 1,
            "max_attempts": config.max_attempts,
            "strategy": config.strategy.value,
            "last_error": task.last_error,
        })
        self.redis.expire(retry_key, 86400)

        # 任务入延迟重试队列
        self.redis.zadd(self.retry_queue, {task.id: next_execute_at})

    def process_ready_retries(self):
        """调度周期调用：将到期的重试任务放回优先级队列"""
        now = time.time() * 1000
        # 取出所有已到期的重试任务
        ready_tasks = self.redis.zrangebyscore(self.retry_queue,
                                                 "-inf", now,
                                                 limit=1000)
        if not ready_tasks:
            return

        # 原子性地从重试队列移除并放入就绪队列
        pipe = self.redis.pipeline()
        for task_id in ready_tasks:
            pipe.zrem(self.retry_queue, task_id)
            # 写回优先级队列（保持原优先级）
            retry_key = f"retry:meta:{task_id}"
            priority = self.redis.hget(retry_key, "priority") or "MEDIUM"
            pipe.zadd(f"pq:{priority}", {task_id: time.time() * 10000})
        pipe.execute()
```

### 3.4 死信队列（Dead Letter Queue）

```
场景：
1. 任务达到最大重试次数后仍失败 → 进 DLQ_RETRY_EXHAUSTED
2. 任务执行超时（超长运行） → 进 DLQ_TIMEOUT
3. 任务参数校验失败 → 进 DLQ_INVALID_PARAMS

DLQ 处理流程：
  DLQ Topic (Redis Stream / Kafka Topic)
       │
       ▼
  DLQ 消费者 → 告警通知（钉钉/企微/邮件）
       │
       ▼
  可选：人工处理 / 自动重放（加权重复执行）
```

---

## 四、分布式一致性

### 4.1 分布式锁实现（防止重复执行）

**方案选型对比：**

| 方案 | 一致性 | 性能 | 复杂度 | 可靠性 |
|------|--------|------|--------|--------|
| Redis SET NX PX | 最终一致 | 极高 | 低 | 单点？→ 用 Redlock |
| ZooKeeper 临时节点 | 强一致 | 中 | 中 | 高（CP） |
| etcd | 强一致 | 高 | 中 | 高（CP） |
| Redlock（多 Redis） | 高 | 高 | 高 | 极高 |

**推荐：Redlock + 任务级细粒度锁**

```python
import time
import uuid
from contextlib import contextmanager

class DistributedLock:
    """
    Redis Redlock 单机简化实现
    实际生产建议使用 Redlock 算法（5 个独立 Redis 实例）
    """
    def __init__(self, redis_client, ttl_ms=30000):
        self.redis = redis_client
        self.ttl_ms = ttl_ms
        self.owner_id = str(uuid.uuid4())

    def acquire(self, resource: str) -> bool:
        lock_key = f"lock:{resource}"
        return self.redis.set(lock_key, self.owner_id,
                              nx=True, px=self.ttl_ms)

    def release(self, resource: str) -> bool:
        """
        Lua 脚本保证原子性：只有锁持有者才能释放
        """
        release_script = """
        if redis.call("get", KEYS[1]) == ARGV[1] then
            return redis.call("del", KEYS[1])
        else
            return 0
        end
        """
        lock_key = f"lock:{resource}"
        result = self.redis.eval(release_script, 1, lock_key, self.owner_id)
        return result == 1

    def extend(self, resource: str, extra_ms: int = None) -> bool:
        """延长锁持有时间（用于长任务）"""
        extend_script = """
        if redis.call("get", KEYS[1]) == ARGV[1] then
            return redis.call("pexpire", KEYS[1], ARGV[2])
        else
            return 0
        end
        """
        lock_key = f"lock:{resource}"
        ttl = extra_ms or self.ttl_ms
        result = self.redis.eval(extend_script, 1, lock_key,
                                  self.owner_id, ttl)
        return result == 1

    @contextmanager
    def hold(self, resource: str):
        """上下文管理器用法"""
        acquired = self.acquire(resource)
        if not acquired:
            raise LockNotAcquiredError(f"Cannot acquire lock: {resource}")
        try:
            yield
        finally:
            self.release(resource)
```

**任务执行锁的应用：**

```python
class TaskExecutor:
    def execute_task(self, task: Task):
        lock = DistributedLock(self.redis, ttl_ms=60000)
        try:
            with lock.hold(f"task:{task.id}"):
                # 双检：确认任务未被执行
                if self.is_already_executed(task.id):
                    return
                self.do_execute(task)
        except LockNotAcquiredError:
            # 任务正在被其他节点执行，跳过
            pass
```

### 4.2 任务分片和负载均衡

**分片策略：**

| 策略 | 原理 | 优点 | 缺点 |
|------|------|------|------|
| 随机分配 | 均匀打散 | 简单 | 无负载感知 |
| 轮询 | Round-Robin | 简单、均匀 | 无负载感知 |
| **Least-Load** ✅ | 选当前任务最少的 Worker | 负载均衡 | 需维护 Worker 负载状态 |
| 一致性哈希 | 相同任务总派给同一 Worker | 利用缓存 | 负载不均时难调整 |

**Least-Load 负载均衡实现：**

```python
class WorkerRegistry:
    """
    基于 Redis 的 Worker 注册与负载感知
    Worker 每 10s 上报一次心跳，同时上报当前负载
    """
    def __init__(self, redis_client):
        self.redis = redis_client
        self.registry_key = "workers:available"

    def heartbeat(self, worker_id: str, current_load: int, capacity: int):
        """Worker 定期调用，维持注册"""
        self.redis.hset(self.registry_key, worker_id, json.dumps({
            "load": current_load,
            "capacity": capacity,
            "last_seen": time.time(),
        }))
        # 设置过期，防止 Worker 崩溃后残留
        self.redis.expire(self.registry_key, 60)

    def get_least_loaded(self, count: int = 1) -> List[str]:
        """返回负载最低的 N 个 Worker"""
        workers = self.redis.hgetall(self.registry_key)
        now = time.time()

        valid_workers = []
        for worker_id, info_json in workers.items():
            info = json.loads(info_json)
            # 淘汰 30s 未心跳的 Worker
            if now - info["last_seen"] > 30:
                continue
            valid_workers.append((worker_id, info["load"]))

        valid_workers.sort(key=lambda x: x[1])
        return [w[0] for w in valid_workers[:count]]
```

### 4.3 故障转移和恢复

**节点故障检测与自动转移：**

```
场景：调度节点 A 崩溃

1. 健康检查器每 5s 检测调度节点心跳
       │
       ▼
2. 发现节点 A 超过 15s 无心跳 → 标记为 UNHEALTHY
       │
       ▼
3. 触发锁超时释放（锁自动过期，任务自动归还队列）
       │
       ▼
4. 其他调度节点接管分配（任务在 Redis 中，仍可被其他节点调度）
       │
       ▼
5. 任务状态恢复：
   - ASSIGNED/RUNNING 状态的任务 → 等待 Worker 心跳超时检测
   - Worker 侧：30s 无心跳 → 任务状态回退为 PENDING，重新入队
```

**调度节点选举（高可用）：**

```
方案：Redis 主从 + Sentinel 或 etcd Raft 组

推荐方案：etcd / Consul 做调度节点选主
- 优势：强一致、成熟、运维可控
- 选主后，主节点承担调度职责
- 从节点监听，主节点挂了立即切换
```

---

## 五、核心数据结构定义

### 5.1 数据库表结构（PostgreSQL）

```sql
-- 任务主表
CREATE TABLE tasks (
    id              VARCHAR(36) PRIMARY KEY,    -- UUID / ULID
    name            VARCHAR(255) NOT NULL,
    payload         JSONB NOT NULL,              -- 任务入参
    priority        SMALLINT NOT NULL DEFAULT 2, -- 1=LOW, 2=MEDIUM, 3=HIGH
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count     SMALLINT NOT NULL DEFAULT 0,
    max_retries     SMALLINT NOT NULL DEFAULT 3,
    retry_strategy  VARCHAR(20) NOT NULL DEFAULT 'EXPONENTIAL_BACKOFF',
    retry_base_ms   INTEGER NOT NULL DEFAULT 1000,
    retry_max_ms    INTEGER NOT NULL DEFAULT 60000,
    execute_timeout INTEGER NOT NULL DEFAULT 3600, -- 秒

    -- 时间戳
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    scheduled_at    TIMESTAMPTZ,                 -- 计划执行时间
    started_at      TIMESTAMPTZ,                 -- 实际开始时间
    completed_at    TIMESTAMPTZ,

    -- 执行信息
    assigned_worker  VARCHAR(100),
    result_payload   JSONB,                      -- 执行结果
    last_error       TEXT,                       -- 最近一次错误信息

    -- 索引
    CONSTRAINT tasks_status_check CHECK (status IN (
        'PENDING', 'ASSIGNED', 'RUNNING',
        'RETRYING', 'SUCCESS', 'FAILED', 'DEAD_LETTER'
    ))
);

CREATE INDEX idx_tasks_status ON tasks(status);
CREATE INDEX idx_tasks_priority_status ON tasks(priority, status);
CREATE INDEX idx_tasks_scheduled_at ON tasks(scheduled_at) WHERE status = 'PENDING';

-- 任务执行历史（审计）
CREATE TABLE task_executions (
    id              BIGSERIAL PRIMARY KEY,
    task_id         VARCHAR(36) NOT NULL REFERENCES tasks(id),
    worker_id       VARCHAR(100) NOT NULL,
    attempt         SMALLINT NOT NULL,
    status          VARCHAR(20) NOT NULL,
    started_at      TIMESTAMPTZ NOT NULL,
    completed_at    TIMESTAMPTZ,
    error_message   TEXT,
    duration_ms     BIGINT,
    UNIQUE(task_id, attempt)
);

CREATE INDEX idx_executions_task_id ON task_executions(task_id);
```

### 5.2 任务对象模型

```python
from dataclasses import dataclass, field
from datetime import datetime
from enum import IntEnum
from typing import Optional, Any, Dict
import json

class TaskPriority(IntEnum):
    LOW = 1
    MEDIUM = 2
    HIGH = 3

class TaskStatus(Enum):
    PENDING = "PENDING"
    ASSIGNED = "ASSIGNED"
    RUNNING = "RUNNING"
    RETRYING = "RETRYING"
    SUCCESS = "SUCCESS"
    FAILED = "FAILED"
    DEAD_LETTER = "DEAD_LETTER"

@dataclass
class Task:
    id: str
    name: str
    payload: Dict[str, Any]
    priority: TaskPriority = TaskPriority.MEDIUM

    status: TaskStatus = TaskStatus.PENDING
    retry_count: int = 0
    max_retries: int = 3
    retry_strategy: str = "EXPONENTIAL_BACKOFF"
    retry_base_ms: int = 1000
    retry_max_ms: int = 60000
    execute_timeout: int = 3600

    created_at: datetime = field(default_factory=datetime.utcnow)
    scheduled_at: Optional[datetime] = None
    started_at: Optional[datetime] = None
    completed_at: Optional[datetime] = None

    assigned_worker: Optional[str] = None
    result_payload: Optional[Dict[str, Any]] = None
    last_error: Optional[str] = None

    def compute_queue_score(self) -> float:
        """计算 Redis ZSET score：时间戳*10000 + 优先级"""
        ts = (self.scheduled_at or self.created_at).timestamp() * 1000
        return ts * 10000 + self.priority.value

    def to_redis_hash(self) -> Dict[str, str]:
        return {
            "id": self.id,
            "name": self.name,
            "payload": json.dumps(self.payload),
            "priority": str(self.priority.value),
            "status": self.status.value,
            "retry_count": str(self.retry_count),
            "max_retries": str(self.max_retries),
        }

    @classmethod
    def from_db_row(cls, row: Dict) -> "Task":
        return cls(
            id=row["id"],
            name=row["name"],
            payload=json.loads(row["payload"]),
            priority=TaskPriority(int(row["priority"])),
            status=TaskStatus(row["status"]),
            retry_count=int(row["retry_count"]),
            max_retries=int(row["max_retries"]),
            assigned_worker=row.get("assigned_worker"),
            last_error=row.get("last_error"),
            result_payload=json.loads(row["result_payload"]) if row.get("result_payload") else None,
        )
```

### 5.3 Worker 心跳与健康检查

```python
@dataclass
class WorkerInfo:
    id: str
    ip: str
    port: int
    capacity: int           # 最大并发任务数
    current_load: int       # 当前执行任务数
    tags: List[str]         # 标签（用于任务路由匹配）
    last_heartbeat: float
    status: str = "ALIVE"   # ALIVE / UNHEALTHY / SHUTDOWN

@dataclass
class WorkerHeartbeat:
    worker_id: str
    current_load: int
    cpu_percent: float
    memory_percent: float
    active_tasks: List[str]  # 正在执行的任务 ID 列表
```

### 5.4 Redis Key 命名规范

```
pq:HIGH                # 高优先级队列（ZSET）
pq:MEDIUM              # 中优先级队列（ZSET）
pq:LOW                 # 低优先级队列（ZSET）

lock:task:{task_id}    # 任务分布式锁
lock:scheduler:{node_id} # 调度器节点锁（选主用）

workers:available      # 可用 Worker 注册表（HASH）
workers:heartbeat:{worker_id}  # Worker 心跳 TTL

retry:queue            # 延迟重试队列（ZSET, score=下次执行时间）
retry:meta:{task_id}   # 重试元数据（HASH）

deadletter:queue       # 死信队列（STREAM）
events:task_status     # 任务状态变更事件流（STREAM）

exec:queue:{worker_id} # Worker 执行队列（LIST）
```

---

## 六、关键技术选型总结

| 层级 | 技术选型 | 权衡分析 |
|------|---------|---------|
| **任务队列** | Redis ZSET | 优点：O(log N)、天然排序、内嵌于调度链路<br>缺点：内存受限，大规模需集群（Redis Cluster） |
| **分布式锁** | Redlock | 优点：高性能、实现成熟<br>缺点：实现正确的多节点 Redlock 较复杂<br>**替代：etcd/Consul 锁**（如果已有服务发现基础设施） |
| **持久化** | PostgreSQL | 优点：JSONB 支持灵活 payload、事务保障、高可靠性<br>缺点：写入延迟（但异步批量写入可缓解） |
| **消息总线** | Redis Pub/Sub | 优点：低延迟、零运维<br>缺点：无持久化<br>**生产建议：Redis Stream**（有持久化）或 Kafka |
| **选主** | etcd / Consul | 已有基础设施则复用；无则用 Redis SET + TTL 简化 |
| **定时调度** | 时间轮（Timing Wheel） | 替代 Cron：更精确、支持动态调度、内存可控 |

### 扩展建议

1. **规模化扩展**：当 Redis 单实例成为瓶颈时 → 切换为 Redis Cluster，按 priority 分片
2. **任务超时保护**：Worker 执行层增加 SIGALRM / context timeout 双保险
3. **监控告警**：任务积压数 > 阈值、DLQ 增长速率、执行延迟 P99 告警
4. **任务取消**：支持 CANCELLED 状态，调度器在 ZPOP 前检查任务是否被取消

---

*文档由鲁班生成 | 主框架参考：Netflix Conductor、Uber Cadence、xxl-job*
