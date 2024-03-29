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

package com.fasnote.jvm.aop.dependencies.org.slf4j.impl;

import com.fasnote.jvm.aop.agent.core.logging.api.ILog;
import com.fasnote.jvm.aop.agent.core.logging.api.LogManager;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * implement {@link ILoggerFactory},factory adapter logger for {@link Logger}
 */
public class InternalLoggerFactory implements ILoggerFactory {

    private ConcurrentMap<String, Slf4jLoggerAdapter> loggerCache = new ConcurrentHashMap();

    @Override
    public Logger getLogger(final String name) {
        final Slf4jLoggerAdapter hitPLogger = loggerCache.get(name);
        if (hitPLogger != null) {
            return hitPLogger;
        }
        final ILog logger = LogManager.getLogger(name);
        final Slf4jLoggerAdapter slf4jLoggerAdapter = new Slf4jLoggerAdapter(logger, name);
        final Slf4jLoggerAdapter before = loggerCache.putIfAbsent(name, slf4jLoggerAdapter);
        if (before != null) {
            return before;
        }
        return slf4jLoggerAdapter;
    }
}
