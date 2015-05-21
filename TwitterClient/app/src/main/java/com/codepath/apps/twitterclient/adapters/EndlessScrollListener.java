package com.codepath.apps.twitterclient.adapters;

import android.widget.AbsListView;

/**
 * Created by edwardyang on 5/13/15.
 */
public abstract class EndlessScrollListener implements AbsListView.OnScrollListener {

    private int visibleThreshold = 3;
    private int currentPage = 0;
    private int previousTotalItemCount = 0;
    private boolean loading = true;
    private int startingPageIndex = 0;

    public EndlessScrollListener() {

    }

    public EndlessScrollListener(int visibleThreshold) {
        this.visibleThreshold = visibleThreshold;
    }

    public EndlessScrollListener(int visibleThreshold, int startPage) {
        this.visibleThreshold = visibleThreshold;
        this.startingPageIndex = startPage;
        this.currentPage = startPage;
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        if(totalItemCount < previousTotalItemCount) {
            this.currentPage = this.startingPageIndex;
            this.previousTotalItemCount = totalItemCount;
            if(totalItemCount == 0) {
                this.loading = true;
            }
        }
        if(loading && (totalItemCount > previousTotalItemCount)) {
            loading = false;
            previousTotalItemCount = totalItemCount;
            currentPage++;
        }

        if(!loading && (totalItemCount - visibleItemCount) <= (firstVisibleItem + visibleThreshold)) {
            onLoadMore(currentPage + 1, totalItemCount);
            loading = true;
        }

    }

    public abstract void onLoadMore(int page, int totalItemsCount);

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        // do nothing
    }
}
