package com.alibaba.datax.core.taskgroup.runner;

import com.alibaba.datax.common.exception.DataXException;
import com.alibaba.datax.core.taskgroup.TaskGroupContainer;
import com.alibaba.datax.core.util.FrameworkErrorCode;
import com.alibaba.datax.dataxservice.face.domain.enums.State;

/**
 * 任务组容器执行器线程类
 */
public class TaskGroupContainerRunner implements Runnable {
    // 每个 runner 持有一个 container
    private final TaskGroupContainer taskGroupContainer;

    private State state;

    public TaskGroupContainerRunner(TaskGroupContainer taskGroup) {
        this.taskGroupContainer = taskGroup;
        this.state = State.SUCCEEDED;
    }

    @Override
    public void run() {
        try {
            // 设置 runner 线程名称
            Thread.currentThread().setName(
                    String.format("taskGroup-%d", this.taskGroupContainer.getTaskGroupId()));
            // 启动容器
            this.taskGroupContainer.start();
            this.state = State.SUCCEEDED;
        } catch (Throwable e) {
            this.state = State.FAILED;
            throw DataXException.asDataXException(FrameworkErrorCode.RUNTIME_ERROR, e);
        }
    }

    public TaskGroupContainer getTaskGroupContainer() {
        return taskGroupContainer;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }
}
