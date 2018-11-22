package com.hangloose.ui.activities

import android.annotation.SuppressLint
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.databinding.DataBindingUtil
import android.databinding.Observable
import android.databinding.ObservableBoolean
import android.os.Bundle
import android.text.Editable
import android.text.Selection
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.facebook.AccessToken
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.GraphRequest
import com.facebook.Profile
import com.facebook.ProfileTracker
import com.facebook.login.LoginResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.hangloose.R
import com.hangloose.databinding.ActivitySignUpBinding
import com.hangloose.model.ConsumerData
import com.hangloose.model.ConsumerDetails
import com.hangloose.network.ConsumerAuthDetailResponse
import com.hangloose.network.ConsumerLoginRequest
import com.hangloose.utils.AUTH_TYPE
import com.hangloose.utils.PASSWORD_CONFIRM_PASSWORD_DOES_NOT_MATCH
import com.hangloose.utils.VALID_PASSWORD
import com.hangloose.utils.VALID_PHONE
import com.hangloose.viewmodel.ConsumerRegisterViewModel
import kotlinx.android.synthetic.main.activity_sign_up.*
import retrofit2.Response
import java.util.Arrays

class SignUpActivity : BaseActivity(), View.OnClickListener {

    private val TAG: String = "SignUpActivity"
    private var mGoogleSignInClient: GoogleSignInClient? = null
    private val RC_SIGN_IN = 9001
    private var mFBCallbackManager: CallbackManager? = null
    private var mProfileTracker: ProfileTracker? = null
    private var mActivitySignUpBinding: ActivitySignUpBinding? = null
    private lateinit var mConsumerRegisterViewModel: ConsumerRegisterViewModel

    @SuppressLint("NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initBinding()
        intializeGoogleSignInOptions()
        signInWithFacebook()
        //setSpannableString()
    }

    override fun init() {
        btnGoogleSignIn.setOnClickListener(this)
        btnCustomSignInFB.setOnClickListener(this)
        makeSignInClickable()
    }

    override fun onClick(view: View?) {
        when (view!!.id) {
            btnGoogleSignIn.id -> signIn()
            btnCustomSignInFB.id -> btnSignUpFB.performClick()
        }
    }

    override fun onStart() {
        super.onStart()
        mGoogleSignInClient!!.silentSignIn().addOnCompleteListener {
            handleSignInResult(it)
        }
    }

    private fun makeSignInClickable() {
        val spannable = SpannableString(resources.getString(R.string.already_have_an_account))
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(view: View?) {
                finish()
            }
        }
        spannable.setSpan(clickableSpan, 25, 31, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        tvSignUpClick.text = spannable
        tvSignUpClick.movementMethod = LinkMovementMethod.getInstance()
    }

    /**
     * method to perform click on close button
     * @view view clicked
     */
    fun onCloseClick(view: View) {
        finish()
    }

    /**
     * method to intialize data binding with view
     */
    private fun initBinding() {
        mActivitySignUpBinding = DataBindingUtil.setContentView(this, R.layout.activity_sign_up)
        mActivitySignUpBinding!!.clickHandler = this
        mConsumerRegisterViewModel = ViewModelProviders.of(this).get(ConsumerRegisterViewModel::class.java)
        mActivitySignUpBinding!!.consumerRegisterViewModel = mConsumerRegisterViewModel
        mConsumerRegisterViewModel.registerResponse()
            .observe(this, Observer<Response<ConsumerAuthDetailResponse>> { t ->
                var consumerDetails = t!!.body()!!.consumer!!
                var headers = t.headers()
                val consumerData = ConsumerData(
                    headers.get("X-AUTH-TOKEN").toString(),
                    consumerDetails.existing,
                    consumerDetails.id,
                    consumerDetails.mobile,
                    consumerDetails.authType
                )
                ConsumerDetails.consumerData = consumerData
                Log.i(TAG, """onChanged : ${ConsumerDetails.consumerData}""")
                onNavigateOTPScreen()
            })

        mConsumerRegisterViewModel.isPhoneValid.addOnPropertyChangedCallback(object :
            Observable.OnPropertyChangedCallback() {
            override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
                ilPhone.isErrorEnabled = !(sender as ObservableBoolean).get()
                if (ilPhone.isErrorEnabled) {
                    ilPhone.error = VALID_PHONE
                }
            }
        })
        mConsumerRegisterViewModel.isPasswordValid.addOnPropertyChangedCallback(object :
            Observable.OnPropertyChangedCallback() {
            override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
                ilPassword.isErrorEnabled = !(sender as ObservableBoolean).get()
                if (ilPassword.isErrorEnabled) {
                    ilPassword.error = VALID_PASSWORD
                }
            }
        })
        mConsumerRegisterViewModel.isConfirmPasswordValid.addOnPropertyChangedCallback(object :
            Observable.OnPropertyChangedCallback() {
            override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
                ilConfirmPassword.isErrorEnabled = !(sender as ObservableBoolean).get()
                if (ilConfirmPassword.isErrorEnabled) {
                    ilConfirmPassword.error = PASSWORD_CONFIRM_PASSWORD_DOES_NOT_MATCH
                }
            }
        })
    }

    /**
     * method to intialize google signIn options
     */
    private fun intializeGoogleSignInOptions() {
        // Configure sign-in to request the user's ID, email address, and basic
        // profile. ID and basic profile are included in DEFAULT_SIGN_IN.
        var gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.server_client_id))
            .requestEmail()
            .build()
        // Build a GoogleSignInClient with the options specified by gso.
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun signInWithFacebook() {

        mFBCallbackManager = CallbackManager.Factory.create()

        btnSignUpFB.setReadPermissions(Arrays.asList("email", "public_profile"))

        btnSignUpFB.registerCallback(mFBCallbackManager, object : FacebookCallback<LoginResult> {
            override fun onSuccess(loginResult: LoginResult?) {

                if (Profile.getCurrentProfile() == null) {
                    mProfileTracker = object : ProfileTracker() {
                        override fun onCurrentProfileChanged(oldProfile: Profile?, currentProfile: Profile?) {
                            Log.v(TAG, currentProfile!!.firstName)
                            getFacebookUserProfile(AccessToken.getCurrentAccessToken())
                            mProfileTracker!!.stopTracking()
                        }
                    }
                } else {
                    Log.d(TAG, "Username is: " + Profile.getCurrentProfile().name)
                    getFacebookUserProfile(AccessToken.getCurrentAccessToken())
                }
                mProfileTracker!!.startTracking()
            }

            override fun onCancel() {
                Log.i(TAG, "onCancel")
            }

            override fun onError(error: FacebookException?) {
                Log.i(TAG, "onError")
            }
        })
    }

    fun getFacebookUserProfile(accessToken: AccessToken) {
        val dataRequest = GraphRequest.newMeRequest(
            accessToken
        ) { jsonObject, response ->
            val email = jsonObject.getString("email")

            mConsumerRegisterViewModel.onFacebookSignUpClick(
                ConsumerLoginRequest(
                    AUTH_TYPE.FACEBOOK.name,
                    email,
                    accessToken.token
                )
            )
        }

        val parameters = Bundle()
        parameters.putString("fields", "first_name,last_name,email,id")
        dataRequest.parameters = parameters
        dataRequest.executeAsync()
    }

    private fun signIn() {
        val signInIntent = mGoogleSignInClient?.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...)
        if (requestCode == RC_SIGN_IN) {
            // The Task returned from this call is always completed, no need to attach
            // a listener.
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        } else {
            mFBCallbackManager!!.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onBackPressed() {}

    /**
     * method to get successful/failure google login
     */
    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            mConsumerRegisterViewModel.onGoogleSignUpClick(
                ConsumerLoginRequest(
                    AUTH_TYPE.GOOGLE.name,
                    account!!.email,
                    account!!.idToken
                )
            )
        } catch (e: ApiException) {
            // The ApiException status code indicates the detailed failure reason.
            // Please refer to the GoogleSignInStatusCodes class reference for more information.
            Log.i(TAG, """signInResult:failed code=${e.statusCode}""")
        }
    }

    fun onNavigateOTPScreen() {
        var intent = Intent(this@SignUpActivity, OTPActivity::class.java)
        startActivity(intent)
    }

    @SuppressLint("NewApi")
    private fun setSpannableString() {
        // Make links in the EditText clickable
        etPhone.movementMethod = LinkMovementMethod.getInstance()
        // Setup my Spannable with clickable URLs
        val spannable = SpannableString("IN +91 | ")
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(textView: View) {
                etPhone.post {
                    Selection.setSelection(etPhone.text, etPhone.text.length)
                }
                Toast.makeText(this@SignUpActivity, "Spannable Clicked", Toast.LENGTH_SHORT).show()
            }

            override fun updateDrawState(ds: TextPaint?) {
                super.updateDrawState(ds)
                ds!!.isUnderlineText = false
                ds.bgColor = android.R.color.white
            }
        }

        spannable.setSpan(RelativeSizeSpan(1.5f), 7, 8, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        spannable.setSpan(clickableSpan, 0, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(
            ForegroundColorSpan(resources.getColor(android.R.color.white, null)),
            0,
            spannable.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        //etPhoneNumber.setText(spannable, TextView.BufferType.NORMAL)
        //Selection.setSelection(etPhoneNumber.text, etPhoneNumber.text.length)
        //etPhoneNumber.post { etPhoneNumber.setSelection(etPhoneNumber.length()) }
        //tvCountryCode.text = spannable
        etPhone.addTextChangedListener(object : TextWatcher {

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (!s.toString().startsWith(spannable)) {
                    etPhone.setText(spannable, TextView.BufferType.SPANNABLE)
                    Selection.setSelection(etPhone.text, etPhone.text.length)
                }
            }

            override fun beforeTextChanged(
                s: CharSequence, start: Int, count: Int,
                after: Int
            ) {
                // TODO Auto-generated method stub
            }

            override fun afterTextChanged(s: Editable) {
            }
        })
    }
}