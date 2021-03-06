package edu.uw.tcss450.team3chatapp.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import edu.uw.tcss450.team3chatapp.R;
import edu.uw.tcss450.team3chatapp.model.Chat;
import edu.uw.tcss450.team3chatapp.model.ChatMessage;
import edu.uw.tcss450.team3chatapp.model.ChatMessageNotification;
import edu.uw.tcss450.team3chatapp.model.Connection;
import edu.uw.tcss450.team3chatapp.model.Credentials;
import me.pushy.sdk.Pushy;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;

/** Logic for login screen. */
public class LoginFragment extends Fragment {

    /** User credentials object. */
    private Credentials mCredentials;

    /** Required empty constructor. */
    public LoginFragment() {/* Required empty constructor. */}

    /** {@inheritDoc} */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    /** {@inheritDoc} */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    /**
     * {@inheritDoc}
     * Checks shared preferences for user preference to staying signed in.
     */
    @Override
    public void onStart() {
        super.onStart();

        SharedPreferences prefs = Objects.requireNonNull(getActivity()).getSharedPreferences(
                getString(R.string.keys_shared_prefs), Context.MODE_PRIVATE);

        if (prefs.contains(getString(R.string.keys_prefs_email)) &&
                prefs.contains(getString(R.string.keys_prefs_password)) &&
                prefs.contains(getString(R.string.keys_prefs_stay_logged_in))) {

            final String email = prefs.getString(getString(R.string.keys_prefs_email), "");
            final String password = prefs.getString(getString(R.string.keys_prefs_password), "");
            //Load the two login EditTexts with the credentials found in SharedPrefs
            EditText emailEdit = Objects.requireNonNull(getActivity()).findViewById(R.id.et_login_email);
            emailEdit.setText(email);
            EditText passwordEdit = Objects.requireNonNull(getActivity()).findViewById(R.id.et_login_password);
            passwordEdit.setText(password);

            loginAttempt();
        }
    }

    /**
     * {@inheritDoc}
     * Sets up views on screen and sets onClickListeners.
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //Custom back button functionality to exit app from this fragment
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Handle the back button event
                Objects.requireNonNull(getActivity()).finish();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(this, callback);

        //Set onClickListener for Login Button:
        TextView btnLogin = view.findViewById(R.id.btn_login_login);
        btnLogin.setOnClickListener(v -> loginAttempt());

        TextView tvRegister = view.findViewById(R.id.tv_login_register);
        tvRegister.setOnClickListener(v -> Navigation.findNavController(v)
                .navigate(R.id.nav_action_loginToRegister));
    }

    /** Validates entered credentials with server. */
    private void loginAttempt() {
        EditText etEmail = Objects.requireNonNull(getView()).findViewById(R.id.et_login_email);
        EditText etPass = getView().findViewById(R.id.et_login_password);

        boolean validated = validateLogin(etEmail, etPass);

        if (validated) {
            mCredentials = new Credentials
                    .Builder(etEmail.getText().toString().toLowerCase().trim(), etPass.getText().toString())
                    .build();

            Uri uri = new Uri.Builder()
                    .scheme("https")
                    .appendPath(getString(R.string.ep_base))
                    .appendPath(getString(R.string.ep_login))
                    .build();

            new AttemptLoginTask().execute(uri.toString());
        }
    }

    /**
     * Saves credentials objects to shared preferences.
     * @param theCredentials credentials object to save.
     */
    private void saveCredentials(final Credentials theCredentials) {
        SharedPreferences prefs =
                Objects.requireNonNull(getActivity()).getSharedPreferences(
                        getString(R.string.keys_shared_prefs),
                        Context.MODE_PRIVATE);
        //Store the credentials in SharedPrefs
        prefs.edit().putString(getString(R.string.keys_prefs_email), theCredentials.getEmail()).apply();
        prefs.edit().putString(getString(R.string.keys_prefs_password), theCredentials.getPassword()).apply();
        prefs.edit().putString(getString(R.string.keys_prefs_stay_logged_in), "true").apply();
    }

    /** AsyncTask class responsible for handling login tasks. */
    @SuppressLint("StaticFieldLeak")
    private class AttemptLoginTask extends AsyncTask<String, Void, String> {

        /**
         * {@inheritDoc}
         * Shows login screen and disables buttons.
         */
        @Override
        protected void onPreExecute() {
            Objects.requireNonNull(getActivity()).findViewById(R.id.layout_login_wait).setVisibility(View.VISIBLE);
            Objects.requireNonNull(getActivity()).findViewById(R.id.btn_login_login).setEnabled(false);
            Objects.requireNonNull(getActivity()).findViewById(R.id.tv_login_register).setEnabled(false);
        }

        /**
         * {@inheritDoc}
         * hides loading screen and re-enables buttons.
         */
        @Override
        protected void onCancelled(String result) {
            Log.e("ASYNC_TASK_ERROR", result);
            // Restore layout's appearance
            Objects.requireNonNull(getActivity()).findViewById(R.id.layout_login_wait).setVisibility(View.GONE);
            Objects.requireNonNull(getActivity()).findViewById(R.id.btn_login_login).setEnabled(true);
            Objects.requireNonNull(getActivity()).findViewById(R.id.tv_login_register).setEnabled(true);
        }

        /**
         * {@inheritDoc}
         * makes post request to given url.
         * @param urls the url(s)
         */
        @Override
        protected String doInBackground(String... urls) {

            String deviceToken;
            try {
                // Assign a unique token to this device
                deviceToken = Pushy.register(Objects.requireNonNull(getActivity()).getApplicationContext());
            }
            catch (Exception exc) {
                cancel(true);
                // Return exc to onCancelled
                return exc.getMessage();
            }

            Log.d("LOGIN", "Pushy Token: " + deviceToken);

            StringBuilder response = new StringBuilder();
            HttpURLConnection urlConnection = null;

            try {
                URL urlObject = new URL(urls[0]);
                urlConnection = (HttpURLConnection) urlObject.openConnection();
                urlConnection.setRequestMethod("POST");
                urlConnection.setRequestProperty("Content-Type", "application/json");

                urlConnection.setDoOutput(true);
                OutputStreamWriter wr = new OutputStreamWriter(urlConnection.getOutputStream());

                JSONObject message = mCredentials.asJSONObject();
                message.put(getString(R.string.keys_json_login_pushtoken_str), deviceToken);

                wr.write(message.toString());
                wr.flush();
                wr.close();

                InputStream content = urlConnection.getInputStream();
                BufferedReader buffer = new BufferedReader(new InputStreamReader(content));
                String s;
                while((s = buffer.readLine()) != null) {
                    response.append(s);
                }
                publishProgress();
            } catch (Exception e) {
                response = new StringBuilder("Unable to connect, Reason: "
                        + e.getMessage());
                cancel(true);
            } finally {
                if(urlConnection != null) {
                    urlConnection.disconnect();
                }
            }
            return response.toString();
        }

        /**
         * {@inheritDoc}
         * Parses JSON response and sets up fragment.
         * @param result JSON response from server.
         */
        @Override
        protected void onPostExecute(String result) {
            try {
                JSONObject root = new JSONObject(result);
                if(root.has(getString(R.string.keys_json_login_success_bool))) {
                    boolean success = root.getBoolean(getString(R.string.keys_json_register_success_bool));
                    if(success) {
                        Credentials userCreds = new Credentials.Builder(
                                ((EditText) Objects.requireNonNull(getActivity()).findViewById(R.id.et_login_email)).getText().toString(),
                                ((EditText) Objects.requireNonNull(getActivity()).findViewById(R.id.et_login_password)).getText().toString())
                                .addFirstName(root.getString("firstname"))
                                .addLastName(root.getString("lastname"))
                                .addUsername(root.getString("username"))
                                .build();

                        if(((Switch) Objects.requireNonNull(getActivity()).findViewById(R.id.swt_login_stayloggedin)).isChecked()) {
                            saveCredentials(userCreds);
                        }

                        LoginFragmentDirections.NavActionLoginToHome homeActivity =
                                LoginFragmentDirections.navActionLoginToHome(userCreds);
                        Log.d("JWT", root.getString("token"));
                        homeActivity.setJwt(root.getString("token"));
                        homeActivity.setUserId((Integer) root.get("memberid"));

                        if (getArguments() != null) {
                            if (getArguments().containsKey("type")) {
                                if (Objects.requireNonNull(getArguments().getString("type")).equals("msg")) {
                                    // Go to chat room using information from push notification
                                    try {
                                        JSONObject msg = new JSONObject(Objects.requireNonNull(getArguments().getString("message")));
                                        ChatMessage pushed = new ChatMessage(msg.getInt(getString(R.string.keys_json_connections_memberid_int))  == root.getInt("memberid"),
                                                getArguments().getString("sender"),
                                                msg.getString(getString(R.string.keys_json_push_chatmessage_time)),
                                                msg.getString(getString(R.string.keys_json_push_chatmessage_text)));
                                        int room = msg.getInt(getString(R.string.keys_json_push_chatmessage_room));

                                        ChatMessageNotification chat =
                                                new ChatMessageNotification.Builder(pushed, room).build();
                                        homeActivity.setChatMessage(chat);
                                    } catch(JSONException e) {
                                        // Couldn't get the notification properly, just give up
                                        Objects.requireNonNull(getActivity()).finish();
                                    }
                                } else if (Objects.requireNonNull(getArguments().get("type")).equals("conn")) {
                                    // Go to view connection using information from push notification
                                    try {
                                        JSONObject info = new JSONObject(Objects.requireNonNull(getArguments().getString("message")));
                                        Connection pushed =
                                                new Connection(info.getInt(getString(R.string.keys_json_connections_memberid_int)),
                                                        info.getString(getString(R.string.keys_json_connections_firstname_str)),
                                                        info.getString(getString(R.string.keys_json_connections_lastname_str)),
                                                        info.getString(getString(R.string.keys_json_connections_username_str)),
                                                        info.getString(getString(R.string.keys_prefs_email)),
                                                        0, false);
                                        homeActivity.setConnection(pushed);
                                    } catch (JSONException e) {
                                        // Couldn't get the notification properly, just give up
                                        Objects.requireNonNull(getActivity()).finish();
                                    }
                                } else if (Objects.requireNonNull(getArguments().get("type")).equals("convo")) {
                                    // Go to chatroom using information from push notification
                                    try {
                                        JSONObject info = new JSONObject(Objects.requireNonNull(getArguments().getString("message")));
                                        Chat pushed = new Chat(info.getInt(getString(R.string.keys_json_chats_id)),
                                                info.getString(getString(R.string.keys_json_chats_name)),
                                                info.getString(getString(R.string.keys_json_chats_description)));
                                        homeActivity.setChat(pushed);
                                    } catch (JSONException e) {
                                        // Couldn't get the notification properly, just give up
                                        Objects.requireNonNull(getActivity()).finish();
                                    }
                                }
                            }
                        }

                        Navigation
                                .findNavController(Objects.requireNonNull(getView()))
                                .navigate(homeActivity);

                        Objects.requireNonNull(getActivity()).finish();
                    } else {
                        Log.e("ERROR_LOGIN", "Unsuccessful");
                        ((EditText) Objects
                                .requireNonNull(getView())
                                .findViewById(R.id.et_login_email))
                                .setError("Unable to login.\nPlease check credentials and retry.");
                    }
                    Objects.requireNonNull(getActivity()).findViewById(R.id.layout_login_wait).setVisibility(View.GONE);
                    Objects.requireNonNull(getActivity()).findViewById(R.id.btn_login_login).setEnabled(true);
                    Objects.requireNonNull(getActivity()).findViewById(R.id.tv_login_register).setEnabled(true);
                } else {
                    Log.e("ERROR", "Unsuccessful");
                }
            } catch(JSONException error) {
                Log.e("JSON_PARSE_ERROR", result
                        + System.lineSeparator()
                        + error.getMessage());

                Objects.requireNonNull(getActivity()).findViewById(R.id.layout_login_wait).setVisibility(View.GONE);
                Objects.requireNonNull(getActivity()).findViewById(R.id.btn_login_login).setEnabled(true);
                Objects.requireNonNull(getActivity()).findViewById(R.id.tv_login_register).setEnabled(true);

                ((TextView) Objects.requireNonNull(getView()).findViewById(R.id.et_login_email)).setError("Unable to login.\nPlease try again later");
            }
        }
    }

    /**
     * Basic in-app validation so more obvious invalid logins don't go to server for validation.
     * @param etEmail   where the user entered their email.
     * @param etPass    where the user entered their password.
     * @return          true if the credentials are valid, false otherwise.
     */
    private boolean validateLogin(EditText etEmail, EditText etPass) {
        boolean result = true;
        String email = etEmail.getText().toString().trim();
        String pass = etPass.getText().toString().trim();
        String emailPattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+";

        if ("".equals(email) || !email.matches(emailPattern)) {
            result = false;
            etEmail.setError("Invalid Email Address");
        } else if (pass.length() < 6) {
            result = false;
            etPass.setError("Invalid Password");
        }
        return result;
    }
}