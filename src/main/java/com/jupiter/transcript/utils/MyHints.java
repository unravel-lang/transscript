package com.jupiter.transcript.utils;

import kotlin.internal.AccessibleLateinitPropertyLiteral;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.ResolvableType;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

import java.util.Set;

public class MyHints implements RuntimeHintsRegistrar {
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        // 使用 Spring 自己的扫描器，它对 Jar 包的支持非常成熟
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter((t, d) -> true);
        // 执行扫描
        Set<BeanDefinition> components = scanner.findCandidateComponents("com.tencentcloudapi.tmt.v20180321.models");
        reflectionReg(hints, classLoader, components);
        // 执行扫描
        Set<BeanDefinition> components1 = scanner.findCandidateComponents("com.tencentcloudapi.common");
        reflectionReg(hints, classLoader, components1);
        // 执行扫描
//        Set<BeanDefinition> components2 = scanner.findCandidateComponents("com.github.sardine");
//        reflectionReg(hints, classLoader, components2);
        // 执行扫描
        Set<BeanDefinition> components3 = scanner.findCandidateComponents("org.apache.tools.an");
        reflectionReg(hints, classLoader, components3);

    }

    private static void reflectionReg(RuntimeHints hints, ClassLoader classLoader, Set<BeanDefinition> components) {
        for (BeanDefinition component : components) {
            String beanClassName = component.getBeanClassName();
            if (beanClassName == null) {
                continue;
            }
            try {
                Class<?> clazz = ClassUtils.forName(beanClassName, classLoader);
                System.out.println("Registering for reflection: " + beanClassName);

                hints.reflection()
                        .registerType(clazz,
                                builder -> builder.withMembers(MemberCategory.INVOKE_PUBLIC_METHODS,
                                        MemberCategory.ACCESS_DECLARED_FIELDS,
                                MemberCategory.UNSAFE_ALLOCATED));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }
}