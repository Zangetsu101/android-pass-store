package com.zangetsu101.pass.autofill

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import com.zangetsu101.pass.MainActivity
import com.zangetsu101.pass.passstore.PassEntry
import com.zangetsu101.pass.passstore.PassStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PassAndroidAutofillService : AutofillService() {
    @Inject lateinit var passStore: PassStore

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback,
    ) {
        val context =
            request.fillContexts.lastOrNull() ?: run {
                callback.onSuccess(null)
                return
            }

        val structure = context.structure
        val packageName = structure.activityComponent.packageName

        if (packageName == applicationContext.packageName) {
            callback.onSuccess(null)
            return
        }

        val webDomain = findWebDomain(structure)
        val candidates =
            if (webDomain != null) {
                passStore.resolve(webDomain)
            } else {
                passStore.resolveByPackage(packageName)
            }

        val usernameNode = findNode(structure, ::isUsernameNode)
        val passwordNode = findNode(structure, ::isPasswordNode)

        if (usernameNode == null && passwordNode == null) {
            callback.onSuccess(null)
            return
        }

        val inlineSpecs =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                request.inlineSuggestionsRequest?.inlinePresentationSpecs ?: emptyList()
            } else {
                emptyList()
            }

        val datasets =
            candidates.mapIndexed { i, entry ->
                val spec = inlineSpecs.getOrNull(i) ?: inlineSpecs.lastOrNull()
                buildLockedDataset(entry, usernameNode?.autofillId, passwordNode?.autofillId, i, spec)
            }

        val searchSpec = inlineSpecs.getOrNull(candidates.size) ?: inlineSpecs.lastOrNull()
        val initialQuery = webDomain ?: packageName
        val searchDataset =
            buildSearchDataset(
                usernameNode?.autofillId,
                passwordNode?.autofillId,
                candidates.size,
                searchSpec,
                initialQuery,
            )

        val response =
            FillResponse
                .Builder()
                .apply {
                    datasets.forEach { addDataset(it) }
                    addDataset(searchDataset)
                }.build()
        callback.onSuccess(response)
    }

    override fun onSaveRequest(
        request: SaveRequest,
        callback: SaveCallback,
    ) {
        callback.onSuccess()
    }

    private fun buildLockedDataset(
        entry: PassEntry,
        usernameId: AutofillId?,
        passwordId: AutofillId?,
        requestCode: Int,
        inlineSpec: InlinePresentationSpec?,
    ): android.service.autofill.Dataset {
        val label = "${entry.username}${entry.domain?.let { " ($it)" } ?: ""}"
        val presentation =
            RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                setTextViewText(android.R.id.text1, label)
            }

        val authIntent =
            Intent(this, AutofillAuthActivity::class.java).apply {
                putExtra(AutofillAuthActivity.EXTRA_ENTRY_PATH, entry.path)
                usernameId?.let { putExtra(AutofillAuthActivity.EXTRA_USERNAME_ID, it) }
                passwordId?.let { putExtra(AutofillAuthActivity.EXTRA_PASSWORD_ID, it) }
            }
        val sender =
            PendingIntent.getActivity(
                this,
                requestCode,
                authIntent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val inlinePresentation =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inlineSpec != null) {
                buildInlinePresentation(label, inlineSpec, sender)
            } else {
                null
            }

        return android.service.autofill.Dataset
            .Builder()
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inlinePresentation != null) {
                    // setValue(AutofillId, AutofillValue, RemoteViews, InlinePresentation) deprecated in API 33
                    @Suppress("DEPRECATION")
                    usernameId?.let { setValue(it, AutofillValue.forText(""), presentation, inlinePresentation) }
                    @Suppress("DEPRECATION")
                    passwordId?.let { setValue(it, AutofillValue.forText(""), presentation, inlinePresentation) }
                } else {
                    // setValue(AutofillId, AutofillValue, RemoteViews) deprecated in API 33; replacement requires minSdk 33
                    @Suppress("DEPRECATION")
                    usernameId?.let { setValue(it, AutofillValue.forText(""), presentation) }
                    @Suppress("DEPRECATION")
                    passwordId?.let { setValue(it, AutofillValue.forText(""), presentation) }
                }
                setAuthentication(sender.intentSender)
            }.build()
    }

    private fun buildSearchDataset(
        usernameId: AutofillId?,
        passwordId: AutofillId?,
        requestCode: Int,
        inlineSpec: InlinePresentationSpec?,
        initialQuery: String,
    ): android.service.autofill.Dataset {
        val label = "Search..."
        val presentation =
            RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                setTextViewText(android.R.id.text1, label)
            }

        val searchIntent =
            Intent(this, AutofillSearchActivity::class.java).apply {
                usernameId?.let { putExtra(AutofillSearchActivity.EXTRA_USERNAME_ID, it) }
                passwordId?.let { putExtra(AutofillSearchActivity.EXTRA_PASSWORD_ID, it) }
                putExtra(AutofillSearchActivity.EXTRA_INITIAL_QUERY, initialQuery)
            }
        val sender =
            PendingIntent.getActivity(
                this,
                requestCode,
                searchIntent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val inlinePresentation =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inlineSpec != null) {
                buildInlinePresentation(label, inlineSpec, sender)
            } else {
                null
            }

        return android.service.autofill.Dataset
            .Builder()
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inlinePresentation != null) {
                    @Suppress("DEPRECATION")
                    usernameId?.let { setValue(it, AutofillValue.forText(""), presentation, inlinePresentation) }
                    @Suppress("DEPRECATION")
                    passwordId?.let { setValue(it, AutofillValue.forText(""), presentation, inlinePresentation) }
                } else {
                    @Suppress("DEPRECATION")
                    usernameId?.let { setValue(it, AutofillValue.forText(""), presentation) }
                    @Suppress("DEPRECATION")
                    passwordId?.let { setValue(it, AutofillValue.forText(""), presentation) }
                }
                setAuthentication(sender.intentSender)
            }.build()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun buildInlinePresentation(
        label: String,
        spec: InlinePresentationSpec,
        authSender: PendingIntent,
    ): InlinePresentation? {
        if (!UiVersions.getVersions(spec.style).contains(UiVersions.INLINE_UI_VERSION_1)) return null
        val attributionIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val slice =
            InlineSuggestionUi
                .newContentBuilder(attributionIntent)
                .setTitle(label)
                .build()
                .slice
        return InlinePresentation(slice, spec, false)
    }

    private fun findWebDomain(structure: AssistStructure): String? {
        for (i in 0 until structure.windowNodeCount) {
            val found = findWebDomainInNode(structure.getWindowNodeAt(i).rootViewNode)
            if (found != null) return found
        }
        return null
    }

    private fun findWebDomainInNode(node: AssistStructure.ViewNode): String? {
        val domain = node.webDomain
        if (!domain.isNullOrBlank()) return domain
        for (i in 0 until node.childCount) {
            val found = findWebDomainInNode(node.getChildAt(i))
            if (found != null) return found
        }
        return null
    }

    private fun findNode(
        structure: AssistStructure,
        predicate: (AssistStructure.ViewNode) -> Boolean,
    ): AssistStructure.ViewNode? {
        for (i in 0 until structure.windowNodeCount) {
            val found = findNodeInTree(structure.getWindowNodeAt(i).rootViewNode, predicate)
            if (found != null) return found
        }
        return null
    }

    private fun findNodeInTree(
        node: AssistStructure.ViewNode,
        predicate: (AssistStructure.ViewNode) -> Boolean,
    ): AssistStructure.ViewNode? {
        if (node.autofillId != null && predicate(node)) return node
        for (i in 0 until node.childCount) {
            val found = findNodeInTree(node.getChildAt(i), predicate)
            if (found != null) return found
        }
        return null
    }

    private fun isPasswordNode(node: AssistStructure.ViewNode): Boolean {
        if (node.autofillHints?.any { it == View.AUTOFILL_HINT_PASSWORD } == true) return true
        val inputType = node.inputType
        return (inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0 ||
            (inputType and InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) != 0 ||
            (inputType and InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) != 0 ||
            node.htmlInfo?.attributes?.any { it.first == "type" && it.second == "password" } == true
    }

    private fun isUsernameNode(node: AssistStructure.ViewNode): Boolean {
        if (node.autofillHints?.any {
                it == View.AUTOFILL_HINT_USERNAME || it == View.AUTOFILL_HINT_EMAIL_ADDRESS
            } == true
        ) {
            return true
        }
        val inputType = node.inputType
        if ((inputType and InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) != 0 ||
            (inputType and InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS) != 0
        ) {
            return true
        }
        val htmlAttrs = node.htmlInfo?.attributes ?: return false
        val typeAttr = htmlAttrs.firstOrNull { it.first == "type" }?.second
        if (typeAttr == "email" || typeAttr == "text") {
            val nameAttr = htmlAttrs.firstOrNull { it.first == "name" || it.first == "id" }?.second?.lowercase()
            return nameAttr != null &&
                (nameAttr.contains("email") || nameAttr.contains("user") || nameAttr.contains("login") || nameAttr.contains("identifier"))
        }
        return false
    }
}
