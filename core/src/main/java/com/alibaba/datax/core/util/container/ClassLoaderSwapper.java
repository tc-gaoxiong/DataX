package com.alibaba.datax.core.util.container;

/**
 * Created by jingxing on 14-8-29.
 * <p>
 * 为避免 jar 冲突，比如 hbase 可能有多个版本的读写依赖 jar 包，JobContainer 和 TaskGroupContainer
 * 就需要脱离当前 classLoader 去加载这些 jar 包，执行完成后，又退回到原来 classLoader 上继续执行接下来的代码
 */
public final class ClassLoaderSwapper {
    private ClassLoader storeClassLoader = null;

    private ClassLoaderSwapper() {
    }

    public static ClassLoaderSwapper newCurrentThreadClassLoaderSwapper() {
        return new ClassLoaderSwapper();
    }

    /**
     * 保存当前classLoader，并将当前线程的classLoader设置为所给classLoader
     */
    public ClassLoader setCurrentThreadClassLoader(ClassLoader classLoader) {
        this.storeClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(classLoader);
        return this.storeClassLoader;
    }

    /**
     * 将当前线程的类加载器设置为保存的类加载
     */
    public ClassLoader restoreCurrentThreadClassLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(this.storeClassLoader);
        return classLoader;
    }
}
