package net.kriomant.gortrans

import android.support.v7.app.AppCompatActivity
import android.widget.Toast

class ActionBarProcessIndicator(activity: AppCompatActivity) extends DataManager.ProcessIndicator {
  def startFetch() {
    Toast.makeText(activity, R.string.background_update_started, Toast.LENGTH_SHORT).show()
    activity.setSupportProgressBarIndeterminateVisibility(true)
    activity.setProgressBarIndeterminateVisibility(true)
  }

  def stopFetch() {
    activity.setSupportProgressBarIndeterminateVisibility(true)
    activity.setProgressBarIndeterminateVisibility(true)
  }

  def onSuccess() {
    Toast.makeText(activity, R.string.background_update_stopped, Toast.LENGTH_SHORT).show()
  }

  def onError() {
    Toast.makeText(activity, R.string.background_update_error, Toast.LENGTH_SHORT).show()
  }
}

class FragmentActionBarProcessIndicator(activity: AppCompatActivity) extends DataManager.ProcessIndicator {
  def startFetch() {
    Toast.makeText(activity, R.string.background_update_started, Toast.LENGTH_SHORT).show()
    activity.setSupportProgressBarIndeterminateVisibility(true)
    activity.setProgressBarIndeterminateVisibility(true)
  }

  def stopFetch() {
    activity.setSupportProgressBarIndeterminateVisibility(true)
    activity.setProgressBarIndeterminateVisibility(true)
  }

  def onSuccess() {
    Toast.makeText(activity, R.string.background_update_stopped, Toast.LENGTH_SHORT).show()
  }

  def onError() {
    Toast.makeText(activity, R.string.background_update_error, Toast.LENGTH_SHORT).show()
  }
}
