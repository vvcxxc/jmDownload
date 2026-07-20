package com.jmdl.data

enum class TaskStatus { QUEUED, RUNNING, ZIPPING, DONE, FAILED }

data class Task(
    val id: String,               // unique task id (uuid)
    val jmId: String,             // JM album id
    val title: String? = null,    // resolved album title (once known)
    val status: TaskStatus = TaskStatus.QUEUED,
    val done: Int = 0,            // images downloaded so far
    val total: Int = 0,          // total images (0 = unknown yet)
    val message: String? = null,  // error text / completion note
    val zipName: String? = null,  // final zip file name once saved
    val saveTreeUri: String? = null, // SAF folder this task's zip was saved to (null = default Downloads)
    val fileUri: String? = null,  // content Uri of the saved zip (MediaStore or SAF document)
    val createdAt: Long = 0L,
)
