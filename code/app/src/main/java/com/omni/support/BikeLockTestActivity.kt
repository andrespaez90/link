package com.omni.support

import android.os.Environment
import android.util.Log
import android.widget.Toast
import com.omni.support.ble.core.*
import com.omni.support.ble.protocol.base.model.ReadResult
import com.omni.support.ble.protocol.base.model.StartReadResult
import com.omni.support.ble.protocol.base.model.WriteResult
import com.omni.support.ble.protocol.bike.model.*
import com.omni.support.ble.rover.CommandManager
import com.omni.support.ble.session.sub.Bike3In1Session
import com.omni.support.ble.session.ISessionListener
import com.omni.support.ble.session.SimpleSessionListener
import com.omni.support.ble.task.*
import com.omni.support.ble.task.bike.BLReadTask
import com.omni.support.ble.task.bike.BLUpgradeTask
import com.omni.support.ble.task.bike.BLWriteTask
import com.omni.support.ble.utils.DataUtils
import com.omni.support.widget.base.BaseActivity
import kotlinx.android.synthetic.main.activity_ble.*
import java.io.File

/**
 * @author 邱永恒
 *
 * @time 2019/8/7 17:48
 *
 * @desc
 *
 */
class BikeLockTestActivity : BaseActivity() {
    private lateinit var session: Bike3In1Session

    override fun getLayoutId(): Int {
        return R.layout.activity_ble
    }

    override fun initListener() {
        session = Bike3In1Session.Builder()
//            .address("10:10:00:00:01:D0")
            .address("F3:FF:9E:7B:24:7D")
//            .address("10:30:00:06:03:3E")
//            .address("10:00:30:00:01:F0")
            .deviceKey("yOTmK50z")
            .deviceType("A1")
            .updateKey("Vgz7")
            .build()

        session.setListener(object : SimpleSessionListener() {
            override fun onConnecting() {
                Log.e("Bike3In1Session", "onConnecting");
                Toast.makeText(this@BikeLockTestActivity, "onConnecting...", Toast.LENGTH_SHORT).show()
            }

            override fun onConnected() {
                Log.e("Bike3In1Session", "onConnected");
                Toast.makeText(this@BikeLockTestActivity, "onConnected", Toast.LENGTH_SHORT).show()
            }

            override fun onDisconnected() {
                Log.e("Bike3In1Session", "onDisconnected");
                Toast.makeText(this@BikeLockTestActivity, "onDisconnected", Toast.LENGTH_SHORT).show()
            }

            override fun onDeviceNoSupport() {
                Log.e("Bike3In1Session", "onDeviceNoSupport");
                Toast.makeText(this@BikeLockTestActivity, "onDeviceNoSupport", Toast.LENGTH_SHORT).show()
            }

            override fun onReady() {
                Log.e("Bike3In1Session", "onReady");
                Toast.makeText(this@BikeLockTestActivity, "onReady", Toast.LENGTH_SHORT).show()
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
            }
        })

        btn_connect.setOnClickListener {
            session.connect()
        }

        btn_disconnect.setOnClickListener {
            session.disConnect()
        }

        btn_key.setOnClickListener {
            session.initConfig()
        }

        btn_unlock.setOnClickListener {
            session.call(CommandManager.blCommand.unlock(0, System.currentTimeMillis() / 1000, 0))
                .timeout(3000)
                .enqueue(object : SessionCallback<Boolean> {
                    override fun onSuccess(call: ISessionCall<Boolean>, data: IResp<Boolean>) {
                        val isSuccess = data.getResult() ?: false
                        Toast.makeText(this@BikeLockTestActivity, if (isSuccess) "开锁成功" else "开锁失败", Toast.LENGTH_SHORT)
                            .show()
                        // 开锁回复
                        session.call(CommandManager.blCommand.unlockReply()).execute()
                    }

                    override fun onFailure(call: ISessionCall<Boolean>, e: Throwable) {
                    }
                })
        }

        btn_info.setOnClickListener {
            session.call(CommandManager.blCommand.getLockInfo())
                .enqueue(object : SessionCallback<BLInfoResult> {
                    override fun onSuccess(call: ISessionCall<BLInfoResult>, data: IResp<BLInfoResult>) {
                        val result = data.getResult()
                        if (result != null) {
                            Log.d("=====", result.toString())
                        }
                    }

                    override fun onFailure(call: ISessionCall<BLInfoResult>, e: Throwable) {

                    }
                })
        }

        btn_fw_info.setOnClickListener {
            session.call(CommandManager.blCommand.startRead())
                .enqueue(object : SessionCallback<StartReadResult> {
                    override fun onSuccess(call: ISessionCall<StartReadResult>, data: IResp<StartReadResult>) {
                        val result = data.getResult()
                        if (result != null) {
                            Log.d("=====", result.toString())
                        }
                    }

                    override fun onFailure(call: ISessionCall<StartReadResult>, e: Throwable) {
                    }
                })
        }

        btn_fw.setOnClickListener {
            session.call(CommandManager.blCommand.read(0, session.getDeviceType()))
                .enqueue(object : SessionCallback<ReadResult> {
                    override fun onSuccess(call: ISessionCall<ReadResult>, data: IResp<ReadResult>) {
                        val result = data.getResult()
                        if (result != null) {
                            Log.d("=====", result.toString())
                        }
                    }

                    override fun onFailure(call: ISessionCall<ReadResult>, e: Throwable) {
                    }
                })
        }

        btn_get_fw_info.setOnClickListener {
            BLReadTask(session, object : OnProgressListener<String> {
                override fun onProgress(progress: Progress) {
                    Log.d("=====", "percent = ${progress.getPercent()}%, speed = ${progress.getSpeed()}b/s")
                }

                override fun onStatusChanged(status: Int, e: Throwable?) {
                    Log.e("=====", "status = $status")
                }

                override fun onComplete(t: String) {
                    Log.d("=====", "complete = $t")
                }
            }).start()
        }

        btn_start_modify.setOnClickListener {
            val unPack = DataUtils.unPack("IP:egosystem.egomovement.com,PORT:9679,IPMODE:1,")
            session.call(CommandManager.blCommand.startWrite(unPack.totalPack, unPack.crc, session.getDeviceType()))
                .enqueue(object : SessionCallback<WriteResult> {
                    override fun onSuccess(call: ISessionCall<WriteResult>, data: IResp<WriteResult>) {
                        val result = data.getResult()
                        if (result != null) {
                            Log.d("=====", result.toString())
                        }
                        session.call(CommandManager.blCommand.write(0, unPack.pack[0]))
                            .enqueue(object : SessionCallback<WriteResult> {
                                override fun onSuccess(call: ISessionCall<WriteResult>, data: IResp<WriteResult>) {
                                    val result = data.getResult()
                                    if (result != null) {
                                        Log.d("=====", result.toString())
                                    }
                                }

                                override fun onFailure(call: ISessionCall<WriteResult>, e: Throwable) {
                                }
                            })
                    }

                    override fun onFailure(call: ISessionCall<WriteResult>, e: Throwable) {
                    }
                })
        }

        btn_send_pack.setOnClickListener {
            BLWriteTask(
                session,
                "IP:egosystem.egomovement.com,PORT:9679,IPMODE:1,",
                session.getDeviceType(),
                object :
                    OnProgressListener<Boolean> {
                    override fun onProgress(progress: Progress) {
                        Log.d("=====", "percent = ${progress.getPercent()}%, speed = ${progress.getSpeed()}b/s")
                    }

                    override fun onStatusChanged(status: Int, e: Throwable?) {
                        Log.e("=====", "status = $status")
                    }

                    override fun onComplete(t: Boolean) {
                        Log.d("=====", "complete = $t")
                    }
                }).start()
        }

        btn_upgrade.setOnClickListener {
            val filePath = Environment.getExternalStorageDirectory().toString()

            val file = File(filePath, "upgrade.txt")
            BLUpgradeTask(
                session,
                file,
                session.getDeviceType(),
                session.getUpdateKey(),
                object :
                    OnProgressListener<Boolean> {
                    override fun onProgress(progress: Progress) {

                    }

                    override fun onStatusChanged(status: Int, e: Throwable?) {
                        Log.e("=====", "status = $status")
                    }

                    override fun onComplete(t: Boolean) {
                        Log.d("=====", "complete = $t")
                    }
                }).start()
        }

        btn_shutdown.setOnClickListener {
            session.call(CommandManager.blCommand.shutdown())
                .enqueue(object : SessionCallback<BLShutdownResult> {
                    override fun onSuccess(call: ISessionCall<BLShutdownResult>, data: IResp<BLShutdownResult>) {
                        val result = data.getResult()
                        if (result != null) {
                            Log.d("=====", result.toString())
                        }
                    }

                    override fun onFailure(call: ISessionCall<BLShutdownResult>, e: Throwable) {
                    }
                })
        }

        btn_get_log.setOnClickListener {
            session.call(CommandManager.blCommand.getLog())
                .asyncTimeout(10000)
                .asyncCall(object : AsyncCallback<String> {
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
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        session.disConnect()
    }
}