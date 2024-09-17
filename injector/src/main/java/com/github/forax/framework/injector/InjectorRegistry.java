package com.github.forax.framework.injector;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.List;

public final class InjectorRegistry {
    private final HashMap<Class<?>, Supplier<?>> registry = new HashMap<>();


    public <T> void registerInstance(Class<T> type, T instance){
        Objects.requireNonNull(type, "type is null");
        Objects.requireNonNull(instance, "instance is null");
        registerProvider(type, () -> instance);
    }

    public <T> void registerProvider(Class<T> type, Supplier<T> supplier){
        Objects.requireNonNull(type, "type is null");
        Objects.requireNonNull(supplier, "supplier is null");
        var oldValue = registry.putIfAbsent(type, supplier);
        if(oldValue != null){
            throw new IllegalStateException("already a configuration for " + type.getName()); /* L'état de l'object qui n'est pas bon */
        }
    }

    private Constructor<?> findInjectableConstructor(Class<?> type){
        var constructors = Arrays.stream(type.getConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(Inject.class))
                .toList();
        return switch (constructors.size()){
            case 0 -> Utils.defaultConstructor(type);
            case 1 -> constructors.getFirst();
            default -> throw new IllegalStateException("more than one costructor annotated with @inject");
        };
    }
    public <T> void registerProviderClass(Class<T> type, Class<? extends T> implementation){
        Objects.requireNonNull(type, "type is null");
        Objects.requireNonNull(implementation, "providerClass is null");
        var constructor = findInjectableConstructor(implementation);
        var properties = findInjectableProperties(implementation);
        registerProvider(type, () -> {
            var args = Arrays.stream(constructor.getParameterTypes())
                    .map(this::lookupInstance)
                    .toArray();
            var instance = Utils.newInstance(constructor, args);
            for(var property : properties){
                var propertyType = property.getPropertyType();
                var value = lookupInstance(propertyType);
                Utils.invokeMethod(instance, property.getWriteMethod(), value);
            }
            return implementation.cast(instance);
        });
    }

    public <T> T lookupInstance(Class<T> type){
        Objects.requireNonNull(type,"type is null");
        var supplier = registry.get(type);
        if(supplier == null){
            throw new IllegalStateException("no configuration for " + type.getName());
        }
        return type.cast(supplier.get());
    }

    static List<PropertyDescriptor> findInjectableProperties(Class<?> type){
    var beanInfo = Utils.beanInfo(type);
    return Arrays.stream(beanInfo.getPropertyDescriptors())
            .filter(property -> !property.getName().equals("class"))
            .filter(property -> {
                var setter = property.getWriteMethod();
                if(setter == null){
                    return false;
                }
                return setter.isAnnotationPresent(Inject.class);
            })
            .toList();
    }


}