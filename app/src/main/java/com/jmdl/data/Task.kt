package com.jmdl.data

enum class TaskStatus { QUEUED, RUNNING, ZIPPING, DONE, FAILED, PAUSED }

data class Task(
    val id: String,               // unique task id (uuid)
    val jmId: String,             // JM album id
    val title: String? = null,    // resolved album title (once known)
    val authors: List<String> = emptyList(), // resolved album authors (once known)
    val status: TaskStatus = TaskStatus.QUEUED,
    val done: Int = 0,            // images downloaded so far
    val total: Int = 0,          // total images (0 = unknown yet)
    val failed: Int = 0,         // images that failed (DONE with failed>0 = partial)
    val message: String? = null,  // error text / completion note
    val zipName: String? = null,  // final zip file name once saved
    val saveTreeUri: String? = null, // SAF folder this task's zip was saved to (null = default Downloads)
    val fileUri: String? = null,  // content Uri of the saved zip (MediaStore or SAF document)
    val createdAt: Long = 0L,
    val finishedAt: Long? = null, // completion or failure time
    val currentPage: Int? = null, // last page the user read in the viewer
    val pageCount: Int? = null,   // total pages in the saved zip
)