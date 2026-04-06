# DataX Docker 镜像构建与使用指南

## 1. 概述

DataX 镜像采用多阶段构建：编译阶段使用 Maven + JDK 8 打包，运行阶段使用 JRE + Python 3。镜像入口为 `python3 bin/datax.py`，所有运行参数通过命令行传入。`DATAX_HOME` 约定为 `/datax`，包含 `bin/`（脚本）、`lib/`（核心 JAR）、`plugin/`（Reader/Writer 插件）、`conf/`（core.json、logback.xml）、`log/` 和 `log_perf/` 目录。

## 2. 构建策略

### 2.1 多阶段构建

使用 Docker 多阶段构建，将编译阶段（Maven + JDK 8）与运行阶段（JRE + Python 3）分离，产出包含完整 DataX 的最终镜像。

```dockerfile
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-8 AS builder

WORKDIR /datax-build

COPY docker/settings.xml /usr/share/maven/ref/settings.xml
COPY . .

RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:8-jre

RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 \
    tzdata \
    && ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && rm -rf /var/lib/apt/lists/*

ENV DATAX_HOME=/datax \
    PYTHONIOENCODING=utf-8

RUN groupadd -g 65532 datax && \
    useradd -u 65532 -g datax -m datax

COPY --from=builder /datax-build/packaging/target/datax-bin/datax ${DATAX_HOME}

RUN mkdir -p ${DATAX_HOME}/log ${DATAX_HOME}/log_perf ${DATAX_HOME}/tmp && \
    chown -R datax:datax ${DATAX_HOME}

WORKDIR ${DATAX_HOME}

USER datax

ENTRYPOINT ["python3", "bin/datax.py"]
```

构建命令（从 DataX 根目录执行，需配合 `.dockerignore` 排除构建产物）：

```bash
docker build -t datax:local -f docker/Dockerfile .
```

## 3. 运行环境配置

### 3.1 入口参数

| 参数 | 简写 | 默认值 | 说明 |
|------|------|--------|------|
| `--jvm` | `-j` | `-Xms1g -Xmx1g` | JVM 堆内存参数 |
| `--jobid` | | `-1` | 作业唯一 ID |
| `--mode` | `-m` | `standalone` | 运行模式 |
| `--params` | `-p` | | 参数替换，格式 `-Dkey=value` |
| `--reader` | `-r` | | 查看 Reader 作业模板 |
| `--writer` | `-w` | | 查看 Writer 作业模板 |
| `--debug` | `-d` | | 开启远程调试（端口 9999） |
| `--loglevel` | | `info` | 日志级别 |

### 3.2 参数传递方式

作业文件通过 `docker run` 命令行参数传递给容器。`ENTRYPOINT` 为 `python3 bin/datax.py`，所有命令行参数直接追加到入口命令后。

**作业文件挂载**

```bash
docker run --rm \
  -v /path/to/job.json:/datax/job/job.json \
  datax:local /datax/job/job.json
```

**JVM 内存**（`-j`）

```bash
docker run --rm \
  -v /path/to/job.json:/datax/job/job.json \
  datax:local /datax/job/job.json -j"-Xms2g -Xmx4g"
```

**作业参数替换**（`-p`）

```bash
docker run --rm \
  -v /path/to/job.json:/datax/job/job.json \
  datax:local /datax/job/job.json -p"-DtableName=orders -Ddate=20240101"
```

## 4. 日志与监控

### 4.1 标准输出

日志输出到容器 stdout/stderr，通过 `docker logs` 查看：

```bash
docker logs -f <container_id>
```

### 4.2 文件日志

将 `log/` 和 `log_perf/` 目录挂载到宿主机，实现持久化。日志写入 `{ymd}/{log.file.name}-{HH_mm_ss.SSS}.log`，`{log.file.name}` 默认取作业文件名（不含扩展名），多次运行自动按时间戳区分。

运行示例：

```bash
docker run --rm \
  -v ./core/src/main/job/job.json:/datax/job/job.json \
  -v ./log:/datax/log \
  datax:local /datax/job/job.json
```

自定义日志文件名：

```bash
docker run --rm \
  -v ./core/src/main/job/job.json:/datax/job/job.json \
  -v ./log:/datax/log \
  datax:local /datax/job/job.json \
  -p"-Dlog.file.name=my_sync_job"
```
