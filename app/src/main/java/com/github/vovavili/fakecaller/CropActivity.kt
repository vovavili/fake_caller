package com.github.vovavili.fakecaller

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.canhub.cropper.CropImageView

class CropActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop)

        val cropImageView = findViewById<CropImageView>(R.id.cropImageView)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnCancel = findViewById<Button>(R.id.btnCancel)

        // 1. Load the image passed from MainActivity
        val sourceUri = intent.data
        if (sourceUri != null) {
            cropImageView.setImageUriAsync(sourceUri)
        }

        // 2. Button Logic
        btnCancel.setOnClickListener { finish() }

        btnSave.setOnClickListener {
            // Trigger the crop
            cropImageView.croppedImageAsync()
        }

        // 3. Listen for Crop Result
        cropImageView.setOnCropImageCompleteListener { _, result ->
            if (result.isSuccessful) {
                // Send the cropped image back to Main
                val resultIntent = Intent()
                resultIntent.data = result.uriContent
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            } else {
                // Handle error if needed
            }
        }
    }
}