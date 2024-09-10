package com.github.forax.framework.mapper;

import java.beans.BeanInfo;
import java.beans.PropertyDescriptor;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;

public final class JSONWriter {

    private static final ClassValue<List<Generator>> BEAN_INFO_CLASS_VALUE = new ClassValue<>() {
        @Override
        protected List<Generator> computeValue(Class<?> type) {
            return Arrays.stream(Utils.beanInfo(type).getPropertyDescriptors())
                    .filter(property -> !property.getName().equals("class"))
                    .<Generator>map(property ->{
                        var getter = property.getReadMethod();
                        var annotation = getter.getAnnotation(JSONProperty.class);
                        var name = annotation == null ? property.getName() : annotation.value();
                        var prefix = '"' + name + '"' + ": ";
                        return (writer, bean) -> {
                            return  prefix + writer.toJSON( Utils.invokeMethod(bean, getter));
                        };
                    }).toList();
        }
    };
    @FunctionalInterface
    private interface Generator {
        String generate(JSONWriter writer, Object bean);
    }
    public String toJSON(Object o) {

        return switch (o) {
            case null -> "null";
            case Boolean b -> ""+b;
            case Integer i -> ""+i;
            case Double d -> ""+d;
            case String s -> '"' + s + '"';
            default -> {
                var generators = BEAN_INFO_CLASS_VALUE.get(o.getClass());
                yield generators.stream()
                        .map(generator -> generator.generate(this, o))
                        .collect(joining(", ","{", "}"));
//                return '"' + name + '"' + ": " + toJSON( Utils.invokeMethod(o, getter));
            }
        };
    }
}
