package com.omni.support

import android.util.Log
import android.widget.Toast
import com.omni.support.ble.core.IResp
import com.omni.support.ble.core.ISessionCall
import com.omni.support.ble.core.NotifyCallback
import com.omni.support.ble.core.SessionCallback
import com.omni.support.ble.protocol.bike.model.BLLockResult
import com.omni.support.ble.protocol.bsj.model.BSJBatteryStatusResult
import com.omni.support.ble.protocol.bsj.model.BSJTokenResult
import com.omni.support.ble.rover.CommandManager
import com.omni.support.ble.session.ISessionListener
import com.omni.support.ble.session.SimpleSessionListener
import com.omni.support.ble.session.sub.BSJSession
import com.omni.support.ble.utils.BSJUtils
import com.omni.support.ble.utils.BufferBuilder
import com.omni.support.ble.utils.BufferConverter
import com.omni.support.widget.base.BaseActivity
import kotlinx.android.synthetic.main.activity_bsj.*
import java.util.*

/**
 * @author 邱永恒
 *
 * @time 2019/9/6 11:34
 *
 * @desc
 *
 */
class BSJTestActivity : BaseActivity() {
    private lateinit var session: BSJSession

    override fun getLayoutId(): Int {
        return R.layout.activity_bsj
    }

    override fun initListener() {
        session = BSJSession.Builder()
//            .address("42:53:4A:00:06:3E")
//            .address("42:53:4A:00:07:6E")
            .address("42:53:4A:00:07:6D")
            .aesKey(byteArrayOf(32, 87, 47, 82, 54, 75, 63, 71, 48, 80, 65, 88, 17, 99, 45, 43))
            .build()

        session.setListener(object : SimpleSessionListener() {
            override fun onConnecting() {
                Toast.makeText(this@BSJTestActivity, "正在连接...", Toast.LENGTH_SHORT).show()
            }

            override fun onConnected() {
                Toast.makeText(this@BSJTestActivity, "连接成功", Toast.LENGTH_SHORT).show()
            }

            override fun onDisconnected() {
                Toast.makeText(this@BSJTestActivity, "断开连接", Toast.LENGTH_SHORT).show()
            }

            override fun onDeviceNoSupport() {
                Toast.makeText(this@BSJTestActivity, "设备不支持", Toast.LENGTH_SHORT).show()
            }

            override fun onReady() {
                // 关锁监听

            }
        })

        btn_connect.setOnClickListener {
            session.connect()
        }

        btn_disconnect.setOnClickListener {
            session.disConnect()
        }

        btn_token.setOnClickListener {
            session. call(CommandManager.bsjCommand.getToken())
                .timeout(2000)
                .enqueue(object : SessionCallback<BSJTokenResult> {
                    override fun onSuccess(
                        call: ISessionCall<BSJTokenResult>,
                        data: IResp<BSJTokenResult>
                    ) {
                        val result = data.getResult()
                        if (result != null) {
                            Log.d("=====", result.toString())
                        }
                    }

                    override fun onFailure(call: ISessionCall<BSJTokenResult>, e: Throwable) {

                    }
                })
        }

        btn_unlock.setOnClickListener {
            session.call(CommandManager.bsjCommand.unlock(BSJUtils.toByteArray("000000")))
                .timeout(3000)
                .retry(0)
                .enqueue(object : SessionCallback<Boolean> {
                    override fun onSuccess(call: ISessionCall<Boolean>, data: IResp<Boolean>) {
                        val result = data.getResult()
                        if (result != null) {
                            Log.d("=====", result.toString())
                        }
                    }

                    override fun onFailure(call: ISessionCall<Boolean>, e: Throwable) {
                    }
                })
        }

        btn_battery_status.setOnClickListener {
            session.call(CommandManager.bsjCommand.getBatteryStatus())
                .enqueue(object : SessionCallback<BSJBatteryStatusResult> {
                    override fun onSuccess(
                        call: ISessionCall<BSJBatteryStatusResult>,
                        data: IResp<BSJBatteryStatusResult>
                    ) {
                        val result = data.getResult()
                        if (result != null) {
                            Log.d("=====", result.toString())
                        }
                    }

                    override fun onFailure(
                        call: ISessionCall<BSJBatteryStatusResult>,
                        e: Throwable
                    ) {
                    }
                })
        }

        btn_modify_pwd.setOnClickListener {
            session.call(
                CommandManager.bsjCommand.modifyUnlockKey(BSJUtils.toByteArray("k6xzyj")),
                CommandManager.bsjCommand.modifyUnlockKey(BSJUtils.toByteArray("000000"))
            )
                .retry(0)
                .enqueue(object : SessionCallback<Boolean> {
                    override fun onSuccess(call: ISessionCall<Boolean>, data: IResp<Boolean>) {
                        val result = data.getResult()
                        if (result != null) {
                            Log.d("=====", result.toString())
                        }
                    }

                    override fun onFailure(call: ISessionCall<Boolean>, e: Throwable) {
                    }
                })
        }

        btn_reboot.setOnClickListener {
            session.call(CommandManager.bsjCommand.reboot())
                .enqueue(object : SessionCallback<Boolean> {
                    override fun onSuccess(call: ISessionCall<Boolean>, data: IResp<Boolean>) {
                        val result = data.getResult()
                        if (result != null) {
                            Log.d("=====", result.toString())
                        }
                    }

                    override fun onFailure(call: ISessionCall<Boolean>, e: Throwable) {
                    }
                })
        }

        btn_modify_name.setOnClickListener {
            val converter = BufferConverter(18)
            converter.putString("ULock")
            val buffer = converter.buffer()
            val data = BufferConverter(buffer)
            session.call(
                CommandManager.bsjCommand.modifyReciverName(data.getBytes(9)),
                CommandManager.bsjCommand.modifyReciverName(data.getBytes(9))
            )
                .timeout(3000)
                .retry(0)
                .enqueue(object : SessionCallback<Boolean> {
                    override fun onSuccess(call: ISessionCall<Boolean>, data: IResp<Boolean>) {
                        val result = data.getResult()
                        if (result != null) {
                            Log.d("=====", result.toString())
                        }
                    }

                    override fun onFailure(call: ISessionCall<Boolean>, e: Throwable) {
                    }
                })
        }
    }
}