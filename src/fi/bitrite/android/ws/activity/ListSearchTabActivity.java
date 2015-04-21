package fi.bitrite.android.ws.activity;

import android.app.Dialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.TextView.OnEditorActionListener;

import com.google.android.gms.analytics.GoogleAnalytics;

import fi.bitrite.android.ws.R;
import fi.bitrite.android.ws.WSAndroidApplication;
import fi.bitrite.android.ws.api.RestClient;
import fi.bitrite.android.ws.host.Search;
import fi.bitrite.android.ws.host.impl.RestTextSearch;
import fi.bitrite.android.ws.model.Host;
import fi.bitrite.android.ws.model.HostBriefInfo;
import fi.bitrite.android.ws.util.Tools;

import java.util.ArrayList;

public class ListSearchTabActivity
        extends WSBaseActivity
        implements android.widget.AdapterView.OnItemClickListener {

    ArrayList<HostBriefInfo> mListSearchHosts;

    TextView mNoNetworkWarning;
    EditText mListSearchEdit;
    ImageView mListSearchButton;
    ListView mListSearchResult;
    DialogHandler mDialogHandler;
    TextSearchTask mTextSearchTask;
    LinearLayout mSearchEditLayout;
    LinearLayout mSearchResultsLayout;
    TextView mMultipleHostsAddress;
    TextView mHostsAtAddress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list_tab);
        initView();

        mDialogHandler = new DialogHandler(this);

        mNoNetworkWarning = (TextView) findViewById(R.id.noNetworkWarningList);
        mListSearchResult = (ListView) findViewById(R.id.lstSearchResult);
        mSearchResultsLayout = (LinearLayout) findViewById(R.id.listSummaryLayout);
        mMultipleHostsAddress = (TextView) findViewById(R.id.multipleHostAddress);
        mHostsAtAddress = (TextView) findViewById(R.id.hostsAtAddress);

        mListSearchResult.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent i = new Intent(ListSearchTabActivity.this, HostInformationActivity.class);
                HostBriefInfo briefInfo = (HostBriefInfo) mListSearchResult.getItemAtPosition(position);
                Host host = Host.createFromBriefInfo(briefInfo);
                i.putExtra("host", host);
                i.putExtra("id", briefInfo.getId());
                startActivityForResult(i, 0);
            }
        });

        if (savedInstanceState != null) {
            mListSearchHosts = savedInstanceState.getParcelableArrayList("list_search_hosts");
        }

        handleIntent(getIntent());

    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        handleIntent(intent);
    }

    void handleIntent(Intent intent) {

        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            doTextSearch(query);
        } else if (intent.hasExtra("search_results")) {
            // TODO: What is search_results used for, why is it passed this way?
            // It may be an obsolete attempt to show cluster members from maps2activity
            mListSearchHosts = intent.getParcelableArrayListExtra("search_results");
            if (!mListSearchHosts.isEmpty()) {
                // Hide the SearchEdit
                mSearchEditLayout.setVisibility(View.INVISIBLE);
                mSearchEditLayout.getLayoutParams().height = 0;
                // Show the search results header
                mSearchResultsLayout.setVisibility(View.VISIBLE);
                mSearchResultsLayout.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;

                mMultipleHostsAddress.setText(mListSearchHosts.get(0).getLocation());
                mHostsAtAddress.setText(getString(R.string.host_count, mListSearchHosts.size()));
            }
        }
    }

    // TODO: Remove this
//    private void setupListSearch(Bundle savedInstanceState) {
//
//        // Hide the SearchResults header by default
//        mSearchResultsLayout.setVisibility(View.GONE);
//
//        boolean inProgress = DialogHandler.inProgress();
//        if (mListSearchHosts != null) {
//            mListSearchResult.setAdapter(new HostListAdapter(WSAndroidApplication.getAppContext(),
//                    R.layout.host_list_item, mListSearchHosts));
//        }
//
//        if (inProgress) {
//            mDialogHandler.dismiss();
//            doTextSearch(savedInstanceState.getString("search_text"));
//        }
//    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        return mDialogHandler.createDialog(id, getResources().getString(R.string.performing_search));
    }

    public void doTextSearch(String text) {
        mDialogHandler.showDialog(DialogHandler.TEXT_SEARCH);
        Search search = new RestTextSearch(text);
        mTextSearchTask = new TextSearchTask();
        mTextSearchTask.execute(search);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        super.onItemClick(parent, view, position, id);
    }

    private class TextSearchTask extends AsyncTask<Search, Void, Object> {

        @Override
        protected Object doInBackground(Search... params) {
            Search search = params[0];
            Object retObj;

            try {
                retObj = search.doSearch();
            } catch (Exception e) {
                Log.e(WSAndroidApplication.TAG, e.getMessage(), e);
                retObj = e;
            }

            return retObj;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void onPostExecute(Object result) {
            mDialogHandler.dismiss();

            if (result instanceof Exception) {
                RestClient.reportError(ListSearchTabActivity.this, result);
                return;
            }

            mListSearchHosts = (ArrayList<HostBriefInfo>) result;
            mListSearchResult.setAdapter(new HostListAdapter(WSAndroidApplication.getAppContext(),
                    R.layout.host_list_item, mListSearchHosts));

            if (mListSearchHosts.isEmpty()) {
                mDialogHandler.alert(getResources().getString(R.string.no_results));
            }
        }

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putParcelableArrayList("list_search_hosts", mListSearchHosts);
        if (DialogHandler.inProgress() && mTextSearchTask != null) {
            mTextSearchTask.cancel(true);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!Tools.isNetworkConnected(this)) {
            mNoNetworkWarning.setText(getString(R.string.not_connected_to_network));
            return;
        }
        mNoNetworkWarning.setText("");
        mNoNetworkWarning.setVisibility(View.GONE);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        GoogleAnalytics.getInstance(this).reportActivityStop(this);
        super.onStop();
    }

    @Override
    protected void onStart() {
        super.onStart();
        GoogleAnalytics.getInstance(this).reportActivityStart(this);
    }

}
