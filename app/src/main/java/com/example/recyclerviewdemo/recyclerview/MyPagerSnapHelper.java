package com.ftrend.cpos.widget;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearSmoothScroller;
import android.support.v7.widget.OrientationHelper;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SnapHelper;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewTreeObserver;

public class MyPagerSnapHelper extends SnapHelper {
    //控制滑动速速，越小速度越快。SnapHelper里面默认是100f，滚动太慢。
    private static final float MILLISECONDS_PER_INCH = 40f;
    private RecyclerView mRecyclerView;

    private OrientationHelper mVerticalHelper = null;
    private OrientationHelper mHorizontalHelper = null;
    private int mRecyclerViewWidth = 0;
    private int mRecyclerViewHeight = 0;
    private int mCurrentScrolledX = 0;
    private int mCurrentScrolledY = 0;
    private int mScrolledX = 0;
    private int mScrolledY = 0;
    private boolean mFlung = false;
    private int itemCount;

    private RecyclerView.OnScrollListener mScrollListener = new RecyclerView.OnScrollListener() {
        private boolean scrolledByUser = false;

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
            if (newState == RecyclerView.SCROLL_STATE_DRAGGING) scrolledByUser = true;
            if (newState == RecyclerView.SCROLL_STATE_IDLE && scrolledByUser) {
                scrolledByUser = false;
            }
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            mScrolledX += dx;
            mScrolledY += dy;
            if (scrolledByUser) {
                mCurrentScrolledX += dx;
                mCurrentScrolledY += dy;
            }
        }
    };

    public MyPagerSnapHelper(int itemCount) {
        this.itemCount = itemCount;
    }

    @Override
    public void attachToRecyclerView(@Nullable RecyclerView recyclerView) throws IllegalStateException {
        super.attachToRecyclerView(recyclerView);
        mRecyclerView = recyclerView;
        if (recyclerView != null) {
            recyclerView.addOnScrollListener(mScrollListener);
            recyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    //使用完立刻撤销监听，否则比较耗性能
                    recyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    mRecyclerViewWidth = recyclerView.getWidth();
                    mRecyclerViewHeight = recyclerView.getHeight();
                }
            });
        }
    }


    @Nullable
    @Override
    public int[] calculateDistanceToFinalSnap(@NonNull RecyclerView.LayoutManager layoutManager, @NonNull View targetView) {
        int[] out = new int[2];
        if (layoutManager.canScrollHorizontally()) {
            out[0] = distanceToStart(targetView, getHorizontalHelper(layoutManager));
            out[1] = 0;
        } else if (layoutManager.canScrollVertically()) {
            out[0] = 0;
            out[1] = distanceToStart(targetView, getVerticalHelper(layoutManager));
        }
        return out;
    }

    private OrientationHelper getHorizontalHelper(RecyclerView.LayoutManager layoutManager) {
        if (mHorizontalHelper == null) {
            mHorizontalHelper = OrientationHelper.createHorizontalHelper(layoutManager);
        }
        return mHorizontalHelper;
    }

    private OrientationHelper getVerticalHelper(RecyclerView.LayoutManager layoutManager) {
        if (mVerticalHelper == null) {
            mVerticalHelper = OrientationHelper.createVerticalHelper(layoutManager);
        }
        return mVerticalHelper;
    }


    private int distanceToStart(View targetView, OrientationHelper orientationHelper) {
        return orientationHelper.getDecoratedStart(targetView) - orientationHelper.getStartAfterPadding();
    }

    @Nullable
    @Override
    public View findSnapView(RecyclerView.LayoutManager layoutManager) {
        if (mFlung) {
            resetCurrentScrolled();
            mFlung = false;
            return null;
        }
        if (layoutManager == null) return null;
        int targetPosition = getTargetPosition();
        if (targetPosition == RecyclerView.NO_POSITION) return null;
        LinearSmoothScroller snapScroller = createSnapScroller(layoutManager);
        if (snapScroller != null) {
            snapScroller.setTargetPosition(targetPosition);
            layoutManager.startSmoothScroll(snapScroller);
        }
        return null;
    }


    @Nullable
    @Override
    protected LinearSmoothScroller createSnapScroller(RecyclerView.LayoutManager layoutManager) {
        if (!(layoutManager instanceof RecyclerView.SmoothScroller.ScrollVectorProvider)) {
            return null;
        }
        return new LinearSmoothScroller(mRecyclerView.getContext()) {
            @Override
            protected void onTargetFound(View targetView, RecyclerView.State state, Action action) {
                int[] snapDistances = calculateDistanceToFinalSnap(mRecyclerView.getLayoutManager(),
                        targetView);
                final int dx = snapDistances[0];
                final int dy = snapDistances[1];
                final int time = calculateTimeForDeceleration(Math.max(Math.abs(dx), Math.abs(dy)));
                if (time > 0) {
                    action.update(dx, dy, time, mDecelerateInterpolator);
                }
            }

            @Override
            protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
                return MILLISECONDS_PER_INCH / displayMetrics.densityDpi;
            }
        };
    }

    @Override
    public int findTargetSnapPosition(RecyclerView.LayoutManager layoutManager, int velocityX, int velocityY) {
        int targetPosition = getTargetPosition();
        mFlung = targetPosition != RecyclerView.NO_POSITION;
        return targetPosition;
    }

    private int getTargetPosition() {
        int page;
        if (mRecyclerViewWidth <= 0 || mRecyclerViewHeight <= 0) {
            page = RecyclerView.NO_POSITION;
//            LogUtil.E("mRecyclerViewWidth <= 0 || mRecyclerViewHeight <= 0");
        } else {
            if (mCurrentScrolledX > 0) {
                page = mScrolledX / mRecyclerViewWidth + 1;
            } else if (mCurrentScrolledX < 0) {
                page = mScrolledX / mRecyclerViewWidth;

            } else if (mCurrentScrolledY > 0) {
                page = mScrolledY / mRecyclerViewHeight + 1;

            } else if (mCurrentScrolledY < 0) {
                page = mScrolledY / mRecyclerViewHeight;

            } else {
                page = RecyclerView.NO_POSITION;
            }
        }
        resetCurrentScrolled();
        return (page == RecyclerView.NO_POSITION) ? RecyclerView.NO_POSITION : page * itemCount;
    }

    private void resetCurrentScrolled() {
        mCurrentScrolledX = 0;
        mCurrentScrolledY = 0;
    }

}
