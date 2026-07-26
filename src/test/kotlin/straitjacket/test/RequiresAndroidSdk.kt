package straitjacket.test

import kotlin.test.fail
import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ConditionEvaluationResult.disabled
import org.junit.jupiter.api.extension.ConditionEvaluationResult.enabled
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ExtendWith(AndroidSdkCondition::class)
internal annotation class RequiresAndroidSdk

private class AndroidSdkCondition : ExecutionCondition {
  override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult =
    when {
      ANDROID_SDK.exists() && ANDROID_SDK.isDirectory -> enabled("Android SDK at $ANDROID_SDK")
      CI -> fail("No Android SDK at '$ANDROID_SDK' on CI, set ANDROID_HOME")
      else -> disabled("No Android SDK found, set ANDROID_HOME")
    }
}
