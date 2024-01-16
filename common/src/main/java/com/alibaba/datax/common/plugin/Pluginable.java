package com.alibaba.datax.common.plugin;

import com.alibaba.datax.common.util.Configuration;

public interface Pluginable {
    String getDeveloper();

    String getDescription();

    void setPluginConf(Configuration pluginConf);

    void init();

    void destroy();

    String getPluginName();

    Configuration getPluginJobConf();

    void setPluginJobConf(Configuration jobConf);

    Configuration getPeerPluginJobConf();

    void setPeerPluginJobConf(Configuration peerPluginJobConf);

    public String getPeerPluginName();

    public void setPeerPluginName(String peerPluginName);
}
