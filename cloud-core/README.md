# 简介
本项目定义了微服务模式时的相关依赖和组件

# 结构

websocket


# 注意
### 1. pom需引入 `spring-cloud-starter-bootstrap`，否则不加载`bootstrap.yml`
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-bootstrap</artifactId>
</dependency>
```

