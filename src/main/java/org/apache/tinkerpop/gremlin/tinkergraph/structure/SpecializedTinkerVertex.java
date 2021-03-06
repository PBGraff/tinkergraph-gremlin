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
package org.apache.tinkerpop.gremlin.tinkergraph.structure;

import org.apache.tinkerpop.gremlin.structure.*;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import org.apache.tinkerpop.gremlin.util.iterator.IteratorUtils;

import java.util.*;

public abstract class SpecializedTinkerVertex<IdType> extends TinkerVertex {

    private final Set<String> specificKeys;

    protected SpecializedTinkerVertex(IdType id, String label, TinkerGraph graph, Set<String> specificKeys) {
        super(id, label, graph);
        this.specificKeys = specificKeys;
    }

    @Override
    public Set<String> keys() {
        return specificKeys;
    }

    @Override
    public <V> VertexProperty<V> property(String key) {
        return specificProperty(key);
    }

    /* implement in concrete specialised instance to avoid using generic HashMaps */
    protected abstract <V> VertexProperty<V> specificProperty(String key);

    @Override
    public <V> Iterator<VertexProperty<V>> properties(String... propertyKeys) {
        if (propertyKeys.length == 0) { // return all properties
            return (Iterator) specificKeys.stream().map(key -> property(key)).filter(vp -> vp.isPresent()).iterator();
        } else if (propertyKeys.length == 1) { // treating as special case for performance
            VertexProperty<V> ret = property(propertyKeys[0]);
            if (ret.isPresent()) {
                return IteratorUtils.of(ret);
            } else {
                return Collections.emptyIterator();
            }
        } else {
            return Arrays.stream(propertyKeys).map(key -> (VertexProperty<V>) property(key)).filter(vp -> vp.isPresent()).iterator();
        }
    }

    @Override
    public <V> VertexProperty<V> property(VertexProperty.Cardinality cardinality, String key, V value, Object... keyValues) {
        return updateSpecificProperty(key, value);
    }

    protected abstract <V> VertexProperty<V> updateSpecificProperty(String key, V value);

    @Override
    public Edge addEdge(String label, Vertex vertex, Object... keyValues) {
        if (null == vertex) {
            throw Graph.Exceptions.argumentCanNotBeNull("inVertex");
        }
        if (this.removed) {
            throw elementAlreadyRemoved(Vertex.class, this.id);
        }

        if (graph.specializedEdgeFactoryByLabel.containsKey(label)) {
            SpecializedElementFactory.ForEdge factory = graph.specializedEdgeFactoryByLabel.get(label);
            Object idValue = graph.edgeIdManager.convert(ElementHelper.getIdValue(keyValues).orElse(null));
            if (null != idValue) {
                if (graph.edges.containsKey(idValue)) {
                    throw Graph.Exceptions.edgeWithIdAlreadyExists(idValue);
                }
            } else {
                idValue = graph.edgeIdManager.getNextId(graph);
            }

            ElementHelper.legalPropertyKeyValueArray(keyValues);
            TinkerVertex inVertex = (TinkerVertex) vertex;
            TinkerVertex outVertex = this;
            SpecializedTinkerEdge edge = factory.createEdge(idValue, outVertex, inVertex, ElementHelper.asMap(keyValues));
            graph.edges.put(idValue, edge);

            // TODO: allow to connect non-specialised vertices with specialised edges and vice versa
            this.addSpecializedOutEdge(edge);
            ((SpecializedTinkerVertex) inVertex).addSpecializedInEdge(edge);
            return edge;
        } else { // edge label not registered for a specialized factory, treating as generic edge
            if (graph.usesSpecializedElements) {
                throw new IllegalArgumentException(
                    "this instance of TinkerGraph uses specialized elements, but doesn't have a factory for label " + label
                        + ". Mixing specialized and generic elements is not (yet) supported");
            }
            return super.addEdge(label, vertex, keyValues);
        }
    }

    protected abstract void addSpecializedOutEdge(Edge edge);

    protected abstract void addSpecializedInEdge(Edge edge);

    @Override
    public Iterator<Edge> edges(final Direction direction, final String... edgeLabels) {
        return specificEdges(direction, edgeLabels);
    }

    /* implement in concrete specialised instance to avoid using generic HashMaps */
    protected abstract Iterator<Edge> specificEdges(final Direction direction, final String... edgeLabels);

    @Override
    public Iterator<Vertex> vertices(final Direction direction, final String... edgeLabels) {
        Iterator<Edge> edges = specificEdges(direction, edgeLabels);
        if (direction == Direction.IN) {
            return IteratorUtils.map(edges, Edge::outVertex);
        } else if (direction == Direction.OUT) {
            return IteratorUtils.map(edges, Edge::inVertex);
        } else if (direction == Direction.BOTH) {
            return IteratorUtils.flatMap(edges, Edge::bothVertices);
        } else {
            return Collections.emptyIterator();
        }
    }

    public void removeOutEdge(Edge edge) {
        removeSpecificOutEdge(edge);
    }

    protected abstract void removeSpecificOutEdge(Edge edge);

    public void removeInEdge(Edge edge) {
        removeSpecificInEdge(edge);
    }

    protected abstract void removeSpecificInEdge(Edge edge);
}
