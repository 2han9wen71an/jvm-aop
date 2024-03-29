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

import com.fasnote.jvm.aop.agent.bytebuddy.MethodNameTransformer;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;

public class NativeMethodStrategy implements AgentBuilder.Default.NativeMethodStrategy {

    private String nameTrait;

    public NativeMethodStrategy(String nameTrait) {
        this.nameTrait = nameTrait;
    }

    @Override
    public net.bytebuddy.dynamic.scaffold.inline.MethodNameTransformer resolve() {
        return new MethodNameTransformer(nameTrait);
    }

    @Override
    public void apply(Instrumentation instrumentation, ClassFileTransformer classFileTransformer) {
    }
}