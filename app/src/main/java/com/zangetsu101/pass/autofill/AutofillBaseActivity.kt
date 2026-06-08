package com.zangetsu101.pass.autofill

import android.content.Intent
import android.service.autofill.Dataset
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.zangetsu101.pass.decryption.Decryption
import com.zangetsu101.pass.decryption.DecryptionError
import com.zangetsu101.pass.keymanagement.session.SessionError
import com.zangetsu101.pass.passstore.PassEntry
import com.zangetsu101.pass.session.SessionStartActivity
import kotlinx.coroutines.launch
import javax.inject.Inject

abstract class AutofillBaseActivity : FragmentActivity() {
    @AutofillDecryption @Inject
    lateinit var decryption: Decryption

    protected fun authenticate(
        entry: PassEntry,
        usernameId: AutofillId?,
        passwordId: AutofillId?,
    ) {
        lifecycleScope.launch {
            try {
                val creds = decryption.decrypt(entry, this@AutofillBaseActivity)

                val label = "${entry.username}${entry.domain?.let { " ($it)" } ?: ""}"
                val presentation =
                    RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                        setTextViewText(android.R.id.text1, label)
                    }

                // setValue(AutofillId, AutofillValue, RemoteViews) deprecated in API 33; replacement requires minSdk 33
                @Suppress("DEPRECATION")
                val dataset =
                    Dataset
                        .Builder()
                        .apply {
                            usernameId?.let { setValue(it, AutofillValue.forText(creds.username), presentation) }
                            passwordId?.let { setValue(it, AutofillValue.forText(String(creds.password)), presentation) }
                        }.build()

                creds.zero()

                val replyIntent =
                    Intent().apply {
                        putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
                    }
                setResult(RESULT_OK, replyIntent)
                finish()
            } catch (e: SessionError.NoActiveSession) {
                startActivity(Intent(this@AutofillBaseActivity, SessionStartActivity::class.java))
                cancelAndFinish()
            } catch (e: DecryptionError) {
                cancelAndFinish()
            } catch (e: Exception) {
                cancelAndFinish()
            }
        }
    }

    protected fun cancelAndFinish() {
        setResult(RESULT_CANCELED)
        finish()
    }
}
