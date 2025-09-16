package com.alibaba.datax.common.element;

import java.util.Map;

/**
 * Created by jingxing on 14-8-24.
 */
public interface DataRecord {
  void addColumn(Column column);

  void setColumn(int i, final Column column);

  Column getColumn(int i);

  String toString();

  int getColumnNumber();

  int getByteSize();

  int getMemorySize();

  Map<String, String> getMeta();

  void setMeta(Map<String, String> meta);

}
