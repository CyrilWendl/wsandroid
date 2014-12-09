package fi.bitrite.android.ws.auth.http;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import fi.bitrite.android.ws.WSAndroidApplication;
import fi.bitrite.android.ws.auth.AuthenticationHelper;
import fi.bitrite.android.ws.auth.AuthenticationService;
import fi.bitrite.android.ws.util.GlobalInfo;
import fi.bitrite.android.ws.util.http.HttpUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.CircularRedirectException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Responsible for authenticating the user against the WarmShowers web service.
 */
public class HttpAuthenticator {

    private final String wsUserAuthUrl = GlobalInfo.warmshowersBaseUrl + "/services/rest/user/login";
    private final String wsUserAuthTestUrl = GlobalInfo.warmshowersBaseUrl + "/search/wsuser";

    private String username;
    private String authtoken;
    private String mCookieSessId = "";
    private String mCookieSessName = "";

    /**
     * Load a page in order to see if we are authenticated
     */
    public boolean isAuthenticated() {
        HttpClient client = HttpUtils.getDefaultClient();
        int responseCode;
        try {
            String url = HttpUtils.encodeUrl(wsUserAuthTestUrl);
            HttpGet get = new HttpGet(url);
            HttpContext context = HttpSessionContainer.INSTANCE.getSessionContext();

            HttpResponse response = client.execute(get, context);
            HttpEntity entity = response.getEntity();
            responseCode = response.getStatusLine().getStatusCode();
            EntityUtils.toString(entity, "UTF-8");
        } catch (Exception e) {
            throw new HttpAuthenticationFailedException(e);
        } finally {
            client.getConnectionManager().shutdown();
        }

        return (responseCode == HttpStatus.SC_OK);
    }

    public void authenticate() {
        try {
            getCredentialsFromAccount();
            authenticate(username, authtoken);
        } catch (Exception e) {
            throw new HttpAuthenticationFailedException(e);
        }
    }

    private void getCredentialsFromAccount() throws OperationCanceledException, AuthenticatorException, IOException {
        AccountManager accountManager = AccountManager.get(WSAndroidApplication.getAppContext());
        Account account = AuthenticationHelper.getWarmshowersAccount();

        authtoken = accountManager.blockingGetAuthToken(account, AuthenticationService.ACCOUNT_TYPE, true);
        username = account.name;
    }

    /**
     * Returns the user id after logging in or 0 if already logged in.
     */
    public int authenticate(String username, String password) throws HttpAuthenticationFailedException, IOException {
        HttpClient client = HttpUtils.getDefaultClient();
        HttpContext httpContext = HttpSessionContainer.INSTANCE.getSessionContext();
        int userId = 0;

        try {
            List<NameValuePair> credentials = generateCredentialsForPost(username, password);
            HttpPost post = new HttpPost(wsUserAuthUrl);
            post.setEntity(new UrlEncodedFormEntity(credentials));
            HttpResponse response = client.execute(post, httpContext);

            HttpEntity entity = response.getEntity();
            String rawJson = EntityUtils.toString(entity, "UTF-8");

            if (rawJson.contains("Wrong username or password")) {
                throw new HttpAuthenticationFailedException("Wrong username or password");
            }

            if (rawJson.contains("Already logged in")) {
                return 0;
            }

            JsonParser parser = new JsonParser();
            JsonObject o = (JsonObject) parser.parse(rawJson);
            String s = o.get("user").getAsJsonObject().get("uid").getAsString();
            userId = Integer.valueOf(s);

            mCookieSessName = o.get("session_name").getAsString();
            mCookieSessId = o.get("sessid").getAsString();
        } catch (ClientProtocolException e) {
            if (e.getCause() instanceof CircularRedirectException) {
                // If we get this authentication has still been successful, so ignore it
            } else {
                throw new HttpAuthenticationFailedException(e);
            }
        } catch (IOException e) {
            // Rethrow; we want to know this was IO exception
            throw e;
        } catch (Exception e) {
            throw new HttpAuthenticationFailedException(e);
        } finally {
            client.getConnectionManager().shutdown();
        }


        if (!isAuthenticated()) {
            throw new HttpAuthenticationFailedException("Invalid credentials");
        }

        return userId;
    }

    private List<NameValuePair> generateCredentialsForPost(String username, String password) {
        List<NameValuePair> args = new ArrayList<NameValuePair>();
        args.add(new BasicNameValuePair("username", username));
        args.add(new BasicNameValuePair("password", password));
        return args;
    }

    public String getCookieSessId() {
        return mCookieSessId;
    }

    public String getCookieSessName() {
        return mCookieSessName;
    }
}