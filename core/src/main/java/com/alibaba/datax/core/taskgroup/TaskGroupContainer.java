package com.alibaba.datax.core.taskgroup;

import com.alibaba.datax.common.constant.PluginType;
import com.alibaba.datax.common.exception.CommonErrorCode;
import com.alibaba.datax.common.exception.DataXException;
import com.alibaba.datax.common.plugin.RecordSender;
import com.alibaba.datax.common.plugin.TaskPluginCollector;
import com.alibaba.datax.common.statistics.PerfRecord;
import com.alibaba.datax.common.statistics.PerfTrace;
import com.alibaba.datax.common.statistics.VMInfo;
import com.alibaba.datax.common.util.Configuration;
import com.alibaba.datax.core.AbstractContainer;
import com.alibaba.datax.core.statistics.communication.Communication;
import com.alibaba.datax.core.statistics.communication.CommunicationTool;
import com.alibaba.datax.core.statistics.container.communicator.taskgroup.StandaloneTGContainerCommunicator;
import com.alibaba.datax.core.statistics.plugin.task.AbstractTaskPluginCollector;
import com.alibaba.datax.core.taskgroup.runner.AbstractRunner;
import com.alibaba.datax.core.taskgroup.runner.ReaderRunner;
import com.alibaba.datax.core.taskgroup.runner.WriterRunner;
import com.alibaba.datax.core.transport.channel.Channel;
import com.alibaba.datax.core.transport.exchanger.BufferedRecordExchanger;
import com.alibaba.datax.core.transport.exchanger.BufferedRecordTransformerExchanger;
import com.alibaba.datax.core.transport.transformer.TransformerExecution;
import com.alibaba.datax.core.util.ClassUtil;
import com.alibaba.datax.core.util.FrameworkErrorCode;
import com.alibaba.datax.core.util.TransformerUtil;
import com.alibaba.datax.core.util.container.CoreConstant;
import com.alibaba.datax.core.util.container.LoadUtil;
import com.alibaba.datax.dataxservice.face.domain.enums.State;
import com.alibaba.fastjson2.JSON;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * 任务组容器，它本身并不是一个线程类，而是由 runner 线程调用执行
 */
public class TaskGroupContainer extends AbstractContainer {
    private static final Logger LOG = LoggerFactory.getLogger(TaskGroupContainer.class);

    // 当前 taskGroup 所属jobId
    private final long jobId;

    // 当前 taskGroupId
    private final int taskGroupId;

    // 使用的 channel 类
    private final String channelClazz;

    // task 收集器使用的类
    private final String taskCollectorClass;

    private final TaskMonitor taskMonitor = TaskMonitor.getInstance();

    public TaskGroupContainer(Configuration configuration) {
        super(configuration);

        initCommunicator(configuration);

        this.jobId = this.configuration.getLong(
                CoreConstant.DATAX_CORE_CONTAINER_JOB_ID);
        this.taskGroupId = this.configuration.getInt(
                CoreConstant.DATAX_CORE_CONTAINER_TASKGROUP_ID);

        this.channelClazz = this.configuration.getString(
                CoreConstant.DATAX_CORE_TRANSPORT_CHANNEL_CLASS);
        this.taskCollectorClass = this.configuration.getString(
                CoreConstant.DATAX_CORE_STATISTICS_COLLECTOR_PLUGIN_TASKCLASS);
    }

    private void initCommunicator(Configuration configuration) {
        super.setContainerCommunicator(new StandaloneTGContainerCommunicator(configuration));
    }

    public long getJobId() {
        return jobId;
    }

    public int getTaskGroupId() {
        return taskGroupId;
    }

    @Override
    public void start() {
        try {
            // 状态 check 时间间隔，较短，可以把任务及时分发到对应 channel 中
            int sleepIntervalInMillSec = this.configuration.getInt(
                    CoreConstant.DATAX_CORE_CONTAINER_TASKGROUP_SLEEPINTERVAL, 100);
            // 状态汇报时间间隔，稍长，避免大量汇报
            long reportIntervalInMillSec = this.configuration.getLong(
                    CoreConstant.DATAX_CORE_CONTAINER_TASKGROUP_REPORTINTERVAL, 10000);

            // 2 分钟汇报一次性能统计

            // 获取 channel 数目
            int channelNumber = this.configuration.getInt(
                    CoreConstant.DATAX_CORE_CONTAINER_TASKGROUP_CHANNEL);

            int taskMaxRetryTimes = this.configuration.getInt(
                    CoreConstant.DATAX_CORE_CONTAINER_TASK_FAILOVER_MAXRETRYTIMES, 1);

            long taskRetryIntervalInMses = this.configuration.getLong(
                    CoreConstant.DATAX_CORE_CONTAINER_TASK_FAILOVER_RETRYINTERVALINMSEC, 10000);

            long taskMaxWaitInMses = this.configuration.getLong(
                    CoreConstant.DATAX_CORE_CONTAINER_TASK_FAILOVER_MAXWAITINMSEC, 60000);

            List<Configuration> taskConfigs = this.configuration
                    .getListConfiguration(CoreConstant.DATAX_JOB_CONTENT);

            if (LOG.isDebugEnabled()) {
                LOG.debug(
                        "taskGroup[{}]'s task configs[{}]",
                        this.taskGroupId,
                        JSON.toJSONString(taskConfigs));
            }

            int taskCountInThisTaskGroup = taskConfigs.size();
            LOG.info(String.format(
                    "taskGroupId=[%d] start [%d] channels for [%d] tasks.",
                    this.taskGroupId, channelNumber, taskCountInThisTaskGroup));

            this.containerCommunicator.registerCommunication(taskConfigs);

            // taskId 与 task 配置
            Map<Integer, Configuration> taskConfigMap = buildTaskConfigMap(taskConfigs);
            // 待运行 task 队列
            List<Configuration> taskQueue = buildRemainTasks(taskConfigs);
            // taskId 与上次失败实例
            Map<Integer, TaskExecutor> taskFailedExecutorMap = new HashMap<>();
            // 正在运行 task，channel 数量即同时运行的 task 数量
            List<TaskExecutor> runTasks = new ArrayList<>(channelNumber);
            // 任务开始时间
            Map<Integer, Long> taskStartTimeMap = new HashMap<>();

            long lastReportTimeStamp = 0;
            Communication lastTaskGroupContainerCommunication = new Communication();

            while (true) {
                // 1. 判断 task 状态
                boolean failedOrKilled = false;
                Map<Integer, Communication> communicationMap = containerCommunicator.getCommunicationMap();
                for (Map.Entry<Integer, Communication> entry : communicationMap.entrySet()) {
                    Integer taskId = entry.getKey();
                    Communication taskCommunication = entry.getValue();
                    if (!taskCommunication.isFinished()) {
                        continue;
                    }
                    TaskExecutor taskExecutor = removeTask(runTasks, taskId);

                    // 上面从 runTasks 里移除了，因此对应在 monitor 里移除
                    taskMonitor.removeTask(taskId);

                    // 失败，看 task 是否支持 fail over，重试次数未超过最大限制
                    if (taskCommunication.getState() == State.FAILED) {
                        taskFailedExecutorMap.put(taskId, taskExecutor);
                        if (taskExecutor.supportFailOver()
                                && taskExecutor.getAttemptCount() < taskMaxRetryTimes) {
                            taskExecutor.shutdown(); // 关闭老的 executor
                            containerCommunicator.resetCommunication(taskId); // 将 task 的状态重置
                            Configuration taskConfig = taskConfigMap.get(taskId);
                            taskQueue.add(taskConfig); // 重新加入任务列表
                        } else {
                            failedOrKilled = true;
                            break;
                        }
                    } else if (taskCommunication.getState() == State.KILLED) {
                        failedOrKilled = true;
                        break;
                    } else if (taskCommunication.getState() == State.SUCCEEDED) {
                        Long taskStartTime = taskStartTimeMap.get(taskId);
                        if (taskStartTime != null) {
                            long usedTime = System.currentTimeMillis() - taskStartTime;
                            LOG.info("taskGroup[{}] taskId[{}] is successed, used[{}]ms",
                                    this.taskGroupId, taskId, usedTime);
                            // usedTime*1000*1000 转换成 PerfRecord 记录的 ns，
                            // 这里主要是简单登记，进行最长任务的打印。因此增加特定静态方法
                            PerfRecord.addPerfRecord(taskGroupId,
                                    taskId,
                                    PerfRecord.PHASE.TASK_TOTAL,
                                    taskStartTime,
                                    usedTime * 1000L * 1000L);
                            taskStartTimeMap.remove(taskId);
                            taskConfigMap.remove(taskId);
                        }
                    }
                }

                // 2. 发现该 taskGroup 下 taskExecutor 的总状态失败则汇报错误
                if (failedOrKilled) {
                    lastTaskGroupContainerCommunication = reportTaskGroupCommunication(
                            lastTaskGroupContainerCommunication, taskCountInThisTaskGroup);

                    throw DataXException.asDataXException(
                            FrameworkErrorCode.PLUGIN_RUNTIME_ERROR,
                            lastTaskGroupContainerCommunication.getThrowable());
                }

                // 3. 有任务未执行，且正在运行的任务数小于最大通道限制
                Iterator<Configuration> iterator = taskQueue.iterator();
                while (iterator.hasNext() && runTasks.size() < channelNumber) {
                    Configuration taskConfig = iterator.next();
                    Integer taskId = taskConfig.getInt(CoreConstant.TASK_ID);
                    int attemptCount = 1;
                    TaskExecutor lastExecutor = taskFailedExecutorMap.get(taskId);
                    if (lastExecutor != null) {
                        attemptCount = lastExecutor.getAttemptCount() + 1;
                        long now = System.currentTimeMillis();
                        long failedTime = lastExecutor.getTimeStamp();
                        if (now - failedTime < taskRetryIntervalInMses) {  // 未到等待时间，继续留在队列
                            continue;
                        }
                        if (!lastExecutor.isShutdown()) { // 上次失败的 task 仍未结束
                            if (now - failedTime > taskMaxWaitInMses) {
                                markCommunicationFailed(taskId);
                                reportTaskGroupCommunication(
                                        lastTaskGroupContainerCommunication,
                                        taskCountInThisTaskGroup);
                                throw DataXException.asDataXException(
                                        CommonErrorCode.WAIT_TIME_EXCEED,
                                        "task failover等待超时");
                            } else {
                                lastExecutor.shutdown(); // 再次尝试关闭
                                continue;
                            }
                        } else {
                            LOG.info("taskGroup[{}] taskId[{}] attemptCount[{}] has already shutdown",
                                    this.taskGroupId,
                                    taskId,
                                    lastExecutor.getAttemptCount());
                        }
                    }

                    Configuration taskConfigForRun =
                            taskMaxRetryTimes > 1 ? taskConfig.clone() : taskConfig;
                    TaskExecutor taskExecutor = new TaskExecutor(taskConfigForRun, attemptCount);
                    taskStartTimeMap.put(taskId, System.currentTimeMillis());

                    // 启动 executor 执行读写
                    taskExecutor.doStart();

                    iterator.remove();
                    runTasks.add(taskExecutor);

                    // 上面，增加 task 到 runTasks 列表，因此在 monitor 里注册。
                    taskMonitor.registerTask(
                            taskId,
                            this.containerCommunicator.getCommunication(taskId));

                    taskFailedExecutorMap.remove(taskId);
                    LOG.info("taskGroup[{}] taskId[{}] attemptCount[{}] is started",
                            this.taskGroupId, taskId, attemptCount);
                }

                // 4. 任务列表为空，executor 已结束, 搜集状态为 success --> 成功
                if (taskQueue.isEmpty() && isAllTaskDone(runTasks)
                        && containerCommunicator.collectState() == State.SUCCEEDED) {
                    // 成功的情况下，也需要汇报一次。否则在任务结束非常快的情况下，采集的信息将会不准确
                    lastTaskGroupContainerCommunication = reportTaskGroupCommunication(
                            lastTaskGroupContainerCommunication, taskCountInThisTaskGroup);

                    LOG.info("taskGroup[{}] completed it's tasks.", this.taskGroupId);
                    break;
                }

                // 5. 如果当前时间已经超出汇报时间的 interval，那么我们需要马上汇报
                long now = System.currentTimeMillis();
                if (now - lastReportTimeStamp > reportIntervalInMillSec) {
                    lastTaskGroupContainerCommunication = reportTaskGroupCommunication(
                            lastTaskGroupContainerCommunication, taskCountInThisTaskGroup);

                    lastReportTimeStamp = now;

                    // taskMonitor 对于正在运行的 task，每 reportIntervalInMillSec 进行检查
                    for (TaskExecutor taskExecutor : runTasks) {
                        taskMonitor.report(
                                taskExecutor.getTaskId(),
                                this.containerCommunicator.getCommunication(taskExecutor.getTaskId()));
                    }
                }

                Thread.sleep(sleepIntervalInMillSec);
            }

            // 6. 最后还要汇报一次
            reportTaskGroupCommunication(
                    lastTaskGroupContainerCommunication,
                    taskCountInThisTaskGroup);
        } catch (Throwable e) {
            Communication nowTaskGroupContainerCommunication = this.containerCommunicator.collect();

            if (nowTaskGroupContainerCommunication.getThrowable() == null) {
                nowTaskGroupContainerCommunication.setThrowable(e);
            }

            nowTaskGroupContainerCommunication.setState(State.FAILED);

            this.containerCommunicator.report(nowTaskGroupContainerCommunication);

            throw DataXException.asDataXException(FrameworkErrorCode.RUNTIME_ERROR, e);
        } finally {
            if (!PerfTrace.getInstance().isJob()) {
                // 最后打印 cpu 的平均消耗，GC 的统计
                VMInfo vmInfo = VMInfo.getVmInfo();
                if (vmInfo != null) {
                    vmInfo.getDelta(false);
                    LOG.info(vmInfo.totalString());
                }

                LOG.info(PerfTrace.getInstance().summarizeNoException());
            }
        }
    }

    /**
     * taskId 及其配置文件
     *
     * @param configurations
     */
    private Map<Integer, Configuration> buildTaskConfigMap(List<Configuration> configurations) {
        Map<Integer, Configuration> map = new HashMap<>();
        for (Configuration taskConfig : configurations) {
            int taskId = taskConfig.getInt(CoreConstant.TASK_ID);
            map.put(taskId, taskConfig);
        }

        return map;
    }

    private List<Configuration> buildRemainTasks(List<Configuration> configurations) {
        return new LinkedList<Configuration>(configurations);
    }

    private TaskExecutor removeTask(List<TaskExecutor> taskList, int taskId) {
        Iterator<TaskExecutor> iterator = taskList.iterator();
        while (iterator.hasNext()) {
            TaskExecutor taskExecutor = iterator.next();
            if (taskExecutor.getTaskId() == taskId) {
                iterator.remove();
                return taskExecutor;
            }
        }

        return null;
    }

    private boolean isAllTaskDone(List<TaskExecutor> taskList) {
        for (TaskExecutor taskExecutor : taskList) {
            if (!taskExecutor.isTaskFinished()) {
                return false;
            }
        }
        return true;
    }

    private Communication reportTaskGroupCommunication(
            Communication lastTaskGroupContainerCommunication,
            int taskCount) {
        Communication nowTaskGroupContainerCommunication = this.containerCommunicator.collect();
        nowTaskGroupContainerCommunication.setTimestamp(System.currentTimeMillis());
        Communication reportCommunication = CommunicationTool.getReportCommunication(nowTaskGroupContainerCommunication,
                lastTaskGroupContainerCommunication,
                taskCount);
        this.containerCommunicator.report(reportCommunication);
        return reportCommunication;
    }

    private void markCommunicationFailed(Integer taskId) {
        Communication communication = containerCommunicator.getCommunication(taskId);
        communication.setState(State.FAILED);
    }

    /**
     * TaskExecutor 是一个完整 task 的执行器
     * 其中包括 1：1 的 reader 和 writer
     */
    class TaskExecutor {
        private final Configuration taskConfig;

        private final int taskId;

        private final int attemptCount;

        private final Channel channel;

        private final Thread readerThread;

        private final Thread writerThread;

        private final ReaderRunner readerRunner;

        private final WriterRunner writerRunner;

        /**
         * 该处的 taskCommunication 在多处用到：
         * 1. channel
         * 2. readerRunner 和 writerRunner
         * 3. reader 和 writer 的 taskPluginCollector
         */
        private final Communication taskCommunication;

        public TaskExecutor(Configuration taskConf, int attemptCount) {
            // 获取该 taskExecutor 的配置
            this.taskConfig = taskConf;
            // 验证参数
            Validate.isTrue(
                    null != this.taskConfig.getConfiguration(CoreConstant.JOB_READER)
                            && null != this.taskConfig.getConfiguration(CoreConstant.JOB_WRITER),
                    "[reader|writer]的插件参数不能为空!");

            // 得到 taskId
            this.taskId = this.taskConfig.getInt(CoreConstant.TASK_ID);
            this.attemptCount = attemptCount;

            // 由 taskId 得到该 taskExecutor 的 Communication
            // 要传给 readerRunner 和 writerRunner，同时要传给 channel 作统计用
            this.taskCommunication = containerCommunicator.getCommunication(taskId);
            Validate.notNull(
                    this.taskCommunication,
                    String.format("taskId[%d]的Communication没有注册过", taskId));
            this.channel = ClassUtil.instantiate(channelClazz, Channel.class, configuration);
            this.channel.setCommunication(this.taskCommunication);

            // 获取 transformer 的参数
            List<TransformerExecution> transformerInfoExecs = TransformerUtil.buildTransformerInfo(
                    taskConfig);

            // 生成 writerThread
            writerRunner = (WriterRunner) generateRunner(PluginType.WRITER);
            this.writerThread = new Thread(
                    writerRunner,
                    String.format("%d-%d-%d-writer", jobId, taskGroupId, this.taskId));
            // 通过设置 thread 的 contextClassLoader，即可实现同步和主程序不同的加载器
            this.writerThread.setContextClassLoader(LoadUtil.getJarLoader(
                    PluginType.WRITER, this.taskConfig.getString(CoreConstant.JOB_WRITER_NAME)));

            // 生成 readerThread
            readerRunner = (ReaderRunner) generateRunner(PluginType.READER, transformerInfoExecs);
            this.readerThread = new Thread(
                    readerRunner,
                    String.format("%d-%d-%d-reader", jobId, taskGroupId, this.taskId));
            // 通过设置 thread 的 contextClassLoader，即可实现同步和主程序不同的加载器
            this.readerThread.setContextClassLoader(LoadUtil.getJarLoader(
                    PluginType.READER, this.taskConfig.getString(CoreConstant.JOB_READER_NAME)));
        }

        // 启动 task
        public void doStart() {
            // 启动 writer 线程
            this.writerThread.start();

            // reader 没有起来，writer 不可能结束
            if (!this.writerThread.isAlive() || this.taskCommunication.getState() == State.FAILED) {
                throw DataXException.asDataXException(
                        FrameworkErrorCode.RUNTIME_ERROR,
                        this.taskCommunication.getThrowable());
            }

            // 启动 reader 线程
            this.readerThread.start();

            // 这里 reader 可能很快结束
            if (!this.readerThread.isAlive() && this.taskCommunication.getState() == State.FAILED) {
                // 这里有可能出现Reader线上启动即挂情况 对于这类情况 需要立刻抛出异常
                throw DataXException.asDataXException(
                        FrameworkErrorCode.RUNTIME_ERROR,
                        this.taskCommunication.getThrowable());
            }
        }

        private AbstractRunner generateRunner(PluginType pluginType) {
            return generateRunner(pluginType, null);
        }

        private AbstractRunner generateRunner(
                PluginType pluginType,
                List<TransformerExecution> transformerInfoExecs) {
            AbstractRunner newRunner = null;
            TaskPluginCollector pluginCollector;

            switch (pluginType) {
                case READER:
                    newRunner = LoadUtil.loadPluginRunner(
                            pluginType,
                            this.taskConfig.getString(CoreConstant.JOB_READER_NAME));
                    newRunner.setJobConf(this.taskConfig.getConfiguration(
                            CoreConstant.JOB_READER_PARAMETER));

                    pluginCollector = ClassUtil.instantiate(
                            taskCollectorClass, AbstractTaskPluginCollector.class,
                            configuration, this.taskCommunication,
                            PluginType.READER);

                    RecordSender recordSender;
                    if (transformerInfoExecs != null && transformerInfoExecs.size() > 0) {
                        recordSender = new BufferedRecordTransformerExchanger(
                                taskGroupId,
                                this.taskId,
                                this.channel,
                                this.taskCommunication,
                                pluginCollector,
                                transformerInfoExecs);
                    } else {
                        recordSender = new BufferedRecordExchanger(this.channel, pluginCollector);
                    }

                    ((ReaderRunner) newRunner).setRecordSender(recordSender);

                    // 设置 taskPlugin 的 collector，用来处理脏数据和 job/task 通信
                    newRunner.setTaskPluginCollector(pluginCollector);

                    break;
                case WRITER:
                    newRunner = LoadUtil.loadPluginRunner(
                            pluginType,
                            this.taskConfig.getString(CoreConstant.JOB_WRITER_NAME));
                    newRunner.setJobConf(this.taskConfig
                            .getConfiguration(CoreConstant.JOB_WRITER_PARAMETER));

                    pluginCollector = ClassUtil.instantiate(
                            taskCollectorClass, AbstractTaskPluginCollector.class,
                            configuration, this.taskCommunication,
                            PluginType.WRITER);

                    ((WriterRunner) newRunner).setRecordReceiver(new BufferedRecordExchanger(
                            this.channel, pluginCollector));

                    // 设置 taskPlugin 的 collector，用来处理脏数据和 job/task 通信
                    newRunner.setTaskPluginCollector(pluginCollector);

                    break;
                default:
                    throw DataXException.asDataXException(
                            FrameworkErrorCode.ARGUMENT_ERROR,
                            "Cant generateRunner for:" + pluginType);
            }

            newRunner.setTaskGroupId(taskGroupId);
            newRunner.setTaskId(this.taskId);
            newRunner.setRunnerCommunication(this.taskCommunication);

            return newRunner;
        }

        /**
         * 检查人物是否结束
         */
        private boolean isTaskFinished() {
            // 如果 reader 或 writer 没有完成工作，那么直接返回工作没有完成
            if (readerThread.isAlive() || writerThread.isAlive()) {
                return false;
            }

            return taskCommunication != null && taskCommunication.isFinished();
        }

        private int getTaskId() {
            return taskId;
        }

        private long getTimeStamp() {
            return taskCommunication.getTimestamp();
        }

        private int getAttemptCount() {
            return attemptCount;
        }

        private boolean supportFailOver() {
            return writerRunner.supportFailOver();
        }

        private void shutdown() {
            writerRunner.shutdown();
            readerRunner.shutdown();
            if (writerThread.isAlive()) {
                writerThread.interrupt();
            }
            if (readerThread.isAlive()) {
                readerThread.interrupt();
            }
        }

        private boolean isShutdown() {
            return !readerThread.isAlive() && !writerThread.isAlive();
        }
    }
}
