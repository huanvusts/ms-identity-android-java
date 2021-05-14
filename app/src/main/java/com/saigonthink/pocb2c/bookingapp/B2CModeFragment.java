// Copyright (c) Microsoft Corporation.
// All rights reserved.
//
// This code is licensed under the MIT License.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files(the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and / or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions :
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.

package com.saigonthink.pocb2c.bookingapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.microsoft.identity.client.AcquireTokenParameters;
import com.microsoft.identity.client.AuthenticationCallback;
import com.microsoft.identity.client.IAccount;
import com.microsoft.identity.client.IAuthenticationResult;
import com.microsoft.identity.client.IMultipleAccountPublicClientApplication;
import com.microsoft.identity.client.IPublicClientApplication;
import com.microsoft.identity.client.Prompt;
import com.microsoft.identity.client.PublicClientApplication;
import com.microsoft.identity.client.SilentAuthenticationCallback;
import com.microsoft.identity.client.exception.MsalClientException;
import com.microsoft.identity.client.exception.MsalException;
import com.microsoft.identity.client.exception.MsalServiceException;
import com.microsoft.identity.client.exception.MsalUiRequiredException;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation sample for 'B2C' mode.
 */
public class B2CModeFragment extends Fragment {
    private static final String TAG = B2CModeFragment.class.getSimpleName();

    /* UI & Debugging Variables */
    Button getWeatherForecastButton;
    Button testButton;
    Button removeAccountButton;
    Button runUserFlowButton;
    Button acquireTokenSilentButton;
    TextView graphResourceTextView;
    TextView logTextView;
    Spinner policyListSpinner;
    Spinner b2cUserList;

    private List<B2CUser> users;

    private String accessToken = "";

    /* Azure AD Variables */
    private IMultipleAccountPublicClientApplication b2cApp;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        final View view = inflater.inflate(R.layout.fragment_b2c_mode, container, false);
        initializeUI(view);

        // Creates a PublicClientApplication object with res/raw/auth_config_single_account.json
        PublicClientApplication.createMultipleAccountPublicClientApplication(getContext(),
                R.raw.auth_config_b2c,
                new IPublicClientApplication.IMultipleAccountApplicationCreatedListener() {
                    @Override
                    public void onCreated(IMultipleAccountPublicClientApplication application) {
                        b2cApp = application;
                        loadAccounts();
                    }

                    @Override
                    public void onError(MsalException exception) {
                        displayError(exception);
                        removeAccountButton.setEnabled(false);
                        runUserFlowButton.setEnabled(false);
                        acquireTokenSilentButton.setEnabled(false);
                    }
                });

        return view;
    }

    /**
     * Initializes UI variables and callbacks.
     */
    private void initializeUI(@NonNull final View view) {
        getWeatherForecastButton = view.findViewById(R.id.btn_getWeatherForecast);
        testButton = view.findViewById(R.id.btn_test);
        removeAccountButton = view.findViewById(R.id.btn_removeAccount);
        runUserFlowButton = view.findViewById(R.id.btn_runUserFlow);
        acquireTokenSilentButton = view.findViewById(R.id.btn_acquireTokenSilently);
        graphResourceTextView = view.findViewById(R.id.msgraph_url);
        logTextView = view.findViewById(R.id.txt_log);
        policyListSpinner = view.findViewById(R.id.policy_list);
        b2cUserList = view.findViewById(R.id.user_list);

        final ArrayAdapter<String> dataAdapter = new ArrayAdapter<>(
                getContext(), android.R.layout.simple_spinner_item,
                new ArrayList<String>() {{
                    for (final String policyName : B2CConfiguration.Policies)
                        add(policyName);
                }}
        );

        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        policyListSpinner.setAdapter(dataAdapter);
        dataAdapter.notifyDataSetChanged();

        runUserFlowButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (b2cApp == null) {
                    return;
                }

                /**
                 * Runs user flow interactively.
                 * <p>
                 * Once the user finishes with the flow, you will also receive an access token containing the claims for the scope you passed in (see B2CConfiguration.getScopes()),
                 * which you can subsequently use to obtain your resources.
                 */

                AcquireTokenParameters parameters = new AcquireTokenParameters.Builder()
                        .startAuthorizationFromActivity(getActivity())
                        .fromAuthority(B2CConfiguration.getAuthorityFromPolicyName(policyListSpinner.getSelectedItem().toString()))
                        .withScopes(B2CConfiguration.getScopes())
                        .withPrompt(Prompt.WHEN_REQUIRED)
                        .withCallback(getAuthInteractiveCallback())
                        .build();

                b2cApp.acquireToken(parameters);

            }
        });

        acquireTokenSilentButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (b2cApp == null) {
                    return;
                }

                final B2CUser selectedUser = users.get(b2cUserList.getSelectedItemPosition());
                selectedUser.acquireTokenSilentAsync(b2cApp,
                        policyListSpinner.getSelectedItem().toString(),
                        B2CConfiguration.getScopes(),
                        getAuthSilentCallback());
            }
        });

        removeAccountButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (b2cApp == null) {
                    return;
                }

                final B2CUser selectedUser = users.get(b2cUserList.getSelectedItemPosition());
                selectedUser.signOutAsync(b2cApp,
                        new IMultipleAccountPublicClientApplication.RemoveAccountCallback() {
                            @Override
                            public void onRemoved() {
                                // Redirect to Azure B2CUI
                                String tenantName = "stpocb2c";
                                String policy = "B2C_1_poc_signup_signin";
                                String clientId = "a07ecc71-a16d-4327-a231-7d6a60d052ea";
                                String scope = "https%3A%2F%2Fstpocb2c.onmicrosoft.com%2F20f88c09-5a06-46b9-a0b2-5c654df73ad6%2Fapp.read.all%20openid%20offline_access%20profile";
                                String redirect_uri = "msauth%3A%2F%2Fcom.saigonthink.poc.b2c%2FJT6%252BNmaebjCknIwbtnUv6DSy3hM%253D";
                                String logoutURL = String.format("https://%s.b2clogin.com/tfp/%s.onmicrosoft.com/%s/oAuth2/v2.0/logout?response_type=code&x-client-Ver=1.5.9&code_challenge_method=S256&x-client-SKU=MSAL.Android&client_id=%s&instance_aware=false&scope=%s&redirect_uri=%s",
                                        tenantName,
                                        tenantName,
                                        policy,
                                        clientId,
                                        scope,
                                        redirect_uri);
                                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(logoutURL));
                                if (browserIntent.resolveActivity(getContext().getPackageManager()) != null) {
                                    startActivity(browserIntent);
                                }

                                // Clear local access token and reload
                                logTextView.setText("Signed Out.");
                                loadAccounts();
                                accessToken = "";
                            }

                            @Override
                            public void onError(@NonNull MsalException exception) {
                                displayError(exception);
                            }
                        });
            }
        });

        getWeatherForecastButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String apiURL = "https://app-fetching-data-web-api.azurewebsites.net/api/v1/WeatherForecast";
                callGetWeatherForecastAPI(apiURL, accessToken);
            }
        });

        testButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            }
        });
    }

    /**
     * Load signed-in accounts, if there's any.
     */
    private void loadAccounts() {
        if (b2cApp == null) {
            return;
        }

        b2cApp.getAccounts(new IPublicClientApplication.LoadAccountsCallback() {
            @Override
            public void onTaskCompleted(final List<IAccount> result) {
                users = B2CUser.getB2CUsersFromAccountList(result);
                updateUI(users);
            }

            @Override
            public void onError(MsalException exception) {
                displayError(exception);
            }
        });
    }

    /**
     * Callback used in for silent acquireToken calls.
     */
    private SilentAuthenticationCallback getAuthSilentCallback() {
        return new SilentAuthenticationCallback() {

            @Override
            public void onSuccess(IAuthenticationResult authenticationResult) {
                Log.d(TAG, "Successfully authenticated");

                /* Successfully got a token. */
                displayResult(authenticationResult);

                accessToken = authenticationResult.getAccessToken();
            }

            @Override
            public void onError(MsalException exception) {
                /* Failed to acquireToken */
                Log.d(TAG, "Authentication failed: " + exception.toString());
                displayError(exception);

                if (exception instanceof MsalClientException) {
                    /* Exception inside MSAL, more info inside MsalError.java */
                } else if (exception instanceof MsalServiceException) {
                    /* Exception when communicating with the STS, likely config issue */
                } else if (exception instanceof MsalUiRequiredException) {
                    /* Tokens expired or no session, retry with interactive */
                }
            }
        };
    }

    /**
     * Callback used for interactive request.
     * If succeeds we use the access token to call the Microsoft Graph.
     * Does not check cache.
     */
    private AuthenticationCallback getAuthInteractiveCallback() {
        return new AuthenticationCallback() {

            @Override
            public void onSuccess(IAuthenticationResult authenticationResult) {
                /* Successfully got a token, use it to call a protected resource - MSGraph */
                Log.d(TAG, "Successfully authenticated");

                /* display result info */
                displayResult(authenticationResult);

                accessToken = authenticationResult.getAccessToken();

                /* Reload account asynchronously to get the up-to-date list. */
                loadAccounts();
            }

            @Override
            public void onError(MsalException exception) {
                final String B2C_PASSWORD_CHANGE = "AADB2C90118";
                if (exception.getMessage().contains(B2C_PASSWORD_CHANGE)) {
                    logTextView.setText("The user clicks the 'Forgot Password' link in a sign-up or sign-in user flow.\n" +
                            "Your application needs to handle this error code by running a specific user flow that resets the password.");
                    return;
                }

                /* Failed to acquireToken */
                Log.d(TAG, "Authentication failed: " + exception.toString());
                displayError(exception);

                if (exception instanceof MsalClientException) {
                    /* Exception inside MSAL, more info inside MsalError.java */
                } else if (exception instanceof MsalServiceException) {
                    /* Exception when communicating with the STS, likely config issue */
                }
            }

            @Override
            public void onCancel() {
                /* User canceled the authentication */
                Log.d(TAG, "User cancelled login.");
            }
        };
    }

    //
    // Helper methods manage UI updates
    // ================================
    // displayResult() - Display the authentication result.
    // displayError() - Display the token error.
    // updateSignedInUI() - Updates UI when the user is signed in
    // updateSignedOutUI() - Updates UI when app sign out succeeds
    //

    /**
     * Display the graph response
     */
    private void displayResult(@NonNull final IAuthenticationResult result) {
        final String output =
                "Access Token :" + result.getAccessToken() + "\n" +
                        "Scope : " + result.getScope() + "\n" +
                        "Expiry : " + result.getExpiresOn() + "\n" +
                        "Tenant ID : " + result.getTenantId() + "\n";

        logTextView.setText(output);
    }

    /**
     * Display the error message
     */
    private void displayError(@NonNull final Exception exception) {
        logTextView.setText(exception.toString());
    }

    private void displayError(@NonNull final String exception) {
        logTextView.setText(exception);
    }

    /**
     * Updates UI based on the obtained user list.
     */
    private void updateUI(final List<B2CUser> users) {
        if (users.size() != 0) {
            removeAccountButton.setEnabled(true);
            acquireTokenSilentButton.setEnabled(true);
        } else {
            removeAccountButton.setEnabled(false);
            acquireTokenSilentButton.setEnabled(false);
        }

        final ArrayAdapter<String> dataAdapter = new ArrayAdapter<>(
                getContext(), android.R.layout.simple_spinner_item,
                new ArrayList<String>() {{
                    for (final B2CUser user : users)
                        add(user.getDisplayName());
                }}
        );

        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        b2cUserList.setAdapter(dataAdapter);
        dataAdapter.notifyDataSetChanged();
    }

    private void callGetWeatherForecastAPI(String testUrl, String accessToken) {

        if (accessToken.isEmpty()) {
            displayError("Access token is required!");
            return;
        }

        MSGraphRequestWrapper.callTestAPIUsingVolley(
                getContext(),
                testUrl,
                accessToken,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        /* Successfully called graph, process data and send to UI */
                        Log.d(TAG, "Response: " + response.toString());
                        displayTestApiResult(response);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.d(TAG, "Error: " + error.toString());
                        displayError(error);
                    }
                });
    }

    private void displayTestApiResult(@NonNull final String result) {
        logTextView.setText(result.toString());
    }
}

