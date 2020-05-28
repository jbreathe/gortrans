package net.kriomant.gortrans

import android.support.v7.app.AppCompatActivity
import android.widget.Toast

class ActionBarProcessIndicator(activity: AppCompatActivity) extends DataManager.ProcessIndicator {
  override def startFetch() {
    Toast.makeText(activity, R.string.background_update_started, Toast.LENGTH_SHORT).show()
  }

  override def stopFetch() {
  }

  override def onSuccess() {
    Toast.makeText(activity, R.string.background_update_stopped, Toast.LENGTH_SHORT).show()
  }

  override def onError() {
    Toast.makeText(activity, R.string.background_update_error, Toast.LENGTH_SHORT).show()
  }
}

class FragmentActionBarProcessIndicator(activity: AppCompatActivity) extends DataManager.ProcessIndicator {
  override def startFetch() {
    Toast.makeText(activity, R.string.background_update_started, Toast.LENGTH_SHORT).show()
  }

  override def stopFetch() {
  }

  override def onSuccess() {
    Toast.makeText(activity, R.string.background_update_stopped, Toast.LENGTH_SHORT).show()
  }

  override def onError() {
    Toast.makeText(activity, R.string.background_update_error, Toast.LENGTH_SHORT).show()
  }
}
