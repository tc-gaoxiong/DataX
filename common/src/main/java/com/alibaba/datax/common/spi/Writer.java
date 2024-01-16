package com.alibaba.datax.common.spi;

import com.alibaba.datax.common.base.BaseObject;
import com.alibaba.datax.common.plugin.AbstractJobPlugin;
import com.alibaba.datax.common.plugin.AbstractTaskPlugin;
import com.alibaba.datax.common.plugin.RecordReceiver;
import com.alibaba.datax.common.util.Configuration;

import java.util.List;

/**
 * 每个 Writer 插件需要实现 Writer 类，并在其内部实现 Job、Task 两个内部类。
 */
public abstract class Writer extends BaseObject {
    /**
     * 每个 Writer 插件必须实现 Job 内部类
     */
    public abstract static class Job extends AbstractJobPlugin {
        /**
         * 切分任务，为了做到 Reader、Writer任务数对等，这里要求Writer插件必须按照源端的切分数进行切分。否则框架报错！ <br>
         */
        public abstract List<Configuration> split(int mandatoryNumber);
    }

    /**
     * 每个 Writer 插件必须实现 Task 内部类
     */
    public abstract static class Task extends AbstractTaskPlugin {
        public abstract void startWrite(RecordReceiver lineReceiver);

        public boolean supportFailOver() {
            return false;
        }
    }
}
