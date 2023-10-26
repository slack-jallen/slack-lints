// Copyright (C) 2021 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
package slack.lint.text

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UastCallVisitor
import com.android.tools.lint.detector.api.UastLintUtils
import com.android.tools.lint.detector.api.interprocedural.getTarget
import com.intellij.openapi.command.executeCommand
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UPrefixExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.UUnaryExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getContainingUFile
import org.jetbrains.uast.tryResolve
import slack.lint.text.SpanMarkPointMissingMaskDetector.Companion.ISSUE
import slack.lint.util.resolveQualifiedNameOrNull
import slack.lint.util.sourceImplementation
import java.util.EnumSet
import java.util.regex.Pattern

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

  override fun createUastHandler(context: JavaContext): UElementHandler = ReportingHandler(context)


}

/** Reports violations of SpanMarkPointMissingMask. */
private class ReportingHandler(private val context: JavaContext) : UElementHandler() {
  override fun visitFile(file: UFile) {
      println("Checking ${file.sourcePsi.virtualFile?.presentableUrl}")
    file.accept(object: UastCallVisitor() {
      override fun visitCall(node: UCallExpression): Boolean {
        if (node.methodName == "returns") {
            val escapedText = escape(node.sourcePsi!!.text)
            val escapedFirstArg: String
            val escapedSecondArg: String
            if (node.valueArguments.size == 2) {
                escapedFirstArg = escape(node.valueArguments[0].sourcePsi!!.text)
                escapedSecondArg = escape(node.valueArguments[1].sourcePsi!!.text)

            } else {
                escapedFirstArg = escape(node.receiver!!.sourcePsi!!.text)
                escapedSecondArg = escape(node.valueArguments[0].sourcePsi!!.text)
            }
            // perl multiline replace from https://unix.stackexchange.com/a/26289
            val args = arrayOf(
                "perl",
                "-0777",
                "-i",
                "-pe",
                "s/$escapedText/whenever\\($escapedFirstArg\\).thenReturn\\($escapedSecondArg\\)/igs",
                node.sourcePsi!!.containingFile.virtualFile.presentableUrl
            )
            println(args.joinToString(separator = " ") { "\"$it\"" })
            Runtime.getRuntime().exec(args)
          return true
        }
        return false
      }
    })
      // node.uastParent?.sourcePsi ${node.uastParent?.sourcePsi?.text}
  }

    fun escape(input: String): String {
        val specialCharacters = listOf("\\", "{", "}", "(", ")", "[", "]", ".", "+", "*", "?", "^", "$", "|", "\'", "\"")
        var escaped = input
        for (char in specialCharacters) {
            escaped = escaped.replace(char, "\\$char")
        }
        escaped = escaped.replace("\n", "\\n")
        return escaped
    }
}
