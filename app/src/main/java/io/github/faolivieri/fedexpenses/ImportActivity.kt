package io.github.faolivieri.fedexpenses

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.faolivieri.fedexpenses.ui.ShareActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Invisible entry point for share files opened or shared from other apps. Reads the file, opens the review
 * screen in the app's own task, and finishes, leaving the other app's task as it was.
 */
class ImportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { ShareActions.fromIntent(this@ImportActivity, intent) }
            result?.onSuccess {
                container.incomingShare.value = it
                startActivity(
                    Intent(this@ImportActivity, MainActivity::class.java)
                        .setAction(MainActivity.ACTION_RECEIVE)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }?.onFailure {
                Toast.makeText(this@ImportActivity, it.message ?: "Not a Fedexpenses share", Toast.LENGTH_LONG).show()
            }
            finish()
        }
    }
}
