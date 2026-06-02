"""
AsyncTaskQueue - Python 异步任务队列
======================================

生产级别的 Python 异步任务队列库，支持优先级和重试机制。

功能特性
--------
- 支持任务优先级（高/中/低三级）
- 任务失败自动重试（可配置重试次数和间隔）
- 异步执行（asyncio）
- 任务状态跟踪
- 死信队列（dead letter queue）
- 任务取消和超时控制

示例用法
--------
    async def main():
        queue = AsyncTaskQueue()
        
        # 添加任务
        queue.enqueue(my_func, args=(), kwargs={}, priority=Priority.HIGH, max_retries=3)
        
        # 启动队列
        queue.start()
        
        # 等待一段时间
        await asyncio.sleep(10)
        
        # 停止队列
        await queue.stop()

    # 运行
    asyncio.run(main())
"""

import asyncio
import heapq
import uuid
import logging
import time
from enum import Enum
from dataclasses import dataclass, field
from typing import Callable, Any, Optional, List, Dict, Tuple
from asyncio import PriorityQueue as AsyncioPriorityQueue
from contextlib import suppress

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class Priority(Enum):
    """任务优先级枚举，数字越大优先级越高"""
    LOW = 1
    MEDIUM = 2
    HIGH = 3


class RetryStrategy(Enum):
    """重试策略枚举"""
    FIXED = "fixed"       # 固定间隔重试
    EXPONENTIAL = "exponential"  # 指数退避重试


class TaskStatus(Enum):
    """任务状态枚举"""
    PENDING = "pending"     # 等待执行
    RUNNING = "running"     # 执行中
    SUCCESS = "success"     # 执行成功
    FAILED = "failed"       # 执行失败（不重试）
    RETRYING = "retrying"   # 等待重试
    DEAD = "dead"           # 死信队列（超过最大重试次数）
    CANCELLED = "cancelled" # 已取消


@dataclass
class Task:
    """
    任务数据结构
    
    Attributes:
        id: 唯一标识符
        func: 要执行的异步函数
        args: 位置参数
        kwargs: 关键字参数
        priority: 优先级 (Priority enum)
        max_retries: 最大重试次数
        retry_delay: 重试间隔（秒）
        retry_strategy: 重试策略
        retry_count: 当前重试次数
        status: 当前状态
        timeout: 任务超时时间（秒），None表示无超时
        created_at: 创建时间戳
        scheduled_at: 计划执行时间戳（用于延迟重试）
        result: 任务结果（完成后填充）
        error: 错误信息（失败时填充）
    """
    # 由于需要用 heapq 排序，我们把 priority 作为比较键
    # priority_order 用于堆排序: (-priority_value, scheduled_time)
    # 使用负优先级实现最大堆行为（高优先级先执行）
    id: str = field(default="")
    func: Callable = field(default=None)
    args: tuple = field(default=())
    kwargs: dict = field(default_factory=dict)
    priority: Priority = field(default=Priority.MEDIUM)
    max_retries: int = field(default=3)
    retry_delay: float = field(default=1.0)
    retry_strategy: RetryStrategy = field(default=RetryStrategy.FIXED)
    retry_count: int = field(default=0)
    status: TaskStatus = field(default=TaskStatus.PENDING)
    timeout: Optional[float] = field(default=None)
    created_at: float = field(default_factory=time.time)
    scheduled_at: float = field(default_factory=time.time)
    result: Any = field(default=None)
    error: Optional[str] = field(default=None)
    
    def __post_init__(self):
        if not self.id:
            self.id = str(uuid.uuid4())
    
    @property
    def priority_order(self) -> Tuple[int, float]:
        """计算堆排序用的优先级顺序"""
        return (-self.priority.value, self.scheduled_at)
    
    def __lt__(self, other: "Task") -> bool:
        """支持 heapq 比较"""
        if not isinstance(other, Task):
            return NotImplemented
        return self.priority_order < other.priority_order


class AsyncTaskQueue:
    """
    异步任务队列主类
    
    Features:
        - 基于 heapq 的优先级队列
        - 支持指数退避和固定间隔重试
        - 死信队列管理
        - 任务取消
        - 可选任务超时
    
    Example:
        >>> async def main():
        ...     queue = AsyncTaskQueue()
        ...     queue.enqueue(my_func, args=(1, 2), kwargs={}, priority=Priority.HIGH)
        ...     queue.start()
        ...     await asyncio.sleep(10)
        ...     await queue.stop()
        >>> asyncio.run(main())
    """
    
    def __init__(self, max_workers: int = 5, max_dead_letter_size: int = 1000):
        """
        初始化任务队列
        
        Args:
            max_workers: 最大并发 worker 数量
            max_dead_letter_size: 死信队列最大容量
        """
        self._task_heap: List[Task] = []  # 任务堆
        self._task_map: Dict[str, Task] = {}  # task_id -> Task 映射
        self._dead_letter_queue: List[Task] = []  # 死信队列
        self._max_dead_letter_size = max_dead_letter_size
        
        self._max_workers = max_workers
        self._workers: List[asyncio.Task] = []
        self._running = False
        self._paused = False
        self._lock = asyncio.Lock()
        self._not_empty = asyncio.Condition(self._lock)
        self._not_full = asyncio.Condition(self._lock)
        
        self._stats = {
            "total_enqueued": 0,
            "total_completed": 0,
            "total_failed": 0,
            "total_dead": 0,
        }

    @property
    def dead_letter_queue(self) -> List[Task]:
        """返回死信队列（只读副本）"""
        return self._dead_letter_queue.copy()

    @property
    def stats(self) -> Dict[str, int]:
        """返回队列统计信息"""
        return self._stats.copy()

    def enqueue(
        self,
        func: Callable,
        args: tuple = (),
        kwargs: Optional[dict] = None,
        priority: Priority = Priority.MEDIUM,
        max_retries: int = 3,
        retry_delay: float = 1.0,
        retry_strategy: RetryStrategy = RetryStrategy.EXPONENTIAL,
        timeout: Optional[float] = None,
        task_id: Optional[str] = None
    ) -> Task:
        """
        添加任务到队列
        
        Args:
            func: 要执行的异步函数
            args: 位置参数
            kwargs: 关键字参数
            priority: 优先级 (HIGH/MEDIUM/LOW)
            max_retries: 最大重试次数
            retry_delay: 重试间隔（秒）
            retry_strategy: 重试策略 (FIXED/EXPONENTIAL)
            timeout: 任务超时时间（秒），None表示无超时
            task_id: 可选的任务ID，不提供则自动生成
        
        Returns:
            创建的 Task 对象
        """
        if kwargs is None:
            kwargs = {}
        
        task = Task(
            id=task_id or str(uuid.uuid4()),
            func=func,
            args=args,
            kwargs=kwargs,
            priority=priority,
            max_retries=max_retries,
            retry_delay=retry_delay,
            retry_strategy=retry_strategy,
            retry_count=0,
            status=TaskStatus.PENDING,
            timeout=timeout,
            scheduled_at=time.time()
        )
        
        self._add_task(task)
        self._stats["total_enqueued"] += 1
        
        logger.debug(f"Task {task.id} enqueued with priority {priority.name}")
        return task

    def _compute_priority_order(self, priority: Priority, scheduled_time: float) -> Tuple[int, float]:
        """
        计算堆排序用的优先级顺序
        
        使用负优先级实现最大堆行为（高优先级先执行）
        使用 scheduled_time 实现延迟任务的排序
        
        Returns:
            (-priority_value, scheduled_time) 元组
        """
        return (-priority.value, scheduled_time)

    def _add_task(self, task: Task) -> None:
        """内部方法：将任务添加到队列"""
        heapq.heappush(self._task_heap, task)
        self._task_map[task.id] = task

    def cancel_task(self, task_id: str) -> bool:
        """
        取消指定任务
        
        Args:
            task_id: 要取消的任务ID
        
        Returns:
            True 如果任务被取消，False 如果任务不存在或已完成
        """
        task = self._task_map.get(task_id)
        if not task:
            return False
        
        if task.status in (TaskStatus.SUCCESS, TaskStatus.FAILED, TaskStatus.DEAD, TaskStatus.CANCELLED):
            return False
        
        task.status = TaskStatus.CANCELLED
        # 从堆中移除（注意：这不会改变堆的性质，因为已取消的任务不会被执行）
        try:
            self._task_heap.remove(task)
            heapq.heapify(self._task_heap)
        except ValueError:
            pass  # 任务可能已经在堆顶被处理
        
        logger.info(f"Task {task_id} cancelled")
        return True

    def get_task(self, task_id: str) -> Optional[Task]:
        """
        获取任务信息
        
        Args:
            task_id: 任务ID
        
        Returns:
            Task 对象或 None
        """
        return self._task_map.get(task_id)

    def get_pending_tasks(self) -> List[Task]:
        """返回所有待执行任务"""
        return [t for t in self._task_heap if t.status == TaskStatus.PENDING]

    def start(self) -> None:
        """启动队列 worker"""
        if self._running:
            return
        
        self._running = True
        for i in range(self._max_workers):
            worker = asyncio.create_task(self._worker(i))
            self._workers.append(worker)
        logger.info(f"Task queue started with {self._max_workers} workers")

    async def stop(self, timeout: float = 10.0) -> None:
        """
        停止队列
        
        Args:
            timeout: 等待worker完成的最大时间（秒）
        """
        if not self._running:
            return
        
        self._running = False
        
        # 取消所有待处理任务
        async with self._lock:
            for task in self._task_heap:
                if task.status == TaskStatus.PENDING:
                    task.status = TaskStatus.CANCELLED
            self._task_heap.clear()
        
        # 等待所有 worker 完成
        if self._workers:
            try:
                await asyncio.wait_for(
                    asyncio.gather(*self._workers, return_exceptions=True),
                    timeout=timeout
                )
            except asyncio.TimeoutError:
                logger.warning("Timeout waiting for workers to finish")
                for w in self._workers:
                    w.cancel()
        
        self._workers.clear()
        logger.info("Task queue stopped")

    async def _worker(self, worker_id: int) -> None:
        """
        Worker 协程：持续从队列获取任务并执行
        
        Args:
            worker_id: Worker 标识符
        """
        logger.debug(f"Worker {worker_id} started")
        
        while self._running:
            task = None
            
            async with self._not_empty:
                # 等待有任务
                while self._running and (not self._task_heap or self._paused):
                    self._not_empty.notify()
                    await asyncio.wait_for(self._not_empty.wait(), timeout=1.0)
                
                if not self._running:
                    break
                
                # 获取最高优先级任务（堆顶）
                while self._task_heap:
                    candidate = heapq.heappop(self._task_heap)
                    # 检查任务是否已取消或已完成
                    if candidate.status in (TaskStatus.CANCELLED, TaskStatus.SUCCESS, 
                                            TaskStatus.FAILED, TaskStatus.DEAD):
                        self._task_map.pop(candidate.id, None)
                        continue
                    # 检查是否到执行时间
                    if candidate.scheduled_at > time.time():
                        # 重新放回堆中，等待下次
                        heapq.heappush(self._task_heap, candidate)
                        await asyncio.sleep(min(0.1, candidate.scheduled_at - time.time()))
                        continue
                    task = candidate
                    break
            
            if not task:
                continue
            
            await self._execute_task(task, worker_id)
        
        logger.debug(f"Worker {worker_id} stopped")

    async def _execute_task(self, task: Task, worker_id: int) -> None:
        """
        执行单个任务
        
        Args:
            task: 要执行的任务
            worker_id: 执行任务的 worker ID
        """
        logger.info(f"Worker {worker_id} executing task {task.id}")
        task.status = TaskStatus.RUNNING
        
        try:
            if task.timeout:
                result = await asyncio.wait_for(
                    task.func(*task.args, **task.kwargs),
                    timeout=task.timeout
                )
            else:
                result = await task.func(*task.args, **task.kwargs)
            
            task.status = TaskStatus.SUCCESS
            task.result = result
            self._stats["total_completed"] += 1
            logger.info(f"Task {task.id} completed successfully")
            
        except asyncio.TimeoutError:
            await self._handle_task_failure(task, f"Task timed out after {task.timeout}s")
        except asyncio.CancelledError:
            task.status = TaskStatus.CANCELLED
            logger.info(f"Task {task.id} was cancelled")
        except Exception as e:
            await self._handle_task_failure(task, str(e))

    async def _handle_task_failure(self, task: Task, error_msg: str) -> None:
        """
        处理任务失败
        
        Args:
            task: 失败的任务
            error_msg: 错误信息
        """
        task.error = error_msg
        task.retry_count += 1
        
        logger.warning(f"Task {task.id} failed (attempt {task.retry_count}/{task.max_retries}): {error_msg}")
        
        if task.retry_count >= task.max_retries:
            # 进入死信队列
            task.status = TaskStatus.DEAD
            self._dead_letter_queue.append(task)
            
            # 限制死信队列大小
            if len(self._dead_letter_queue) > self._max_dead_letter_size:
                self._dead_letter_queue.pop(0)
            
            self._stats["total_dead"] += 1
            logger.error(f"Task {task.id} moved to dead letter queue after {task.retry_count} attempts")
            self._task_map.pop(task.id, None)
        else:
            # 计算重试延迟
            delay = self._calculate_retry_delay(task)
            task.status = TaskStatus.RETRYING
            task.scheduled_at = time.time() + delay
            
            # 重新加入队列
            heapq.heappush(self._task_heap, task)
            logger.info(f"Task {task.id} scheduled for retry in {delay}s")

    def _calculate_retry_delay(self, task: Task) -> float:
        """
        计算重试延迟
        
        Args:
            task: 任务对象
        
        Returns:
            延迟时间（秒）
        """
        if task.retry_strategy == RetryStrategy.EXPONENTIAL:
            # 指数退避: delay * (2 ^ retry_count)
            return task.retry_delay * (2 ** (task.retry_count - 1))
        else:
            # 固定间隔
            return task.retry_delay

    async def pause(self) -> None:
        """暂停队列（不停止已运行的任务）"""
        self._paused = True
        logger.info("Queue paused")

    async def resume(self) -> None:
        """恢复队列"""
        self._paused = False
        async with self._not_empty:
            self._not_empty.notify_all()
        logger.info("Queue resumed")

    def clear_dead_letter_queue(self) -> int:
        """
        清空死信队列
        
        Returns:
            被清空的任务数量
        """
        count = len(self._dead_letter_queue)
        self._dead_letter_queue.clear()
        return count

    def retry_dead_task(self, task_id: str) -> bool:
        """
        重试死信队列中的任务
        
        Args:
            task_id: 任务ID
        
        Returns:
            True 如果成功，False 如果任务不在死信队列中
        """
        task = None
        for i, t in enumerate(self._dead_letter_queue):
            if t.id == task_id:
                task = self._dead_letter_queue.pop(i)
                break
        
        if not task:
            return False
        
        # 重置任务状态
        task.status = TaskStatus.PENDING
        task.retry_count = 0
        task.error = None
        task.scheduled_at = time.time()  # priority_order is auto-computed via property
        
        self._add_task(task)
        logger.info(f"Task {task_id} retried from dead letter queue")
        return True


# ============ 便捷函数 ============

def create_task_queue(max_workers: int = 5) -> AsyncTaskQueue:
    """创建任务队列的便捷函数"""
    return AsyncTaskQueue(max_workers=max_workers)
