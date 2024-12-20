package com.omni.support.ble.session

import android.bluetooth.BluetoothDevice
import android.os.Handler
import android.text.TextUtils
import android.util.Log
import android.util.SparseArray
import com.omni.support.ble.core.*
import com.omni.support.ble.exception.BleException
import com.omni.support.ble.link.Link
import com.omni.support.ble.link.LinkGlobalSetting
import com.omni.support.ble.profile.SimpleBleCallbacks

/**
 * @author 邱永恒
 *
 * @time 2019/8/7 11:45
 *
 * @desc 回话基类
 *
 */
abstract class BaseSession(val link: Link, val build: BaseBuilder<*>) : ISession, ILink.OnReceivedListener {
    protected var sessionListener: ISessionListener? = null
    private val responses = SparseArray<IResponse>()
    private val dispatcher = Dispatcher()
    protected var key: Int = 0
    protected val handler = Handler()
    private val callback = object : SimpleBleCallbacks() {
        override fun onDeviceDisconnected(device: BluetoothDevice) {
            handler.removeCallbacksAndMessages(null)
            sessionListener?.onDisconnected()
        }

        override fun onDeviceConnecting(device: BluetoothDevice) {
            sessionListener?.onConnecting()
        }

        override fun onDeviceConnected(device: BluetoothDevice) {
            sessionListener?.onConnected()
        }

        override fun onDeviceNotSupported(device: BluetoothDevice) {
            sessionListener?.onDeviceNoSupport()
        }

        override fun onDeviceNoFound() {
            sessionListener?.onDeviceNoFound()
        }

        override fun onError(device: BluetoothDevice, message: String, errorCode: Int) {
            sessionListener?.onError(device, message, errorCode)
        }

        override fun onDeviceReady(device: BluetoothDevice) {
            initConfig()
        }
    }

    init {
        link.setOnReceivedListener(this)
        link.setOnBleOperationCallback(callback)
    }

    override fun connect() {
        link.open()
    }

    override fun initConfig() {
    }

    override fun disConnect() {
        handler.removeCallbacksAndMessages(null)
        link.close()
    }

    override fun isConnect(): Boolean {
        return link.isOpen()
    }

    override fun <T> call(command: ICommand<T>): ISessionCall<T> {
        val call = SessionCall(command).session(this)
        return call.dispatcher(dispatcher)
    }

    override fun <T> call(vararg command: ICommand<T>): ISessionCall<T> {
        val call = BSJSessionCall(*command).session(this)
        return call.dispatcher(dispatcher)
    }

    override fun setListener(listener: ISessionListener?) {
        this.sessionListener = listener
    }

    fun getDeviceType(): Int {
        val deviceType = build.deviceType
        if (TextUtils.isEmpty(deviceType)) {
            throw BleException(BleException.ERR_DATA_NULL, "没有设置Device type")
        }
        return try {
            deviceType!!.toInt(16)
        } catch (e: Exception) {
            throw BleException(BleException.ERR_DATA_FORMAT_ERROR, "不是数字")
        }
    }

    fun getUpdateKey(): String {
        val updateKey = build.updateKey
        if (TextUtils.isEmpty(updateKey)) {
            throw BleException(BleException.ERR_DATA_NULL, "没有设置Device type")
        }
        return updateKey!!
    }

    fun getMac(): String {
        val mac = build.mac
        if (TextUtils.isEmpty(mac)) {
            throw BleException(BleException.ERR_DATA_NULL, "没有设置MAC")
        }

        return mac!!
    }

    override fun getResponse(commandId: Int): IResponse {
        var response: IResponse? = responses.get(commandId)
        if (response == null) {
            response = Response()
            response.id = commandId
            responses.put(commandId, response)
        }
        return response
    }

    override fun isReadLog(isReadLog: Boolean) {
        link.isReadLog = isReadLog
    }

    protected fun debug(msg: String) {
        if (LinkGlobalSetting.DEBUG) {
            Log.d(this.javaClass.simpleName, msg)
        }
    }

}