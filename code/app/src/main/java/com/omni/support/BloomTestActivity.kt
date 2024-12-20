package com.omni.support

import android.bluetooth.BluetoothDevice
import android.util.Log
import android.widget.Toast
import com.omni.support.ble.core.IResp
import com.omni.support.ble.core.ISessionCall
import com.omni.support.ble.core.NotifyCallback
import com.omni.support.ble.protocol.bloom.model.BloomResult
import com.omni.support.ble.rover.CommandManager
import com.omni.support.ble.session.ISessionListener
import com.omni.support.ble.session.SimpleSessionListener
import com.omni.support.ble.session.sub.BloomSession
import com.omni.support.ble.session.sub.PersonalLockSession
import com.omni.support.widget.base.BaseActivity
import kotlinx.android.synthetic.main.activity_bloom.*

/**
 * @author 邱永恒
 *
 * @time 2019/8/7 17:48
 *
 * @desc
 *
 */
class BloomTestActivity : BaseActivity() {
    private lateinit var session: PersonalLockSession

    override fun getLayoutId(): Int {
        return R.layout.activity_bloom
    }

    override fun initListener() {
        session = PersonalLockSession.Builder()
            .address("DF:D2:B2:DB:0E:87")
//            .address("E2:A1:C2:1A:44:F5")
//            .address("CE:46:9F:41:66:F9")
            .keyOrg(byteArrayOf(0x61, 0x66, 0x6B, 0x33, 0x74, 0x79, 0x73, 0x77, 0x34, 0x70, 0x73, 0x6B, 0x32, 0x36, 0x68, 0x6A))
//            .address("ED:E5:16:EE:B2:74")
            .build()

        session.setListener(object : SimpleSessionListener() {
            override fun onError(device: BluetoothDevice, message: String, errorCode: Int) {

            }

            override fun onConnecting() {
                Toast.makeText(this@BloomTestActivity, "正在连接...", Toast.LENGTH_SHORT).show()
            }

            override fun onConnected() {
                Toast.makeText(this@BloomTestActivity, "连接成功", Toast.LENGTH_SHORT).show()
            }

            override fun onDisconnected() {
                Toast.makeText(this@BloomTestActivity, "断开连接", Toast.LENGTH_SHORT).show()
            }

            override fun onDeviceNoSupport() {
                Toast.makeText(this@BloomTestActivity, "设备不支持", Toast.LENGTH_SHORT).show()
            }

            override fun onReady() {
                // 数据监听
                session.call(CommandManager.bloomCommand.recv())
                    .subscribe(object : NotifyCallback<BloomResult> {
                        override fun onSuccess(
                            call: ISessionCall<BloomResult>,
                            data: IResp<BloomResult>
                        ) {
                            val result = data.getResult()
                            if (result != null) {
                                Log.d("=====", result.toString())
                            }
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

        btn_unlock.setOnClickListener {
            session.call(CommandManager.bloomCommand.unlock()).execute()
        }

        btn_app_btn.setOnClickListener {
            session.call(CommandManager.bloomCommand.setUnlockMode(0x10)).execute()
        }

        btn_app.setOnClickListener {
            session.call(CommandManager.bloomCommand.setUnlockMode(0x20)).execute()
        }

        btn_clean.setOnClickListener {
            session.call(CommandManager.bloomCommand.deleteKey(0)).execute()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        session.disConnect()
    }
}