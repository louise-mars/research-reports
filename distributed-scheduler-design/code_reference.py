# 分布式任务调度系统 — 核心代码实现参考

> 本文件为架构文档的补充，提供关键模块的可执行代码参考

---

## 1. 任务对象与状态机

```python
from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import Optional, Dict, Any, List
import json
import uuid
import time


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
    CANCELLED = "CANCELLED"


class RetryStrategy(Enum):
    FIXED_INTERVAL = "fixed"
    EXPONENTIAL_BACKOFF = "exp"
    LINEAR_BACKOFF = "linear"


@dataclass
class TaskConfig:
    max_retries: int = 3
    retry_strategy: RetryStrategy = RetryStrategy.EXPONENTIAL_BACKOFF
    retry_base_ms: int = 1000
    retry_max_ms: int = 60000
    execute_timeout: int = 3600  # seconds
    retry_jitter: bool = True


@dataclass
class Task:
    id: str
    name: str
    payload: Dict[str, Any]
    priority: TaskPriority = TaskPriority.MEDIUM
    config: TaskConfig = field(default_factory=TaskConfig)

    status: TaskStatus = TaskStatus.PENDING
    retry_count: int = 0
    created_at: datetime = field(default_factory=datetime.utcnow)
    scheduled_at: Optional[datetime] = None
    started_at: Optional[datetime] = None
    completed_at: Optional[datetime] = None

    assigned_worker: Optional[str] = None
    result_payload: Optional[Dict[str, Any]] = None
    last_error: Optional[str] = None

    @classmethod
    def create(cls, name: str, payload: Dict, priority: TaskPriority = TaskPriority.MEDIUM) -> "Task":
        return cls(
            id=str(uuid.uuid4()),
            name=name,
            payload=payload,
            priority=priority,
            scheduled_at=datetime.utcnow(),
        )

    def compute_zset_score(self) -> float:
        """Redis ZSET score: timestamp_ms * 10000 + priority_rank"""
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
            "max_retries": str(self.config.max_retries),
        }

    @classmethod
    def from_db_row(cls, row: Dict) -> "Task":
        config = TaskConfig(
            max_retries=int(row.get("max_retries", 3)),
            retry_strategy=RetryStrategy(row.get("retry_strategy", "EXPONENTIAL_BACKOFF")),
            retry_base_ms=int(row.get("retry_base_ms", 1000)),
            retry_max_ms=int(row.get("retry_max_ms", 60000)),
        )
        return cls(
            id=row["id"],
            name=row["name"],
            payload=json.loads(row["payload"]) if isinstance(row["payload"], str) else row["payload"],
            priority=TaskPriority(int(row["priority"])),
            config=config,
            status=TaskStatus(row["status"]),
            retry_count=int(row["retry_count"]),
            created_at=row.get("created_at", datetime.utcnow()),
            scheduled_at=row.get("scheduled_at"),
            started_at=row.get("started_at"),
            completed_at=row.get("completed_at"),
            assigned_worker=row.get("assigned_worker"),
            last_error=row.get("last_error"),
            result_payload=json.loads(row["result_payload"]) if row.get("result_payload") else None,
        )


# ─────────────────────────────────────────────────────────────────────────────
# 2. 重试延迟计算
# ─────────────────────────────────────────────────────────────────────────────

def compute_retry_delay(attempt: int, config: TaskConfig) -> int:
    """
    根据重试策略计算下次重试的延迟时间（毫秒）
    """
    strategy = config.retry_strategy

    if strategy == RetryStrategy.FIXED_INTERVAL:
        delay = config.retry_base_ms
    elif strategy == RetryStrategy.EXPONENTIAL_BACKOFF:
        delay = config.retry_base_ms * (2 ** attempt)
    else:  # LINEAR
        delay = config.retry_base_ms * (attempt + 1)

    delay = min(delay, config.retry_max_ms)

    if config.retry_jitter:
        import random
        jitter_range = delay * 0.25
        delay = int(delay + random.uniform(-jitter_range, jitter_range))

    return delay


# ─────────────────────────────────────────────────────────────────────────────
# 3. 分布式锁
# ─────────────────────────────────────────────────────────────────────────────

import redis

class LockAcquisitionError(Exception):
    pass


class DistributedLock:
    RELEASE_SCRIPT = """
    if redis.call("get", KEYS[1]) == ARGV[1] then
        return redis.call("del", KEYS[1])
    else
        return 0
    end
    """

    EXTEND_SCRIPT = """
    if redis.call("get", KEYS[1]) == ARGV[1] then
        return redis.call("pexpire", KEYS[1], ARGV[2])
    else
        return 0
    end
    """

    def __init__(self, redis_client: redis.Redis, ttl_ms: int = 30000):
        self.redis = redis_client
        self.ttl_ms = ttl_ms
        self.owner_id = str(uuid.uuid4())
        self._held_resources: set = set()

    def acquire(self, resource: str) -> bool:
        lock_key = f"lock:{resource}"
        acquired = self.redis.set(
            lock_key, self.owner_id,
            nx=True, px=self.ttl_ms
        )
        if acquired:
            self._held_resources.add(resource)
        return acquired

    def release(self, resource: str) -> bool:
        lock_key = f"lock:{resource}"
        result = self.redis.eval(self.RELEASE_SCRIPT, 1, lock_key, self.owner_id)
        self._held_resources.discard(resource)
        return result == 1

    def extend(self, resource: str, extra_ms: int = None) -> bool:
        ttl = extra_ms or self.ttl_ms
        lock_key = f"lock:{resource}"
        result = self.redis.eval(self.EXTEND_SCRIPT, 1, lock_key, self.owner_id, ttl)
        return result == 1

    def release_all(self):
        for resource in list(self._held_resources):
            self.release(resource)

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.release_all()
        return False


# ─────────────────────────────────────────────────────────────────────────────
# 4. 优先级调度器
# ─────────────────────────────────────────────────────────────────────────────

class PriorityScheduler:
    QUEUE_KEYS = {
        TaskPriority.HIGH: "pq:HIGH",
        TaskPriority.MEDIUM: "pq:MEDIUM",
        TaskPriority.LOW: "pq:LOW",
    }

    def __init__(self, redis_client: redis.Redis, worker_registry: "WorkerRegistry",
                 lock_ttl_ms: int = 30000):
        self.redis = redis_client
        self.workers = worker_registry
        self.lock_ttl_ms = lock_ttl_ms
        self.worker_id = str(uuid.uuid4())

    def schedule_batch(self, batch_size: int = 100) -> List[Dict]:
        """
        从各优先级队列拉取任务，尝试分配给可用 Worker
        返回已调度任务列表
        """
        candidates = []

        # Step 1: 从高到低依次拉取
        for priority in [TaskPriority.HIGH, TaskPriority.MEDIUM, TaskPriority.LOW]:
            queue_key = self.QUEUE_KEYS[priority]
            tasks = self.redis.zrange(queue_key, 0, batch_size - 1)
            for task_id in tasks:
                candidates.append((priority, task_id))

        # Step 2: 优先级排序（同优先级的按分数即时间排序）
        # score 越小越早（最早入队的先调度）
        candidates.sort(key=lambda x: (-x[0].value, x[1]))

        scheduled = []
        for priority, task_id in candidates:
            # Step 3: 尝试获取任务锁（防止重复调度）
            lock_key = f"lock:task:{task_id}"
            lock_acquired = self.redis.set(
                lock_key, self.worker_id,
                nx=True, px=self.lock_ttl_ms
            )
            if not lock_acquired:
                continue  # 已被其他节点锁住，跳过

            # Step 4: 选择负载最低的 Worker
            worker = self.workers.get_least_loaded(count=1)
            if worker is None:
                self.redis.delete(lock_key)
                break  # 无可用 Worker，暂停调度

            # Step 5: 推送至 Worker 执行队列
            self.redis.lpush(f"exec:queue:{worker['id']}", task_id)
            scheduled.append({
                "task_id": task_id,
                "priority": priority.name,
                "worker_id": worker["id"],
                "queued_at": time.time(),
            })

        return scheduled

    def get_queue_depths(self) -> Dict[str, int]:
        """返回各优先级队列的深度"""
        return {
            name: self.redis.zcard(key)
            for name, key in self.QUEUE_KEYS.items()
        }


# ─────────────────────────────────────────────────────────────────────────────
# 5. Worker 注册与健康检查
# ─────────────────────────────────────────────────────────────────────────────

class WorkerRegistry:
    def __init__(self, redis_client: redis.Redis, heartbeat_ttl: int = 30):
        self.redis = redis_client
        self.registry_key = "workers:available"
        self.heartbeat_ttl = heartbeat_ttl

    def register(self, worker_id: str, ip: str, port: int,
                 capacity: int, tags: List[str] = None):
        """Worker 启动时调用"""
        self.redis.hset(self.registry_key, worker_id, json.dumps({
            "id": worker_id,
            "ip": ip,
            "port": port,
            "capacity": capacity,
            "current_load": 0,
            "tags": tags or [],
            "registered_at": time.time(),
            "last_seen": time.time(),
            "status": "ALIVE",
        }))
        # 设置 Key 过期，防止崩溃后残留
        self.redis.expire(self.registry_key, self.heartbeat_ttl * 3)

    def heartbeat(self, worker_id: str, current_load: int,
                   cpu_percent: float = 0, memory_percent: float = 0):
        """Worker 定期心跳（建议每 10s 调用一次）"""
        info = self.redis.hget(self.registry_key, worker_id)
        if not info:
            return  # 未注册，忽略

        data = json.loads(info)
        data["current_load"] = current_load
        data["cpu_percent"] = cpu_percent
        data["memory_percent"] = memory_percent
        data["last_seen"] = time.time()

        self.redis.hset(self.registry_key, worker_id, json.dumps(data))
        # 刷新 TTL
        self.redis.expire(self.registry_key, self.heartbeat_ttl * 3)

    def get_least_loaded(self, count: int = 1, tag_filter: List[str] = None) -> List[Dict]:
        """
        返回负载最低的 N 个健康 Worker
        """
        raw = self.redis.hgetall(self.registry_key)
        now = time.time()

        candidates = []
        for worker_id, info_json in raw.items():
            info = json.loads(info_json)
            # 淘汰心跳过期的 Worker
            if now - info["last_seen"] > self.heartbeat_ttl:
                continue
            if info.get("status") != "ALIVE":
                continue
            # 标签过滤
            if tag_filter and not any(t in info.get("tags", []) for t in tag_filter):
                continue
            candidates.append(info)

        candidates.sort(key=lambda w: w["current_load"] / w["capacity"])
        return candidates[:count]

    def unregister(self, worker_id: str):
        self.redis.hdel(self.registry_key, worker_id)


# ─────────────────────────────────────────────────────────────────────────────
# 6. 重试管理器
# ─────────────────────────────────────────────────────────────────────────────

class RetryManager:
    RETRY_META_PREFIX = "retry:meta:"
    RETRY_QUEUE_KEY = "retry:queue"

    def __init__(self, redis_client: redis.Redis):
        self.redis = redis_client

    def schedule_retry(self, task: Task) -> None:
        """将任务加入延迟重试队列"""
        if task.retry_count >= task.config.max_retries:
            return  # 达到上限，不重试

        delay_ms = compute_retry_delay(task.retry_count, task.config)
        next_execute_at_ms = time.time() * 1000 + delay_ms

        # 记录重试元数据
        meta_key = f"{self.RETRY_META_PREFIX}{task.id}"
        self.redis.hset(meta_key, mapping={
            "attempt": str(task.retry_count + 1),
            "max_attempts": str(task.config.max_retries),
            "strategy": task.config.retry_strategy.value,
            "priority": str(task.priority.value),
            "last_error": task.last_error or "",
        })
        self.redis.expire(meta_key, 86400)  # 保留 24h

        # 入延迟重试队列（score = 下次可执行时间戳）
        self.redis.zadd(self.RETRY_QUEUE_KEY, {task.id: next_execute_at_ms})

    def process_ready_retries(self, batch_size: int = 500) -> int:
        """
        将已到期的重试任务写回优先级队列
        返回处理数量
        """
        now_ms = time.time() * 1000
        ready_task_ids = self.redis.zrangebyscore(
            self.RETRY_QUEUE_KEY, "-inf", now_ms, start=0, num=batch_size
        )
        if not ready_task_ids:
            return 0

        pipe = self.redis.pipeline()
        for task_id in ready_task_ids:
            # 获取元数据
            meta_key = f"{self.RETRY_META_PREFIX}{task_id}"
            meta = self.redis.hgetall(meta_key)

            priority = int(meta.get("priority", TaskPriority.MEDIUM.value))
            priority_name = {3: "HIGH", 2: "MEDIUM", 1: "LOW"}.get(priority, "MEDIUM")

            # 从重试队列移除
            pipe.zrem(self.RETRY_QUEUE_KEY, task_id)
            # 写回优先级队列（score 用当前时间，保持 FIFO）
            score = time.time() * 10000 + priority
            pipe.zadd(f"pq:{priority_name}", {task_id: score})
            # 删除元数据
            pipe.delete(meta_key)

        pipe.execute()
        return len(ready_task_ids)


# ─────────────────────────────────────────────────────────────────────────────
# 7. 调度主循环（伪代码框架）
# ─────────────────────────────────────────────────────────────────────────────

class SchedulerNode:
    """
    调度节点主循环（实际使用时替换为 Tornado / asyncio / Go channel）
    这里展示逻辑流程，不绑定具体 I/O 框架
    """

    def __init__(self, redis_client: redis.Redis, node_id: str):
        self.redis = redis_client
        self.node_id = node_id
        self.scheduler = PriorityScheduler(redis_client, WorkerRegistry(redis_client))
        self.retry_manager = RetryManager(redis_client)
        self.running = False

    def try_become_leader(self, lease_ms: int = 10000) -> bool:
        """
        尝试成为主调度节点（Redis SET NX PX 实现简易选主）
        """
        leader_key = "scheduler:leader"
        acquired = self.redis.set(leader_key, self.node_id, nx=True, px=lease_ms)
        return acquired

    def main_loop(self, tick_interval_ms: int = 100):
        """
        主调度循环（实际部署时用 setInterval / time.Ticker 驱动）
        """
        import time
        while self.running:
            tick_start = time.time()

            # 1. 尝试成为主节点
            if not self.try_become_leader():
                time.sleep(tick_interval_ms / 1000)
                continue

            # 2. 处理到期的重试任务
            self.retry_manager.process_ready_retries()

            # 3. 执行优先级调度
            scheduled = self.scheduler.schedule_batch(batch_size=100)
            if scheduled:
                pass  # 日志/指标上报

            # 4. 收集队列深度
            depths = self.scheduler.get_queue_depths()
            self.redis.hset("scheduler:metrics", self.node_id, json.dumps({
                "scheduled_this_tick": len(scheduled),
                "queues": depths,
                "ts": time.time(),
            }))

            # 5. 动态调整 tick 间隔（自适应）
            elapsed = (time.time() - tick_start) * 1000
            sleep_ms = max(10, tick_interval_ms - elapsed)
            time.sleep(sleep_ms / 1000)

    def start(self):
        self.running = True

    def stop(self):
        self.running = False


# ─────────────────────────────────────────────────────────────────────────────
# 8. Worker 执行器（示例）
# ─────────────────────────────────────────────────────────────────────────────

class TaskExecutor:
    """
    Worker 侧任务执行器
    """

    def __init__(self, redis_client: redis.Redis, worker_id: str,
                 exec_callback_registry: Dict[str, callable]):
        self.redis = redis_client
        self.worker_id = worker_id
        self.callbacks = exec_callback_registry
        self.redis.lpush(f"workers:active", worker_id)

    def poll_and_execute(self, poll_timeout_ms: int = 1000) -> bool:
        """
        阻塞拉取任务并执行
        返回是否有任务被执行
        """
        result = self.redis.brpop(f"exec:queue:{self.worker_id}",
                                   timeout=int(poll_timeout_ms / 1000))
        if not result:
            return False

        _, task_id = result
        task_id = task_id.decode() if isinstance(task_id, bytes) else task_id

        lock = DistributedLock(self.redis, ttl_ms=60000)
        try:
            with lock.hold(f"task:{task_id}"):
                self._do_execute(task_id)
        except LockAcquisitionError:
            pass  # 任务被其他 Worker 抢走

        return True

    def _do_execute(self, task_id: str):
        # 从 DB 加载任务（实际应该用 ORM/Reposity）
        row = self.redis.hgetall(f"task:data:{task_id}")
        if not row:
            # fallback：从 PostgreSQL 查（实际走 DB）
            return

        task = Task.from_db_row({k.decode(): v.decode() for k, v in row.items()})

        task.status = TaskStatus.RUNNING
        task.started_at = datetime.utcnow()
        self._update_task_status(task)

        try:
            # 执行用户回调
            callback = self.callbacks.get(task.name)
            if callback:
                result = callback(task.payload)
            else:
                result = {"status": "no_callback_registered"}

            task.status = TaskStatus.SUCCESS
            task.result_payload = result
            task.completed_at = datetime.utcnow()

        except Exception as e:
            task.last_error = str(e)
            if task.retry_count < task.config.max_retries:
                task.status = TaskStatus.RETRYING
                task.retry_count += 1
                RetryManager(self.redis).schedule_retry(task)
            else:
                task.status = TaskStatus.DEAD_LETTER

        finally:
            self._update_task_status(task)

    def _update_task_status(self, task: Task):
        # 写回 Redis（最终一致性）
        # 生产环境建议直接写 PostgreSQL（强一致性）
        key = f"task:data:{task.id}"
        self.redis.hset(key, mapping={
            "status": task.status.value,
            "retry_count": str(task.retry_count),
            "last_error": task.last_error or "",
            "started_at": task.started_at.isoformat() if task.started_at else "",
            "completed_at": task.completed_at.isoformat() if task.completed_at else "",
            "result_payload": json.dumps(task.result_payload) if task.result_payload else "",
        })
```

---

## 附录：关键时序图

### 时序图1：任务正常调度流程

```
Client          API Gateway      Scheduler         Redis           Worker          DB
  │                  │               │               │               │             │
  │──提交任务────────>│               │               │               │             │
  │                  │──写入任务──────>│               │               │             │
  │                  │               │──ZADD pq:HIGH──>               │             │
  │                  │               │               │               │             │
  │<───task_id───────│               │               │               │             │
  │                  │               │               │               │             │
  │                  │               │◄─ZRANGE───────               │             │
  │                  │               │──SETNX lock───>               │             │
  │                  │               │◄─OK─────────────────────────────            │
  │                  │               │──LPUSH exec:queue:1──>       │             │
  │                  │               │               │               │             │
  │                  │               │               │◄──BRPOP───────              │
  │                  │               │               │──GET task────>             │
  │                  │               │               │◄──task data───────────────│
  │                  │               │               │               │             │
  │                  │               │               │──UPDATE status────────────>│
  │                  │               │               │               │             │
  │                  │               │               │◄──execute()───────────────│
  │                  │               │               │──WRITE result────────────>│
```

### 时序图2：任务故障重试流程

```
Worker          Scheduler         Redis           DB              DLQ
  │                 │               │               │               │
  │──执行失败───────>│               │               │               │
  │                 │──UPDATE status (RETRYING)───>│               │
  │                 │               │               │               │
  │                 │──ZADD retry:queue (score=next_time)──>        │
  │                 │               │               │               │
  │<──返回重试次数───│               │               │               │
  │                 │               │               │               │
  │                 │◄─ZRANGEBYSCORE retry:queue───────────        │
  │                 │──ZREM + ZADD pq:HIGH────>                   │
  │                 │               │               │               │
  │                 │◄─下一轮调度...              │               │
  │                 │               │               │               │
  │                 │               │               │               │
  │   (N次重试后仍失败)│               │               │               │
  │                 │──UPDATE status (DEAD_LETTER)──>│               │
  │                 │──XADD deadletter:queue───────>│               │
  │                 │               │               │               │
  │                 │               │               │◄─DLQ Consumer───告警通知
```