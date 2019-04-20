# zipkin-server
zipkin-server是一个[Spring Boot](http://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/)应用程序，打包为一个可执行jar。您需要JRE 8+来启动zipkin-server。

Span存储方式和采集方法是可配置的。默认情况下，存储在内存中，通过http采集(POST /api/v2/spans endpoint)，服务监听端口为9411。

## 快速开始

开始的最快方法是下载[最新发布的服务](https://search.maven.org/remote_content?g=io.zipkin.java&a=zipkin-server&v=LATEST&c=exec)作为一个独立的可执行jar，注意，zipkin-server至少需要JRE 8。例如:

```bash
$ curl -sSL https://zipkin.io/quickstart.sh | bash -s
$ java -jar zipkin.jar
```

启动之后，在浏览器打开http://your_host:9411以找到traces!

## Endpoints

以下端点在 http://your_host:9411 下定义
* / - [UI](../zipkin-ui)
* /config.json - [Configuration for the UI](#configuration-for-the-ui)
* /api/v2 - [Api](https://zipkin.io/zipkin-api/#/)
* /health - Returns 200 status if OK
* /info - Provides the version of the running instance
* /metrics - Includes collector metrics broken down by transport type

Spring Boot提供了更多[内置端点](https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-endpoints.html)，比如`/metrics`。要全面列出端点，`GET /mappings`。

仍然支持 [legacy /api/v1 Api](https://zipkin.io/zipkin-api/#/)。后端通过数据转换与HTTP api解耦。这意味着您仍然可以接受新后端上的遗留数据，反之亦然。
输入`https://zipkin.io/zipkin-api/zipkin-api.yaml`进入Swagger UI的探索框以查看旧的定义

### CORS(跨源资源共享)

默认情况下，`/api/v2`下的所有端点都配置为允许跨源请求。

这可以通过修改YAML配置文件(`zipkin.query.allowed-origins`)或设置环境变量来更改。

例如，要允许来自`http://foo.bar.com`的CORS请求:

```
ZIPKIN_QUERY_ALLOWED_ORIGINS=http://foo.bar.com
```

## Logging

默认情况下，zipkin将日志消息写入INFO级别及以上的控制台。您可以使用参数`--logging.level.XXX`或系统属性`-Dlogging.level.XXX`来调整日志级别，或通过调整yaml配置。

例如，如果您想为所有zipkin类别启用调试日志记录，可以这样启动服务:

```bash
$ java -jar zipkin.jar --logging.level.zipkin2=DEBUG
```

在底层，zipkin-server使用[Spring Boot - Logback integration](http://docs.spring.io/spring-boot/docs/current/reference/html/howto-logging.html#howto-configure-logback-for-logging)。例如，您可以添加`--logging.exception-conversion-word=%wEx{full}`来转储完整的堆栈跟踪，而不是截断的堆栈跟踪。

## 资源监控

资源状态可通过`/metrics`查看，并扩展 [defaults reported by spring-boot](https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-metrics.html).

如果类路径中有`zipkin-autoconfigure-metrics-prometheus`可用，那么资源状态也可以通过`/prometheus`查看
有关更多信息，请参见prometheus metrics [README](../zipkin-autoconfigure/metrics-prometheus/README.md)。

### Collector

Collector metrics通过传输分解。以下内容可通过"/metrics"端点查看:

Metric | Description
--- | ---
counter.zipkin_collector.messages.$transport | cumulative messages received; should relate to messages reported by instrumented apps
counter.zipkin_collector.messages_dropped.$transport | cumulative messages dropped; reasons include client disconnects or malformed content
counter.zipkin_collector.bytes.$transport | cumulative message bytes
counter.zipkin_collector.spans.$transport | cumulative spans read; should relate to messages reported by instrumented apps
counter.zipkin_collector.spans_dropped.$transport | cumulative spans dropped; reasons include sampling or storage failures
gauge.zipkin_collector.message_spans.$transport | last count of spans in a message
gauge.zipkin_collector.message_bytes.$transport | last count of bytes in a message

## Self-Tracing
zipkin-server通过Self tracing对服务的性能进行故障排除。支持self-tracing的应用应该将采样率从1.0(100%)降低到更小的水平，比如0.001(0.1%或千分之一)。

当类路径中有可靠的依赖项时，并且`zipkin.self-tracing.enabled=true`, Zipkin将通过api的调用self-trace。

[yaml configuration](src/main/resources/zipkin-server-shared.yml) 将以下环境变量绑定到spring属性:

Variable | Property | Description
--- | --- | ---
SELF_TRACING_ENABLED | zipkin.self-tracing.enabled | Set to true to enable self-tracing. Defaults to false
SELF_TRACING_SAMPLE_RATE`: Percentage of self-traces to retain, defaults to always sample (1.0).
SELF_TRACING_FLUSH_INTERVAL | zipkin.self-tracing.flush-interval | Interval in seconds to flush self-tracing data to storage. Defaults to 1

## Configuration for the UI
Zipkin有一个web UI，当您依赖`io.zipkin:zipkin-ui`时，默认情况下该UI是启用的。这个UI自动包含在exec jar中，默认情况下托管在端口9411上。

当UI加载时，它从/config中读取默认配置。json端点。这些值可以被系统属性或[Spring Boot支持](https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html).的任何其他选项覆盖。

Attribute | Property | Description
--- | --- | ---
environment | zipkin.ui.environment | The value here becomes a label in the top-right corner. Not required.
defaultLookback | zipkin.ui.default-lookback | Default duration in millis to look back when finding traces. Affects the "Start time" element in the UI. Defaults to 3600000 (1 hour in millis).
searchEnabled | zipkin.ui.search-enabled | If the Find Traces screen is enabled. Defaults to true.
queryLimit | zipkin.ui.query-limit | Default limit for Find Traces. Defaults to 10.
instrumented | zipkin.ui.instrumented | Which sites this Zipkin UI covers. Regex syntax. e.g. `http:\/\/example.com\/.*` Defaults to match all websites (`.*`).
logsUrl | zipkin.ui.logs-url | Logs query service url pattern. If specified, a button will appear on the trace page and will replace {traceId} in the url by the traceId. Not required.
dependency.lowErrorRate | zipkin.ui.dependency.low-error-rate | The rate of error calls on a dependency link that turns it yellow. Defaults to 0.5 (50%) set to >1 to disable.
dependency.highErrorRate | zipkin.ui.dependency.high-error-rate | The rate of error calls on a dependency link that turns it red. Defaults to 0.75 (75%) set to >1 to disable.
basePath | zipkin.ui.basepath | path prefix placed into the <base> tag in the UI HTML; useful when running behind a reverse proxy. Default "/zipkin"
suggestLens | zipkin.ui.suggest-lens | When true, a button will appear on the navigation bar, when pressed switches to the Lens Ui (for Beta testing). Default true

例如，如果使用docker，可以将`ZIPKIN_UI_QUERY_LIMIT=100`影响`/config.json`中的`$.queryLimit`.

## 环境变量
zipkin-server是[scala query service](https://github.com/openzipkin/zipkin/tree/scala/zipkin-query-service)的一个临时替代品。

[yaml configuration](src/main/resources/zipkin-server-shared.yml) 绑定以下来自zipkin-scala的环境变量:

* `QUERY_PORT`: 监听http api和web ui端口;默认为9411
* `QUERY_ENABLED`: `false` 禁用查询api和UI静态资源。如果不需要存储后端，也可以禁用该存储后端搜索;默认值为true
* `SEARCH_ENABLED`: `false` 在存储后端禁用trace搜索请求。不禁用ID或依赖项查询的trace。当您使用其他服务(如日志)查找trace id时禁用此功能;默认值为true
* `QUERY_LOG_LEVEL`: 写入控制台的日志级别;默认值为INFO
* `QUERY_LOOKBACK`: endTs可以返回多少毫秒的查询;默认为24小时(每天两个buckets:一个用于今天，一个用于昨天)
* `STORAGE_TYPE`: SpanStore实现:`mem`, `mysql`, `cassandra`, `elasticsearch`之一
* `COLLECTOR_SAMPLE_RATE`: 保留traces的百分比，默认为总是sample(1.0)。
* `AUTOCOMPLETE_KEYS`: 由 `/api/v2/autocompleteTags` 接口返回的span标记键列表
* `AUTOCOMPLETE_TTL`: 自动补全键/值对的调用所需的毫秒数。默认3600000(1小时)

### Cassandra Storage
Zipkin的[Cassandra storage component](../zipkin-storage/cassandra)支持3.11+版本，当`STORAGE_TYPE`设置为`cassandra3`时生效:

    * `CASSANDRA_KEYSPACE`: 要使用的keyspace。默认为"zipkin2"
    * `CASSANDRA_CONTACT_POINTS`: 逗号分隔的主机地址列表，Cassandra集群的一部分。您还可以使用'host:port'指定自定义端口。默认为localhost:9042。
    * `CASSANDRA_LOCAL_DC`: 为实现延迟负载平衡，将被视为"local" 的数据中心的名称。未设置时，负载平衡是循环的。
    * `CASSANDRA_ENSURE_SCHEMA`: 确保cassandra有最新的schema。如果启用，尝试在类路径中以`cassandra-schema-cql3`前缀的scripts执行脚本。默认值为true
    * `CASSANDRA_USERNAME` and `CASSANDRA_PASSWORD`: Cassandra的身份验证。如果身份验证失败，将在启动时抛出异常。没有默认值
    * `CASSANDRA_USE_SSL`: Requires `javax.net.ssl.trustStore`和`javax.net.ssl.trustStorePassword`，默认为false。

以下是可能与所有用户无关的调整参数:

    * `CASSANDRA_MAX_CONNECTIONS`: 每个datacenter-local主机的最大池连接数。默认为8
    * `CASSANDRA_INDEX_CACHE_MAX`: 要缓存的最大trace index元数据项。0禁用缓存。默认为100000。
    * `CASSANDRA_INDEX_CACHE_TTL`: 缓存trace index元数据的时间。默认为60。
    * `CASSANDRA_INDEX_FETCH_MULTIPLIER`: 要获取的索引行比用户提供的查询限制多多少。默认为3。

日志记录的示例用法:

```bash
$ STORAGE_TYPE=cassandra3 java -jar zipkin.jar --logging.level.zipkin=trace --logging.level.zipkin2=trace --logging.level.com.datastax.driver.core=debug
```

### Elasticsearch Storage
Zipkin的[Elasticsearch storage component](../zipkin-storage/elasticsearch)支持版本2-6.x，当STORAGE_TYPE`STORAGE_TYPE`为`elasticsearch`时生效

当`STORAGE_TYPE`被设置为`elasticsearch时，生效如下:

    * `ES_HOSTS`: 一个逗号分隔的elasticsearch基本url列表，用于连接到ex.http://host:9200。默认为“http://localhost: 9200”。
    * `ES_PIPELINE`: 仅当目标为Elasticsearch 5+时有效。表示spans建立索引之前使用的pipeline。没有默认值。
    * `ES_TIMEOUT`: 控制Elasticsearch Api的连接、读和写套接字超时(以毫秒为单位)。默认为10000(10秒)
    * `ES_MAX_REQUESTS`: 只有当传输是http时才有效。设置从此进程到任何Elasticsearch主机的最大运行中请求。默认为64。
    * `ES_INDEX`: 在生成每日索引名时使用的索引前缀。默认为zipkin。
    * `ES_DATE_SEPARATOR`: 在生成每日索引名时使用的日期分隔符。默认为“-”。
    * `ES_INDEX_SHARDS`: 将索引分割为多个分片的数目。每个分片及其副本都分配给集群中的一台机器。增加集群中分片和机器的数量将提高读写性能。
                         现有索引的分片数不能更改，但新产生的索引将接受对设置的更改。默认为5。
    * `ES_INDEX_REPLICAS`: 索引中每个分片的副本数量。每个分片及其副本都分配给集群中的一台机器。在集群中增加副本和机器的数量将提高读性能，
                         但不会提高写性能。可以为现有索引更改副本的数量。默认为1。不建议将此值设置为0，因为这将意味着机器故障导致数据丢失。
    * `ES_USERNAME` and `ES_PASSWORD`: Elasticsearch基本身份验证，默认为空字符串。当X-Pack安全(以前是盾牌)就绪时使用。
    * `ES_HTTP_LOGGING`: 设置时，控制Elasticsearch Api的HTTP日志记录量。选项有BASIC、HEADERS和BODY
代码示例:

普通连接:
```bash
$ STORAGE_TYPE=elasticsearch ES_HOSTS=http://myhost:9200 java -jar zipkin.jar
```

控制Elasticsearch Api的HTTP日志记录量:
```bash
$ STORAGE_TYPE=elasticsearch ES_HTTP_LOGGING=BASIC java -jar zipkin.jar
```

### Legacy (v1) storage components
下面的组件不再推荐使用，而是过渡过渡使用的组件。这些表示为“v1”，
因为它们使用基于Zipkin的v1 Thrift模型的数据布局，而不是当前使用的更简单的v2数据模型。

#### MySQL Storage
Zipkin的[MySQL component](../zipkin-storage/mysql-v1)是针对MySQL 5.7进行测试的，当`STORAGE_TYPE`设置为`mysql`时生效:

    * `MYSQL_DB`: 要使用的数据库。默认为“zipkin”。
    * `MYSQL_USER` and `MYSQL_PASS`: MySQL认证，默认为空字符串。
    * `MYSQL_HOST`: 默认为localhost
    * `MYSQL_TCP_PORT`: 默认为3306
    * `MYSQL_MAX_CONNECTIONS`: 最大并发连接，默认为10
    * `MYSQL_USE_SSL`: Requires `javax.net.ssl.trustStore` and `javax.net.ssl.trustStorePassword`, 默认值为false。

注意:不建议在生产中使用此模块。在使用此方法之前，必须[应用schema](../zipkin-storage/mysql-v1#applying-the-schema).。

或者，您可以使用`MYSQL_JDBC_URL`并自己指定完整的JDBC url。注意，使用上述单独设置构造的URL还将包括以下参数:
`?autoReconnect=true&useSSL=false&useUnicode=yes&characterEncoding=UTF-8`. 如果您自己指定JDBC url，也可以添加这些参数。

代码示例:

```bash
$ STORAGE_TYPE=mysql MYSQL_USER=root java -jar zipkin.jar
```

### Cassandra Storage
Zipkin的 [Legacy (v1) Cassandra storage component](../zipkin-storage/cassandra-v1) 支持2.2+版本，当`STORAGE_TYPE`被设置为`cassandra`时，该组件也适用:

环境变量与`STORAGE_TYPE=cassandra3`相同，只是默认的键空间名称是"zipkin"。

代码示例:

```bash
$ STORAGE_TYPE=cassandra java -jar zipkin.jar
```

#### Service and Span names 查询
[Zipkin Api](https://zipkin.io/zipkin-api/#/default/get_services)没有包含一个参数，用于确定查找service或span names的时间间隔。
为了防止过度负载，`QUERY_LOOKBACK`将service和service查询限制为24小时(每天两个buckets:一个用于今天，一个用于昨天)。

### HTTP收集器
默认情况下启用HTTP收集器。它通过`POST /api/v1/spans`和`POST /api/v2/spans`收集span。
HTTP收集器支持以下配置:

Property | Environment Variable | Description
--- | --- | ---
`zipkin.collector.http.enabled` | `HTTP_COLLECTOR_ENABLED` | `false` disables the HTTP collector. Defaults to `true`.

### Scribe (Legacy) 收集器
收集器支持的Scribe作为外部模块可用。详情查看[zipkin-autoconfigure/collector-scribe](../zipkin-autoconfigure/collector-scribe/).

### Kafka Collector
The Kafka collector is enabled when `KAFKA_BOOTSTRAP_SERVERS` is set to
a v0.10+ server. The following apply and are further documented [here](../zipkin-autoconfigure/collector-kafka/).


Variable | New Consumer Config | Description
--- | --- | ---
`KAFKA_BOOTSTRAP_SERVERS` | bootstrap.servers | Comma-separated list of brokers, ex. 127.0.0.1:9092. No default
`KAFKA_GROUP_ID` | group.id | The consumer group this process is consuming on behalf of. Defaults to `zipkin`
`KAFKA_TOPIC` | N/A | Comma-separated list of topics that zipkin spans will be consumed from. Defaults to `zipkin`
`KAFKA_STREAMS` | N/A | Count of threads consuming the topic. Defaults to `1`

Example usage:

```bash
$ KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9092 java -jar zipkin.jar
```

Example targeting Kafka running in Docker:

```bash
$ export KAFKA_BOOTSTRAP_SERVERS=$(docker-machine ip `docker-machine active`)
# Run Kafka in the background
$ docker run -d -p 9092:9092 \
    --env ADVERTISED_HOST=$KAFKA_BOOTSTRAP_SERVERS \
    --env AUTO_CREATE_TOPICS=true \
    spotify/kafka
# Start the zipkin server, which reads $KAFKA_BOOTSTRAP_SERVERS
$ java -jar zipkin.jar
```

#### Overriding other properties
You may need to override other consumer properties than what zipkin
explicitly defines. In such case, you need to prefix that property name
with "zipkin.collector.kafka.overrides" and pass it as a CLI argument or
system property.

For example, to override "overrides.auto.offset.reset", you can set a
prefixed system property:

```bash
$ KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9092 java -Dzipkin.collector.kafka.overrides.auto.offset.reset=largest -jar zipkin.jar
```

#### Kafka (Legacy) Collector
The default collector is for Kafka 0.10.x+ brokers. You can use Kafka
0.8 brokers via an external module. See [zipkin-autoconfigure/collector-kafka08](../zipkin-autoconfigure/collector-kafka08/).

### RabbitMQ collector
The [RabbitMQ collector](../zipkin-collector/rabbitmq) will be enabled when the `addresses` or `uri` for the RabbitMQ server(s) is set.

Example usage:

```bash
$ RABBIT_ADDRESSES=localhost java -jar zipkin.jar
```

### 128-bit trace IDs

Zipkin supports 64 and 128-bit trace identifiers, typically serialized
as 16 or 32 character hex strings. By default, spans reported to zipkin
with the same trace ID will be considered in the same trace.

For example, `463ac35c9f6413ad48485a3953bb6124` is a 128-bit trace ID,
while `48485a3953bb6124` is a 64-bit one.

Note: Span (or parent) IDs within a trace are 64-bit regardless of the
length or value of their trace ID.

#### Migrating from 64 to 128-bit trace IDs

Unless you only issue 128-bit traces when all applications support them,
the process of updating applications from 64 to 128-bit trace IDs results
in a mixed state. This mixed state is mitigated by the setting
`STRICT_TRACE_ID=false`, explained below. Once a migration is complete,
remove the setting `STRICT_TRACE_ID=false` or set it to true.

Here are a few trace IDs the help what happens during this setting.

* Trace ID A: 463ac35c9f6413ad48485a3953bb6124
* Trace ID B: 48485a3953bb6124
* Trace ID C: 463ac35c9f6413adf1a48a8cff464e0e
* Trace ID D: 463ac35c9f6413ad

In a 64-bit environment, trace IDs will look like B or D above. When an
application upgrades to 128-bit instrumentation and decides to create a
128-bit trace, its trace IDs will look like A or C above.

Applications who aren't yet 128-bit capable typically only retain the
right-most 16 characters of the trace ID. When this happens, the same
trace could be reported as trace ID A or trace ID B.

By default, Zipkin will think these are different trace IDs, as they are
different strings. During a transition from 64-128 bit trace IDs, spans
would appear split across two IDs. For example, it might start as trace
ID A, but the next hop might truncate it to trace ID B. This would render
the system unusable for applications performing upgrades.

One way to address this problem is to not use 128-bit trace IDs until
all applications support them. This prevents a mixed scenario at the cost
of coordination. Another way is to set `STRICT_TRACE_ID=false`.

When `STRICT_TRACE_ID=false`, only the right-most 16 of a 32 character
trace ID are considered when grouping or retrieving traces. This setting
should only be applied when transitioning from 64 to 128-bit trace IDs
and removed once the transition is complete.

See https://github.com/openzipkin/b3-propagation/issues/6 for the status
of known open source libraries on 128-bit trace identifiers.

See `zipkin2.storage.StorageComponent.Builder` for even more details!

## Running with Docker
Released versions of zipkin-server are published to Docker Hub as `openzipkin/zipkin`.
See [docker-zipkin](https://github.com/openzipkin/docker-zipkin) for details.

## Building locally

To build and run the server from the currently checked out source, enter the following.
```bash
# Build the server and also make its dependencies
$ ./mvnw -Dlicense.skip=true -DskipTests --also-make -pl zipkin-server clean install
# Run the server
$ java -jar ./zipkin-server/target/zipkin-server-*exec.jar
```
