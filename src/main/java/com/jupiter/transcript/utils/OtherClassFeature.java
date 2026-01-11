package com.jupiter.transcript.utils;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

public class OtherClassFeature implements Feature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        try (ScanResult scan = new ClassGraph()
                .enableAllInfo()
                .acceptPackages(
                "com.jupiter.transcript")
                .enableExternalClasses()
                .overrideClassLoaders(Thread.currentThread().getContextClassLoader())
                .scan()) {
            System.out.println("扫描数量" + scan.getAllClasses().size());
            for (ClassInfo scanResult : scan.getAllClasses()) {
                // 获取指定类的信息

                System.out.println("类名: " + scanResult.getName());
                System.out.println("Registering for reflection: " + scanResult.getName());
                Class<?> aClass = scanResult.loadClass();
                RuntimeReflection.register(aClass);
                RuntimeReflection.registerForReflectiveInstantiation(aClass);
                RuntimeReflection.register(aClass.getDeclaredFields());
                RuntimeReflection.register(aClass.getDeclaredMethods());
            }
        }



    }


}