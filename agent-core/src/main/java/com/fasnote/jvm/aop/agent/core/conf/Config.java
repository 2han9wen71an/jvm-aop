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

package com.fasnote.jvm.aop.agent.core.conf;

import com.fasnote.jvm.aop.agent.core.logging.core.LogLevel;
import com.fasnote.jvm.aop.agent.core.logging.core.LogOutput;
import com.fasnote.jvm.aop.agent.core.logging.core.PatternLogger;
import com.fasnote.jvm.aop.agent.core.logging.core.ResolverType;
import com.fasnote.jvm.aop.agent.core.logging.core.WriterFactory;

import java.util.Arrays;
import java.util.List;

/**
 * This is the core config in sniffer agent.
 */
public class Config {

    public static class Agent {

        /**
         * If true, jvm aop agent will save all instrumented classes files in `/debugging` folder. jvm aop team
         * may ask for these files in order to resolve compatible problem.
         */
        public static boolean IS_OPEN_DEBUGGING_CLASS = false;

        /**
         * Enable the agent kernel services and instrumentation.
         */
        public static boolean ENABLE = true;
    }

    public static class Logging {
        /**
         * Log file name.
         */
        public static String FILE_NAME = "JVMAop-api.log";

        /**
         * Log files directory. Default is blank string, means, use "{theJVMAopAgentJarDir}/logs  " to output logs.
         * {theJVMAopAgentJarDir} is the directory where the JVMAop agent jar file is located.
         * <p>
         * Ref to {@link WriterFactory#getLogWriter()}
         */
        public static String DIR = "";

        /**
         * The max size of log file. If the size is bigger than this, archive the current file, and write into a new
         * file.
         */
        public static int MAX_FILE_SIZE = 300 * 1024 * 1024;

        /**
         * The max history log files. When rollover happened, if log files exceed this number, then the oldest file will
         * be delete. Negative or zero means off, by default.
         */
        public static int MAX_HISTORY_FILES = -1;

        /**
         * The log level. Default is debug.
         */
        public static LogLevel LEVEL = LogLevel.DEBUG;

        /**
         * The log output. Default is FILE.
         */
        public static LogOutput OUTPUT = LogOutput.FILE;

        /**
         * The log resolver type. Default is PATTERN which will create PatternLogResolver later.
         */
        public static ResolverType RESOLVER = ResolverType.PATTERN;

        /**
         * The log patten. Default is "%level %timestamp %thread %class : %msg %throwable". Each conversion specifiers
         * starts with a percent sign '%' and fis followed by conversion word. There are some default conversion
         * specifiers: %thread = ThreadName %level = LogLevel  {@link LogLevel} %timestamp = The now() who format is
         * 'yyyy-MM-dd HH:mm:ss:SSS' %class = SimpleName of TargetClass %msg = Message of user input %throwable =
         * Throwable of user input
         *
         * @see PatternLogger#DEFAULT_CONVERTER_MAP
         */
        public static String PATTERN = "%level %timestamp %thread %class : %msg %throwable";
    }

    public static class Plugin {
        /**
         * Control the length of the peer field.
         */
        public static int PEER_MAX_LENGTH = 200;

        /**
         * Exclude activated plugins
         */
        public static String EXCLUDE_PLUGINS = "";

        /**
         * Mount the folders of the plugins. The folder path is relative to agent.jar.
         */
        public static List<String> MOUNT = Arrays.asList("plugins", "activations");
    }
}
