package dev.oreo.modulith.core;

import java.lang.reflect.*;
import java.util.*;

/** Immutable validated snapshot of the module YAML backed by a Java record. */
public final class TypedModuleConfiguration {
    private TypedModuleConfiguration() {}

    public static <T extends Record> T read(ModuleConfiguration source, Class<T> type) {
        Objects.requireNonNull(source);
        if (!type.isRecord()) throw new IllegalArgumentException("Configuration type must be a record");
        RecordComponent[] fields = type.getRecordComponents();
        Class<?>[] signature = new Class<?>[fields.length];
        Object[] args = new Object[fields.length];
        for (int i = 0; i < fields.length; i++) {
            RecordComponent field = fields[i];
            signature[i] = field.getType();
            ConfigKey path = field.getAnnotation(ConfigKey.class);
            String key = path == null ? field.getName() : path.value();
            Object value = source.get(key);
            if (value == null) throw new ModulithException("Missing configuration key '" + key +
                    "' for " + type.getName());
            args[i] = convert(key, value, field.getType());
            ConfigRange range = field.getAnnotation(ConfigRange.class);
            if (range != null) {
                if (!(args[i] instanceof Number number)) throw new ModulithException(
                        "@ConfigRange requires numeric component " + key);
                if (number.doubleValue() < range.min() || number.doubleValue() > range.max()) {
                    throw new ModulithException("Configuration key '" + key + "' out of range [" +
                            range.min() + ", " + range.max() + "]");
                }
            }
        }
        try {
            Constructor<T> ctor = type.getDeclaredConstructor(signature);
            ctor.setAccessible(true);
            return ctor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new ModulithException("Cannot instantiate configuration record " + type.getName(), e);
        }
    }

    private static Object convert(String key, Object value, Class<?> target) {
        if (target.isInstance(value)) return value;
        try {
            if (target == String.class) return String.valueOf(value);
            if (target == int.class || target == Integer.class) return Integer.parseInt(value.toString());
            if (target == long.class || target == Long.class) return Long.parseLong(value.toString());
            if (target == double.class || target == Double.class) return Double.parseDouble(value.toString());
            if (target == boolean.class || target == Boolean.class) {
                String literal = value.toString();
                if (!literal.equalsIgnoreCase("true") && !literal.equalsIgnoreCase("false"))
                    throw new IllegalArgumentException("Not a boolean: " + literal);
                return Boolean.parseBoolean(literal);
            }
            if (target.isEnum()) {
                @SuppressWarnings({"rawtypes", "unchecked"})
                Object result = Enum.valueOf((Class<? extends Enum>) target, value.toString());
                return result;
            }
        } catch (IllegalArgumentException e) {
            throw new ModulithException("Invalid configuration '" + key + "': " + value, e);
        }
        throw new ModulithException("Unsupported configuration type " + target.getName() + " for " + key);
    }
}
