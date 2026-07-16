package com.actionforward.atree;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * An event: a set of attribute-value pairs (paper Sec. 3.1). Attribute values can be bound
 * statically, or lazily through a {@link Supplier} registered per attribute. A supplier is
 * invoked at most once per event — and only if the index actually holds a predicate that
 * references its attribute — then its result is memoized. A supplier returning {@code null}
 * marks the attribute as absent (predicate evaluation result {@code undefined}).
 *
 * <p>Instances are not thread-safe while being matched, because of supplier memoization.
 */
public final class Event {

    private final Map<String, Object> resolved;
    private final Map<String, Supplier<?>> suppliers;

    private Event(Map<String, Object> values, Map<String, Supplier<?>> suppliers) {
        this.resolved = new HashMap<>(values);
        this.suppliers = Map.copyOf(suppliers);
    }

    /** Convenience factory for a fully static event: {@code Event.of("age", 17, "city", "paris")}. */
    public static Event of(Object... attrValuePairs) {
        if (attrValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("expected an even number of arguments (attr, value, attr, value, ...)");
        }
        Builder builder = builder();
        for (int i = 0; i < attrValuePairs.length; i += 2) {
            if (!(attrValuePairs[i] instanceof String attr)) {
                throw new IllegalArgumentException("attribute name at position " + i + " is not a String");
            }
            builder.with(attr, attrValuePairs[i + 1]);
        }
        return builder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** All attribute names carried by this event (statically bound and supplier-backed). */
    public Set<String> attributes() {
        Set<String> names = new java.util.LinkedHashSet<>(resolved.keySet());
        names.addAll(suppliers.keySet());
        return names;
    }

    /**
     * The value bound to an attribute, invoking and memoizing its supplier on first access.
     * Returns {@code null} when the attribute is absent from this event.
     */
    public Object value(String attr) {
        if (resolved.containsKey(attr)) {
            return resolved.get(attr);
        }
        Supplier<?> supplier = suppliers.get(attr);
        if (supplier == null) {
            return null;
        }
        Object value = supplier.get();
        resolved.put(attr, value); // memoize, including null results
        return value;
    }

    @Override
    public String toString() {
        return "Event" + resolved + (suppliers.isEmpty() ? "" : " + suppliers" + suppliers.keySet());
    }

    public static final class Builder {

        private final Map<String, Object> values = new LinkedHashMap<>();
        private final Map<String, Supplier<?>> suppliers = new LinkedHashMap<>();

        private Builder() {
        }

        /** Binds a static value to an attribute. */
        public Builder with(String attr, Object value) {
            Objects.requireNonNull(attr, "attr");
            Objects.requireNonNull(value, "value");
            requireFresh(attr);
            values.put(attr, value);
            return this;
        }

        /** Binds an attribute to a supplier computing its value on demand. */
        public Builder supply(String attr, Supplier<?> supplier) {
            Objects.requireNonNull(attr, "attr");
            Objects.requireNonNull(supplier, "supplier");
            requireFresh(attr);
            suppliers.put(attr, supplier);
            return this;
        }

        /** Registers every entry of the map as a supplier-backed attribute. */
        public Builder supplyAll(Map<String, Supplier<?>> attrSuppliers) {
            attrSuppliers.forEach(this::supply);
            return this;
        }

        private void requireFresh(String attr) {
            if (values.containsKey(attr) || suppliers.containsKey(attr)) {
                throw new IllegalArgumentException("attribute already bound: " + attr);
            }
        }

        public Event build() {
            return new Event(values, suppliers);
        }
    }
}
