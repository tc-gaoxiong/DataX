package com.alibaba.datax.common;

import com.alibaba.datax.common.util.Configuration;

public class ConfigurationTest {
    public static void main(String[] args) {
        Configuration conf = Configuration.from("{\"a\": {\"b\": {\"c\": [0,1,2,3]}}}");

        Object o = conf.get("a.b.c[0]");
    }
}
