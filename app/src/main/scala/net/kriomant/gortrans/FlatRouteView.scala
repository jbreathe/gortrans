package net.kriomant.gortrans

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.{Canvas, Color, Paint}
import android.os.Parcelable
import android.support.v4.content.ContextCompat
import android.support.v4.content.res.ResourcesCompat
import android.util.AttributeSet
import android.view.View.MeasureSpec
import android.view.{MotionEvent, View}

class FlatRouteView(context: Context, attributes: AttributeSet) extends View(context, attributes) {
  private[this] val stopImage = ResourcesCompat.getDrawable(context.getResources, R.drawable.route_stop_marker, null)
  private[this] val selectedStopImage = ResourcesCompat.getDrawable(context.getResources, R.drawable.route_stop_marker_selected, null)
  private[this] val vehicleIcon = new VehicleMarker(getResources, None, ContextCompat.getColor(getContext, R.color.forward_vehicle))
  private[this] val _padding = stopImage.getIntrinsicWidth / 2
  var currentTouchHandler: TouchHandler = _
  private[this] var _totalLength: Float = 0
  private[this] var _stops: Seq[(Float, String)] = Seq()
  private[this] var _fixedStopIndex: Int = 0
  private[this] var _vehicles: Seq[Float] = Seq()
  private[this] var _minScale: Float = 0
  private[this] var _maxScale: Float = 0
  private[this] var scale = 1000.0
  private[this] var _fixedStopX: Int = _

  def setStops(totalLength: Float, stops: Seq[(Float, String)], fixedStop: Int) {
    require(stops.isEmpty || (fixedStop >= 0 && fixedStop < stops.length))

    _totalLength = totalLength
    _stops = stops
    _fixedStopIndex = fixedStop

    calculateScaleBounds()
    scale = clampScale(scale)

    invalidate()
  }

  def calculateScaleBounds() {
    val effectiveWidth = getWidth - 2 * _padding

    if (_stops.nonEmpty) {
      // At least one previous stop must be shown.
      _maxScale = effectiveWidth / distanceToPreviousStop(_fixedStopIndex)
      // Whole route to the left of fixed stop may be shown.
      _minScale = effectiveWidth / _totalLength
    } else {
      _maxScale = 0
      _minScale = 0
    }
  }

  def distanceToPreviousStop(stopIndex: Int): Float = stopIndex match {
    case 0 => _totalLength - _stops.last._1
    case _ => _stops(stopIndex)._1 - _stops(stopIndex - 1)._1
  }

  def clampScale(value: Double): Double = math.max(math.min(value, _maxScale), _minScale)

  def setVehicles(positions: Seq[Float]) {
    _vehicles = positions

    invalidate()
  }

  override def onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    setMeasuredDimension(
      MeasureSpec.getSize(widthMeasureSpec),
      vehicleIcon.getIntrinsicHeight + stopImage.getIntrinsicHeight / 2
    )
  }

  override def onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    _fixedStopX = w - selectedStopImage.getIntrinsicWidth / 2

    calculateScaleBounds()
    scale = clampScale(scale)

    super.onSizeChanged(w, h, oldw, oldh)
  }

  override def onDraw(canvas: Canvas) {
    if (_stops.isEmpty)
      return

    def drawCentered(drawable: Drawable, x: Int, y: Int) {
      val left = x - drawable.getIntrinsicWidth / 2
      val top = y - drawable.getIntrinsicHeight / 2
      drawable.setBounds(left, top, left + drawable.getIntrinsicWidth, top + drawable.getIntrinsicHeight)
      drawable.draw(canvas)
    }

    def drawHorizontallyCentered(drawable: Drawable, x: Int, y: Int) {
      val left = x - drawable.getIntrinsicWidth / 2
      drawable.setBounds(left, y, left + drawable.getIntrinsicWidth, y + drawable.getIntrinsicHeight)
      drawable.draw(canvas)
    }

    val paint = new Paint
    paint.setColor(Color.WHITE)
    paint.setStrokeWidth(4)

    // Position of the first stop in pixels.
    val startX = _fixedStopX - (_stops(_fixedStopIndex)._1 * scale).toInt

    val y = vehicleIcon.getIntrinsicHeight
    canvas.drawLine(0, y, getWidth, y, paint)

    // Draw fixed stop double.
    locally {
      val fixedStop = _stops(_fixedStopIndex)
      val x = _fixedStopX - (_totalLength * scale).toInt
      drawCentered(selectedStopImage, x, y)
    }

    _stops.view.drop(_fixedStopIndex + 1) foreach { case (stopPosition, name) =>
      val x = startX + ((stopPosition - _totalLength) * scale).toInt
      drawCentered(stopImage, x, y)
    }

    _stops.view.take(_fixedStopIndex) foreach { case (stopPosition, name) =>
      val x = startX + (stopPosition * scale).toInt
      drawCentered(stopImage, x, y)
    }

    // Draw fixed stop.
    drawCentered(selectedStopImage, _fixedStopX, y)

    _vehicles foreach { position =>
      val pos = if (position <= _stops(_fixedStopIndex)._1) position else position - _totalLength
      val x = startX + (pos * scale).toInt
      drawHorizontallyCentered(vehicleIcon, x, 0)
    }
  }

  override def onTouchEvent(event: MotionEvent): Boolean = event.getAction match {
    case MotionEvent.ACTION_DOWN =>
      assert(currentTouchHandler == null)
      if (event.getX < _fixedStopX)
        currentTouchHandler = new ScalingEventHandler(event)
      true

    case MotionEvent.ACTION_UP =>
      if (currentTouchHandler != null) {
        currentTouchHandler.finish(event)
        currentTouchHandler = null
      }
      true

    case MotionEvent.ACTION_CANCEL =>
      if (currentTouchHandler != null) {
        currentTouchHandler.cancel()
        currentTouchHandler = null
      }
      true

    case _ =>
      if (currentTouchHandler != null) {
        currentTouchHandler(event)
      } else {
        false
      }
  }

  override def onRestoreInstanceState(state: Parcelable) {
    super.onRestoreInstanceState(state)
  }

  override def onSaveInstanceState(): Parcelable = super.onSaveInstanceState()

  trait TouchHandler {
    def apply(event: MotionEvent): Boolean

    def cancel()

    def finish(event: MotionEvent) {}
  }

  class ScalingEventHandler(downEvent: MotionEvent) extends TouchHandler {
    val initialX: Float = downEvent.getX
    val initialScale: Double = FlatRouteView.this.scale
    var pointerId: Int = downEvent.getPointerId(0)

    def apply(event: MotionEvent): Boolean = event.getAction match {
      case MotionEvent.ACTION_MOVE =>
        if (pointerId != -1) {
          val idx = event.findPointerIndex(pointerId)
          val x = event.getX(idx)
          FlatRouteView.this.scale = clampScale(initialScale / (_fixedStopX - initialX) * (_fixedStopX - x))
          FlatRouteView.this.invalidate()
        }
        true

      case MotionEvent.ACTION_POINTER_UP =>
        if (pointerId != -1 && event.getPointerId(event.getActionIndex) == pointerId) {
          // Pointer which initiated action is up.
          pointerId = -1
        }
        true

      case _ => false
    }

    def cancel() {
      FlatRouteView.this.scale = initialScale
      FlatRouteView.this.invalidate()
    }
  }

}
