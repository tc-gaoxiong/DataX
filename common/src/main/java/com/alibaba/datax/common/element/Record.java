package com.alibaba.datax.common.element;

import java.util.Map;

/**
 * Created by jingxing on 14-8-24.
 */

public interface Record {
    public void addColumn(Column column);

    public void setColumn(int i, final Column column);

    public Column getColumn(int i);

    public String toString();

    public int getColumnNumber();

    public int getByteSize();

    public int getMemorySize();

    public Map<String, String> getMeta();

    public void setMeta(Map<String, String> meta);

}
