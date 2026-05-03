package com.example.pass.autofill

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.example.pass.passstore.PassEntry
import com.example.pass.passstore.PassStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PassDroidAutofillService : AutofillService() {

    @Inject lateinit var passStore: PassStore

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback,
    ) {
        val context = request.fillContexts.lastOrNull() ?: run {
            callback.onSuccess(null)
            return
        }

        val structure = context.structure
        val packageName = structure.activityComponent.packageName

        val webDomain = findWebDomain(structure)
        val candidates = if (webDomain != null) passStore.resolve(webDomain)
                         else passStore.resolveByPackage(packageName)

        if (candidates.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        val usernameNode = findNode(structure, ::isUsernameNode)
        val passwordNode = findNode(structure, ::isPasswordNode)

        if (usernameNode == null && passwordNode == null) {
            callback.onSuccess(null)
            return
        }

        val datasets = candidates.mapIndexed { i, entry ->
            buildLockedDataset(entry, usernameNode?.autofillId, passwordNode?.autofillId, i)
        }

        val response = FillResponse.Builder()
            .apply { datasets.forEach { addDataset(it) } }
            .build()
        callback.onSuccess(response)
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

    private fun buildLockedDataset(
        entry: PassEntry,
        usernameId: AutofillId?,
        passwordId: AutofillId?,
        requestCode: Int,
    ): android.service.autofill.Dataset {
        val label = "${entry.username}${entry.domain?.let { " ($it)" } ?: ""}"
        val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1, label)
        }

        val authIntent = Intent(this, AutofillAuthActivity::class.java).apply {
            putExtra(AutofillAuthActivity.EXTRA_ENTRY_PATH, entry.path)
            usernameId?.let { putExtra(AutofillAuthActivity.EXTRA_USERNAME_ID, it) }
            passwordId?.let { putExtra(AutofillAuthActivity.EXTRA_PASSWORD_ID, it) }
        }
        val sender = PendingIntent.getActivity(
            this,
            requestCode,
            authIntent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // setValue(AutofillId, AutofillValue, RemoteViews) deprecated in API 33; replacement requires minSdk 33
        @Suppress("DEPRECATION")
        return android.service.autofill.Dataset.Builder().apply {
            usernameId?.let { setValue(it, AutofillValue.forText(""), presentation) }
            passwordId?.let { setValue(it, AutofillValue.forText(""), presentation) }
            setAuthentication(sender.intentSender)
        }.build()
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
        } == true) return true
        val inputType = node.inputType
        if ((inputType and InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) != 0 ||
            (inputType and InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS) != 0) return true
        val htmlAttrs = node.htmlInfo?.attributes ?: return false
        val typeAttr = htmlAttrs.firstOrNull { it.first == "type" }?.second
        if (typeAttr == "email" || typeAttr == "text") {
            val nameAttr = htmlAttrs.firstOrNull { it.first == "name" || it.first == "id" }?.second?.lowercase()
            return nameAttr != null && (nameAttr.contains("email") || nameAttr.contains("user") || nameAttr.contains("login") || nameAttr.contains("identifier"))
        }
        return false
    }
}
