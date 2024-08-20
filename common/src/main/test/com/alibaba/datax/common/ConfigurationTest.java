package com.alibaba.datax.common;

import com.alibaba.datax.common.util.Configuration;

import java.util.Set;

public class ConfigurationTest {
  public static void main(String[] args) {
    Configuration conf = Configuration.from("{\n" +
        "    \"job\":{\n" +
        "        \"setting\":{\n" +
        "            \"speed\":{\n" +
        "                \"channel\":3\n" +
        "            }\n" +
        "        },\n" +
        "        \"content\":[\n" +
        "            {\n" +
        "                \"reader\":{\n" +
        "                    \"name\":\"hdfsreader\",\n" +
        "                    \"parameter\":{\n" +
        "                        \"path\":\"/user/hive/warehouse/mytable01/*\",\n" +
        "                        \"defaultFS\":\"hdfs://xxx:port\",\n" +
        "                        \"column\":[\n" +
        "                            {\n" +
        "                                \"index\":0,\n" +
        "                                \"type\":\"long\"\n" +
        "                            },\n" +
        "                            {\n" +
        "                                \"index\":1,\n" +
        "                                \"type\":\"boolean\"\n" +
        "                            },\n" +
        "                            {\n" +
        "                                \"type\":\"string\",\n" +
        "                                \"value\":\"hello\"\n" +
        "                            },\n" +
        "                            {\n" +
        "                                \"index\":2,\n" +
        "                                \"type\":\"double\"\n" +
        "                            }\n" +
        "                        ],\n" +
        "                        \"fileType\":\"orc\",\n" +
        "                        \"encoding\":\"UTF-8\",\n" +
        "                        \"fieldDelimiter\":\",\",\n" +
        "                        \"hadoopConfig\":{\n" +
        "                            \"dfs.nameservices\":\"testDfs\",\n" +
        "                            \"dfs.ha.namenodes.testDfs\":\"namenode1,namenode2\",\n" +
        "                            \"dfs.namenode.rpc-address.aliDfs.namenode1\":\"\",\n" +
        "                            \"dfs.namenode.rpc-address.aliDfs.namenode2\":\"\",\n" +
        "                            \"dfs.client.failover.proxy.provider.testDfs\":\"org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider\"\n" +
        "                        }\n" +
        "                    }\n" +
        "                },\n" +
        "                \"writer\":{\n" +
        "                    \"name\":\"streamwriter\",\n" +
        "                    \"parameter\":{\n" +
        "                        \"print\":true\n" +
        "                    }\n" +
        "                }\n" +
        "            }\n" +
        "        ]\n" +
        "    }\n" +
        "}");

    Object o = conf.get("a.b.c[0]");

    Set<String> keys = conf.getKeys();
    for (String key : keys) {
      System.out.println(key);
    }
  }
}
