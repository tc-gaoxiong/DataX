package com.alibaba.datax.common.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StrUtil {
  private static final Logger LOG = LoggerFactory.getLogger(StrUtil.class);

  private final static long KB_IN_BYTES = 1024;

  private final static long MB_IN_BYTES = 1024 * KB_IN_BYTES;

  private final static long GB_IN_BYTES = 1024 * MB_IN_BYTES;

  private final static long TB_IN_BYTES = 1024 * GB_IN_BYTES;

  private final static DecimalFormat df = new DecimalFormat("0.00");

  // 匹配以 $ 开始，后跟一个单词字符（字母、数字、下划线），{} 是可选的
  // \\{?，{ 有特殊含义，所以需要转义，? 表示 0 或 1 次匹配
  // + 表示匹配前面的元素一次或多次
  private static final Pattern VARIABLE_PATTERN = Pattern.compile("(\\$)\\{?(\\w+)}?");

  private static String SYSTEM_ENCODING = System.getProperty("file.encoding");

  static {
    if (SYSTEM_ENCODING == null) {
      SYSTEM_ENCODING = "UTF-8";
    }
  }

  private StrUtil() {
  }

  public static String stringify(long byteNumber) {
    if (byteNumber / TB_IN_BYTES > 0) {
      return df.format((double) byteNumber / (double) TB_IN_BYTES) + "TB";
    } else if (byteNumber / GB_IN_BYTES > 0) {
      return df.format((double) byteNumber / (double) GB_IN_BYTES) + "GB";
    } else if (byteNumber / MB_IN_BYTES > 0) {
      return df.format((double) byteNumber / (double) MB_IN_BYTES) + "MB";
    } else if (byteNumber / KB_IN_BYTES > 0) {
      return df.format((double) byteNumber / (double) KB_IN_BYTES) + "KB";
    } else {
      return byteNumber + "B";
    }
  }

  /**
   * 替换变量，将 ${var}、$var，替换为系统变量中的值
   */
  public static String replaceVariable(final String param) {
    Map<String, String> mapping = new HashMap<>();

    // 匹配类似 ${var}、$var 的字符串
    Matcher matcher = VARIABLE_PATTERN.matcher(param);
    // 搜索字符串
    while (matcher.find()) {
      String variable = matcher.group(2); // ${var} 中的 var
      // 获取系统属性
      String value = System.getProperty(variable);
      LOG.debug("get property from system: " + variable);

      if (StringUtils.isBlank(value)) {
        value = matcher.group();
      }

      mapping.put(matcher.group(), value);
    }

    String retString = param;
    for (final String key : mapping.keySet()) {
      retString = retString.replace(key, mapping.get(key));
    }

    return retString;
  }

  public static String compressMiddle(String s, int headLength, int tailLength) {
    Validate.notNull(s, "Input string must not be null");
    Validate.isTrue(headLength > 0, "Head length must be larger than 0");
    Validate.isTrue(tailLength > 0, "Tail length must be larger than 0");

    if (headLength + tailLength >= s.length()) {
      return s;
    }
    return s.substring(0, headLength) + "..." + s.substring(s.length() - tailLength);
  }

  public static String getMd5(String plainText) {
    try {
      StringBuilder builder = new StringBuilder();
      for (byte b : MessageDigest.getInstance("MD5").digest(plainText.getBytes())) {
        int i = b & 0xff;
        if (i < 0x10) {
          builder.append('0');
        }
        builder.append(Integer.toHexString(i));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

}
