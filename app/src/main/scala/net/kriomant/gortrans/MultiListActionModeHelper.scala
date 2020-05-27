package net.kriomant.gortrans

import android.support.v7.app.AppCompatActivity
import android.view.{ActionMode, View}
import android.widget.AdapterView.{OnItemClickListener, OnItemLongClickListener}
import android.widget.{AbsListView, AdapterView, ListView}

import scala.collection.mutable

trait ListSelectionActionModeCallback {
  def itemCheckedStateChanged(mode: ActionMode)
}

class MultiListActionModeHelper(
                                 activity: AppCompatActivity,
                                 actionModeCallback: ActionMode.Callback with ListSelectionActionModeCallback
                               ) {
  private[this] val listViews = new mutable.ArrayBuffer[ListView]
  private var actionMode: ActionMode = _
  private var savedItemClickListeners = new mutable.ArrayBuffer[OnItemClickListener]

  def getListViews: Seq[ListView] = listViews

  def finish() {
    actionMode.finish()
  }

  def attach(listView: ListView) {
    listViews += listView
    listView.setOnItemLongClickListener(new OnItemLongClickListener {
      def onItemLongClick(parent: AdapterView[_], view: View, position: Int, id: Long): Boolean = {
        if (actionMode == null) {
          savedItemClickListeners = listViews.map(_.getOnItemClickListener)
          listViews.foreach { listView =>
            setUpClickListener(listView)
            listView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE)
          }

          listView.setItemChecked(position, true)

          actionMode = activity.startActionMode(new ActionModeCallbackWrapper(actionModeCallback) {
            override def onDestroyActionMode(mode: ActionMode) {
              onActionModeFinished()
              super.onDestroyActionMode(mode)
            }
          })
          actionModeCallback.itemCheckedStateChanged(actionMode)
        }
        true
      }
    })

    if (actionMode != null) {
      savedItemClickListeners += listView.getOnItemClickListener
      setUpClickListener(listView)
      listView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE)
    }
  }

  private def setUpClickListener(listView: ListView) {
    listView.setOnItemClickListener(new OnItemClickListener {
      def onItemClick(parent: AdapterView[_], view: View, position: Int, id: Long) {
        // For some reason list item has already changed it's checked state here.
        val selected = listView.getCheckedItemPositions.get(position)

        if (!selected && Compatibility.getCheckedItemCount(listView) == 0 && listViews.forall(Compatibility.getCheckedItemCount(_) == 0)) {
          actionMode.finish()
        } else {
          actionModeCallback.itemCheckedStateChanged(actionMode)
        }
      }
    })
  }

  private def onActionModeFinished() {
    listViews.zip(savedItemClickListeners).foreach { case (listView, listener) =>
      listView.setOnItemClickListener(listener)
    }
    savedItemClickListeners.clear()

    actionMode = null
    listViews.foreach { listView =>
      listView.clearChoices()
      // Due to some Android bug ListView clears choices, but doesn't redraw
      // checked elements, so they remain highlighted until they go out of view.
      // Force ListView to redraw items using `requestLayout`.
      listView.requestLayout()
      // If choice mode is set to none before relayouting, then
      // during layout process ListView won't event bother to set
      // 'unchecked' state to Checkable items, because choice mode
      // is 'none'. So don't change mode immediately, but schedule it.
      listView.post(new Runnable {
        def run() {
          listView.setChoiceMode(AbsListView.CHOICE_MODE_NONE)
        }
      })
    }
  }
}
