package org.rogbid.bridge

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log

/** Diagnostic only: proves whether the OS can wake our app through JobScheduler. */
class BootDiagnosticJob : JobService() {
 override fun onStartJob(params: JobParameters): Boolean {
  Log.i("RogbidJob", "JobScheduler started app process; jobId=${params.jobId}")
  return false
 }
 override fun onStopJob(params: JobParameters): Boolean {
  Log.i("RogbidJob", "JobScheduler stopped diagnostic job; jobId=${params.jobId}")
  return false
 }

 companion object {
  private const val JOB_ID = 7301

  fun schedule(context: Context) {
   try {
    val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
    val builder = JobInfo.Builder(JOB_ID, ComponentName(context, BootDiagnosticJob::class.java))
     .setPeriodic(15 * 60 * 1000L)
     .setPersisted(true)
    val result = scheduler.schedule(builder.build())
    Log.i("RogbidJob", "Scheduled persisted 15-minute diagnostic job: result=$result")
   } catch (e: Exception) {
    Log.e("RogbidJob", "Failed to schedule diagnostic job", e)
   }
  }
 }
}
