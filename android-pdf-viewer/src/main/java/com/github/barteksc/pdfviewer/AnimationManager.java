/**
 * Copyright 2016 Bartosz Schiller
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.barteksc.pdfviewer;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.graphics.PointF;
import android.view.animation.DecelerateInterpolator;
import android.widget.OverScroller;

import androidx.annotation.NonNull;


/**
 * This manager is used by the PDFView to launch animations.
 * It uses the ValueAnimator appeared in API 11 to start
 * an animation, and call moveTo() on the PDFView as a result
 * of each animation update.
 */
class AnimationManager {

    private final PDFView pdfView;

    private ValueAnimator animation;

    private final OverScroller scroller;

    private boolean flinging = false;

    private boolean pageFlinging = false;

    public AnimationManager(PDFView pdfView) {
        this.pdfView = pdfView;
        scroller = new OverScroller(pdfView.getContext());
    }

    public void startXAnimation(float xFrom, float xTo, long duration) {
        stopAll();
        animation = ValueAnimator.ofFloat(xFrom, xTo);
        XAnimation xAnimation = new XAnimation();
        animation.setInterpolator(new DecelerateInterpolator());
        animation.addUpdateListener(xAnimation);
        animation.addListener(xAnimation);
        animation.setDuration(duration);
        animation.start();
    }

    public void startYAnimation(float yFrom, float yTo, long duration) {
        stopAll();
        animation = ValueAnimator.ofFloat(yFrom, yTo);
        YAnimation yAnimation = new YAnimation();
        animation.setInterpolator(new DecelerateInterpolator());
        animation.addUpdateListener(yAnimation);
        animation.addListener(yAnimation);
        animation.setDuration(duration);
        animation.start();
    }

    public void startZoomAnimation(float centerX, float centerY, float zoomFrom, float zoomTo, long duration) {
        stopAll();
        animation = ValueAnimator.ofFloat(zoomFrom, zoomTo);
        animation.setInterpolator(new DecelerateInterpolator());
        ZoomAnimation zoomAnim = new ZoomAnimation(centerX, centerY);
        animation.addUpdateListener(zoomAnim);
        animation.addListener(zoomAnim);
        animation.setDuration(duration);
        animation.start();
    }

    public void startFlingAnimation(int startX, int startY, int velocityX, int velocityY, int minX, int maxX, int minY, int maxY) {
        stopAll();
        flinging = true;
        scroller.fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY);
    }

    public void startPageFlingAnimation(float targetOffset, long duration) {
        if (pdfView.isSwipeVertical()) {
            startYAnimation(pdfView.getCurrentYOffset(), targetOffset, duration);
        } else {
            startXAnimation(pdfView.getCurrentXOffset(), targetOffset, duration);
        }
        pageFlinging = true;
    }

    void computeFling() {
        if (scroller.computeScrollOffset()) {
            pdfView.moveTo(scroller.getCurrX(), scroller.getCurrY());
            pdfView.loadPageByOffset();
        } else if (flinging) { // fling finished
            flinging = false;
            pdfView.loadPages();
            hideHandle();
            pdfView.performPageSnap();
        }
    }

    public void stopAll() {
        if (animation != null) {
            animation.cancel();
            animation = null;
        }
        stopFling();
    }

    public void stopFling() {
        flinging = false;
        scroller.forceFinished(true);
    }

    public boolean isFlinging() {
        return flinging || pageFlinging;
    }

    class XAnimation extends AnimatorListenerAdapter implements AnimatorUpdateListener {

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            float offset = (Float) animation.getAnimatedValue();
            pdfView.moveTo(offset, pdfView.getCurrentYOffset());
            pdfView.loadPageByOffset();
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            pdfView.loadPages();
            pageFlinging = false;
            hideHandle();
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            pdfView.loadPages();
            pageFlinging = false;
            hideHandle();
        }
    }

    class YAnimation extends AnimatorListenerAdapter implements AnimatorUpdateListener {

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            float offset = (Float) animation.getAnimatedValue();
            pdfView.moveTo(pdfView.getCurrentXOffset(), offset);
            pdfView.loadPageByOffset();
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            pdfView.loadPages();
            pageFlinging = false;
            hideHandle();
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            pdfView.loadPages();
            pageFlinging = false;
            hideHandle();
        }
    }

    class ZoomAnimation implements AnimatorUpdateListener, AnimatorListener {

        private final float centerX;
        private final float centerY;

        public ZoomAnimation(float centerX, float centerY) {
            this.centerX = centerX;
            this.centerY = centerY;
        }

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            float zoom = (Float) animation.getAnimatedValue();
            pdfView.zoomCenteredTo(zoom, new PointF(centerX, centerY));
        }

        @Override
        public void onAnimationCancel(@NonNull Animator animation) {
            pdfView.loadPages();
            hideHandle();
        }

        @Override
        public void onAnimationEnd(@NonNull Animator animation) {
            pdfView.loadPages();
            pdfView.performPageSnap();
            hideHandle();
        }

        @Override
        public void onAnimationRepeat(@NonNull Animator animation) {
        }

        @Override
        public void onAnimationStart(@NonNull Animator animation) {
        }

    }

    private void hideHandle() {
        if (pdfView.getScrollHandle() != null) {
            pdfView.getScrollHandle().hideDelayed();
        }
    }

}
