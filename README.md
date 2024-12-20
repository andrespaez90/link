# 蓝牙库的使用流程

## 框架概述
本框架将蓝牙操作抽象成三层, 分别是:
1. 链路层: 负责底层的蓝牙连接通信, 与业务无关
2. 协议层: 负责实现不同蓝牙设备的协议, 数据的打包和拆包
3. 会话层: 负责代码业务的实现, 超时, 重试, 同异步请求等操作

### 1. 集成到Android studio
```
implementation 'com.omni.support:ble:1.0.9'
```

### 2. 初始化
```
// 在application中初始化蓝牙模块
BleModuleHelper.init // kotlin
BleModuleHelper.INSTANCE.init(this) // java
```

### 3. 创建会话session
每个设备创建一个会话, 会话管理设备的连接通信操作
```
// 什么设备, 就要创建对应的设备的session, 这里创建的是马蹄锁的会话
// session持有设备的一些配置信息
val session = Bike3In1Session.Builder()
    .address("10:30:00:10:0C:A5")
    .deviceKey("yOTmK50z")
    .deviceType("A1")
    .updateKey("Vgz7")
    .build()
    
// session可以设置监听器, 监听设备的一些状态, 方便开发者处理
session.setListener(object : ISessionListener {
    override fun onConnecting() {
        // 开始连接
    }
    override fun onConnected() {
        // 蓝牙连接成功, 可能还不能进行正常通讯
    }
    override fun onDisconnected() {
        // 断开连接
    }
    override fun onDeviceNoSupport() {
        // 设备UUID不支持, 一般是连错设备了
    }
    override fun onReady() {
        // 设备已准备好进行通信了, 可以在这里设置一下类似关锁的监听器
    }
})
```

### 4. 设备的操作
```
// 连接设备
session.connect()

// 断开连接
session.disConnect()

// 是否已连接
session.isConnect()
```

### 5. 指令发送
1. 指令的定义
```
// 通过接口定义
@CommandID(0x21)
fun unlock(@S32 uid: Int, @U32 timestamp: Long, @U8 optType: Int): Command<Boolean>
```
2. 异步发送指令, 不会阻塞主线程
```
session.call(CommandManager.blCommand.unlock(0, System.currentTimeMillis() / 1000, 0))
    .timeout(3000) // 设置超时时间
    .retry(0) // 设置重试次数
    .enqueue(object : SessionCallback<Boolean> {
        override fun onSuccess(call: ISessionCall<Boolean>, data: IResp<Boolean>) {
            val isSuccess = data.getResult() ?: false
            Toast.makeText(this@BikeLockTestActivity, if (isSuccess) "开锁成功" else "开锁失败", Toast.LENGTH_SHORT)
                .show()
        }

        override fun onFailure(call: ISessionCall<Boolean>, e: Throwable) {
        }
    })
```
3. 同步发送指令
```
session.call(CommandManager.blCommand.unlockReply()).execute()
```

### 6. 指令的订阅
有些指令, 不需要发送, 只需要等待设备返回结果, 可以使用subscribe操作符
```
// 关锁监听, 可以在onReady中设置监听
session.call(CommandManager.blCommand.lock())
    .subscribe(object : NotifyCallback<BLLockResult> {
        override fun onSuccess(call: ISessionCall<BLLockResult>, data: IResp<BLLockResult>) {
            val result = data.getResult()
            if (result != null) {
                Log.d("=====", result.toString())
            }
            // 关锁回复
            session.call(CommandManager.blCommand.lockReply()).execute()
        }
    })
```

### 7. 一个指令接收多条回调
例如读取日志, 会返回多条数据, 使用asynCall操作符
```
session.call(CommandManager.blCommand.getLog(session.getDeviceType()))
    .asyncTimeout(10000) // 指定读取时间
    .asyncCall(object : AsynCallback<String> {
        override fun onTimeout() {
        }
        override fun onStarted(success: Boolean) {
        }
        override fun onReceiving(call: ISessionCall<String>, data: IResp<String>) {
            Log.d("=====", "${data.getResult()}")
        }
        override fun onReceived() {
        }
    })
```
