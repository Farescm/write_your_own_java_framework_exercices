package org.github.forax.framework.interceptor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.stream.Stream;

public final class InterceptorRegistry {
    private AroundAdvice advice;

    private final HashMap<Class<?>, List<AroundAdvice>> adviceMap = new HashMap<>();

    public void addAroundAdvice(Class<? extends Annotation> annotationClass, AroundAdvice advice) {
        Objects.requireNonNull(annotationClass);
        Objects.requireNonNull(advice);
        adviceMap
                .computeIfAbsent(annotationClass, _ -> new ArrayList<>())
                .add(advice);
    }

    private List<AroundAdvice> findAllAdvices(Method method) {
        return Arrays.stream(method.getAnnotations())
                .flatMap(annotation ->
                {
                    var advice = adviceMap.getOrDefault(annotation.annotationType(), List.of());
                    return advice.stream();
                })
                .toList();
    }

    public <T> T createProxy(Class<T> type, T instance) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(instance);
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type},
                (proxy, method, args) -> {
                    var advices = findAllAdvices(method);
                    for (var advice : advices) {
                        advice.before(instance, method, args);
                    }
                    Object result = null;
                    try {
                        result = Utils.invokeMethod(instance, method, args);
                    } finally {
                        for (var advice : advices.reversed()) {
                            advice.after(instance, method, args, result);
                        }
                    }
                    return result;
                }));
    }
}