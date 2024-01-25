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

package com.fasnote.jvm.aop.agent;

import com.fasnote.jvm.aop.agent.bytebuddy.AuxiliaryTypeNamingStrategy;
import com.fasnote.jvm.aop.agent.bytebuddy.MethodGraphCompilerDelegate;
import com.fasnote.jvm.aop.agent.bytebuddy.MethodNameTransformer;
import com.fasnote.jvm.aop.agent.core.boot.AgentPackageNotFoundException;
import com.fasnote.jvm.aop.agent.core.boot.ServiceManager;
import com.fasnote.jvm.aop.agent.core.conf.Config;
import com.fasnote.jvm.aop.agent.core.conf.SnifferConfigInitializer;
import com.fasnote.jvm.aop.agent.core.logging.api.ILog;
import com.fasnote.jvm.aop.agent.core.logging.api.LogManager;
import com.fasnote.jvm.aop.agent.core.plugin.AbstractClassEnhancePluginDefine;
import com.fasnote.jvm.aop.agent.core.plugin.EnhanceContext;
import com.fasnote.jvm.aop.agent.core.plugin.InstrumentDebuggingClass;
import com.fasnote.jvm.aop.agent.core.plugin.PluginBootstrap;
import com.fasnote.jvm.aop.agent.core.plugin.PluginException;
import com.fasnote.jvm.aop.agent.core.plugin.PluginFinder;
import com.fasnote.jvm.aop.agent.core.plugin.bootstrap.BootstrapInstrumentBoost;
import com.fasnote.jvm.aop.agent.core.plugin.interceptor.enhance.DelegateNamingResolver;
import com.fasnote.jvm.aop.agent.core.plugin.jdk9module.JDK9ModuleExporter;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilderDefault;
import net.bytebuddy.agent.builder.DescriptionStrategy;
import net.bytebuddy.agent.builder.NativeMethodStrategy;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.scaffold.MethodGraph;
import net.bytebuddy.dynamic.scaffold.TypeValidation;
import net.bytebuddy.implementation.ImplementationContextFactory;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.fasnote.jvm.aop.agent.core.conf.Constants.NAME_TRAIT;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;

public class AgentLauncher {
    private static ILog LOGGER = LogManager.getLogger(AgentLauncher.class);

    /**
     * Main entrance. Use byte-buddy transform to enhance all classes, which define in plugins.
     */
    public static void premain(String agentArgs, Instrumentation instrumentation) throws PluginException {
        final PluginFinder pluginFinder;
        try {
            SnifferConfigInitializer.initializeCoreConfig(agentArgs);
        } catch (Exception e) {
            // try to resolve a new logger, and use the new logger to write the error log here
            LogManager.getLogger(AgentLauncher.class)
                    .error(e, "JVMAop agent initialized failure. Shutting down.");
            return;
        } finally {
            // refresh logger again after initialization finishes
            LOGGER = LogManager.getLogger(AgentLauncher.class);
        }

        if (!Config.Agent.ENABLE) {
            LOGGER.warn("JVMAop agent is disabled.");
            return;
        }

        try {
            pluginFinder = new PluginFinder(new PluginBootstrap().loadPlugins());
        } catch (AgentPackageNotFoundException ape) {
            LOGGER.error(ape, "Locate agent.jar failure. Shutting down.");
            return;
        } catch (Exception e) {
            LOGGER.error(e, "JVMAop agent initialized failure. Shutting down.");
            return;
        }

        try {
            installClassTransformer(instrumentation, pluginFinder);
        } catch (Exception e) {
            LOGGER.error(e, "JVMAop agent installed class transformer failure.");
        }

        try {
            ServiceManager.INSTANCE.boot();
        } catch (Exception e) {
            LOGGER.error(e, "JVMAop agent boot failure.");
        }

        Runtime.getRuntime()
                .addShutdownHook(new Thread(ServiceManager.INSTANCE::shutdown, "JVMAop service shutdown thread"));
    }

    static void installClassTransformer(Instrumentation instrumentation, PluginFinder pluginFinder) throws Exception {
        LOGGER.info("JVMAop agent begin to install transformer ...");

        AgentBuilder agentBuilder = newAgentBuilder().ignore(
                nameStartsWith("net.bytebuddy.")
                        .or(nameStartsWith("org.slf4j."))
                        .or(nameStartsWith("org.groovy."))
                        .or(nameContains("javassist"))
                        .or(nameContains(".asm."))
                        .or(nameContains(".reflectasm."))
                        .or(nameStartsWith("sun.reflect"))
                        .or(allJvmAop())
                        .or(ElementMatchers.isSynthetic()));

        JDK9ModuleExporter.EdgeClasses edgeClasses = new JDK9ModuleExporter.EdgeClasses();
        try {
            agentBuilder = BootstrapInstrumentBoost.inject(pluginFinder, instrumentation, agentBuilder, edgeClasses);
        } catch (Exception e) {
            throw new Exception("JVMAop agent inject bootstrap instrumentation failure. Shutting down.", e);
        }

        try {
            agentBuilder = JDK9ModuleExporter.openReadEdge(instrumentation, agentBuilder, edgeClasses);
        } catch (Exception e) {
            throw new Exception("JVMAop agent open read edge in JDK 9+ failure. Shutting down.", e);
        }

        agentBuilder.type(pluginFinder.buildMatch())
                .transform(new Transformer(pluginFinder))
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .with(new RedefinitionListener())
                .with(new Listener())
                .installOn(instrumentation);

        PluginFinder.pluginInitCompleted();

        LOGGER.info("JVMAop agent transformer has installed.");
    }

    /**
     * Create a new agent builder through customized {@link ByteBuddy} powered by
     * {@link AuxiliaryTypeNamingStrategy} {@link DelegateNamingResolver} {@link MethodNameTransformer} and {@link ImplementationContextFactory}
     */
    private static AgentBuilder newAgentBuilder() {
        final ByteBuddy byteBuddy = new ByteBuddy()
                .with(TypeValidation.of(Config.Agent.IS_OPEN_DEBUGGING_CLASS))
                .with(new AuxiliaryTypeNamingStrategy(NAME_TRAIT))
                .with(new ImplementationContextFactory(NAME_TRAIT))
                .with(new MethodGraphCompilerDelegate(MethodGraph.Compiler.DEFAULT));

        return new AgentBuilderDefault(byteBuddy, new NativeMethodStrategy(NAME_TRAIT))
                .with(new DescriptionStrategy(NAME_TRAIT));
    }

    private static ElementMatcher.Junction<NamedElement> allJvmAop() {
        return nameStartsWith("com.fasnote.jvm.aop.");
    }

    private static class Transformer implements AgentBuilder.Transformer {
        private final PluginFinder pluginFinder;

        Transformer(PluginFinder pluginFinder) {
            this.pluginFinder = pluginFinder;
        }

        @Override
        public DynamicType.Builder<?> transform(final DynamicType.Builder<?> builder,
                                                final TypeDescription typeDescription,
                                                final ClassLoader classLoader,
                                                final JavaModule javaModule,
                                                final ProtectionDomain protectionDomain) {
            List<AbstractClassEnhancePluginDefine> pluginDefines = pluginFinder.find(typeDescription);
            if (!pluginDefines.isEmpty()) {
                DynamicType.Builder<?> newBuilder = builder;
                EnhanceContext context = new EnhanceContext();
                for (AbstractClassEnhancePluginDefine define : pluginDefines) {
                    DynamicType.Builder<?> possibleNewBuilder = define.define(
                            typeDescription, newBuilder, classLoader, context);
                    if (possibleNewBuilder != null) {
                        newBuilder = possibleNewBuilder;
                    }
                }
                if (context.isEnhanced()) {
                    LOGGER.debug("Finish the prepare stage for {}.", typeDescription.getName());
                }

                return newBuilder;
            }

            LOGGER.debug("Matched class {}, but ignore by finding mechanism.", typeDescription.getTypeName());
            return builder;
        }
    }

    private static class Listener implements AgentBuilder.Listener {
        @Override
        public void onDiscovery(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {

        }

        @Override
        public void onTransformation(final TypeDescription typeDescription,
                                     final ClassLoader classLoader,
                                     final JavaModule module,
                                     final boolean loaded,
                                     final DynamicType dynamicType) {
            if (LOGGER.isDebugEnable()) {
                LOGGER.debug("On Transformation class {}.", typeDescription.getName());
            }

            InstrumentDebuggingClass.INSTANCE.log(dynamicType);
        }

        @Override
        public void onIgnored(final TypeDescription typeDescription,
                              final ClassLoader classLoader,
                              final JavaModule module,
                              final boolean loaded) {

        }

        @Override
        public void onError(final String typeName,
                            final ClassLoader classLoader,
                            final JavaModule module,
                            final boolean loaded,
                            final Throwable throwable) {
            LOGGER.error("Enhance class " + typeName + " error.", throwable);
        }

        @Override
        public void onComplete(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {
        }
    }

    private static class RedefinitionListener implements AgentBuilder.RedefinitionStrategy.Listener {

        @Override
        public void onBatch(int index, List<Class<?>> batch, List<Class<?>> types) {
            /* do nothing */
        }

        @Override
        public Iterable<? extends List<Class<?>>> onError(int index,
                                                          List<Class<?>> batch,
                                                          Throwable throwable,
                                                          List<Class<?>> types) {
            LOGGER.error(throwable, "index={}, batch={}, types={}", index, batch, types);
            return Collections.emptyList();
        }

        @Override
        public void onComplete(int amount, List<Class<?>> types, Map<List<Class<?>>, Throwable> failures) {
            /* do nothing */
        }
    }
}
