package com.codepath.apps.twitterclient.activities;

import android.content.Intent;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.support.v7.widget.Toolbar;

import com.codepath.apps.twitterclient.R;
import com.codepath.apps.twitterclient.adapters.EndlessScrollListener;
import com.codepath.apps.twitterclient.adapters.TweetResultsAdapter;
import com.codepath.apps.twitterclient.fragments.TweetComposerDialog;
import com.codepath.apps.twitterclient.helpers.AsyncRetrieveFromDB;
import com.codepath.apps.twitterclient.helpers.AsyncSaveToDB;
import com.codepath.apps.twitterclient.helpers.TweetUtilities;
import com.codepath.apps.twitterclient.helpers.TwitterApplication;
import com.codepath.apps.twitterclient.helpers.TwitterClient;
import com.codepath.apps.twitterclient.interfaces.AsyncListResponse;
import com.codepath.apps.twitterclient.models.Tweet;
import com.codepath.apps.twitterclient.models.User;
import com.loopj.android.http.JsonHttpResponseHandler;

import org.apache.http.Header;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class TimelineActivity extends ActionBarActivity {

    private TwitterClient client;
    private ArrayList<Tweet> tweets;
    private TweetResultsAdapter aTweets;
    private TweetUtilities utils;
    private AsyncRetrieveFromDB cacheMgr;

    Toolbar toolbar;
    ProgressBar pbFooterLoading;
    private ListView lvTweets;

    private SwipeRefreshLayout swipeContainer;

    public long lowestId = 0;
    public long highestId = 0;
    public User currentUser;

    private int TWEET_DETAILS_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_timeline);

        setupViews();

        tweets = new ArrayList<>();
        aTweets = new TweetResultsAdapter(this, tweets);
        lvTweets.setAdapter(aTweets);

        // setup swipe-to-refresh
        swipeContainer = (SwipeRefreshLayout) findViewById(R.id.swipeContainer);
        swipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                // only get new results (not db cached results)
                populateTimeline(0, highestId);
            }
        });

        client = TwitterApplication.getRestClient();

        // get current user information
        if (currentUser == null) {
            getCurrentUser();
        }
    }

    private void setupViews() {

        // enable actionbar icon
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(null);

        lvTweets = (ListView) findViewById(R.id.lvTimeline);
        lvTweets.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent i = new Intent(TimelineActivity.this, TweetDetailsActivity.class);
                // get tweet from adapter and pass into intent
                i.putExtra("Tweet", aTweets.getItem(position));
                // launch new activity for result
                startActivityForResult(i, TWEET_DETAILS_REQUEST_CODE);
            }
        });

        // add a progress bar for listview footer
        View footer = getLayoutInflater().inflate(R.layout.activity_timeline_progress_footer, null);
        pbFooterLoading = (ProgressBar) footer.findViewById(R.id.pbFooterLoading);
        lvTweets.addFooterView(footer);

        // set infinite scroll
        lvTweets.setOnScrollListener(new EndlessScrollListener() {
            @Override
            public void onLoadMore(int page, int totalItemsCount) {
                populateTimelineFromDbOrRest(lowestId);
            }
        });

    }

    private void populateTimeline(long lowest, long highest) {

        if(!utils.isNetworkAvailable(this)) {
//            Toast.makeText(this, "No internet connection, please try again later", Toast.LENGTH_SHORT).show();
            swipeContainer.setRefreshing(false);
            return;
        }

        pbFooterLoading.setVisibility(View.VISIBLE);

        client.getHomeTimeline(lowest, highest, new JsonHttpResponseHandler() {

            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONArray response) {
                ArrayList<Tweet> currentTweets = Tweet.fromJSONArray(response);

//                Toast.makeText(TimelineActivity.this, "fetching new tweets, please wait..", Toast.LENGTH_SHORT).show();

                // some logic to paginate results more efficiently
                if (currentTweets.size() > 0) {
                    Tweet lastTweet = currentTweets.get(currentTweets.size() - 1);
                    Tweet firstTweet = currentTweets.get(0);
                    if (currentTweets.size() == 1 && firstTweet.uid == highestId) {
                        // dont append to adapter since its a duplicate
                    } else {
                        if (firstTweet.uid > highestId) {
                            highestId = firstTweet.uid;
                        }
                        if (lastTweet.uid < lowestId || lowestId == 0) {
                            lowestId = lastTweet.uid;
                        }

                        aTweets.addAll(currentTweets);

                        // store new tweets and users to database without blocking UI thread
                        new AsyncSaveToDB().execute(currentTweets);
                    }
                }

                // update views
                swipeContainer.setRefreshing(false);
                pbFooterLoading.setVisibility(View.GONE);
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject error) {
                displayTwitterError("retrieving timeline", error);
//                aTweets.add(new Tweet().getMockTweet());
                pbFooterLoading.setVisibility(View.GONE);
            }

        });
    }

    public void populateTimelineFromDbOrRest(long lowest) {

        AsyncRetrieveFromDB asyncTask = new AsyncRetrieveFromDB(new AsyncListResponse() {

            @Override
            public void processFinish(List<Tweet> queryResults) {

                int resultSize = queryResults.size();

                if (resultSize > 0) {
                    Tweet lastTweet = queryResults.get(resultSize - 1);
                    Tweet firstTweet = queryResults.get(0);
                    if (firstTweet.uid > highestId) {
                        highestId = firstTweet.uid;
                    }
                    if (lastTweet.uid < lowestId || lowestId == 0) {
                        lowestId = lastTweet.uid;
                    }
                    // add cached tweets to adapter
                    aTweets.addAll(queryResults);
                }

                // if cached results are less than the results per page, call rest api
                if (resultSize < TwitterClient.RESULTS_PER_PAGE) {
                    populateTimeline(lowestId, 0);
                }
            }

        });

        // start async task
        asyncTask.execute(lowestId);

    }

    private void getCurrentUser() {

        if(!utils.isNetworkAvailable(this)) {
//            Toast.makeText(this, "No internet connection, please try again later", Toast.LENGTH_SHORT).show();
            return;
        }

        client.getCurrentUserInfo(new JsonHttpResponseHandler() {

            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                currentUser = User.fromJSON(response);
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject error) {
                displayTwitterError("retrieving current user info", error);
            }

        });
    }

    private void sendTweetAsync(final String tweetBody, long replyToId) {

        if(!utils.isNetworkAvailable(this)) {
//            Toast.makeText(this, "No internet connection, please try again later", Toast.LENGTH_SHORT).show();
            return;
        }

        client.postStatusUpdate(tweetBody, replyToId, new JsonHttpResponseHandler() {

            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                // only get new results (not db cached results)
//                populateTimeline(0, highestId);
                Tweet newTweet = new Tweet(response);

                // save new tweet async
                ArrayList<Tweet> newTweets = new ArrayList<Tweet>();
                newTweets.add(newTweet);
                new AsyncSaveToDB().execute(newTweets);

                // insert to top of arrayadapter
                aTweets.insert(newTweet, 0);
                highestId = newTweet.uid;

                Toast.makeText(TimelineActivity.this, "Your status has been updated!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject error) {
                displayTwitterError("composing tweet", error);
            }

        });
    }

    // an error handler for twitter error responses to display a toast with one or more error messages returned from twitter
    private void displayTwitterError(String action, JSONObject error) {
        try {
            JSONArray errorArray = error.getJSONArray("errors");
            String errors = "";
            for (int i = 0; i < errorArray.length(); i++) {
                if (i != 0) errors += ", ";
                errors += errorArray.getJSONObject(i).getString("message");
            }
            Toast.makeText(TimelineActivity.this, "Error " + action + ": " + errors, Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            Toast.makeText(TimelineActivity.this, "Error " + action + ", please try again later", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_timeline, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.miComposeTweet) {
            launchTweetComposerDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // launch tweet composer in a dialog
    private void launchTweetComposerDialog() {
        android.support.v4.app.FragmentManager fm = getSupportFragmentManager();
        TweetComposerDialog esd = TweetComposerDialog.newInstance(this, new TweetComposerDialogFragmentListener() {
            public void sendTweet(String tweetBody){
                sendTweetAsync(tweetBody, 0);
            }
        }, currentUser);
        esd.show(fm, "fragment_tweet_composer");
    }

    public interface TweetComposerDialogFragmentListener {
        public void sendTweet(String tweetBody);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(resultCode == RESULT_OK && requestCode == TWEET_DETAILS_REQUEST_CODE) {
            // get tweet reply and send async
            String tweetReply = data.getStringExtra("tweetReply");
            long replyToId = data.getLongExtra("replyToId", 0);
            sendTweetAsync(tweetReply, replyToId);
        }
    }


}
