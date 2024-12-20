package com.omni.support

import android.util.Log
import android.widget.Toast
import com.omni.support.ble.core.*
import com.omni.support.ble.protocol.base.model.KeyResult
import com.omni.support.ble.protocol.base.model.OldDataResult
import com.omni.support.ble.protocol.carport.model.*
import com.omni.support.ble.rover.CommandManager
import com.omni.support.ble.session.BaseSession
import com.omni.support.ble.session.ISessionListener
import com.omni.support.ble.session.SimpleSessionListener
import com.omni.support.ble.session.sub.CarportKeySession
import com.omni.support.ble.session.sub.CarportSession
import com.omni.support.ble.task.OnProgressListener
import com.omni.support.ble.task.Progress
import com.omni.support.ble.task.carport.CarportReadTask
import com.omni.support.ble.task.carport.CarportWriteTask
import com.omni.support.ble.utils.HexString
import com.omni.support.widget.base.BaseActivity
import kotlinx.android.synthetic.main.activity_carport.*

/**
 * @author 邱永恒
 *
 * @time 2019/8/31 14:01
 *
 * @desc
 *
 */
class CarportTestActivity : BaseActivity() {
    private lateinit var session: BaseSession
    private lateinit var keySession: BaseSession

    override fun getLayoutId(): Int {
        return R.layout.activity_carport
    }

    override fun initListener() {
        session = CarportSession.Builder()
//            .address("D1:5A:1F:61:85:0B")
//            .address("FC:A8:87:FF:66:75")
//            .address("FC:A0:2E:40:44:39")
            .address("F8:FF:1D:FD:C7:DC")
            .deviceKey("OmniW4GX")
//            .deviceType("80")
            .deviceType("91")
            .updateKey("Vgz7")
            .build()

        keySession = CarportKeySession.Builder()
            .address("E1:8A:81:D5:C3:72")
            .deviceKey("OmniW4GX")
            .deviceType("80")
            .updateKey("Vgz7")
            .build()

        session.setListener(object : SimpleSessionListener() {
            override fun onConnecting() {
                Toast.makeText(this@CarportTestActivity, "正在连接...", Toast.LENGTH_SHORT).show()
            }

            override fun onConnected() {
                Toast.makeText(this@CarportTestActivity, "连接成功", Toast.LENGTH_SHORT).show()
            }

            override fun onDisconnected() {
                Toast.makeText(this@CarportTestActivity, "断开连接", Toast.LENGTH_SHORT).show()
            }

            override fun onDeviceNoSupport() {
                Toast.makeText(this@CarportTestActivity, "设备不支持", Toast.LENGTH_SHORT).show()
            }

            override fun onReady() {
                Log.d("=====", "onReady()")
            }
        })

        btn_connect.setOnClickListener {
            session.connect()
        }

        btn_disconnect.setOnClickListener {
            session.disConnect()
        }

        btn_key.setOnClickListener {
            session.call(CommandManager.carportCommand.getKey("OmniW4GX"))
                .enqueue(object : SessionCallback<KeyResult> {
                    override fun onSuccess(call: ISessionCall<KeyResult>, data: IResp<KeyResult>) {
                        val result = data.getResult()
                        if (result != null) {
                            Log.d("=====", result.toString())
                        }
                    }

                    override fun onFailure(call: ISessionCall<KeyResult>, e: Throwable) {
                    }
                })
        }

        btn_unlock.setOnClickListener {
            session.call(
                CommandManager.carportCommand.carportDown(
                    0x01,
                    0,
                    System.currentTimeMillis() / 1000
                )
            ).asyncCall(object : AsyncCallback<CarportUnlockResult> {
                override fun onStarted(success: Boolean) {
                    Log.d("=====", "开始")
                }

                override fun onReceiving(
                    call: ISessionCall<CarportUnlockResult>,
                    data: IResp<CarportUnlockResult>
                ) {
                    val result = data.getResult()
                    if (result != null) {
                        Log.d("=====", result.toString())

                        if (result.isSuccess) {
                            call.cancel()
                            session.call(CommandManager.carportCommand.downReply()).execute()
                        }
                    }
                }

                override fun onReceived() {
                    Log.d("=====", "接收完毕")
                }

                override fun onTimeout() {
                    Log.d("=====", "接收超时")
                }
            })
        }

        btn_lock.setOnClickListener {
            session.call(CommandManager.carportCommand.carportUp())
                .asyncTimeout(15_000)
                .asyncCall(object : AsyncCallback<CarportLockResult> {
                    override fun onStarted(success: Boolean) {
                        Log.d("=====", "开始")
                    }

                    override fun onReceiving(
                        call: ISessionCall<CarportLockResult>,
                        data: IResp<CarportLockResult>
                    ) {
                        val result = data.getResult()
                        if (result != null) {
                            Log.d("=====", result.toString())

                            if (result.isSuccess) {
                                call.cancel()
                                session.call(CommandManager.carportCommand.upReply()).execute()
                            }
                        }
                    }

                    override fun onReceived() {
                        Log.d("=====", "接收完毕")
                    }

                    override fun onTimeout() {
                        Log.d("=====", "接收超时")
                    }
                })
        }

        btn_modify_key.setOnClickListener {
            session.call(CommandManager.carportCommand.modifyDeviceKey(deviceKey = "OmniW4GX"))
                .enqueue(object : SessionCallback<CarportModifyDeviceKeyResult> {
                    override fun onSuccess(
                        call: ISessionCall<CarportModifyDeviceKeyResult>,
                        data: IResp<CarportModifyDeviceKeyResult>
                    ) {
                        val result = data.getResult()
                        if (result != null) {
                            Log.d("=====", result.toString())
                        }
                        session.call(CommandManager.carportCommand.modifyDeviceKeyReply()).execute()
                    }

                    override fun onFailure(
                        call: ISessionCall<CarportModifyDeviceKeyResult>,
                        e: Throwable
                    ) {
                    }
                })
        }

        btn_get_old_data.setOnClickListener {
            session.call(CommandManager.carportCommand.getOldData())
                .enqueue(object : SessionCallback<OldDataResult> {
                    override fun onSuccess(
                        call: ISessionCall<OldDataResult>,
                        data: IResp<OldDataResult>
                    ) {
                        val result = data.getResult()
                        if (result != null) {
                            Log.d("=====", result.toString())
                        }
                    }

                    override fun onFailure(call: ISessionCall<OldDataResult>, e: Throwable) {
                    }
                })
        }

        btn_clean_old_data.setOnClickListener {
            session.call(CommandManager.carportCommand.cleanOldData())
                .enqueue(object : SessionCallback<CarportCleanOldDataResult> {
                    override fun onSuccess(
                        call: ISessionCall<CarportCleanOldDataResult>,
                        data: IResp<CarportCleanOldDataResult>
                    ) {
                        val result = data.getResult()
                        if (result != null) {
                            Log.d("=====", result.toString())
                        }
                    }

                    override fun onFailure(
                        call: ISessionCall<CarportCleanOldDataResult>,
                        e: Throwable
                    ) {
                    }
                })
        }

        btn_pair.setOnClickListener {
            session.call(
                CommandManager.carportCommand.pair(
                    mac = HexString.toBytes(
                        "FF:FF:FF:FF:FF:FF",
                        ":"
                    )
                )
            )
                .enqueue(object : SessionCallback<CarportPairResult> {
                    override fun onSuccess(
                        call: ISessionCall<CarportPairResult>,
                        data: IResp<CarportPairResult>
                    ) {
                        val result = data.getResult()
                        if (result != null) {
                            Log.d("=====", result.toString())
                        }
                    }

                    override fun onFailure(call: ISessionCall<CarportPairResult>, e: Throwable) {
                    }
                })
        }

        btn_rc_info.setOnClickListener {
            session.call(CommandManager.carportCommand.getRCInfo())
                .enqueue(object : SessionCallback<CarportGetMacResult> {
                    override fun onSuccess(
                        call: ISessionCall<CarportGetMacResult>,
                        data: IResp<CarportGetMacResult>
                    ) {
                        val result = data.getResult()
                        if (result != null) {
                            Log.d("=====", result.toString())
                        }
                    }

                    override fun onFailure(call: ISessionCall<CarportGetMacResult>, e: Throwable) {
                    }
                })
        }

        btn_local_mac.setOnClickListener {
            session.call(CommandManager.carportCommand.getMac())
                .enqueue(object : SessionCallback<CarportGetMacResult> {
                    override fun onSuccess(
                        call: ISessionCall<CarportGetMacResult>,
                        data: IResp<CarportGetMacResult>
                    ) {
                        val result = data.getResult()
                        if (result != null) {
                            Log.d("=====", result.toString())
                        }
                    }

                    override fun onFailure(call: ISessionCall<CarportGetMacResult>, e: Throwable) {
                    }
                })
        }

        btn_rc_mode.setOnClickListener {
            session.call(CommandManager.carportCommand.setRcMode(data = 0x01))
                .enqueue(object : SessionCallback<CarportSetModeResult> {
                    override fun onSuccess(
                        call: ISessionCall<CarportSetModeResult>,
                        data: IResp<CarportSetModeResult>
                    ) {
                        val result = data.getResult()
                        if (result != null) {
                            Log.d("=====", result.toString())
                        }
                    }

                    override fun onFailure(call: ISessionCall<CarportSetModeResult>, e: Throwable) {
                    }
                })
        }

        btn_info.setOnClickListener {
            session.call(CommandManager.carportCommand.getLockInfo())
                .enqueue(object : SessionCallback<CarportLockInfoResult> {
                    override fun onSuccess(
                        call: ISessionCall<CarportLockInfoResult>,
                        data: IResp<CarportLockInfoResult>
                    ) {
                        val result = data.getResult()
                        if (result != null) {
                            Log.d("=====", result.toString())
                        }
                    }

                    override fun onFailure(
                        call: ISessionCall<CarportLockInfoResult>,
                        e: Throwable
                    ) {
                    }
                })
        }

        btn_pair_rc.setOnClickListener {
            keySession.connect()
            keySession.setListener(object : SimpleSessionListener() {
                override fun onConnecting() {
                }

                override fun onConnected() {
                }

                override fun onDisconnected() {
                }

                override fun onDeviceNoSupport() {
                }

                override fun onReady() {
                    keySession.call(
                        CommandManager.carportCommand.pair(
                            mac = HexString.toBytes(
                                session.getMac(),
                                ":"
                            )
                        )
                    ).enqueue(object : SessionCallback<CarportPairResult> {
                        override fun onSuccess(
                            call: ISessionCall<CarportPairResult>,
                            data: IResp<CarportPairResult>
                        ) {
                            Log.d("=====", data.getResult().toString())

                            session.call(
                                CommandManager.carportCommand.pair(
                                    mac = HexString.toBytes(
                                        keySession.getMac(),
                                        ":"
                                    )
                                )
                            ).enqueue(object :
                                SessionCallback<CarportPairResult> {
                                override fun onSuccess(
                                    call: ISessionCall<CarportPairResult>,
                                    data: IResp<CarportPairResult>
                                ) {
                                    Log.d("=====", data.getResult().toString())
                                }

                                override fun onFailure(
                                    call: ISessionCall<CarportPairResult>,
                                    e: Throwable
                                ) {
                                }
                            })
                        }

                        override fun onFailure(
                            call: ISessionCall<CarportPairResult>,
                            e: Throwable
                        ) {
                        }
                    })
                }
            })
        }

        btn_get_fw_info.setOnClickListener {
            CarportReadTask(session, object : OnProgressListener<String> {
                override fun onProgress(progress: Progress) {
                    Log.d("=====", "progress: $progress")
                }
                override fun onStatusChanged(status: Int, e: Throwable?) {
                    Log.d("=====", "status change: $status")
                }
                override fun onComplete(t: String) {
                    Log.d("=====", "读取完毕: $t")
                }
            }).start()
        }

        btn_send_pack.setOnClickListener {
            CarportWriteTask(session, "APN:CMNET,IPMODE:1,IP:server.omnibike.net,PORT:10174,", session.getDeviceType(), session.getUpdateKey(), object :
                OnProgressListener<Boolean> {
                override fun onProgress(progress: Progress) {
                    Log.d("=====", "progress: $progress")
                }

                override fun onStatusChanged(status: Int, e: Throwable?) {
                    Log.d("=====", "status change: $status")
                }

                override fun onComplete(t: Boolean) {
                    Log.d("=====", "onComplete: $t")
                }
            }).start()
        }

        btn_get_log.setOnClickListener {
            session.call(CommandManager.carportCommand.getLog(session.getDeviceType()))
                .asyncTimeout((4 * 60 * 1000).toLong())
                .asyncCall(object : AsyncCallback<String> {
                    override fun onStarted(b: Boolean) {

                    }

                    override fun onReceiving(
                        iSessionCall: ISessionCall<String>,
                        iResp: IResp<String>
                    ) {
                        Log.d("=====", "开始读取:" + iResp.getResult()!!)
                    }

                    override fun onReceived() {

                    }

                    override fun onTimeout() {

                    }
                })
        }
    }
}