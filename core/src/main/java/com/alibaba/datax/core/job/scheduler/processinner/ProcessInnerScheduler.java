package com.alibaba.datax.core.job.scheduler.processinner;

import com.alibaba.datax.common.exception.DataXException;
import com.alibaba.datax.common.util.Configuration;
import com.alibaba.datax.core.job.scheduler.AbstractScheduler;
import com.alibaba.datax.core.statistics.container.communicator.AbstractContainerCommunicator;
import com.alibaba.datax.core.taskgroup.TaskGroupContainer;
import com.alibaba.datax.core.taskgroup.runner.TaskGroupContainerRunner;
import com.alibaba.datax.core.util.FrameworkErrorCode;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 进程内调度器，也就是线程调度器
 */
public abstract class ProcessInnerScheduler extends AbstractScheduler {
    private ExecutorService taskGroupContainerExecutorService;

    public ProcessInnerScheduler(AbstractContainerCommunicator containerCommunicator) {
        super(containerCommunicator);
    }

    @Override
    public void startAllTaskGroup(List<Configuration> configurations) {
        // Executors 类提供了几个静态方法来创建线程池，这里初始化一个固定大小的线程池
        this.taskGroupContainerExecutorService = Executors.newFixedThreadPool(configurations.size());

        for (Configuration taskGroupConfiguration : configurations) {
            TaskGroupContainerRunner taskGroupContainerRunner = newTaskGroupContainerRunner(taskGroupConfiguration);
            this.taskGroupContainerExecutorService.execute(taskGroupContainerRunner);
        }

        // 调用 shutdown 方法之后，线程池不能接受新的任务，清除一些空的 worker，等待任务完成，主线程继续往下进行
        this.taskGroupContainerExecutorService.shutdown();
    }

    @Override
    public void dealFailedStat(AbstractContainerCommunicator frameworkCollector, Throwable throwable) {
        this.taskGroupContainerExecutorService.shutdownNow();

        throw DataXException.asDataXException(FrameworkErrorCode.PLUGIN_RUNTIME_ERROR, throwable);
    }

    @Override
    public void dealKillingStat(AbstractContainerCommunicator frameworkCollector, int totalTasks) {
        // 通过进程退出返回码标示状态
        this.taskGroupContainerExecutorService.shutdownNow();

        throw DataXException.asDataXException(FrameworkErrorCode.KILLED_EXIT_VALUE, "job killed status");
    }

    // 初始化一个 runner，每个 runner 持有一个 container
    private TaskGroupContainerRunner newTaskGroupContainerRunner(Configuration configuration) {
        TaskGroupContainer taskGroupContainer = new TaskGroupContainer(configuration);

        return new TaskGroupContainerRunner(taskGroupContainer);
    }
}
