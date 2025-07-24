package com.hwork.simakohost.utils

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.hwork.simakohost.R

class MainActivityMVC(inflater: LayoutInflater, parent: ViewGroup?, application: AppCompatActivity) :
    MainActivityInterface {

    var rootView: View
    var application: AppCompatActivity
    lateinit var enableSimakoHostButton: View

    init {
        rootView = inflater.inflate(R.layout.activity_main, parent, false)
        this.application = application
        this.checkDefault()
    }

    fun checkDefault() {
        //TODO: Correct the default detection, it is done multiple time in multiple ways.
        if (!areWeTheDefaultMessagingApp()) {
            requestsimakohostSelection()
        }
    }
    override fun getRootView_(): View = rootView

    override fun getContext(): Context = rootView.context

    override fun setListeners() {
        this.checkDefault()

    }

    override fun areWeTheDefaultMessagingApp(): Boolean {
            val packageName = application.packageName;
            val smsPackage = Telephony.Sms.getDefaultSmsPackage(application)
            return packageName == smsPackage || smsPackage!=null
    }

    override fun requestsimakohostSelection() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            val roleManager = application.getSystemService(RoleManager::class.java);
            if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                   /* Toast.makeText(
                        application,
                        "SimakoHost set as default.",
                        Toast.LENGTH_SHORT
                    ).show()
//                    application.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))*/
                } else {
                    application.startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS),1)
                }
            }
        } else {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, application.packageName)
            application.startActivity(intent)
        }
    }
}