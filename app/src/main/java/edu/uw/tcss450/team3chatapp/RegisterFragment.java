package edu.uw.tcss450.team3chatapp;

import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import edu.uw.tcss450.team3chatapp.model.Credentials;
import edu.uw.tcss450.team3chatapp.utils.SendPostAsyncTask;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;


/**
 * A simple {@link Fragment} subclass.
 */
public class RegisterFragment extends Fragment {

    public RegisterFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_register, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button btnRegister = getView().findViewById(R.id.btn_register_register);
        btnRegister.setOnClickListener(this::registerAttempt);
    }

    private void registerAttempt(final View tButton) {
        EditText etFirstName = getView().findViewById(R.id.et_register_firstName);
        EditText etLastName = getView().findViewById(R.id.et_register_lastName);
        EditText etUsername = getView().findViewById(R.id.et_register_nickname);
        EditText etEmail = getView().findViewById(R.id.et_register_email);
        EditText etPassword = getView().findViewById(R.id.et_register_password);
        EditText etConfPass = getView().findViewById(R.id.et_register_confpass);

        boolean isValid = validateCredentials(etFirstName, etLastName, etUsername,
                                                etEmail, etPassword, etConfPass);

        if(isValid) {
            Credentials credentials = new Credentials
                    .Builder(etEmail.getText().toString().toLowerCase().trim(), etPassword.getText().toString())
                    .addFirstName(etFirstName.getText().toString().trim())
                    .addLastName(etLastName.getText().toString().trim())
                    .addUsername(etUsername.getText().toString().trim())
                    .build();

            //TODO Send credentials to database
            Uri uri = new Uri.Builder()
                    .scheme("https")
                    .appendPath(getString(R.string.ep_base))
                    .appendPath(getString(R.string.ep_register))
                    .build();

            JSONObject msg = credentials.asJSONObject();

            new SendPostAsyncTask
                    .Builder(uri.toString(), msg)
                    .onPreExecute(this::handleRegisterPre)
                    .onPostExecute(this::handleRegisterPost)
                    .onCancelled(this::handleRegisterCancelled)
                    .build()
                    .execute();
        }
    }

    private boolean validateCredentials(EditText etFirstName,
                                        EditText etLastName,
                                        EditText etUsername,
                                        EditText etEmail,
                                        EditText etPassword,
                                        EditText etConfPass) {

        boolean result = true;
        String firstName = etFirstName.getText().toString().trim();
        String lastName = etLastName.getText().toString().trim();
        String nickname = etUsername.getText().toString().trim();
        String email = etEmail.getText().toString().toLowerCase().trim();
        String pass = etPassword.getText().toString();
        String conf = etConfPass.getText().toString();
        String namePattern = "[a-zA-Z]+";
        String nicknamePattern = "[a-zA-Z_-]+";
        String emailPattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+";

        if ("".equals(firstName) || !firstName.matches(namePattern) || firstName.length() > 16) {
            result = false;
            etFirstName.setError("First Name does not match criteria:\n" +
                    "- Must be at least 1 character\n" +
                    "- Must not exceed 16 characters\n" +
                    "- Must only contain letter characters");
        } else if ("".equals(lastName) || !lastName.matches(namePattern) || lastName.length() > 16) {
            result = false;
            etLastName.setError("Last Name does not match criteria:\n" +
                    "- Must be at least 1 character\n" +
                    "- Must not exceed 16 characters\n" +
                    "- Must only contain letter characters");
        }else if(!nickname.matches(nicknamePattern) || nickname.length() < 4 || nickname.length() > 16) {
            result = false;
            etUsername.setError("Nickname does not match criteria:\n" +
                    "- Must be at least 4 character\n" +
                    "- Must not exceed 16 characters\n" +
                    "- Must only contain alphanumeric characters\n" +
                    "- Only valid symbols are '-' or '_'");
        }else if("".equals(email) || !email.matches(emailPattern)) {
            result = false;
            etEmail.setError("Invalid Email Address");
        }else if("".equals(pass)) {
            result = false;
            etPassword.setError("Password cannot be blank");
        }else if(pass.length() < 6) {
            result = false;
            etPassword.setError("Password must be at least 6 characters in length");
        }else if(!pass.equals(conf)) {
            result = false;
            String error = "Passwords must match";
            etPassword.setError(error);
            etConfPass.setError(error);
        }

        return result;
    }

    private void handleRegisterCancelled(String result) {
        Log.e("ASYNC_TASK_ERROR", result);
    }

    private void handleRegisterPre() {
        //TODO enable wait layout
    }

    private void handleRegisterPost(String result) {
        try {
            JSONObject root = new JSONObject(result);
            if(root.has(getString(R.string.keys_json_register_success_bool))) {
                boolean success = root.getBoolean(getString(R.string.keys_json_register_success_bool));
                if(success) {
                    Navigation.findNavController(Objects.requireNonNull(getView())).navigate(R.id.nav_action_registerToVerification);
                } else {
                    //TODO if duplicate email/username, set the corresponding field in error and make error message prettier.
                    Log.e("ERROR", "Unsuccessful");
                    JSONObject error = root.getJSONObject(getString(R.string.keys_json_register_error_json));
                    String detail = error.getString(getString(R.string.keys_json_register_detail_string));
                    ((TextView) Objects
                            .requireNonNull(getView())
                            .findViewById(R.id.et_register_firstName))
                            .setError(detail);
                }
                //TODO hide wait layout on success here
            } else {
                Log.e("ERROR", "No Success");
            }
        } catch(JSONException error) {
            Log.e("JSON_PARSE_ERROR", result
                    + System.lineSeparator()
                    + error.getMessage());

            //TODO hide wait layout on error here

            ((TextView) Objects.requireNonNull(getView()).findViewById(R.id.et_register_email)).setError("Unable to register, please try again later");
        }
    }
}