/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.tinkerpop.gremlin.process.traversal;

import org.apache.tinkerpop.gremlin.process.traversal.strategy.TraversalStrategyProxy;
import org.apache.tinkerpop.gremlin.structure.util.StringFactory;
import org.apache.tinkerpop.gremlin.util.iterator.IteratorUtils;

import java.io.Serializable;
import java.util.*;

/**
 * When a {@link TraversalSource} is manipulated and then a {@link Traversal} is spawned and mutated, a language
 * agnostic representation of those mutations is recorded in a bytecode instance. Bytecode is simply a list
 * of ordered instructions where an instruction is a string operator and a (flattened) array of arguments.
 * Bytecode is used by {@link Translator} instances which are able to translate a traversal in one language to another
 * by analyzing the bytecode as opposed to the Java traversal object representation on heap.
 * <p>
 * Bytecode can be serialized between environments and machines by way of a GraphSON representation.
 * Thus, Gremlin-Python can create bytecode in Python and ship it to Gremlin-Java for evaluation in Java.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class Bytecode implements Cloneable, Serializable {

    private static final Object[] EMPTY_ARRAY = new Object[]{};

    private List<Instruction> sourceInstructions = new ArrayList<>();
    private List<Instruction> stepInstructions = new ArrayList<>();

    /**
     * Add a {@link TraversalSource} instruction to the bytecode.
     *
     * @param sourceName the traversal source method name (e.g. withSack())
     * @param arguments  the traversal source method arguments
     */
    public void addSource(final String sourceName, final Object... arguments) {
        if (sourceName.equals(TraversalSource.Symbols.withoutStrategies)) {
            final Class<TraversalStrategy>[] classes = new Class[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                classes[i] = arguments[i] instanceof TraversalStrategyProxy ?
                        ((TraversalStrategyProxy) arguments[i]).getStrategyClass() :
                        (Class) arguments[i];
            }
            this.sourceInstructions.add(new Instruction(sourceName, classes));
        } else
            this.sourceInstructions.add(new Instruction(sourceName, flattenArguments(arguments)));
        Bindings.clear();
    }

    /**
     * Add a {@link Traversal} instruction to the bytecode.
     *
     * @param stepName  the traversal method name (e.g. out())
     * @param arguments the traversal method arguments
     */
    public void addStep(final String stepName, final Object... arguments) {
        this.stepInstructions.add(new Instruction(stepName, flattenArguments(arguments)));
        Bindings.clear();
    }

    /**
     * Get the {@link TraversalSource} instructions associated with this bytecode.
     *
     * @return an iterable of instructions
     */
    public Iterable<Instruction> getSourceInstructions() {
        return this.sourceInstructions;
    }

    /**
     * Get the {@link Traversal} instructions associated with this bytecode.
     *
     * @return an iterable of instructions
     */
    public Iterable<Instruction> getStepInstructions() {
        return this.stepInstructions;
    }

    /**
     * Get both the {@link TraversalSource} and {@link Traversal} instructions of this bytecode.
     * The traversal source instructions are provided prior to the traversal instructions.
     *
     * @return an interable of all the instructions in this bytecode
     */
    public Iterable<Instruction> getInstructions() {
        return new Iterable<Instruction>() {
            @Override
            public Iterator<Instruction> iterator() {
                return IteratorUtils.concat(Bytecode.this.sourceInstructions.iterator(), Bytecode.this.stepInstructions.iterator());
            }
        };
    }

    /**
     * Get all the bindings (in a nested, recursive manner) from all the arguments of all the instructions of this bytecode.
     *
     * @return a map of string variable and object value bindings
     */
    public Map<String, Object> getBindings() {
        final Map<String, Object> bindingsMap = new HashMap<>();
        for (final Instruction instruction : this.sourceInstructions) {
            for (final Object argument : instruction.getArguments()) {
                addArgumentBinding(bindingsMap, argument);
            }
        }
        for (final Instruction instruction : this.stepInstructions) {
            for (final Object argument : instruction.getArguments()) {
                addArgumentBinding(bindingsMap, argument);
            }
        }
        return bindingsMap;
    }

    private static final void addArgumentBinding(final Map<String, Object> bindingsMap, final Object argument) {
        if (argument instanceof Binding)
            bindingsMap.put(((Binding) argument).key, ((Binding) argument).value);
        else if (argument instanceof Map) {
            for (final Map.Entry<?, ?> entry : ((Map<?, ?>) argument).entrySet()) {
                addArgumentBinding(bindingsMap, entry.getKey());
                addArgumentBinding(bindingsMap, entry.getValue());
            }
        } else if (argument instanceof Collection) {
            for (final Object item : (Collection) argument) {
                addArgumentBinding(bindingsMap, item);
            }
        } else if (argument instanceof Bytecode)
            bindingsMap.putAll(((Bytecode) argument).getBindings());
    }

    @Override
    public String toString() {
        return Arrays.asList(this.sourceInstructions, this.stepInstructions).toString();
    }

    @Override
    public boolean equals(final Object object) {
        return object instanceof Bytecode &&
                this.sourceInstructions.equals(((Bytecode) object).sourceInstructions) &&
                this.stepInstructions.equals(((Bytecode) object).stepInstructions);
    }

    @Override
    public int hashCode() {
        return this.sourceInstructions.hashCode() + this.stepInstructions.hashCode();
    }

    @SuppressWarnings("CloneDoesntDeclareCloneNotSupportedException")
    @Override
    public Bytecode clone() {
        try {
            final Bytecode clone = (Bytecode) super.clone();
            clone.sourceInstructions = new ArrayList<>(this.sourceInstructions);
            clone.stepInstructions = new ArrayList<>(this.stepInstructions);
            return clone;
        } catch (final CloneNotSupportedException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public static class Instruction implements Serializable {

        private final String operator;
        private final Object[] arguments;

        private Instruction(final String operator, final Object... arguments) {
            this.operator = operator;
            this.arguments = arguments;
        }

        public String getOperator() {
            return this.operator;
        }

        public Object[] getArguments() {
            return this.arguments;
        }

        @Override
        public String toString() {
            return this.operator + "(" + StringFactory.removeEndBrackets(Arrays.asList(this.arguments)) + ")";
        }

        @Override
        public boolean equals(final Object object) {
            return object instanceof Instruction &&
                    this.operator.equals(((Instruction) object).operator) &&
                    Arrays.equals(this.arguments, ((Instruction) object).arguments);
        }

        @Override
        public int hashCode() {
            return this.operator.hashCode() + Arrays.hashCode(this.arguments);
        }
    }

    public static class Binding<V> implements Serializable {

        private final String key;
        private final V value;

        public Binding(final String key, final V value) {
            this.key = key;
            this.value = value;
        }

        public String variable() {
            return this.key;
        }

        public V value() {
            return this.value;
        }

        @Override
        public String toString() {
            return "binding[" + this.key + "=" + this.value + "]";
        }

        @Override
        public boolean equals(final Object object) {
            return object instanceof Binding &&
                    this.key.equals(((Binding) object).key) &&
                    this.value.equals(((Binding) object).value);
        }

        @Override
        public int hashCode() {
            return this.key.hashCode() + this.value.hashCode();
        }
    }

    /////

    private final Object[] flattenArguments(final Object... arguments) {
        if (arguments.length == 0)
            return EMPTY_ARRAY;
        final List<Object> flatArguments = new ArrayList<>();
        for (final Object object : arguments) {
            if (object instanceof Object[]) {
                for (final Object nestObject : (Object[]) object) {
                    flatArguments.add(convertArgument(nestObject, true));
                }
            } else
                flatArguments.add(convertArgument(object, true));
        }
        return flatArguments.toArray();
    }

    private final Object convertArgument(final Object argument, final boolean searchBindings) {
        if (searchBindings) {
            final String variable = Bindings.getBoundVariable(argument);
            if (null != variable)
                return new Binding<>(variable, convertArgument(argument, false));
        }
        //
        if (argument instanceof Traversal)
            return ((Traversal) argument).asAdmin().getBytecode();
        else if (argument instanceof Map) {
            final Map<Object, Object> map = new LinkedHashMap<>(((Map) argument).size());
            for (final Map.Entry<?, ?> entry : ((Map<?, ?>) argument).entrySet()) {
                map.put(convertArgument(entry.getKey(), true), convertArgument(entry.getValue(), true));
            }
            return map;
        } else if (argument instanceof List) {
            final List<Object> list = new ArrayList<>(((List) argument).size());
            for (final Object item : (List) argument) {
                list.add(convertArgument(item, true));
            }
            return list;
        } else if (argument instanceof Set) {
            final Set<Object> set = new LinkedHashSet<>(((Set) argument).size());
            for (final Object item : (Set) argument) {
                set.add(convertArgument(item, true));
            }
            return set;
        } else
            return argument;
    }
}
