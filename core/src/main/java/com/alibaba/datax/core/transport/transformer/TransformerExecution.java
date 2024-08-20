package com.alibaba.datax.core.transport.transformer;

import com.alibaba.datax.transformer.ComplexTransformer;

import java.util.Map;

/**
 * 每个 func 对应一个实例.
 * Created by liqiang on 16/3/16.
 */
public class TransformerExecution {
  private final TransformerExecutionParas transformerExecutionParas;
  private final TransformerInfo transformerInfo;
  private Object[] finalParas;
  /**
   * 以下是动态统计信息，暂时未用
   */
  private long exaustedTime = 0;
  private long successRecords = 0;
  private long failedRecords = 0;
  private long filterRecords = 0;
  /**
   * 参数采取延迟检查
   */
  private boolean isChecked = false;

  public TransformerExecution(TransformerInfo transformerInfo, TransformerExecutionParas transformerExecutionParas) {
    this.transformerExecutionParas = transformerExecutionParas;
    this.transformerInfo = transformerInfo;
  }

  public void genFinalParas() {
    // groovy 不支持传参
    if (transformerInfo.getTransformer().getTransformerName().equals("dx_groovy")) {
      finalParas = new Object[2];
      finalParas[0] = transformerExecutionParas.getCode();
      finalParas[1] = transformerExecutionParas.getExtraPackage();
      return;
    }
    // 其他 function，按照 columnIndex 和 para 的顺序，如果 columnIndex 为空，跳过 columnIndex
    if (transformerExecutionParas.getColumnIndex() != null) {
      if (transformerExecutionParas.getParas() != null) {
        finalParas = new Object[transformerExecutionParas.getParas().length + 1];
        System.arraycopy(transformerExecutionParas.getParas(), 0, finalParas,
            1, transformerExecutionParas.getParas().length);
      } else {
        finalParas = new Object[1];
      }
      finalParas[0] = transformerExecutionParas.getColumnIndex();
    } else {
      if (transformerExecutionParas.getParas() != null) {
        finalParas = transformerExecutionParas.getParas();
      } else {
        finalParas = null;
      }
    }
  }

  public Object[] getFinalParas() {
    return finalParas;
  }

  public long getExaustedTime() {
    return exaustedTime;
  }

  public long getSuccessRecords() {
    return successRecords;
  }

  public long getFailedRecords() {
    return failedRecords;
  }

  public long getFilterRecords() {
    return filterRecords;
  }

  public void setIsChecked(boolean isChecked) {
    this.isChecked = isChecked;
  }

  public boolean isChecked() {
    return isChecked;
  }

  /**
   * 一些代理方法
   */
  public ClassLoader getClassLoader() {
    return transformerInfo.getClassLoader();
  }

  public Integer getColumnIndex() {
    return transformerExecutionParas.getColumnIndex();
  }

  public String getTransformerName() {
    return transformerInfo.getTransformer().getTransformerName();
  }

  public ComplexTransformer getTransformer() {
    return transformerInfo.getTransformer();
  }

  public Map<String, Object> gettContext() {
    return transformerExecutionParas.gettContext();
  }
}
