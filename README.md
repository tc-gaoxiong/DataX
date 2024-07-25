# DataX

基于 DataX 202309 版本代码，用来自己学习 Java，特别是多线程、JVM 知识。

todo:
1. datax on k8s (本来计划 on yarn 的，但是干脆一步到位吧)

打包：
```bash
mvn clean package -DskipTests
```

debug:
```bash
python ./bin/datax.py ./job/job.json -d
```