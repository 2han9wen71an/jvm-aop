/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package net.bytebuddy.agent.builder;

import com.fasnote.jvm.aop.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import net.bytebuddy.description.annotation.AnnotationList;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.PackageDescription;
import net.bytebuddy.description.type.RecordComponentDescription;
import net.bytebuddy.description.type.RecordComponentList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.dynamic.TargetType;
import net.bytebuddy.implementation.bytecode.StackSize;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.JavaModule;
import net.bytebuddy.utility.nullability.MaybeNull;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A DescriptionStrategy to get the original class description by removing dynamic field and method tokens
 * generated by JVMAop.
 */
public class DescriptionStrategy implements AgentBuilder.DescriptionStrategy {

    /**
     * A cache of type descriptions for commonly used types to avoid unnecessary allocations.
     */
    private static final Map<Class<?>, TypeDescription> TYPE_CACHE;

    /*
     * Initializes the type cache.
     */
    static {
        TYPE_CACHE = new HashMap<>();
        TYPE_CACHE.put(TargetType.class, new TypeDescription.ForLoadedType(TargetType.class));
        TYPE_CACHE.put(Class.class, new TypeDescription.ForLoadedType(Class.class));
        TYPE_CACHE.put(Throwable.class, new TypeDescription.ForLoadedType(Throwable.class));
        TYPE_CACHE.put(Annotation.class, new TypeDescription.ForLoadedType(Annotation.class));
        TYPE_CACHE.put(Object.class, new TypeDescription.ForLoadedType(Object.class));
        TYPE_CACHE.put(String.class, new TypeDescription.ForLoadedType(String.class));
        TYPE_CACHE.put(Boolean.class, new TypeDescription.ForLoadedType(Boolean.class));
        TYPE_CACHE.put(Byte.class, new TypeDescription.ForLoadedType(Byte.class));
        TYPE_CACHE.put(Short.class, new TypeDescription.ForLoadedType(Short.class));
        TYPE_CACHE.put(Character.class, new TypeDescription.ForLoadedType(Character.class));
        TYPE_CACHE.put(Integer.class, new TypeDescription.ForLoadedType(Integer.class));
        TYPE_CACHE.put(Long.class, new TypeDescription.ForLoadedType(Long.class));
        TYPE_CACHE.put(Float.class, new TypeDescription.ForLoadedType(Float.class));
        TYPE_CACHE.put(Double.class, new TypeDescription.ForLoadedType(Double.class));
        TYPE_CACHE.put(void.class, new TypeDescription.ForLoadedType(void.class));
        TYPE_CACHE.put(boolean.class, new TypeDescription.ForLoadedType(boolean.class));
        TYPE_CACHE.put(byte.class, new TypeDescription.ForLoadedType(byte.class));
        TYPE_CACHE.put(short.class, new TypeDescription.ForLoadedType(short.class));
        TYPE_CACHE.put(char.class, new TypeDescription.ForLoadedType(char.class));
        TYPE_CACHE.put(int.class, new TypeDescription.ForLoadedType(int.class));
        TYPE_CACHE.put(long.class, new TypeDescription.ForLoadedType(long.class));
        TYPE_CACHE.put(float.class, new TypeDescription.ForLoadedType(float.class));
        TYPE_CACHE.put(double.class, new TypeDescription.ForLoadedType(double.class));
    }

    private final AgentBuilder.DescriptionStrategy delegate = Default.HYBRID;

    private final String nameTrait;

    public DescriptionStrategy(String nameTrait) {
        this.nameTrait = nameTrait;
    }

    @Override
    public boolean isLoadedFirst() {
        return true;
    }

    @Override
    public TypeDescription apply(String name,
                                 @MaybeNull Class<?> type,
                                 TypePool typePool,
                                 AgentBuilder.CircularityLock circularityLock,
                                 @MaybeNull ClassLoader classLoader,
                                 @MaybeNull JavaModule module) {
        // find from type cache
        if (type != null) {
            TypeDescription typeDescription = TYPE_CACHE.get(type);
            if (typeDescription != null) {
                return typeDescription;
            }
        }
        // wrap result
        return new SWTypeDescriptionWrapper(delegate.apply(name, type, typePool, circularityLock, classLoader, module), nameTrait, classLoader, name);
    }

    /**
     * A TypeDescription wrapper to remove fields, methods, interface generated by JVMAop.
     */
    static class SWTypeDescriptionWrapper extends TypeDescription.AbstractBase implements Serializable {

        /**
         * The class's serial version UID.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Original type cache.
         * classloader hashcode -> ( typeName -> type cache )
         */
        private static final Map<Integer, Map<String, TypeCache>> CLASS_LOADER_TYPE_CACHE = new ConcurrentHashMap<>();

        private static final List<String> IGNORED_INTERFACES = List.of(EnhancedInstance.class.getName());
        private final String nameTrait;
        private MethodList<MethodDescription.InDefinedShape> methods;
        private FieldList<FieldDescription.InDefinedShape> fields;
        private ClassLoader classLoader;

        private String typeName;

        private TypeList.Generic interfaces;

        private TypeDescription delegate;

        public SWTypeDescriptionWrapper(TypeDescription delegate, String nameTrait, ClassLoader classLoader, String typeName) {
            this.delegate = delegate;
            this.nameTrait = nameTrait;
            this.classLoader = classLoader;
            this.typeName = typeName;
        }

        private TypeCache getTypeCache() {
            int classLoaderHashCode = classLoader != null ? classLoader.hashCode() : 0;
            Map<String, TypeCache> typeCacheMap = CLASS_LOADER_TYPE_CACHE.computeIfAbsent(classLoaderHashCode, k -> new ConcurrentHashMap<>());
            TypeCache typeCache = typeCacheMap.computeIfAbsent(typeName, k -> new TypeCache(typeName));
            return typeCache;
        }

        @Override
        public TypeList.Generic getInterfaces() {
            if (this.interfaces == null) {
                TypeList.Generic allInterfaces = delegate.getInterfaces();
                if (allInterfaces.stream().anyMatch(s -> IGNORED_INTERFACES.contains(s.getTypeName()))) {
                    // remove interfaces added by JVMAop
                    List<Generic> list = allInterfaces.stream()
                            .filter(s -> !IGNORED_INTERFACES.contains(s.getTypeName()))
                            .collect(Collectors.toList());
                    this.interfaces = new TypeList.Generic.Explicit(list);
                } else {
                    this.interfaces = allInterfaces;
                }
            }
            return this.interfaces;
        }

        @Override
        public FieldList<FieldDescription.InDefinedShape> getDeclaredFields() {
            if (this.fields == null) {
                FieldList<FieldDescription.InDefinedShape> declaredFields = delegate.getDeclaredFields();
                TypeCache typeCache = getTypeCache();
                if (typeCache.fieldNames == null) {
                    // save origin fields
                    typeCache.fieldNames = declaredFields.stream().map(WithRuntimeName::getName).collect(Collectors.toSet());
                    fields = declaredFields;
                } else {
                    // return origin fields
                    fields = new FieldList.Explicit<>(declaredFields.stream()
                            .filter(f -> typeCache.fieldNames.contains(f.getName()))
                            .collect(Collectors.toList()));
                }
            }
            return fields;
        }

        @Override
        public MethodList<MethodDescription.InDefinedShape> getDeclaredMethods() {
            if (this.methods == null) {
                MethodList<MethodDescription.InDefinedShape> declaredMethods = delegate.getDeclaredMethods();
                TypeCache typeCache = getTypeCache();
                if (typeCache.methodCodes == null) {
                    // save original methods
                    typeCache.methodCodes = declaredMethods.stream().map(m -> m.toString().hashCode()).collect(Collectors.toSet());
                    methods = declaredMethods;
                } else {
                    // return original methods in the same order, remove dynamic method tokens generated by JVMAop and ByteBuddy
                    // remove generated methods for delegating superclass methods, such as Jedis.
                    methods = new MethodList.Explicit<>(declaredMethods.stream()
                            .filter(m -> typeCache.methodCodes.contains(m.toString().hashCode()))
                            .collect(Collectors.toList()));
                }
            }
            return methods;
        }

        @Override
        public boolean isAssignableTo(Class<?> type) {
            // ignore interface added by JVMAop
            if (IGNORED_INTERFACES.contains(type.getName())) {
                return false;
            }
            return delegate.isAssignableTo(type);
        }

        @Override
        public boolean isAccessibleTo(TypeDescription typeDescription) {
            // ignore interface added by JVMAop
            if (IGNORED_INTERFACES.contains(typeDescription.getName())) {
                return false;
            }
            return delegate.isAccessibleTo(typeDescription);
        }

        @Override
        public RecordComponentList<RecordComponentDescription.InDefinedShape> getRecordComponents() {
            return delegate.getRecordComponents();
        }

        @Override
        public TypeDescription getComponentType() {
            return delegate.getComponentType();
        }

        @Override
        public TypeDescription getDeclaringType() {
            return delegate.getDeclaringType();
        }

        @Override
        public TypeList getDeclaredTypes() {
            return delegate.getDeclaredTypes();
        }

        @Override
        public MethodDescription.InDefinedShape getEnclosingMethod() {
            return delegate.getEnclosingMethod();
        }

        @Override
        public TypeDescription getEnclosingType() {
            return delegate.getEnclosingType();
        }

        @Override
        public String getSimpleName() {
            return delegate.getSimpleName();
        }

        @Override
        public String getCanonicalName() {
            return delegate.getCanonicalName();
        }

        @Override
        public boolean isAnonymousType() {
            return delegate.isAnonymousType();
        }

        @Override
        public boolean isLocalType() {
            return delegate.isLocalType();
        }

        @Override
        public PackageDescription getPackage() {
            return delegate.getPackage();
        }

        @Override
        public TypeDescription getNestHost() {
            return delegate.getNestHost();
        }

        @Override
        public TypeList getNestMembers() {
            return delegate.getNestMembers();
        }

        @Override
        public TypeList getPermittedSubtypes() {
            return delegate.getPermittedSubtypes();
        }

        @Override
        public String getDescriptor() {
            return delegate.getDescriptor();
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public TypeList.Generic getTypeVariables() {
            return delegate.getTypeVariables();
        }

        @Override
        public AnnotationList getDeclaredAnnotations() {
            return delegate.getDeclaredAnnotations();
        }

        @Override
        public Generic getSuperClass() {
            return delegate.getSuperClass();
        }

        @Override
        public StackSize getStackSize() {
            return delegate.getStackSize();
        }

        @Override
        public boolean isArray() {
            return delegate.isArray();
        }

        @Override
        public boolean isRecord() {
            return delegate.isRecord();
        }

        @Override
        public boolean isPrimitive() {
            return delegate.isPrimitive();
        }

        @Override
        public int getModifiers() {
            return delegate.getModifiers();
        }
    }

    static class TypeCache {
        private String typeName;
        private Set<Integer> methodCodes;
        private Set<String> fieldNames;

        public TypeCache(String typeName) {
            this.typeName = typeName;
        }
    }
}
