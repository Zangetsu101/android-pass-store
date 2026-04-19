package com.example.pass.autofill

import android.app.assist.AssistStructure
import android.content.Intent
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.pass.decryption.Decryption
import com.example.pass.decryption.DecryptionError
import com.example.pass.passstore.PassStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AutofillAuthActivity : FragmentActivity() {

    @Inject lateinit var passStore: PassStore
    @Inject lateinit var decryption: Decryption

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val path = intent.getStringExtra(EXTRA_ENTRY_PATH)
        if (path == null) { cancelAndFinish(); return }

        val entry = passStore.index.value.find { it.path == path }
        if (entry == null) { cancelAndFinish(); return }

        @Suppress("DEPRECATION")
        val usernameId = intent.getParcelableExtra<AutofillId>(EXTRA_USERNAME_ID)
        @Suppress("DEPRECATION")
        val passwordId = intent.getParcelableExtra<AutofillId>(EXTRA_PASSWORD_ID)

        if (usernameId == null && passwordId == null) { cancelAndFinish(); return }

        lifecycleScope.launch {
            try {
                val creds = decryption.decryptForAutofill(entry, this@AutofillAuthActivity)

                val label = "${entry.username}${entry.domain?.let { " ($it)" } ?: ""}"
                val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                    setTextViewText(android.R.id.text1, label)
                }

                val dataset = Dataset.Builder().apply {
                    usernameId?.let { setValue(it, AutofillValue.forText(creds.username), presentation) }
                    passwordId?.let { setValue(it, AutofillValue.forText(String(creds.password)), presentation) }
                }.build()

                creds.zero()

                val replyIntent = Intent().apply {
                    putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
                }
                setResult(RESULT_OK, replyIntent)
            } catch (e: DecryptionError) {
                cancelAndFinish()
                return@launch
            } catch (e: Exception) {
                cancelAndFinish()
                return@launch
            }
            finish()
        }
    }

    private fun cancelAndFinish() {
        setResult(RESULT_CANCELED)
        finish()
    }

    companion object {
        const val EXTRA_ENTRY_PATH = "entry_path"
        const val EXTRA_USERNAME_ID = "username_autofill_id"
        const val EXTRA_PASSWORD_ID = "password_autofill_id"
    }
}
