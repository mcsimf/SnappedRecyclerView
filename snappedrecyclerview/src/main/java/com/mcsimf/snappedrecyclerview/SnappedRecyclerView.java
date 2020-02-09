package com.mcsimf.snappedrecyclerview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.SensorManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Snapped RecyclerView. It corrects fling gesture initial
 * velocity so scrolling always ends in the beginning of the
 * row or column.
 * Also if no fling gesture was detected but scroll was
 * performed and finger was released in any scroll position
 * it is automatically will scroll up to closest edge of first visible
 * item.
 * <p>
 * NOTE: Fling velocity correction assumes that all items in the
 * RecyclerView have equal heights for vertical scrolling, or
 * equal width for horizontal scrolling.
 *
 * @author Maksym Fedyay on 9/3/2015 (mcsimf@gmail.com)
 */
public class SnappedRecyclerView extends RecyclerView {


    private static final String TAG = SnappedRecyclerView.class.getSimpleName();


    private static final boolean LOG_ENABLED = true;


    public SnappedRecyclerView(Context context) {
        super(context);
        init(context);
    }


    public SnappedRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }


    public SnappedRecyclerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }


    private boolean isSnappingEnabled = true;


    /**
     * Enable or disable snapping behavior
     *
     * @param isSnappingEnabled
     */
    public void enableSnapping(boolean isSnappingEnabled) {
        this.isSnappingEnabled = isSnappingEnabled;
    }


    /**
     * Initialization of physical coefficient.
     * <p>
     * Calculation of this coefficient took from
     * {@link android.widget.OverScroller} class.
     *
     * @param context application level context.
     */
    private void init(Context context) {

        float ppi = context.getResources().getDisplayMetrics().density * 160.0f;

        this.mPhysicalCoeff = SensorManager.GRAVITY_EARTH // g (m/s^2)
                * 39.37f // inch/meter
                * ppi
                * 0.84f; // look and feel tuning
    }


    @Override
    public boolean fling(int velocityX, int velocityY) {
        if (!isSnappingEnabled) return super.fling(velocityX, velocityY);
        if (LOG_ENABLED)
            Log.e(TAG, "fling(" + velocityX + ", " + velocityY + ")");
        if (velocityY != 0) {
            int direction = (int) Math.signum(velocityY);
            double totalDy = getSplineFlingDistance(velocityY);
            totalDy = adjustDistance(totalDy, direction, RecyclerView.VERTICAL);
            if (LOG_ENABLED) Log.e(TAG, "adjusted distance = " + totalDy);
            velocityY = getVelocityByDistance(totalDy) * direction;
        } else if (velocityX != 0) {
            int direction = (int) Math.signum(velocityX);
            double totalDx = getSplineFlingDistance(velocityX);
            totalDx = adjustDistance(totalDx, direction, RecyclerView.HORIZONTAL);
            if (LOG_ENABLED) Log.e(TAG, "adjusted distance = " + totalDx);
            velocityX = getVelocityByDistance(totalDx) * direction;
        }

        if (LOG_ENABLED)
            Log.e(TAG, "adjusted fling(" + velocityX + ", " + velocityY + ")");

        return super.fling(velocityX, velocityY);
    }


    /* calculation of fling distance */
    private static float DECELERATION_RATE = (float) (Math.log(0.78) / Math.log(0.9));
    private static final float INFLEXION = 0.35f; // Tension lines cross at (INFLEXION, 1)
    private float mFlingFriction = ViewConfiguration.getScrollFriction();
    private float mPhysicalCoeff;


    /**
     * @param velocity
     * @return
     */
    private double getSplineFlingDistance(int velocity) {
        final double l = getSplineDeceleration(velocity);
        final double decelMinusOne = DECELERATION_RATE - 1.0;
        return mFlingFriction * mPhysicalCoeff * Math.exp(DECELERATION_RATE / decelMinusOne * l);
    }


    /**
     * @param velocity
     * @return
     */
    private double getSplineDeceleration(int velocity) {
        return Math.log(INFLEXION * Math.abs(velocity) / (mFlingFriction * mPhysicalCoeff));
    }


    /* calculate velocity based on required distance */

    /**
     * @param s
     * @return
     */
    private int getVelocityByDistance(double s) {

        // Here is the place where magic taking place

        int result = (int) Math.exp(
                (
                        Math.log(Math.abs(s) / (mFlingFriction * mPhysicalCoeff))
                                * (DECELERATION_RATE - 1.0) / DECELERATION_RATE
                )
                        + Math.log(mFlingFriction * mPhysicalCoeff / INFLEXION)
        );

        return result + 1;
    }


    /* adjust distance depending on item size */

    /**
     * @param s
     * @param direction
     * @param orientation
     * @return
     */
    private int adjustDistance(double s, int direction, int orientation) {

        View v = null;

        if (getLayoutManager() instanceof LinearLayoutManager) {
            LinearLayoutManager llm = (LinearLayoutManager) getLayoutManager();
            int position = llm.findFirstVisibleItemPosition();
            if (position == RecyclerView.NO_POSITION) return 0;
            v = llm.findViewByPosition(position);
        } else if (getLayoutManager() instanceof GridLayoutManager) {
            GridLayoutManager glm = (GridLayoutManager) getLayoutManager();
            int position = glm.findFirstVisibleItemPosition();
            if (position == RecyclerView.NO_POSITION) return 0;
            v = glm.findViewByPosition(position);
        }

        if (null == v) return 0;

        if (orientation == RecyclerView.VERTICAL) {
            float y = v.getY();
            int height = v.getHeight();
            int visibleHeight = (int) (height + y);
            double flingRows = s / height;
            int flingRowsInt = (int) Math.floor(flingRows);
            return (int) (flingRowsInt * height + (direction > 0 ? visibleHeight : -y));
        } else if (orientation == RecyclerView.HORIZONTAL) {
            float x = v.getX();
            int width = v.getWidth();
            int visibleWidth = (int) (width + x);
            double flingRows = s / width;
            int flingColumnsInt = (int) Math.floor(flingRows);
            return (int) (flingColumnsInt * width + (direction > 0 ? visibleWidth : -x));
        }

        return 0;
    }


    /* Scroll to closest edge of first view if no fling detected but scroll performed */

    @SuppressLint("WrongConstant")
    @Override
    public boolean onTouchEvent(MotionEvent e) {

        final boolean result = super.onTouchEvent(e);

        if ((e.getAction() == MotionEvent.ACTION_UP ||
                e.getAction() == MotionEvent.ACTION_CANCEL)
                && getScrollState() == SCROLL_STATE_IDLE) {

            View v = null;
            int orientation = -1;

            if (getLayoutManager() instanceof LinearLayoutManager) {
                LinearLayoutManager llm = (LinearLayoutManager) getLayoutManager();
                int position = llm.findFirstVisibleItemPosition();
                if (position == RecyclerView.NO_POSITION) return result;
                v = llm.findViewByPosition(position);
                orientation = llm.getOrientation();
            } else if (getLayoutManager() instanceof GridLayoutManager) {
                GridLayoutManager glm = (GridLayoutManager) getLayoutManager();
                int position = glm.findFirstVisibleItemPosition();
                if (position == RecyclerView.NO_POSITION) return result;
                v = glm.findViewByPosition(position);
                orientation = glm.getOrientation();
            }

            if (null == v) return result;

            if (orientation == RecyclerView.VERTICAL) {
                int height = v.getHeight();
                float y = v.getY(); // always <=0
                if ((height / 2) > Math.abs(y)) {
                    if (LOG_ENABLED) Log. e(TAG, "scroll to top edge by " + y);
                    smoothScrollBy(0, (int) y); // scroll to top edge
                } else {
                    if (LOG_ENABLED) Log.e(TAG, "scroll to bottom edge by " + (height + y));
                    smoothScrollBy(0, (int) (height + y)); // scroll to bottom edge
                }
            } else if (orientation == RecyclerView.HORIZONTAL) {
                int width = v.getWidth();
                float x = v.getX(); // always <=0
                if ((width / 2) > Math.abs(x)) {
                    if (LOG_ENABLED) Log.e(TAG, "scroll to left edge by " + x);
                    smoothScrollBy((int) x, 0); // scroll to left edge
                } else {
                    if (LOG_ENABLED) Log.e(TAG, "scroll to right edge by " + (width + x));
                    smoothScrollBy((int) (width + x), 0); // scroll to right edge
                }
            }
        }

        return result;
    }

}