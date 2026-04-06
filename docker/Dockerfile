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
