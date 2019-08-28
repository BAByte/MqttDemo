# 使用mosquitto和Eclipse Paho Java实现MQTT

# MQTT的服务器选择

下面是官方推荐的服务器：https://github.com/mqtt/mqtt.github.io/wiki/servers

# java库选择

下面是官方推荐（有很多语言）：https://github.com/mqtt/mqtt.github.io/wiki/libraries

# 大家的选择

根据GIthub上使用次数来讲Eclipse Paho无疑是主流，  Eclipse Paho集成非常方便、简单。该库的具体介绍可以去官网：https://www.eclipse.org/paho/

# 对实现方案的要求：

## 实时性

实时性，大板连续发1000条给服务器一个 “1” 字符串数据，大部分情况是2秒内收完。有时候超过两秒，可能是我的网络不稳定。

# 消息服务质量如何

mosquitto服务器提供了3种服务质量的等级，

> 
> **（1）QoS 0(At most once)“至多一次”** 
> 消息发布完全依赖底层 TCP/IP 网络。会发生消息丢失或重复。这一级别可用于如下情况，环境传感器数据，丢失一次读记录无所谓，因为不久后还会有第二次发送。 
>
> **（2）QoS 1(At least once)“至少一次”** 
> 确保消息到达，但消息重复可能会发生。 
>
> **（3）QoS 2(Exactly once)“只有一次”** 
> 确保消息到达一次。这一级别可用于如下情况，在计费系统中，消息重复或丢失会导致不正确的结果。小型传输，开销很小（固定长度的头部是 2 字节），协议交换最小化，以降低网络流量。 

# 稳定性

在我测试时连接是非常快速的，即使是网络状态发生了改变，网络重新可用后自动重连没有出现失败（有可能测试的次数不够多，网络环境不够复杂），即使是服务器重启，服务器重启成功后，客户端也能收到回调，然后进行自动重连。

MQTT协议在连接一定的时长后，会自动断开，但是Paho库是提供了心跳机制去解决这问题。

发数据时会出现丢包。

我不知道怎么控制弱网络环境这个变量，所以我找了一些资料。

> ### 弱网环境下表现
>
> 手机等终端在弱网络环境下丢包情况会非常明显，连接MQTT Server成功率很低。
>
> 手机终端在每次TCP断开或断网后，会即刻发起TCP重连，连接成功，会重复以前步骤依次发送连接命令（CONNECT），订阅命令（SUBSCRIBLE），在弱网情况下，这个过程将会变得很昂贵

## WebSocket Support？

在Eclipse Paho 库是WebSocket Support的，这点在下图中可以看到java的Paho是支持的。

![image](https://upload-images.jianshu.io/upload_images/6069999-5ef36309928da900.png)

IBM的MQTT文档：https://www.ibm.com/developerworks/cn/websphere/library/techarticles/1308_xiangr_mqtt/1308_xiangr_mqtt.html

## 不同的MQTT服务器并发测试

注意：一个网上的大神测试的，我来不及装那么多服务器，

各个MQTT服务器的测试数据：

> 硬件环境：
> 内存4G
> CPU4核
>
> SERVER及端口：
> apollo端口 61619
> mosquitto:端口 1884
> activeMQ端口：1883
> emqtt 端口1885
>
> 测试方法
> 并发测试：192.168.6.156 上用 emqttd_benchmark 测试 192.168.6.157 上的各MQTT SERVER 并发量
> 消息发送测试：本地电脑 用php程序 使用一个客户端连接情况下 发送10万消息 到 192.168.6.157上的各MQTT SERVER。
>
> 测试时间：1个工作日。
>
> 默认 retain=0 非持久化消息。
>
> QoS0: 最多一次 服务器与 客户端 交互1次 。
> QoS1 :至少一次 服务器与 客户端 交互2次 。
> QoS2:洽好一次 服务器与 客户端 交互4次 。
>
> 测试结果：
> mosquitto:
> 发送消息：QoS0: 18.57秒 cpu:10% ， QoS1: 86.9秒 cpu 10% ， QoS2: 157秒 cpu 10% ， retain=1的各值和retain=0几乎一样 。
> 并发连接： 第1次：12000 第二次11000 第3次 12200 cpu占用 25%左右
> 稳定性高
>
> apollo :
> 发送消息：QoS0 18.37秒 cpu 30% ，QoS1 215秒cpu40% ，QoS2: 超时
> 并发连接 ：第1次 11000 第2次 12200 第3次 15200 第4次 13900 cpu:45%
>
> activeMQ :
> 发送消息：QoS0 18.41秒 cpu 50% , QoS1 超时
> 并发连接 ：第1次 28200 第2次 28000 有2次测试后服务出现崩溃 cpu:70%
>
> emqtt ：
> 发送消息： QoS0 66秒 cpu: 80%, QoS1 204秒 cpu: 55%
> 并发连接 27600 第二次 19000 第3次28200 第4次28200 cpu 70%
> 稳定性高
> —-
>
> 在测试中，发现 mosquitto无法利用多核，emqtt 磁盘io高,activeMQ占内存比较高。
>
> 后来发现是benchmark程序的原因，无法高并发，可以多台开benchmark 程序一起执行，那以上并发结果就要翻倍了
> 结论：emqtt，并发最高，但cpu占用较高，稳定性高。消息发送较慢。
> moqtuitto ,发送消息快，稳定性高，cpu占用很少，并发比较高。
> 其它2个稳定性不太高。
> 以上数据仅供参考 。

# mosquitto + Paho在大板上的并发测试

条件：从大板发出消息，电脑运行的模拟器接收

> 测试次数：3
> mosquitto:
>
> 发送消息：10000条  QoS0: 2分18秒 会丢包

条件：电脑运行的模拟器发出消息，大板接收

> 测试次数：3
> mosquitto:
>
> 发送消息：10000条  QoS0: 1分49秒 ，会丢包

条件：大板自发自收

> 测试次数：3
> mosquitto:
>
> 发送消息：10000条  QoS0: 20秒 ，会丢包

条件：模拟器自发自收

> 测试次数：3
> mosquitto:
>
> 发送消息：10000条  QoS0: 13秒 ，会丢包

可以看到自发自收和两个不同终端之间发送接收差别是很大的，和网络有一定的关系，所以上面的大佬的测试应该是属于在自己电脑上自发自收了。

## 事件发送的负载是否有一定限制？

这个在配置文件里是有说明的，mqtt协议限制每个数据包最大容量为256MB

```java
# This option sets the maximum publish payload size that the broker will allow.
# Received messages that exceed this size will not be accepted by the broker.
# The default value is 0, which means that all valid MQTT messages are
# accepted. MQTT imposes a maximum payload size of 268435455 bytes.
```



## 是否支持粘性事件

> 消息持久性(Retain )
> 作用：服务器存储消息，推送给未来的订阅者。
> Retain 持久消息（粘性消息）
> RETAIN 标记：每个Publish消息都需要指定的标记
> 0 —— 服务端不能存储这个消息，也不能移除或替换任何 现存的保留消息
> 1 —— 服务端必须存储这个应用消息和它的QoS等级，以便它可以被分发给未来的订阅者
> 每个Topic只会保留最多一个 Retain 持久消息
> 客户端订阅带有持久消息的Topic，会立即受到这条消息
> 服务器可以选择丢弃持久消息，比如内存或者存储吃紧的时候
> 如果客户端想要删除某个Topic 上面的持久消息，可以向这个Topic发送一个Payload为空的持久消息
> 遗嘱消息（Will）的Retain持久机制同理

## 当一个事件到来的时候，是否多个观察者都可以收到？

可以，服务器可以主动向终端发事件，终端能主动向服务器发事件，会有对应的回调

# 配置MQTT服务器的方法

### macBook下mosquitto配置方法

```java
控制台命令

//安装
1.   brew install mosquitto

//启动
2.   brew services start mosquitto  

//停止服务   
2.1  brew services stop mosquitto

//设置配置文件
3.   vim /usr/local/etc/mosquitto/mosquitto.conf

```

我测试是在本地，所以服务器ip设置为我电脑的ip和端口

![image](https://imgconvert.csdnimg.cn/aHR0cDovL2ltZy5ibG9nLmNzZG4ubmV0LzIwMTcwNjIyMTEwMTU0NDcw)

### 终端直接调试

```java
// 在 A终端 订阅
mosquitto_sub -v -t sensor
【-t】指定主题，此处为sensor
【-v】打印更多的调试信息


//在 B终端 发布
mosquitto_pub -t sensor -m 12
【-t】指定主题
【-m】指定消息内容

```

![image](https://imgconvert.csdnimg.cn/aHR0cDovL2ltZy5ibG9nLmNzZG4ubmV0LzIwMTcwNjIyMTExNjIwNzgy)



### 网页调试

## MQTTLens插件的使用

[MQTTLens](https://link.juejin.im/?target=https%3A%2F%2Fchrome.google.com%2Fwebstore%2Fdetail%2Fmqttlens%2Fhemojaaeigabkbcookmlgmdigohjobjm%3Fhl%3Dzh-CN)

1.安装：点击链接进行安装。

2.输入以下三个信息： 
connection name ： 随便写 
HostName：写服务器地址，如果自己电脑[测试](https://link.juejin.im/?target=http%3A%2F%2Flib.csdn.net%2Fbase%2Fsoftwaretest)，就写本地地址 
client ID ： 唯一ID 一般是设备唯一识别码

3.保存，使用 。接下来就可以订阅或者发布消息了。

![image](https://user-gold-cdn.xitu.io/2017/7/12/46ec03930f2bc41ca9fc954daa176a6b)

这样服务器就配置好了。

### 安卓端Eclipse Paho java库配置

库的项目地址：https://github.com/eclipse/paho.mqtt.java

库的文档：https://www.eclipse.org/paho/files/javadoc/index.html

## android的demo

功能简介：可以和服务端建立连接，可以向服务端发送事件，也可以接收服务端发送的事件，需要在电脑先搭建MQTT服务器，把项目连接的url改为你的服务器就能测试了。

```java
 // 连接 ActiveMQ 的URI，下面的值改为你服务器的ip+端口
 private String serverUri = "tcp://172.18.90.20:1883";
```



## 使用该库所需要注意的一些小细节

- MqttConnectOptions设置isAutomaticReconnect()为true时可自动重连， 但多个相同的ClientID同时创建连接时会无限的连接中断和自动连接,所以这里我使用的是设备的SN码作为ClientID。
- 网络环境变化时是否会影响自动重连？当网络断开，网络又重新连接上的时候：这个会有对应回调，但是重新连接服务器后，订阅事件不会自动重新订阅，所以可以在自动重连回调接口中去判断是否需要重新订阅。

# 其他常见服务器

很多人也使用Apollo来做服务器，但是该服务器已经改为ActiveMQ 5了，网上的教程不适用，可以参考：http://ddrv.cn/a/152418/



