package be.elchworks.testdatagenerator.codegen;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.HashMap;
import java.util.Map;

/**
 * A compiled record type generated from a JSON schema, offering a dynamic builder
 * that sets properties by name and produces instances of the generated type.
 */
public final class GeneratedType {

    private final Class<?> type;

    GeneratedType(Class<?> type) {
        this.type = type;
    }

    public Builder builder() {
        return new Builder(type);
    }

    public static final class Builder {

        private final Class<?> type;
        private final Map<String, Object> values = new HashMap<>();

        private Builder(Class<?> type) {
            this.type = type;
        }

        public Builder set(String property, Object value) {
            values.put(property, value);
            return this;
        }

        public Object build() {
            RecordComponent[] components = type.getRecordComponents();
            Class<?>[] parameterTypes = new Class<?>[components.length];
            Object[] arguments = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                parameterTypes[i] = components[i].getType();
                arguments[i] = values.get(components[i].getName());
            }
            return instantiate(parameterTypes, arguments);
        }

        private Object instantiate(Class<?>[] parameterTypes, Object[] arguments) {
            try {
                Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
                constructor.setAccessible(true);
                return constructor.newInstance(arguments);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot instantiate " + type.getName(), e);
            }
        }
    }
}
