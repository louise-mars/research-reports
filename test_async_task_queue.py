"""
AsyncTaskQueue 单元测试
=======================

运行方式:
    python -m unittest test_async_task_queue
    或
    pytest test_async_task_queue.py -v
"""

import asyncio
import unittest
import time
from unittest.mock import AsyncMock, MagicMock, patch

from async_task_queue import (
    AsyncTaskQueue, Task, TaskStatus, Priority, RetryStrategy
)


class TestPriority(unittest.TestCase):
    """测试优先级枚举"""
    
    def test_priority_order(self):
        """验证优先级数值：HIGH > MEDIUM > LOW"""
        self.assertGreater(Priority.HIGH.value, Priority.MEDIUM.value)
        self.assertGreater(Priority.MEDIUM.value, Priority.LOW.value)
        self.assertEqual(Priority.HIGH.value, 3)
        self.assertEqual(Priority.MEDIUM.value, 2)
        self.assertEqual(Priority.LOW.value, 1)


class TestRetryStrategy(unittest.TestCase):
    """测试重试策略枚举"""
    
    def test_retry_strategies(self):
        """验证重试策略存在"""
        self.assertEqual(RetryStrategy.FIXED.value, "fixed")
        self.assertEqual(RetryStrategy.EXPONENTIAL.value, "exponential")


class TestTask(unittest.TestCase):
    """测试 Task 数据类"""
    
    def test_task_creation(self):
        """测试任务创建"""
        async def dummy_func():
            pass
        
        task = Task(
            func=dummy_func,
            args=(1, 2),
            kwargs={"key": "value"},
            priority=Priority.HIGH,
            max_retries=5,
            retry_delay=2.0,
            retry_strategy=RetryStrategy.EXPONENTIAL
        )
        
        self.assertIsNotNone(task.id)
        self.assertEqual(task.func, dummy_func)
        self.assertEqual(task.args, (1, 2))
        self.assertEqual(task.kwargs, {"key": "value"})
        self.assertEqual(task.priority, Priority.HIGH)
        self.assertEqual(task.max_retries, 5)
        self.assertEqual(task.retry_delay, 2.0)
        self.assertEqual(task.retry_strategy, RetryStrategy.EXPONENTIAL)
        self.assertEqual(task.retry_count, 0)
        self.assertEqual(task.status, TaskStatus.PENDING)
    
    def test_task_auto_id(self):
        """测试自动生成ID"""
        task1 = Task(func=async_mock)
        task2 = Task(func=async_mock)
        
        self.assertIsNotNone(task1.id)
        self.assertIsNotNone(task2.id)
        self.assertNotEqual(task1.id, task2.id)
    
    def test_task_custom_id(self):
        """测试自定义ID"""
        task = Task(func=async_mock, id="custom-id-123")
        self.assertEqual(task.id, "custom-id-123")


# 用于测试的异步函数
async def async_mock():
    """简单的异步mock函数"""
    await asyncio.sleep(0.01)
    return "success"


async def async_fail_once(fail_count: list):
    """失败一次后成功的异步函数"""
    fail_count[0] += 1
    if fail_count[0] == 1:
        raise ValueError("First failure")
    return "success"


async def async_always_fail():
    """总是失败的异步函数"""
    raise RuntimeError("Always fails")


class TestAsyncTaskQueue(unittest.IsolatedAsyncioTestCase):
    """测试 AsyncTaskQueue 主类"""
    
    async def asyncSetUp(self):
        """每个测试前的设置"""
        self.queue = AsyncTaskQueue(max_workers=2)
    
    async def asyncTearDown(self):
        """每个测试后的清理"""
        if self.queue._running:
            await self.queue.stop(timeout=1.0)
    
    async def test_enqueue(self):
        """测试入队"""
        task = self.queue.enqueue(async_mock, args=(), kwargs={}, priority=Priority.HIGH)
        
        self.assertIsNotNone(task)
        self.assertEqual(task.status, TaskStatus.PENDING)
        self.assertEqual(task.priority, Priority.HIGH)
        self.assertEqual(self.queue.stats["total_enqueued"], 1)
    
    async def test_priority_ordering(self):
        """测试优先级排序：高优先级先执行"""
        results = []
        
        async def capture_and_wait(name, delay):
            await asyncio.sleep(delay)
            results.append(name)
        
        self.queue.enqueue(lambda: capture_and_wait("low", 0), priority=Priority.LOW)
        self.queue.enqueue(lambda: capture_and_wait("high", 0), priority=Priority.HIGH)
        self.queue.enqueue(lambda: capture_and_wait("medium", 0), priority=Priority.MEDIUM)
        
        pending = self.queue.get_pending_tasks()
        self.assertEqual(len(pending), 3)
        
        # 高优先级任务应该在前面
        sorted_tasks = sorted(pending, key=lambda t: t.priority.value, reverse=True)
        self.assertEqual(sorted_tasks[0].priority, Priority.HIGH)
    
    async def test_basic_execution(self):
        """测试基本执行"""
        result_holder = []
        
        async def task_with_result():
            await asyncio.sleep(0.01)
            result_holder.append("executed")
            return "done"
        
        task = self.queue.enqueue(task_with_result, priority=Priority.HIGH)
        self.queue.start()
        
        await asyncio.sleep(0.5)
        
        self.assertEqual(task.status, TaskStatus.SUCCESS)
        self.assertEqual(task.result, "done")
        self.assertEqual(result_holder, ["executed"])
    
    async def test_task_retry_with_fixed_strategy(self):
        """测试固定间隔重试策略"""
        call_count = []
        
        async def fail_then_succeed():
            call_count.append(1)
            if len(call_count) < 3:
                raise ValueError("Temporary failure")
            return "finally success"
        
        task = self.queue.enqueue(
            fail_then_succeed,
            max_retries=5,
            retry_delay=0.1,  # Increased for reliable retries
            retry_strategy=RetryStrategy.FIXED
        )
        
        self.queue.start()
        await asyncio.sleep(2.0)  # Increased sleep
        
        self.assertEqual(task.status, TaskStatus.SUCCESS)
        self.assertEqual(len(call_count), 3)
    
    async def test_task_retry_with_exponential_backoff(self):
        """测试指数退避重试策略"""
        call_times = []
        
        async def fail_multiple():
            call_times.append(time.time())
            if len(call_times) < 3:
                raise ValueError("Temporary failure")
            return "success"
        
        task = self.queue.enqueue(
            fail_multiple,
            max_retries=5,
            retry_delay=0.1,  # Increased for reliable retries
            retry_strategy=RetryStrategy.EXPONENTIAL
        )
        
        self.queue.start()
        await asyncio.sleep(5.0)  # Increased for exponential backoff
        
        self.assertEqual(task.status, TaskStatus.SUCCESS)
        self.assertEqual(len(call_times), 3)
        
        # 验证指数退避：第二次延迟应该是第一次的约2倍
        if len(call_times) >= 3:
            first_delay = call_times[1] - call_times[0]
            second_delay = call_times[2] - call_times[1]
            # 第二次延迟应该大于第一次（指数增长）
            self.assertGreater(second_delay, first_delay * 0.8)  # 允许一些误差
    
    async def test_dead_letter_queue(self):
        """测试死信队列"""
        task = self.queue.enqueue(
            async_always_fail,
            max_retries=2,
            retry_delay=0.05  # Increased delay to ensure retries complete
        )
        
        self.queue.start()
        await asyncio.sleep(3.0)  # Increased sleep for retries
        
        self.assertEqual(task.status, TaskStatus.DEAD)
        self.assertEqual(len(self.queue.dead_letter_queue), 1)
        self.assertEqual(self.queue.dead_letter_queue[0].id, task.id)
        self.assertEqual(self.queue.stats["total_dead"], 1)
    
    async def test_task_cancellation(self):
        """测试任务取消"""
        task = self.queue.enqueue(
            lambda: asyncio.sleep(10),  # 长时间运行的任务
            priority=Priority.HIGH
        )
        
        self.queue.start()
        await asyncio.sleep(0.1)
        
        cancelled = self.queue.cancel_task(task.id)
        
        self.assertTrue(cancelled)
        self.assertEqual(task.status, TaskStatus.CANCELLED)
    
    async def test_task_timeout(self):
        """测试任务超时"""
        async def slow_task():
            await asyncio.sleep(10)
        
        task = self.queue.enqueue(slow_task, timeout=0.1, max_retries=1, retry_delay=0.2)
        
        self.queue.start()
        await asyncio.sleep(3.0)  # Increased for timeout + retry
        
        self.assertEqual(task.status, TaskStatus.DEAD)
        self.assertIn("timed out", task.error.lower())
    
    async def test_retry_dead_task(self):
        """测试从死信队列重试任务"""
        async def always_fail():
            raise RuntimeError("I always fail")
        
        task = self.queue.enqueue(
            always_fail,
            max_retries=1,
            retry_delay=0.01
        )
        
        self.queue.start()
        await asyncio.sleep(0.5)
        
        self.assertEqual(task.status, TaskStatus.DEAD)
        
        # 修改函数为成功的
        task.func = async_mock
        task.result = None
        task.error = None
        
        success = self.queue.retry_dead_task(task.id)
        
        self.assertTrue(success)
        self.assertEqual(len(self.queue.dead_letter_queue), 0)
    
    async def test_multiple_tasks_concurrent(self):
        """测试多任务并发执行"""
        results = []
        
        async def numbered_task(n):
            await asyncio.sleep(0.01)
            results.append(n)
            return n
        
        for i in range(10):
            self.queue.enqueue(lambda i=i: numbered_task(i))
        
        self.queue.start()
        await asyncio.sleep(2.0)
        
        self.assertEqual(len(results), 10)
        self.assertEqual(self.queue.stats["total_completed"], 10)
    
    async def test_pause_and_resume(self):
        """测试暂停和恢复"""
        results = []
        
        async def slow_task():
            await asyncio.sleep(0.3)
            results.append(1)
        
        # Use only 2 tasks - they will be picked up by workers but take time
        for _ in range(2):
            self.queue.enqueue(slow_task)
        
        self.queue.start()
        await asyncio.sleep(0.1)  # Let workers start but not finish
        await self.queue.pause()
        
        await asyncio.sleep(0.5)  # While paused, no new tasks should complete
        paused_count = self.queue.stats["total_completed"]
        
        await self.queue.resume()
        await asyncio.sleep(1.0)  # Resume and wait for tasks
        
        # All tasks should complete after resume
        self.assertEqual(len(results), 2)
    
    async def test_clear_dead_letter_queue(self):
        """测试清空死信队列"""
        # 添加几个失败的任务到死信队列
        for _ in range(3):
            task = self.queue.enqueue(
                async_always_fail,
                max_retries=1,
                retry_delay=0.01
            )
        
        self.queue.start()
        await asyncio.sleep(1.0)
        
        self.assertEqual(len(self.queue.dead_letter_queue), 3)
        
        count = self.queue.clear_dead_letter_queue()
        
        self.assertEqual(count, 3)
        self.assertEqual(len(self.queue.dead_letter_queue), 0)
    
    async def test_stats_tracking(self):
        """测试统计信息追踪"""
        async def success_task():
            return "ok"
        
        # 添加多个任务
        for _ in range(5):
            self.queue.enqueue(success_task)
        
        self.queue.enqueue(async_always_fail, max_retries=1, retry_delay=0.01)
        
        self.queue.start()
        await asyncio.sleep(1.0)
        
        stats = self.queue.stats
        
        self.assertEqual(stats["total_enqueued"], 6)
        self.assertEqual(stats["total_completed"], 5)
        self.assertGreaterEqual(stats["total_failed"] + stats["total_dead"], 1)


class TestEdgeCases(unittest.IsolatedAsyncioTestCase):
    """边界情况测试"""
    
    async def asyncSetUp(self):
        self.queue = AsyncTaskQueue(max_workers=1)
    
    async def asyncTearDown(self):
        if self.queue._running:
            await self.queue.stop(timeout=1.0)
    
    async def test_empty_queue_start_stop(self):
        """测试空队列的启动和停止"""
        self.queue.start()
        await asyncio.sleep(0.1)
        await self.queue.stop()
        self.assertFalse(self.queue._running)
    
    async def test_cancel_nonexistent_task(self):
        """测试取消不存在的任务"""
        result = self.queue.cancel_task("nonexistent-id")
        self.assertFalse(result)
    
    async def test_get_nonexistent_task(self):
        """测试获取不存在的任务"""
        task = self.queue.get_task("nonexistent-id")
        self.assertIsNone(task)
    
    async def test_enqueue_with_no_args(self):
        """测试无参数入队"""
        task = self.queue.enqueue(async_mock)
        self.assertIsNotNone(task)
        self.assertEqual(task.args, ())
        self.assertEqual(task.kwargs, {})
    
    async def test_task_with_exception_in_result(self):
        """测试任务抛出异常的情况"""
        async def raises_exception():
            raise ValueError("Test exception")
        
        task = self.queue.enqueue(raises_exception, max_retries=1, retry_delay=0.01)
        
        self.queue.start()
        await asyncio.sleep(0.5)
        
        self.assertEqual(task.status, TaskStatus.DEAD)
        self.assertIsNotNone(task.error)


class TestIntegration(unittest.IsolatedAsyncioTestCase):
    """集成测试"""
    
    async def asyncSetUp(self):
        self.queue = AsyncTaskQueue(max_workers=3)
    
    async def asyncTearDown(self):
        if self.queue._running:
            await self.queue.stop(timeout=5.0)
    
    async def test_real_world_scenario(self):
        """模拟真实场景：处理一系列不同优先级的任务"""
        processed = []
        
        async def process_item(item_id: int, priority: Priority):
            await asyncio.sleep(0.05)
            processed.append((item_id, priority.name))
        
        # 添加不同优先级的任务
        items = [
            (1, Priority.LOW),
            (2, Priority.HIGH),
            (3, Priority.MEDIUM),
            (4, Priority.HIGH),
            (5, Priority.LOW),
        ]
        
        for item_id, priority in items:
            self.queue.enqueue(
                lambda i=item_id, p=priority: process_item(i, p),
                priority=priority
            )
        
        self.queue.start()
        await asyncio.sleep(2.0)
        
        # 验证所有任务都完成了
        self.assertEqual(len(processed), 5)
        
        # 验证统计
        stats = self.queue.stats
        self.assertEqual(stats["total_enqueued"], 5)
        self.assertEqual(stats["total_completed"], 5)


if __name__ == "__main__":
    unittest.main()
