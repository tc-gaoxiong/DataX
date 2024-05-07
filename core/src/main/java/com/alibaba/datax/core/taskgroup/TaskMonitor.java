package com.alibaba.datax.core.taskgroup;

import com.alibaba.datax.common.exception.CommonErrorCode;
import com.alibaba.datax.common.exception.DataXException;
import com.alibaba.datax.core.statistics.communication.Communication;
import com.alibaba.datax.core.statistics.communication.CommunicationTool;
import com.alibaba.datax.dataxservice.face.domain.enums.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by liqiang on 15/7/23.
 */
public class TaskMonitor {
    private static final Logger LOG = LoggerFactory.getLogger(TaskMonitor.class);

    private static final TaskMonitor instance = new TaskMonitor();

    private ConcurrentHashMap<Integer, TaskCommunication> tasks = new ConcurrentHashMap<>();

    private TaskMonitor() {
    }

    // 饿汉式单例
    public static TaskMonitor getInstance() {
        return instance;
    }

    public void registerTask(Integer taskId, Communication communication) {
        // 如果 task 已经 finish，直接返回
        if (communication.isFinished()) {
            return;
        }

        tasks.putIfAbsent(taskId, new TaskCommunication(taskId, communication));
    }

    public void removeTask(Integer taskId) {
        tasks.remove(taskId);
    }

    public void report(Integer taskId, Communication communication) {
        // 如果 task 已经 finish，直接返回
        if (communication.isFinished()) {
            return;
        }
        if (!tasks.containsKey(taskId)) {
            LOG.warn("unexpected: taskId({}) missed.", taskId);
            tasks.putIfAbsent(taskId, new TaskCommunication(taskId, communication));
        } else {
            tasks.get(taskId).report(communication);
        }
    }

    public TaskCommunication getTaskCommunication(Integer taskId) {
        return tasks.get(taskId);
    }

    public static class TaskCommunication {
        private Integer taskId;
        // 记录最后更新的 communication
        private long lastAllReadRecords = -1;
        // 只有第一次，或者统计变更时才会更新 TS
        private long lastUpdateComunicationTS;
        private long ttl;

        private TaskCommunication(Integer taskid, Communication communication) {
            this.taskId = taskid;
            lastAllReadRecords = CommunicationTool.getTotalReadRecords(communication);
            ttl = System.currentTimeMillis();
            lastUpdateComunicationTS = ttl;
        }

        public void report(Communication communication) {
            ttl = System.currentTimeMillis();
            // 采集的数量增长，则变更当前记录, 优先判断这个条件，因为目的是不卡住，而不是 expired
            if (CommunicationTool.getTotalReadRecords(communication) > lastAllReadRecords) {
                lastAllReadRecords = CommunicationTool.getTotalReadRecords(communication);
                lastUpdateComunicationTS = ttl;
            } else if (isExpired(lastUpdateComunicationTS)) {
                communication.setState(State.FAILED);
                communication.setTimestamp(ttl);
                communication.setThrowable(DataXException.asDataXException(CommonErrorCode.TASK_HUNG_EXPIRED,
                        String.format("task(%s) hung expired [allReadRecord(%s), elased(%s)]", taskId, lastAllReadRecords, (ttl - lastUpdateComunicationTS))));
            }
        }

        private boolean isExpired(long lastUpdateCommunicationTS) {
            long EXPIRED_TIME = 172800 * 1000;
            return System.currentTimeMillis() - lastUpdateCommunicationTS > EXPIRED_TIME;
        }

        public Integer getTaskId() {
            return taskId;
        }
    }
}
