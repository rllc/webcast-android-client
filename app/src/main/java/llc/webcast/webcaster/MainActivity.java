package llc.webcast.webcaster;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends ActionBarActivity {

    private static final int RESULT_SETTINGS = 1;
    String TAG = "llc.webcast.webcaster";

    private final String DEFAULT_WEB_URL = "http://192.168.1.188:8080/webcast";
    private final String DEFAULT_USERNAME = "username";
    private final String DEFAULT_PASSWORD = "password";

    private Timer myStatusTimer;

    private TextView myStatusTextView;
    private Button myActionButton;

    // preferences
    private String myHost = DEFAULT_WEB_URL;
    private String myUsername = "username";
    private String myPassword = "password";

    private ApplicationState applicationState = ApplicationState.STOPPED;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initialize();
        refreshProperties();
        startWebcastListener();
    }

    private void initialize() {
        Log.v(TAG, "initialize");
        myStatusTextView = (TextView) findViewById(R.id.statusText);
        myActionButton = (Button) findViewById(R.id.actionButton);
        myActionButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.v(TAG, "button clicked : " + applicationState);
                switch (applicationState) {
                    case STARTED:
                        new RequestTask().execute(myHost + "/stop");
                        break;
                    case STOPPED:
                        new RequestTask().execute(myHost + "/start");
                        break;
                    case UNKNOWN:
                    case ERROR:
                    case NETWORK_ERROR:
                        break;
                }
            }
        });
    }

    private void startWebcastListener() {
        final Handler handler = new Handler();
        myStatusTimer = new Timer();
        TimerTask doAsynchronousTask = new TimerTask() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    public void run() {
                        try {
                            new RequestTask().execute(myHost + "/status");
                        } catch (Exception e) {
                        }
                    }
                });
            }
        };
        myStatusTimer.schedule(doAsynchronousTask, 0, 5000);
    }

    @Override
    protected void onStop() {
        Log.v(TAG, "onStop");
        super.onStop();
        if (myStatusTimer != null) {
            myStatusTimer.cancel();
            myStatusTimer = null;
        }
    }

    @Override
    public void onPause() {
        Log.v(TAG, "onPause");
        super.onPause();  // Always call the superclass method first
        if (myStatusTimer != null) {
            myStatusTimer.cancel();
            myStatusTimer = null;
        }
    }

    @Override
    public void onResume() {
        Log.v(TAG, "onResume");
        super.onResume();  // Always call the superclass method first
        if (myStatusTimer == null) {
            startWebcastListener();
        }
    }


    private void refreshProperties() {
        Log.v(TAG, "refreshProperties");
        SharedPreferences sharedPrefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        myHost = sharedPrefs.getString(
                getResources().getString(R.string.WEBCAST_HOST_URL_KEY),
                DEFAULT_WEB_URL);

        myUsername = sharedPrefs.getString(
                getResources().getString(R.string.WEBCAST_USERNAME),
                DEFAULT_USERNAME);

        myPassword = sharedPrefs.getString(
                getResources().getString(R.string.WEBCAST_PASSWORD),
                DEFAULT_PASSWORD);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.action_settings:
                Intent i = new Intent(this, SettingsActivity.class);
                startActivityForResult(i, RESULT_SETTINGS);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case RESULT_SETTINGS:
                refreshProperties();
                break;
        }

    }

    private class RequestTask extends AsyncTask<String, String, String> {

        String requestedUrl = "";

        @Override
        protected String doInBackground(String... uri) {
            requestedUrl = uri[0];
            Log.v(TAG, "doInBackground : " + requestedUrl);
            HttpUriRequest request;
            if (requestedUrl.endsWith("start") || requestedUrl.endsWith("stop")) {
                request = new HttpPost(requestedUrl);
            } else {
                request = new HttpGet(requestedUrl);
            }

            String credentials = myUsername + ":" + myPassword;
            String base64EncodedCredentials = Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);
            request.addHeader("Authorization", "Basic " + base64EncodedCredentials);

            HttpClient httpclient = new DefaultHttpClient();
            HttpResponse response;
            String responseString = null;
            try {
                response = httpclient.execute(request);
                StatusLine statusLine = response.getStatusLine();
                if (statusLine.getStatusCode() == HttpStatus.SC_OK) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    response.getEntity().writeTo(out);
                    out.close();
                    responseString = out.toString();
                } else {
                    // Closes the connection.
                    response.getEntity().getContent().close();
                    throw new IOException(statusLine.getReasonPhrase());
                }
            } catch (ClientProtocolException e) {
            } catch (IOException e) {
            }
            return responseString;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            Log.v(TAG, "onPostExecute : " + result);
            if (result != null) {
                if (result.contains("\"state\":\"STARTED\"")) {
                    applicationState = ApplicationState.STARTED;
                } else if (result.contains("\"state\":\"STOPPED\"")) {
                    applicationState = ApplicationState.STOPPED;
                } else if (result.contains("\"state\":\"ERROR\"")) {
                    applicationState = ApplicationState.ERROR;
                } else if (result.contains("\"state\":\"UNKNOWN\"")) {
                    applicationState = ApplicationState.UNKNOWN;
                }
            } else {
                applicationState = ApplicationState.NETWORK_ERROR;
            }
            repaint();
        }
    }

    private void repaint() {
        Log.v(TAG, "repaint : " + applicationState);
        switch (applicationState) {
            case STARTED:
                myStatusTextView.setText(R.string.broadcasting);
                myStatusTextView.setBackgroundColor(Color.GREEN);
                myActionButton.setText(R.string.stop_broadcast);
                break;
            case STOPPED:
                myStatusTextView.setText(R.string.not_broadcasting);
                myStatusTextView.setBackgroundColor(Color.RED);
                myActionButton.setText(R.string.start_broadcast);
                break;
            case UNKNOWN:
                myStatusTextView.setText(R.string.unknown);
                myStatusTextView.setBackgroundColor(Color.RED);
                myActionButton.setText(R.string.start_broadcast);
                break;
            case ERROR:
                myStatusTextView.setText(R.string.error);
                myStatusTextView.setBackgroundColor(Color.RED);
                myActionButton.setText(R.string.start_broadcast);
                break;
            case NETWORK_ERROR:
                myStatusTextView.setText(R.string.network_error);
                myStatusTextView.setBackgroundColor(Color.YELLOW);
                myActionButton.setText(R.string.start_broadcast);
                break;
        }
    }

}
