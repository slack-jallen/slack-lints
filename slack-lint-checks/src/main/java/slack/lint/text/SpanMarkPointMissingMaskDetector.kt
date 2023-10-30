// Copyright (C) 2021 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
package slack.lint.text

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UastCallVisitor
import com.intellij.openapi.util.TextRange
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.textRange
import slack.lint.text.SpanMarkPointMissingMaskDetector.Companion.ISSUE
import slack.lint.util.PackageName
import slack.lint.util.isInPackageName
import slack.lint.util.sourceImplementation
import java.io.File

/** Checks for SpanMarkPointMissingMask. See [ISSUE]. */
class SpanMarkPointMissingMaskDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE =
            Issue.create(
                id = "SpanMarkPointMissingMask",
                briefDescription =
                "Check that Span flags use the bitwise mask SPAN_POINT_MARK_MASK when being compared to.",
                explanation =
                """
        Spans flags can have priority or other bits set. \
        Ensure that Span flags are checked using \
        `currentFlag and Spanned.SPAN_POINT_MARK_MASK == desiredFlag` \
        rather than just `currentFlag == desiredFlag`
      """,
                category = Category.CORRECTNESS,
                priority = 4,
                severity = Severity.ERROR,
                implementation = sourceImplementation<SpanMarkPointMissingMaskDetector>()
            )
    }

    override fun getApplicableUastTypes() = listOf(UFile::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        ReportingHandler(context)


}

/** Reports violations of SpanMarkPointMissingMask. */
private class ReportingHandler(private val context: JavaContext) : UElementHandler() {
    data class Change(val range: TextRange, val newContent: String)

    override fun visitFile(file: UFile) {
        println("Checking ${file.sourcePsi.virtualFile?.presentableUrl}")
        var changes: MutableList<Change>? = mutableListOf<Change>()
        file.accept(object : UastCallVisitor() {
            override fun visitCall(node: UCallExpression): Boolean {
                if (node.methodName == "returns" && node.resolve()?.isInPackageName(
                        PackageName(listOf("slack", "test", "mockito"))
                    ) == true) {
                    if (node.valueArguments.size == 2) {
                        val fistArg = node.valueArguments[0].sourcePsi!!.text
                        val secondArg = if (node.valueArguments[1] is ULiteralExpression) {
                            node.valueArguments[1].asRenderString()
                        } else {
                            node.valueArguments[1].sourcePsi!!.text
                        }
                        changes?.add(
                            Change(
                                node.textRange!!,
                                "whenever($fistArg).thenReturn($secondArg)"
                            )
                        )
                    } else {
                        if (node.receiver == null) {
                            println("Missing receiver: Parent: " + node.uastParent?.sourcePsi?.text + " node " + node.sourcePsi?.text)
                            changes = null
                        } else {
                            val fistArg = node.receiver!!.sourcePsi!!.text
                            val secondArg = node.valueArguments[0].sourcePsi!!.text
                            changes?.add(
                                Change(
                                    node.uastParent!!.textRange!!,
                                    "whenever($fistArg).thenReturn($secondArg)"
                                )
                            )
                        }
                    }
                    return true
                }
                return false
            }
        })
        changes?.let { changesSnapshot ->
            if (changesSnapshot.isNotEmpty()) {
                println("Modifying ${file.sourcePsi.virtualFile?.presentableUrl} ${changesSnapshot.size}x")
                changesSnapshot.sortByDescending { it.range.startOffset }
                val fileLocation = File(file.sourcePsi.containingFile.virtualFile.presentableUrl)
                var fileText = fileLocation.readText()
                for (change in changesSnapshot) {
                    fileText = fileText.replaceRange(
                        change.range.startOffset,
                        change.range.endOffset,
                        change.newContent
                    )
                }
                fileText = fileText.replace("import slack.test.mockito.whenever\n", "")
                fileText = fileText.replace("import slack.test.mockito.returns\n", "import slack.test.mockito.whenever\n")
                fileLocation.writeText(fileText)
            }
        }

    }
}
