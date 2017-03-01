/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.tinkerpop.gremlin.structure.util.detached;

import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import org.apache.tinkerpop.gremlin.structure.util.Host;
import org.apache.tinkerpop.gremlin.structure.util.StringFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 * @author Stephen Mallette (http://stephen.genoprime.com)
 */
public class DetachedVertexProperty<V> extends DetachedElement<VertexProperty<V>> implements VertexProperty<V> {

    protected V value;
    protected transient DetachedVertex vertex;

    private DetachedVertexProperty() {
    }

    protected DetachedVertexProperty(final VertexProperty<V> vertexProperty, final boolean withProperties) {
        super(vertexProperty);
        this.value = vertexProperty.value();
        this.vertex = DetachedFactory.detach(vertexProperty.element(), false);

        // only serialize properties if requested, the graph supports it and there are meta properties present.
        // this prevents unnecessary object creation of a new HashMap which will just be empty.  it will use
        // Collections.emptyMap() by default
        if (withProperties && vertexProperty.graph().features().vertex().supportsMetaProperties()) {
            final Iterator<Property<Object>> propertyIterator = vertexProperty.properties();
            if (propertyIterator.hasNext()) {
                this.properties = new HashMap<>();
                propertyIterator.forEachRemaining(property -> this.properties.put(property.key(), Collections.singletonList(DetachedFactory.detach(property))));
            }
        }
    }

    public DetachedVertexProperty(final Object id, final String label, final V value,
                                  final Map<String, Object> properties,
                                  final Vertex vertex) {
        super(id, label);
        this.value = value;
        this.vertex = DetachedFactory.detach(vertex, true);

        if (!properties.isEmpty()) {
            this.properties = new HashMap<>();
            properties.entrySet().iterator().forEachRemaining(entry -> this.properties.put(entry.getKey(), Collections.singletonList(new DetachedProperty<>(entry.getKey(), entry.getValue(), this))));
        }
    }

    /**
     * This constructor is used by GraphSON when deserializing and the {@link Host} is not known.
     */
    public DetachedVertexProperty(final Object id, final String label, final V value,
                                  final Map<String, Object> properties) {
        super(id, label);
        this.value = value;

        if (properties != null && !properties.isEmpty()) {
            this.properties = new HashMap<>();
            properties.entrySet().iterator().forEachRemaining(entry -> this.properties.put(entry.getKey(), Collections.singletonList(new DetachedProperty<>(entry.getKey(), entry.getValue(), this))));
        }
    }

    @Override
    public boolean isPresent() {
        return true;
    }

    @Override
    public String key() {
        return this.label;
    }

    @Override
    public V value() {
        return this.value;
    }

    @Override
    public Vertex element() {
        return this.vertex;
    }

    @Override
    public void remove() {
        throw Property.Exceptions.propertyRemovalNotSupported();
    }

    @Override
    public String toString() {
        return StringFactory.propertyString(this);
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(final Object object) {
        return ElementHelper.areEqual(this, object);
    }

    @Override
    public <U> Iterator<Property<U>> properties(final String... propertyKeys) {
        return (Iterator) super.properties(propertyKeys);
    }
}
