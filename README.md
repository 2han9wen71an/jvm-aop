# JVM-AOP

+ 灵感来自于skywalking，去除apm相关功能，保留插件和代理相关代码
+ 基于Bytebuddy字节码增强技术及Java Agent实现的无侵入式AOP框架

## 如何添加新的拦截器

+ 和skywalking的插件基本一致，参考skywalking的插件编写拦截器即可。

## 感谢

+ [skywalking-java](https://github.com/apache/skywalking-java)