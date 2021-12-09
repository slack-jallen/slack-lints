/*
 * Copyright (C) 2021 Slack Technologies, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package slack.lint.text

import org.intellij.lang.annotations.Language
import org.junit.Test
import slack.lint.BaseSlackLintTest

/**
 * Tests [SpanPointMarkDangerousCheckDetector].
 */
class SpanPointMarkDangerousCheckDetectorTest : BaseSlackLintTest() {

  override fun getDetector() = SpanPointMarkDangerousCheckDetector()
  override fun getIssues() = listOf(SpanPointMarkDangerousCheckDetector.ISSUE)

  private val androidTextStubs = kotlin(
    """
        package android.text

        object Spanned {
            const val SPAN_INCLUSIVE_INCLUSIVE = 1
            const val SPAN_INCLUSIVE_EXCLUSIVE = 2
            const val SPAN_EXCLUSIVE_INCLUSIVE = 3
            const val SPAN_EXCLUSIVE_EXCLUSIVE = 4
            const val SPAN_POINT_MARK_MASK = 0xFF
        }
      """
  ).indented()

  @Test
  fun `visitBinaryExpression - given conforming expression - has clean report`() {
    @Language("kotlin")
    val testFile = kotlin(
      """
                package slack.text

              import android.text.Spanned

              class MyClass {
                  fun doCheckCorrectly(spanned: Spanned): Boolean {
                    return spanned.getSpanFlags(Object()) and Spanned.SPAN_POINT_MARK_MASK == Spanned.SPAN_INCLUSIVE_INCLUSIVE
                  }
              }
            """
    ).indented()
    lint()
      .files(androidTextStubs, testFile)
      .issues(SpanPointMarkDangerousCheckDetector.ISSUE)
      .run()
      .expectClean()
  }

  @Test
  fun `visitBinaryExpression - given violating expression with INCLUSIVE_INCLUSIVE on left - creates error and fix`() {
    testViolatingExpressionLeft("Spanned.SPAN_INCLUSIVE_INCLUSIVE")
  }

  @Test
  fun `visitBinaryExpression - given violating expression with INCLUSIVE_EXCLUSIVE on left - creates error and fix`() {
    testViolatingExpressionLeft("Spanned.SPAN_INCLUSIVE_EXCLUSIVE")
  }

  @Test
  fun `visitBinaryExpression - given violating expression with EXCLUSIVE_INCLUSIVE on left - creates error and fix`() {
    testViolatingExpressionLeft("Spanned.SPAN_EXCLUSIVE_INCLUSIVE")
  }

  @Test
  fun `visitBinaryExpression - given violating expression with EXCLUSIVE_EXCLUSIVE on left - creates error and fix`() {
    testViolatingExpressionLeft("Spanned.SPAN_EXCLUSIVE_EXCLUSIVE")
  }

  private fun testViolatingExpressionLeft(markPoint: String) {
    @Language("kotlin")
    val testFile = kotlin(
      """
                package slack.text

              import android.text.Spanned

              class MyClass {
                  fun doCheckIncorrectly(spanned: Spanned): Boolean {
                    return spanned.getSpanFlags(Object()) == $markPoint || Spanned.x()
                  }
              }
            """
    ).indented()
    lint()
      .files(androidTextStubs, testFile)
      .issues(SpanPointMarkDangerousCheckDetector.ISSUE)
      .run()
      .expect(
        """
          src/slack/text/MyClass.kt:7: Error: Do not check against $markPoint directly. Instead mask flag with Spanned.SPAN_POINT_MARK_MASK to only check MARK_POINT flags. [SpanPointMarkDangerousCheck]
                return spanned.getSpanFlags(Object()) == $markPoint || Spanned.x()
                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          1 errors, 0 warnings
        """.trimIndent()
      )
      .expectFixDiffs(
        """
          Fix for src/slack/text/MyClass.kt line 7: Use bitwise mask:
          @@ -7 +7
          -       return spanned.getSpanFlags(Object()) == $markPoint || Spanned.x()
          +       return ((spanned.getSpanFlags(Object())) and android.text.Spanned.SPAN_POINT_MARK_MASK) == $markPoint || Spanned.x()
        """.trimIndent()
      )
  }

  @Test
  fun `visitBinaryExpression - given violating expression with INCLUSIVE_INCLUSIVE on right - creates error and fix`() {
    testViolatingExpressionRight("SPAN_INCLUSIVE_INCLUSIVE")
  }

  @Test
  fun `visitBinaryExpression - given violating expression with INCLUSIVE_EXCLUSIVE on right - creates error and fix`() {
    testViolatingExpressionRight("SPAN_INCLUSIVE_EXCLUSIVE")
  }

  @Test
  fun `visitBinaryExpression - given violating expression with EXCLUSIVE_INCLUSIVE on right - creates error and fix`() {
    testViolatingExpressionRight("SPAN_EXCLUSIVE_INCLUSIVE")
  }

  @Test
  fun `visitBinaryExpression - given violating expression with EXCLUSIVE_EXCLUSIVE on right - creates error and fix`() {
    testViolatingExpressionRight("SPAN_EXCLUSIVE_EXCLUSIVE")
  }

  private fun testViolatingExpressionRight(markPoint: String) {
    @Language("kotlin")
    val testFile = kotlin(
      """
                package slack.text

              import android.text.Spanned.$markPoint

              class MyClass {
                  fun doCheckIncorrectly(spanned: Spanned): Boolean {
                    return $markPoint == spanned.getSpanFlags(Object()) || isBoolean1() && isBoolean2()
                  }
              }
            """
    ).indented()
    lint()
      .files(androidTextStubs, testFile)
      .issues(SpanPointMarkDangerousCheckDetector.ISSUE)
      .run()
      .expect(
        """
          src/slack/text/MyClass.kt:7: Error: Do not check against $markPoint directly. Instead mask flag with Spanned.SPAN_POINT_MARK_MASK to only check MARK_POINT flags. [SpanPointMarkDangerousCheck]
                return $markPoint == spanned.getSpanFlags(Object()) || isBoolean1() && isBoolean2()
                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          1 errors, 0 warnings
        """.trimIndent()
      )
      .expectFixDiffs(
        """
          Fix for src/slack/text/MyClass.kt line 7: Use bitwise mask:
          @@ -7 +7
          -       return $markPoint == spanned.getSpanFlags(Object()) || isBoolean1() && isBoolean2()
          +       return $markPoint == ((spanned.getSpanFlags(Object())) and android.text.Spanned.SPAN_POINT_MARK_MASK) || isBoolean1() && isBoolean2()
        """.trimIndent()
      )
  }
}