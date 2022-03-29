package uz.context.intextstoragem6l4

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.core.content.ContextCompat
import uz.context.intextstoragem6l4.databinding.ActivityMainBinding
import java.io.*
import java.nio.charset.Charset
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val isInternal = true
    private var isPersistent = true
    private var readPermissionGranted = false
    private var writePermissionGranted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestPermissions()
        initViews()

    }

    private fun initViews() {
        binding.bCamera.setOnClickListener {
            takePhoto.launch()
        }
        binding.bSaveInt.setOnClickListener {
            saveInternalFile("Pdp Internal")
        }
        binding.bReadInt.setOnClickListener {
            readInternalFile()
        }
        binding.bDeleteInt.setOnClickListener {
            deleteInternalFile()
        }
        binding.bSaveExt.setOnClickListener {
            saveExternalFile("Pdp External")
        }
        binding.bReadExt.setOnClickListener {
            readExternalFile()
        }
        binding.bDeleteExt.setOnClickListener {
            deleteExternalFile()
        }
    }

    private fun requestPermissions() {
        val hasReadPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        val hasWritePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        val minSdk29 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        readPermissionGranted = hasReadPermission
        writePermissionGranted = hasWritePermission || minSdk29

        val permissionToRequest = mutableListOf<String>()
        if (!readPermissionGranted)
            permissionToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (!writePermissionGranted)
            permissionToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)

        if (permissionToRequest.isNotEmpty())
            permissionLauncher.launch(permissionToRequest.toTypedArray())
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permission ->
            readPermissionGranted =
                permission[Manifest.permission.READ_EXTERNAL_STORAGE] ?: readPermissionGranted
            writePermissionGranted =
                permission[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: writePermissionGranted

            if (readPermissionGranted) toast("Read external storage")
            if (writePermissionGranted) toast("Write external storage")
        }

    private val takePhoto =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            val fileName = UUID.randomUUID().toString()

            val isPhotoSaved = if (isInternal) {
                savePhotoToInternalStorage(fileName, bitmap!!)
            } else {
                if (writePermissionGranted) {
                    savePhotoToExternalStorage(fileName, bitmap!!)
                } else {
                    false
                }
            }
            if (isPhotoSaved) {
                toast("Photo saved successfully")
            } else {
                toast("Failed to save photo")
            }
        }

    private fun savePhotoToInternalStorage(fileName: String, bmp: Bitmap): Boolean {
        return try {
            openFileOutput("$fileName.jpg", MODE_PRIVATE).use { stream ->
                if (!bmp.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                    throw IOException("Couldn't save bitmap!")
                }
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    private fun savePhotoToExternalStorage(fileName: String, bmp: Bitmap): Boolean {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.WIDTH, bmp.width)
            put(MediaStore.Images.Media.HEIGHT, bmp.height)
        }
        return try {
            contentResolver.insert(collection, contentValues)?.also { uri ->
                contentResolver.openOutputStream(uri).use { outputStream ->
                    if (!bmp.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)) {
                        throw IOException("Couldn't save bitmap")
                    }
                }
            } ?: throw IOException("Couldn't create MediaStore entry")
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }


    private fun loadPhotosFromExternalStorage(): List<Uri> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
        )
        val photos = mutableListOf<Uri>()
        return contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val displayNameColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val displayName = cursor.getString(displayNameColumn)
                val width = cursor.getInt(widthColumn)
                val height = cursor.getInt(heightColumn)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                photos.add(contentUri)
            }
            photos.toList()
        } ?: listOf()
    }

    private fun loadPhotosFromInternalStorage(): List<Bitmap> {
        val files = filesDir.listFiles()
        return files?.filter { it.canRead() && it.isFile && it.name.endsWith(".jpg") }?.map {
            val bytes = it.readBytes()
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            bmp
        } ?: listOf()
    }

    private fun checkStoragePaths() {
        val internal_m1 = getDir("custom", 0)
        val internal_m2 = filesDir

        val external_m1 = getExternalFilesDir(null)
        val external_m2 = externalCacheDir
        val external_m3 = getExternalFilesDir(Environment.DIRECTORY_PICTURES)

        Log.d("StorageActivity ", internal_m1.absolutePath)
        Log.d("StorageActivity ", internal_m2.absolutePath)
        Log.d("StorageActivity", external_m1!!.absolutePath)
        Log.d("StorageActivity ", external_m2!!.absolutePath)
        Log.d("StorageActivity ", external_m3!!.absolutePath)
    }

    private fun createInternalFile() {
        val fileName = "pdp_internal.txt"
        val file: File = if (isPersistent) {
            File(filesDir, fileName)
        } else {
            File(cacheDir, fileName)
        }
        if (!file.exists()) {
            try {
                file.createNewFile()
                toast("File %s has been created $fileName")
            } catch (e: IOException) {
                toast("File %s creation failed $fileName")
            }
        } else {
            toast("File %s already exists $fileName")
        }
    }

    private fun saveInternalFile(data: String) {
        val fileName = "pdp_internal.txt"
        try {
            val fileOutputStream: FileOutputStream = if (isPersistent) {
                openFileOutput(fileName, MODE_PRIVATE)
            } else {
                val file = File(cacheDir, fileName)
                FileOutputStream(file)
            }
            fileOutputStream.write(data.toByteArray(Charset.forName("UTF-8")))
            toast("Write to %s successful $fileName")
        } catch (e: Exception) {
            e.printStackTrace()
            toast("Write to file %s failed $fileName")
        }
    }

    private fun readInternalFile() {
        val fileName = "pdp_internal.txt"
        try {
            val fileInputStream: FileInputStream = if (isPersistent) {
                openFileInput(fileName)
            } else {
                val file = File(cacheDir, fileName)
                FileInputStream(file)
            }
            val inputStreamReader = InputStreamReader(
                fileInputStream,
                Charset.forName("UTF-8")
            )
            val lines: MutableList<String?> = ArrayList()
            val reader = BufferedReader(inputStreamReader)
            var line = reader.readLine()
            while (line != null) {
                lines.add(line)
                line = reader.readLine()
            }
            val readText = TextUtils.join("\n", lines)
            toast("Read from file %s successful $fileName")
        } catch (e: Exception) {
            toast("Read from file %s failed $fileName")
        }
    }

    private fun deleteInternalFile() {
        val fileName = "pdp_internal.txt"
        val file: File = if (isPersistent) {
            File(filesDir, fileName)
        } else {
            File(cacheDir, fileName)
        }
        if (file.exists()) {
            file.delete()
            toast("File %s has been deleted $fileName")
        } else {
            toast("File %s doesn't exist $fileName")
        }
    }

    private fun createExternalFile() {
        val fileName = "pdp_external.txt"
        val file: File = if (isPersistent) {
            File(getExternalFilesDir(null), fileName)
        } else {
            File(externalCacheDir, fileName)
        }
        if (!file.exists()) {
            try {
                file.createNewFile()
                toast("File %s has been created $fileName")
            } catch (e: IOException) {
                toast("File %s creation failed $fileName")
            }
        } else {
            toast("File %s already exists $fileName")
        }
    }

    private fun saveExternalFile(data: String) {
        val fileName = "pdp_external.txt"

        val file: File = if (isPersistent) {
            File(getExternalFilesDir(null), fileName)
        } else {
            File(externalCacheDir, fileName)
        }
        try {
            val fileOutputStream = FileOutputStream(file)
            fileOutputStream.write(data.toByteArray(Charset.forName("UTF-8")))
            toast("Write to %s successful $fileName")
        } catch (e: Exception) {
            e.printStackTrace()
            toast("Write to file %s failed $fileName")
        }
    }

    private fun readExternalFile() {
        val fileName = "pdp_external.txt"
        val file: File = if (isPersistent)
            File(getExternalFilesDir(null), fileName)
        else
            File(externalCacheDir, fileName)

        try {
            val fileInputStream = FileInputStream(file)
            val inputStreamReader = InputStreamReader(fileInputStream, Charset.forName("UTF-8"))
            val lines: MutableList<String?> = ArrayList()
            val reader = BufferedReader(inputStreamReader)
            var line = reader.readLine()
            while (line != null) {
                lines.add(line)
                line = reader.readLine()
            }
            val readText = TextUtils.join("\n", lines)
            toast("Rear from file %s successful $fileName")
        } catch (e: Exception) {
            e.printStackTrace()
            toast("Read from file %s failed $fileName")
        }
    }

    private fun deleteExternalFile() {
        val fileName = "pdp_external.txt"
        val file: File = if (isPersistent) {
            File(getExternalFilesDir(null), fileName)
        } else {
            File(externalCacheDir, fileName)
        }
        if (file.exists()) {
            file.delete()
            toast("File %s has been deleted $fileName")
        } else {
            toast("File %s doesn't exist $fileName")
        }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}