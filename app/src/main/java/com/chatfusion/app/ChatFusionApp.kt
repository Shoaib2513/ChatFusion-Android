package com.chatfusion.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp

class ChatFusionApp : Application(), Application.ActivityLifecycleCallbacks {
    private var startedActivities = 0

    companion object {
        var currentChatId: String? = null
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        if (startedActivities == 0) {
            updateStatus(true)
        }
        startedActivities++
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities--
        if (startedActivities == 0) {
            updateStatus(false)
        }
    }

    private fun updateStatus(isOnline: Boolean) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val updates = mutableMapOf<String, Any>(
            "online" to isOnline
        )
        if (!isOnline) {
            updates["lastSeen"] = Timestamp.now()
        }
        FirebaseFirestore.getInstance().collection("users").document(uid).update(updates)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
